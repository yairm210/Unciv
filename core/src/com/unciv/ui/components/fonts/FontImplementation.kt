package com.unciv.ui.components.fonts

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.BitmapFont

interface FontImplementation {
    fun setFontFamily(fontFamilyData: FontFamilyData, size: Int)
    fun getFontSize(): Int

    /** Why are we having two [getCharPixmap] overloads:
     *  - The Char one was used alone for a long time. We added the String one for Diacritic support - it still is meant to give one Glyph per input,
     *    but supports both single characters and short combos of diacritics with their target characters.
     *  - The desktop implementation currently uses (java.awt) metric.charWidth for the Char overload and metric.stringWidth for the String overload.
     *  - If there were a guarantee that these were always identical for a char and its toString(), then the Char overload would be redundant.
     *  - The author just wanted to make 100% sure **nothing** changes for non-Diacritic languages.
     *  - This could be tested with FasterUIDevelopment, as there the special Char overload is ignored.
     * */
    fun getCharPixmap(char: Char) = getCharPixmap(char.toString())
    fun getCharPixmap(symbolString: String): Pixmap

    fun getSystemFonts(): Sequence<FontFamilyData>

    fun getBitmapFont(): BitmapFont {
        val fontData = NativeBitmapFontData(this)
        val font = BitmapFont(fontData, fontData.regions, false)
        font.setOwnsTexture(true)
        return font
    }

    fun getMetrics(): FontMetricsCommon
}
