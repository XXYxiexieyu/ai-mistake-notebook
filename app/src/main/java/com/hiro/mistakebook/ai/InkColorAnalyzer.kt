package com.hiro.mistakebook.ai

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class InkColorInfo(
    val coloredInk: Boolean,
    val blueFamily: Boolean,
    val hue: Float,
    val saturation: Float,
    val brightness: Float
)

object InkColorAnalyzer {
    fun analyze(r: Int, g: Int, b: Int): InkColorInfo {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maximum = max(rf, max(gf, bf))
        val minimum = min(rf, min(gf, bf))
        val delta = maximum - minimum
        val saturation = if (maximum <= 0f) 0f else delta / maximum
        val hue = when {
            delta == 0f -> 0f
            maximum == rf -> 60f * (((gf - bf) / delta) % 6f)
            maximum == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val blueFamily = hue in 165f..325f && saturation >= 0.16f && maximum >= 0.18f
        val otherColor = saturation >= 0.32f && maximum >= 0.22f
        return InkColorInfo(
            coloredInk = blueFamily || otherColor,
            blueFamily = blueFamily,
            hue = hue,
            saturation = saturation,
            brightness = maximum
        )
    }

    fun isBlueFamily(r: Int, g: Int, b: Int): Boolean =
        analyze(r, g, b).blueFamily && b >= r - abs(g - b) / 3
}
