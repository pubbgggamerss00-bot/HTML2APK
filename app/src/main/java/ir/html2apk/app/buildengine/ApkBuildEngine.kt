package ir.html2apk.app.buildengine

import android.content.Context
import ir.html2apk.app.model.BuildRequest
import ir.html2apk.app.model.BuildStage
import ir.html2apk.app.model.LogType
import ir.html2apk.app.model.Orientation
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
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
 *  0. PREPARE    — stream input to a temp file, extract ZIP (Zip-Slip hardened), locate index.html
 *  1. RESOURCES  — decode template APK, process the optional launcher icon
 *  2. ASSEMBLE   — patch binary manifest + resources.arsc, rebuild a STORED+aligned ZIP (streamed)
 *  3. SIGN       — load/create keystore, sign with v1+v2+v3 (apksig, file-to-file)
 *  4. SAVE       — publish through MediaStore (Downloads) or app-specific storage (streamed)
 *
 * Memory policy: the user's web content is never held fully in RAM. Every large
 * transfer (input copy, ZIP extraction, APK assembly, signing, saving) is a stream —
 * peak heap stays constant no matter how big the input is. This is what keeps the
 * builder from being killed (OOM crash) on devices with a small heap.
 */
class ApkBuildEngine(private val context: Context) {

    companion object {
        /** Hard cap for the *input* file. Mirrors ZipUtils total cap for archives. */
        const val MAX_INPUT_BYTES: Long = 200L * 1024 * 1024
        private const val COPY_BUF = 64 * 1024
    }

