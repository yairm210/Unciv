package com.unciv.app.web

import com.badlogic.gdx.graphics.Pixmap
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.FontImplementation
import com.unciv.ui.components.fonts.FontMetricsCommon

class WebFont : FontImplementation {
    private var fontSize = 100
    private var fontFamily = "sans-serif"
    private var metrics = FontMetricsCommon(
        ascent = 80f,
        descent = 20f,
        height = 100f,
        leading = 2f,
    )

    override fun setFontFamily(fontFamilyData: FontFamilyData, size: Int) {
        fontSize = size.coerceAtLeast(8)
        // Keep a deterministic default family on web in phase-1.
        fontFamily = "sans-serif"
        val measured = WebFontRasterizer.measureMetrics(fontSize, fontFamily)
        if (measured.size >= 4) {
            metrics = FontMetricsCommon(
                ascent = measured[0],
                descent = measured[1],
                height = measured[2],
                leading = measured[3],
            )
        }
    }

    override fun getFontSize(): Int = fontSize

    override fun getCharPixmap(symbolString: String): Pixmap {
        val rasterized = WebFontRasterizer.rasterizeGlyph(symbolString, fontSize, fontFamily)
        if (rasterized.size < 3) {
            val fallbackSize = fontSize.coerceAtLeast(8)
            return Pixmap(fallbackSize, fallbackSize, Pixmap.Format.RGBA8888)
        }
        val width = rasterized[0].coerceAtLeast(1)
        val height = rasterized[1].coerceAtLeast(1)
        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
        var src = 3
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = rasterized.getOrElse(src) { 0 } and 0xFF
                val g = rasterized.getOrElse(src + 1) { 0 } and 0xFF
                val b = rasterized.getOrElse(src + 2) { 0 } and 0xFF
                val a = rasterized.getOrElse(src + 3) { 0 } and 0xFF
                pixmap.drawPixel(x, y, (r shl 24) or (g shl 16) or (b shl 8) or a)
                src += 4
            }
        }
        return pixmap
    }

    override fun getSystemFonts(): Sequence<FontFamilyData> = sequenceOf(FontFamilyData.default)

    override fun getMetrics() = metrics
}
