package ir.html2apk.app.buildengine

import android.content.Context
import ir.html2apk.app.model.BuildRequest
import ir.html2apk.app.model.BuildStage
import ir.html2apk.app.model.LogType
import ir.html2apk.app.model.Orientation
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Progress callbacks delivered (possibly) from a background thread. */
interface BuildSink {
    fun onStageStart(stage: BuildStage)
    fun onStageEnd(stage: BuildStage, durationMs: Long)
    fun log(type: LogType, message: String)
}

/**
 * The offline HTML/ZIP → signed-APK build engine.
 *
 * Stage pipeline:
 *  0. PREPARE    — copy input, extract ZIP (Zip-Slip hardened), locate index.html
 *  1. RESOURCES  — decode template APK, process the optional launcher icon
 *  2. ASSEMBLE   — patch binary manifest + resources.arsc, rebuild a STORED+aligned ZIP
 *  3. SIGN       — load/create keystore, sign with v1+v2+v3 (apksig)
 *  4. SAVE       — publish through MediaStore (Downloads) or app-specific storage
 */
class ApkBuildEngine(private val context: Context) {

    suspend fun build(request: BuildRequest, sink: BuildSink): OutputStore.SavedOutput {
        val tempRoot = File(context.cacheDir, "html2apk-build-${System.currentTimeMillis()}")
        try {
            val stagingAssets = File(tempRoot, "web")
            stagingAssets.mkdirs()

            // ---------------- Stage 0: PREPARE ----------------
            sink.onStageStart(BuildStage.PREPARE)
            val t0 = System.currentTimeMillis()
            sink.log(LogType.CMD, "mkdir ${tempRoot.name}")

            val inputBytes = context.contentResolver.openInputStream(request.inputUri)?.use {
                it.readBytes()
            } ?: throw IOException("خواندن فایل ورودی ناموفق بود")
            sink.log(LogType.INFO, "input: ${request.inputName} (${formatSize(inputBytes.size)})")

            val webRoot: File = if (request.isZip) {
                sink.log(LogType.CMD, "unzip \"${request.inputName}\" -> web/")
                val files = ZipUtils.extract(ByteArrayInputStream(inputBytes), stagingAssets)
                sink.log(LogType.INFO, "${files.size} فایل استخراج شد (${formatSize(inputBytes.size)})")
                val root = ZipUtils.findRootWithIndexHtml(stagingAssets)
                    ?: throw IllegalArgumentException(
                        "فایل index.html در ریشهٔ ZIP پیدا نشد (یا در یک پوشهٔ تکی قرار نگرفت)"
                    )
                sink.log(LogType.OK, "index.html found: ${root.relativeTo(tempRoot).path}/index.html")
                root
            } else {
                File(stagingAssets, "index.html").writeBytes(inputBytes)
                sink.log(LogType.OK, "index.html آماده شد (${formatSize(inputBytes.size)})")
                stagingAssets
            }
            coroutineContext.ensureActive()
            sink.onStageEnd(BuildStage.PREPARE, System.currentTimeMillis() - t0)

            // ---------------- Stage 1: RESOURCES ----------------
            sink.onStageStart(BuildStage.RESOURCES)
            val t1 = System.currentTimeMillis()
            sink.log(LogType.CMD, "open assets://template.apk")
            val templateBytes = context.assets.open("template.apk").use { it.readBytes() }
            val template = ApkAssembler.readTemplate(templateBytes)
            sink.log(
                LogType.INFO,
                "template: ${template.entries.size} entries, icons=${template.iconEntryPaths.size}"
            )

            val iconReplacements = if (request.iconUri != null) {
                sink.log(LogType.CMD, "bitmap decode + center-crop + resize (48..192px)")
                val icons = IconProcessor.process(context, request.iconUri, template.iconEntryPaths)
                sink.log(LogType.OK, "آیکون برای ${icons.size} تراکم تولید شد (mxpi..xxxhdpi)")
                icons
            } else {
                sink.log(LogType.INFO, "بدون آیکون سفارشی — آیکون پیش‌فرض قالب حفظ می‌شود")
                emptyMap()
            }
            coroutineContext.ensureActive()
            sink.onStageEnd(BuildStage.RESOURCES, System.currentTimeMillis() - t1)

            // ---------------- Stage 2: ASSEMBLE ----------------
            sink.onStageStart(BuildStage.ASSEMBLE)
            val t2 = System.currentTimeMillis()
            sink.log(LogType.CMD, "axml --patch-manifest (package=${request.packageName})")
            val manifestBytes = AXmlEditor.patchManifest(
                template.entries.getValue("AndroidManifest.xml"),
                ManifestPatch(
                    packageName = request.packageName,
                    versionCode = request.versionCode,
                    versionName = request.versionName,
                    label = request.appName,
                    screenOrientation = request.orientation.manifestValue,
                    internetPermission = request.internetPermission,
                ),
            )
            val orientLabel = when (request.orientation) {
                Orientation.PORTRAIT -> "portrait"
                Orientation.LANDSCAPE -> "landscape"
                Orientation.AUTO -> "unspecified"
            }
            sink.log(
                LogType.OK,
                "manifest patched: label=\"${request.appName}\", versionCode=${request.versionCode}, " +
                    "versionName=\"${request.versionName}\", orientation=$orientLabel, " +
                    "INTERNET=${if (request.internetPermission) "keep" else "remove"}"
            )

            val arscBytes = if (template.entries.containsKey("resources.arsc")) {
                ArscPatcher.patchPackageName(template.entries.getValue("resources.arsc"), request.packageName)
            } else template.entries.getValue("resources.arsc") // unreachable for our template
            sink.log(LogType.OK, "resources.arsc package rewritten")

            coroutineContext.ensureActive()
            sink.log(LogType.CMD, "zip -r -X -0 (STORED) + zipalign 4")
            val assembled = ApkAssembler.assemble(template, manifestBytes, arscBytes, iconReplacements, webRoot)
            sink.log(
                LogType.OK,
                "${assembled.entryCount} entries STORED & 4-byte aligned (${formatSize(assembled.totalBytes)})"
            )
            sink.onStageEnd(BuildStage.ASSEMBLE, System.currentTimeMillis() - t2)

            // ---------------- Stage 3: SIGN ----------------
            sink.onStageStart(BuildStage.SIGN)
            val t3 = System.currentTimeMillis()
            coroutineContext.ensureActive()
            val key = KeystoreManager.getOrCreate(context, request.keystorePassword, request.keyAlias)
            sink.log(
                LogType.OK,
                if (key.createdNew) {
                    "keystore generated (RSA-2048, alias=${key.alias})"
                } else {
                    "keystore loaded from internal storage (alias=${key.alias})"
                }
            )
            sink.log(LogType.CMD, "apksigner sign --v1 --v2 --v3")
            val signed = ApkSignUtil.sign(assembled.bytes, key)
            sink.log(LogType.OK, "signed: v1 + v2 + v3 (SHA-256: ${key.sha256Fingerprint.take(23)}…)")
            sink.onStageEnd(BuildStage.SIGN, System.currentTimeMillis() - t3)

            // ---------------- Stage 4: SAVE ----------------
            sink.onStageStart(BuildStage.SAVE)
            val t4 = System.currentTimeMillis()
            coroutineContext.ensureActive()
            val outputName = OutputStore.sanitizeFileName(
                "${request.appName}-v${request.versionName}.apk"
            )
            val saved = OutputStore.saveApk(context, signed, outputName)
            sink.log(
                LogType.OK,
                if (saved.inMediaStore) "saved -> Downloads/${saved.displayName}"
                else "saved -> ${saved.localFile?.absolutePath}"
            )
            sink.onStageEnd(BuildStage.SAVE, System.currentTimeMillis() - t4)

            return saved
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun formatSize(bytes: Int): String = formatSize(bytes.toLong())

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
