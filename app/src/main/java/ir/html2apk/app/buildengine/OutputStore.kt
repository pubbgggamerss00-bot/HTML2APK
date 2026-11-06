package ir.html2apk.app.buildengine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Saves build output (MediaStore → Downloads on Android 10+, app external dir before)
 * and provides install / share / open-folder flows via FileProvider — no direct
 * java.io.File writes into shared storage.
 */
object OutputStore {

    const val APK_MIME = "application/vnd.android.package-archive"
    /** Output subfolder inside Downloads (Android 10+ MediaStore) — same layout v3.4.0 used. */
    const val OUTPUT_SUBDIR = "Download/HTML2APK"

    data class SavedOutput(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val inMediaStore: Boolean,
        val localFile: File?,
        val dateAdded: Long = 0L,
    )

    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "-").trim()
        return if (cleaned.isBlank()) "app.apk" else cleaned
    }

    /**
     * Streams the built APK from [apkFile] into MediaStore (Downloads, Android 10+)
     * or app-specific external storage (Android 8/9). Never loads the APK fully in RAM.
     */
    fun saveApk(context: Context, apkFile: File, suggestedName: String): SavedOutput {
        require(apkFile.isFile && apkFile.length() > 0) { "APK تولیدشده خالی است" }
        val safeName = sanitizeFileName(suggestedName)

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, APK_MIME)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, OUTPUT_SUBDIR)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, values)
                    ?: throw IOException("ثبت فایل خروجی در Downloads ناموفق بود")
                try {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        apkFile.inputStream().buffered(256 * 1024).use { it.copyTo(out) }
                    } ?: throw IOException("نوشتن فایل خروجی ناموفق بود")
                } catch (e: Exception) {
                    runCatching { resolver.delete(itemUri, null, null) }
                    throw e
                }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(itemUri, done, null, null)
                return SavedOutput(
                    itemUri, safeName, apkFile.length(),
                    inMediaStore = true, localFile = null,
                    dateAdded = System.currentTimeMillis() / 1000,
                )
            } catch (_: Exception) {
                // Some OEMs / work profiles / restricted profiles reject MediaStore
                // Downloads inserts. Fall through to the app-private path so the
                // user still gets a usable APK (install/share still work via FileProvider).
            }
        }

        // Android 8/9 or MediaStore fallback: app-specific external directory
        // (no storage permission required on modern Android).
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, OUTPUT_SUBDIR)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("ساخت پوشهٔ خروجی ناموفق بود: ${dir.absolutePath}")
        }
        val file = File(dir, safeName)
        apkFile.copyTo(file, overwrite = true)
        return SavedOutput(
            uri = Uri.fromFile(file),
            displayName = safeName,
            sizeBytes = file.length(),
            inMediaStore = false,
            localFile = file,
            dateAdded = System.currentTimeMillis() / 1000,
        )
    }

    /**
     * Lists every APK this app has produced: MediaStore Download/HTML2APK entries
     * (Android 10+) plus the app-specific fallback folder. Used by the «خروجی‌ها» tab.
     */
    fun listOutputs(context: Context): List<SavedOutput> {
        val result = mutableListOf<SavedOutput>()
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                context.contentResolver.query(
                    collection,
                    arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.DATE_ADDED,
                        MediaStore.MediaColumns.RELATIVE_PATH,
                    ),
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(1) ?: continue
                        if (!name.endsWith(".apk", ignoreCase = true)) continue
                        val relPath = (c.getString(4) ?: "").replace('\\', '/')
                        if (!relPath.startsWith("$OUTPUT_SUBDIR/")) continue
                        result += SavedOutput(
                            uri = android.content.ContentUris.withAppendedId(collection, c.getLong(0)),
                            displayName = name,
                            sizeBytes = c.getLong(2),
                            inMediaStore = true,
                            localFile = null,
                            dateAdded = c.getLong(3),
                        )
                    }
                }
            }
        }
        runCatching {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, OUTPUT_SUBDIR)
            dir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }
                ?.forEach { f ->
                    result += SavedOutput(
                        uri = Uri.fromFile(f),
                        displayName = f.name,
                        sizeBytes = f.length(),
                        inMediaStore = false,
                        localFile = f,
                        dateAdded = f.lastModified() / 1000,
                    )
                }
        }
        return result.distinctBy { it.displayName }.sortedByDescending { it.dateAdded }
    }

    /** Copies the produced APK into cacheDir/apk and returns a FileProvider URI. */
    fun apkContentUri(context: Context, output: SavedOutput): Uri {
        val dir = File(context.cacheDir, "apk")
        dir.mkdirs()
        val target = File(dir, output.displayName)
        if (output.localFile != null) {
            output.localFile.copyTo(target, overwrite = true)
        } else {
            context.contentResolver.openInputStream(output.uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: throw IOException("خواندن APK ذخیره‌شده ناموفق بود")
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }

    fun installIntent(context: Context, output: SavedOutput): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkContentUri(context, output), APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    fun shareIntent(context: Context, output: SavedOutput): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType(APK_MIME)
                .putExtra(Intent.EXTRA_STREAM, apkContentUri(context, output))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "اشتراک‌گذاری APK",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openDownloadsFolderIntent(): Intent =
        Intent("android.intent.action.VIEW_DOWNLOADS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Queries display name + size of a SAF document. */
    fun queryFileInfo(context: Context, uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {
        }
        return name to size
    }
}
