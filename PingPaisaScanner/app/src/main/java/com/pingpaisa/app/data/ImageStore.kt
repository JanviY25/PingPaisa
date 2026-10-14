package com.pingpaisa.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class ImportedImage(val path: String, val hash: String)

/**
 * Copies every picked/captured image into app-private storage as a down-scaled, upright JPEG.
 * Gallery URIs only carry temporary read access, so the copy is what the product keeps.
 * ImageDecoder applies EXIF rotation. Hash of the stored JPEG powers duplicate detection.
 */
class ImageStore(private val context: Context) {
    private val dir = File(context.filesDir, "scans").apply { mkdirs() }

    fun importUri(uri: Uri): ImportedImage =
        store(ImageDecoder.createSource(context.contentResolver, uri))

    fun importFile(file: File): ImportedImage =
        try { store(ImageDecoder.createSource(file)) } finally { file.delete() }

    fun saveBytes(bytes: ByteArray): ImportedImage? = try {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        write(downscale(bmp))
    } catch (_: Exception) { null }

    fun delete(path: String?) { if (path != null) File(path).delete() }

    fun clearAll() { dir.listFiles()?.forEach { it.delete() } }

    private fun store(source: ImageDecoder.Source): ImportedImage {
        val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val s = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * s).roundToInt().coerceAtLeast(1),
                    (info.size.height * s).roundToInt().coerceAtLeast(1)
                )
            }
        }
        return write(bmp)
    }

    private fun downscale(b: Bitmap): Bitmap {
        val longest = max(b.width, b.height)
        if (longest <= MAX_SIDE) return b
        val s = MAX_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(b, (b.width * s).roundToInt(), (b.height * s).roundToInt(), true)
    }

    private fun write(bmp: Bitmap): ImportedImage {
        val out = File(dir, UUID.randomUUID().toString() + ".jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val digest = MessageDigest.getInstance("SHA-256").digest(out.readBytes())
        return ImportedImage(out.absolutePath, digest.joinToString("") { "%02x".format(it) })
    }

    companion object {
        const val MAX_SIDE = 1600

        /** Small bitmap for thumbnails (files are already upright). */
        fun loadThumbnail(path: String, targetPx: Int): Bitmap? = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) { null }

        fun decodeForOcr(path: String): Bitmap? = BitmapFactory.decodeFile(path)
    }
}
