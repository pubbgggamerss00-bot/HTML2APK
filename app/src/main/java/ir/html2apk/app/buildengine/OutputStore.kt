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

    data class SavedOutput(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val inMediaStore: Boolean,
        val localFile: File?,
    )

    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "-").trim()
        return if (cleaned.isBlank()) "app.apk" else cleaned
    }

    fun saveApk(context: Context, apk: ByteArray, suggestedName: String): SavedOutput {
        val safeName = sanitizeFileName(suggestedName)

        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, APK_MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values)
                ?: throw IOException("ثبت فایل خروجی در Downloads ناموفق بود")
            resolver.openOutputStream(itemUri)?.use { it.write(apk) }
                ?: throw IOException("نوشتن فایل خروجی ناموفق بود")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(itemUri, done, null, null)
            return SavedOutput(itemUri, safeName, apk.size.toLong(), inMediaStore = true, localFile = null)
        }

        // Android 8/9: app-specific external directory (no permission required)
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Download")
        dir.mkdirs()
        val file = File(dir, safeName)
        file.writeBytes(apk)
        return SavedOutput(
            uri = Uri.fromFile(file),
            displayName = safeName,
            sizeBytes = apk.size.toLong(),
            inMediaStore = false,
            localFile = file,
        )
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
