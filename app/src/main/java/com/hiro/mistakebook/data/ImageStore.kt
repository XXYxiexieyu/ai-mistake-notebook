package com.hiro.mistakebook.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class ImageStore(private val context: Context) {
    private val imageDir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    fun newCaptureFile(): File = File(imageDir, "capture_${UUID.randomUUID()}.jpg")

    fun resolve(relativePath: String): File =
        if (relativePath.startsWith("images/")) {
            File(context.filesDir, relativePath)
        } else {
            File(imageDir, relativePath)
        }

    fun copyUri(uri: Uri, prefix: String): String {
        val destination = File(imageDir, "${prefix}_${UUID.randomUUID()}.jpg")
        val bitmap = readBitmap(uri, 2400)
        FileOutputStream(destination).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
        bitmap.recycle()
        return relative(destination)
    }

    fun saveBitmap(bitmap: Bitmap, prefix: String): String {
        val destination = File(imageDir, "${prefix}_${UUID.randomUUID()}.jpg")
        FileOutputStream(destination).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
        return relative(destination)
    }

    fun importFile(source: File, prefix: String = "imported"): String {
        val destination = File(imageDir, "${prefix}_${UUID.randomUUID()}.jpg")
        source.copyTo(destination, overwrite = false)
        return relative(destination)
    }

    fun delete(relativePath: String) {
        if (relativePath.isBlank()) return
        resolve(relativePath).takeIf { it.isFile }?.delete()
    }

    fun sha256(relativePath: String): String {
        val file = resolve(relativePath)
        if (!file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readBitmap(uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, options)
                ?: error("无法读取图片")
        }
        val rotation = resolver.openInputStream(uri).use { input ->
            if (input == null) 0f else {
                when (
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }
        if (rotation == 0f) return bitmap
        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(rotation) },
            true
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun relative(file: File): String = "images/${file.name}"
}
