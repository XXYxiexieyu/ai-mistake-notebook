package com.hiro.mistakebook.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object InkImageEnhancer {
    fun createEnhancedImage(sourceFile: File, targetFile: File, maxSide: Int = 1800): Boolean {
        val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return false
        val scale = minOf(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
        val source = if (scale < 0.99f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
        if (source !== decoded) decoded.recycle()

        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val inputRow = IntArray(width)
        val outputRow = IntArray(width)
        for (y in 0 until height) {
            source.getPixels(inputRow, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val pixel = inputRow[x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val info = InkColorAnalyzer.analyze(r, g, b)
                outputRow[x] = if (info.coloredInk) {
                    when {
                        info.blueFamily -> Color.rgb(0, 45, 255)
                        info.hue < 25f || info.hue >= 330f -> Color.rgb(225, 0, 30)
                        info.hue in 75f..165f -> Color.rgb(0, 150, 45)
                        else -> Color.rgb(210, 0, 210)
                    }
                } else {
                    val gray = (r * 0.299f + g * 0.587f + b * 0.114f).roundToInt()
                    val light = if (gray < 245) 218 else 255
                    Color.rgb(light, light, light)
                }
            }
            output.setPixels(outputRow, 0, width, 0, y, width, 1)
        }
        source.recycle()
        targetFile.parentFile?.mkdirs()
        return FileOutputStream(targetFile).use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        }.also {
            output.recycle()
        }
    }
}
