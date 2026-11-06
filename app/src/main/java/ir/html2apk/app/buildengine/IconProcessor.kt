package ir.html2apk.app.buildengine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Decodes the user-selected image, center-crops it to a square and rescales it to the
 * exact sizes of the template's mipmap entries (paths are the template's own ZIP entry
 * names — never guessed).
 */
object IconProcessor {

    // density folder -> pixel size
    private val DENSITY_SIZES = listOf(
        "mdpi" to 48,
        "hdpi" to 72,
        "xhdpi" to 96,
        "xxhdpi" to 144,
        "xxxhdpi" to 192,
    )

    /**
     * @param templateIconEntryPaths exact entry paths like "res/mipmap-xxhdpi/ic_launcher.png"
     * @return map of entry path -> PNG bytes
     */
    fun process(
        context: Context,
        iconUri: Uri,
        templateIconEntryPaths: List<String>,
    ): Map<String, ByteArray> {
        val square = decodeCenterSquare(context, iconUri, target = 256)
            ?: throw IllegalArgumentException("فرمت تصویر آیکون پشتیبانی نمی‌شود")

        val result = mutableMapOf<String, ByteArray>()
        for ((density, px) in DENSITY_SIZES) {
            val entry = templateIconEntryPaths.firstOrNull { it == "res/mipmap-$density/ic_launcher.png" }
                ?: continue
            val scaled = Bitmap.createScaledBitmap(square, px, px, true)
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, bos)
            result[entry] = bos.toByteArray()
        }
        if (result.isEmpty()) {
            throw IllegalArgumentException("قالب هیچ آیکونی برای جایگزینی ندارد")
        }
        return result
    }

    /** Center-cropped square bitmap for the in-app preview (or null when unreadable). */
    fun loadPreview(context: Context, iconUri: Uri, target: Int = 256): Bitmap? =
        decodeCenterSquare(context, iconUri, target)

    private fun decodeCenterSquare(context: Context, uri: Uri, target: Int): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val side = minOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (side / (sample * 2) >= target) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val srcSide = minOf(src.width, src.height)
        val xOff = (src.width - srcSide) / 2
        val yOff = (src.height - srcSide) / 2

        val square = Bitmap.createBitmap(src, xOff, yOff, srcSide, srcSide)
        return Bitmap.createScaledBitmap(square, target, target, true)
    }
}
