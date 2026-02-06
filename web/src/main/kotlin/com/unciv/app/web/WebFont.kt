package com.unciv.app.web

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.FontImplementation
import com.unciv.ui.components.fonts.FontMetricsCommon
import kotlin.math.ceil

class WebFont : FontImplementation {
    private var fontSize = 100

    override fun setFontFamily(fontFamilyData: FontFamilyData, size: Int) {
        // Phase-1 keeps a deterministic default family on web.
        fontSize = size.coerceAtLeast(8)
    }

    override fun getFontSize(): Int = fontSize

    override fun getCharPixmap(symbolString: String): Pixmap {
        val size = ceil(fontSize.toFloat()).toInt().coerceAtLeast(8)
        return Pixmap(size, size, Pixmap.Format.RGBA8888).apply {
            setColor(1f, 1f, 1f, 1f)
            drawRectangle(0, 0, size, size)
        }
    }

    override fun getSystemFonts(): Sequence<FontFamilyData> = sequenceOf(FontFamilyData.default)

    override fun getBitmapFont(): BitmapFont = BitmapFont()

    override fun getMetrics() = FontMetricsCommon(
        ascent = fontSize * 0.8f,
        descent = fontSize * 0.2f,
        height = fontSize.toFloat(),
        leading = fontSize * 0.1f,
    )
}
