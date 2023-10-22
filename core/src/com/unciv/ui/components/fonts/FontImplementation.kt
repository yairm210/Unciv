package com.unciv.ui.components.fonts

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.BitmapFont

interface FontImplementation {
    fun setFontFamily(fontFamilyData: FontFamilyData, size: Int)
    fun getFontSize(): Int
    fun getCharPixmap(char: Char): Pixmap
    fun getSystemFonts(): Sequence<FontFamilyData>

    fun getBitmapFont(): BitmapFont {
        val fontData = NativeBitmapFontData(this)
        val font = BitmapFont(fontData, fontData.regions, false)
        font.setOwnsTexture(true)
        return font
    }

    fun getMetrics(): FontMetricsCommon
}