    suspend fun build(request: BuildRequest, sink: BuildSink): OutputStore.SavedOutput {
        val tempRoot = File(context.cacheDir, "html2apk-build-${System.currentTimeMillis()}")
        try {
            val stagingAssets = File(tempRoot, "web")
            stagingAssets.mkdirs()

            // ---------------- Stage 0: PREPARE ----------------
            sink.onStageStart(BuildStage.PREPARE)
            val t0 = System.currentTimeMillis()
            sink.log(LogType.CMD, "mkdir ${tempRoot.name}")

            val inputFile = File(tempRoot, "input.bin")
            copyInputBounded(context.contentResolver.openInputStream(request.inputUri), inputFile)
            sink.log(LogType.INFO, "input: ${request.inputName} (${formatSize(inputFile.length())})")

            val webRoot: File = if (request.isZip) {
                sink.log(LogType.CMD, "unzip \"${request.inputName}\" -> web/")
                val files = inputFile.inputStream().use { ZipUtils.extract(it, stagingAssets) }
                sink.log(LogType.INFO, "${files.size} فایل استخراج شد (${formatSize(inputFile.length())})")
                val root = ZipUtils.findRootWithIndexHtml(stagingAssets)
                    ?: throw IllegalArgumentException(
                        "فایل index.html در ریشهٔ ZIP پیدا نشد (یا در یک پوشهٔ تکی قرار نگرفت)"
                    )
                sink.log(LogType.OK, "index.html found: ${root.relativeTo(tempRoot).path}/index.html")
                root
            } else {
                val asIndex = File(stagingAssets, "index.html")
                inputFile.copyTo(asIndex, overwrite = true)
                sink.log(LogType.OK, "index.html آماده شد (${formatSize(asIndex.length())})")
                stagingAssets
            }
            coroutineContext.ensureActive()
            sink.onStageEnd(BuildStage.PREPARE, System.currentTimeMillis() - t0)

            // ---------------- Stage 1: RESOURCES ----------------
            sink.onStageStart(BuildStage.RESOURCES)
            val t1 = System.currentTimeMillis()
            sink.log(LogType.CMD, "open assets://template.apk")
            val templateBytes = try {
                context.assets.open("template.apk").use { it.readBytes() }
            } catch (e: Exception) {
                throw IOException(
                    "قالب APK داخل اپ پیدا نشد (assets/template.apk). " +
                        "اپ را دوباره بسازید یا از build_template.sh استفاده کنید.",
                    e,
                )
            }
            if (templateBytes.isEmpty()) {
                throw IOException("قالب APK خالی است — build_template.sh را اجرا کنید")
            }
            val template = try {
                ApkAssembler.readTemplate(templateBytes)
            } catch (e: Exception) {
                throw IOException("خواندن قالب APK ناموفق بود: ${e.message}", e)
            }
            sink.log(
                LogType.INFO,
                "template: ${template.entries.size} entries, icons=${template.iconEntryPaths.size}"
            )

            val iconReplacements = if (request.iconUri != null) {
                sink.log(LogType.CMD, "bitmap decode + center-crop + resize (48..192px)")
                val icons = try {
                    IconProcessor.process(context, request.iconUri, template.iconEntryPaths)
                } catch (e: Exception) {
                    throw IOException("پردازش آیکون ناموفق بود: ${e.message}", e)
                }
                sink.log(LogType.OK, "آیکون برای ${icons.size} تراکم تولید شد (mdpi..xxxhdpi)")
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
            val manifestBytes = try {
                AXmlEditor.patchManifest(
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
            } catch (e: Exception) {
                throw IOException("پچ مانیفست ناموفق بود: ${e.message}", e)
            }
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

            val arscBytes = try {
                if (template.entries.containsKey("resources.arsc")) {
                    ArscPatcher.patchPackageName(
                        template.entries.getValue("resources.arsc"),
                        request.packageName,
                    )
                } else {
                    throw IOException("قالب فاقد resources.arsc است")
                }
            } catch (e: Exception) {
                throw IOException("پچ resources.arsc ناموفق بود: ${e.message}", e)
            }
            sink.log(LogType.OK, "resources.arsc package rewritten")

            coroutineContext.ensureActive()
            sink.log(LogType.CMD, "zip -r -X -0 (STORED) + zipalign 4")
            val assembledFile = File(tempRoot, "assembled.apk")
            val assembled = try {
                ApkAssembler.assemble(
                    template, manifestBytes, arscBytes, iconReplacements, webRoot, assembledFile
                )
            } catch (e: Exception) {
                throw IOException("مونتاژ APK ناموفق بود: ${e.message}", e)
            }
            sink.log(
                LogType.OK,
                "${assembled.entryCount} entries STORED & 4-byte aligned (${formatSize(assembled.totalBytes)})"
            )
            sink.onStageEnd(BuildStage.ASSEMBLE, System.currentTimeMillis() - t2)

            // ---------------- Stage 3: SIGN ----------------
            sink.onStageStart(BuildStage.SIGN)
            val t3 = System.currentTimeMillis()
            coroutineContext.ensureActive()
            val key = try {
                KeystoreManager.getOrCreate(context, request.keystorePassword, request.keyAlias)
            } catch (e: Exception) {
                throw IOException(
                    "ساخت/بارگذاری کلید امضا ناموفق بود: ${e.message}. " +
                        "گذرواژهٔ keystore را بررسی کنید یا دادهٔ اپ را پاک کنید تا کلید جدید ساخته شود.",
                    e,
                )
            }
            sink.log(
                LogType.OK,
                if (key.createdNew) {
                    "keystore generated (RSA-2048, alias=${key.alias})"
                } else {
                    "keystore loaded from internal storage (alias=${key.alias})"
                }
            )
            sink.log(LogType.CMD, "apksigner sign --v1 --v2 --v3")
            val signedFile = File(tempRoot, "signed.apk")
            ApkSignUtil.sign(assembledFile, signedFile, key)
            sink.log(LogType.OK, "signed: v1 + v2 + v3 (SHA-256: ${key.sha256Fingerprint.take(23)}…)")
            sink.onStageEnd(BuildStage.SIGN, System.currentTimeMillis() - t3)

            // ---------------- Stage 4: SAVE ----------------
            sink.onStageStart(BuildStage.SAVE)
            val t4 = System.currentTimeMillis()
            coroutineContext.ensureActive()
            val outputName = OutputStore.sanitizeFileName(
                "${request.appName}-v${request.versionName}.apk"
            )
            val saved = OutputStore.saveApk(context, signedFile, outputName)
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

    /**
     * Streams the input from the SAF [InputStream] into [target], enforcing a size cap.
     * Streaming (instead of readBytes) keeps peak memory constant — a big input would
     * otherwise raise OutOfMemoryError and hard-crash the app (an Error, not Exception).
     */
    private fun copyInputBounded(input: InputStream?, target: File) {
        if (input == null) throw IOException("خواندن فایل ورودی ناموفق بود — فایل دوباره انتخاب شود")
        try {
            var total = 0L
            FileOutputStream(target).use { out ->
                val buf = ByteArray(COPY_BUF)
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    total += r
                    if (total > MAX_INPUT_BYTES) {
                        throw IOException(
                            "حجم فایل ورودی بیش از حد مجاز است (سقف ${formatSize(MAX_INPUT_BYTES)})"
                        )
                    }
                    out.write(buf, 0, r)
                }
            }
            if (total == 0L) throw IOException("فایل ورودی خالی است")
        } finally {
            input.close()
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
