package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.ui.components.FontFamilyData
import com.unciv.ui.components.FontImplementation
import com.unciv.ui.components.Fonts
import java.awt.*
import java.awt.image.BufferedImage
import java.util.*


class FontDesktop : FontImplementation {

    private lateinit var font: Font
    private lateinit var metric: FontMetrics

    override fun setFontFamily(fontFamilyData: FontFamilyData, size: Int) {

        // Mod font
        if (fontFamilyData.filePath != null)
        {
            this.font = createFontFromFile(fontFamilyData.filePath!!, size)
        }
        // System font
        else
        {
            this.font = Font(fontFamilyData.invariantName, Font.PLAIN, size)
        }

        val bufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR)
        val graphics = bufferedImage.createGraphics()
        this.metric = graphics.getFontMetrics(font)
        graphics.dispose()
    }

    private fun createFontFromFile(path: String, size: Int): Font {
        var font: Font
        try
        {
            // Try to create and register new font
            val fontFile = Gdx.files.local(path).file()
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            font = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(size.toFloat())
            ge.registerFont(font)
        }
        catch (e: Exception)
        {
            // Fallback to default, if failed.
            font = Font(Fonts.DEFAULT_FONT_FAMILY, Font.PLAIN, size)
        }
        return font
    }

    override fun getFontSize(): Int {
        return font.size
    }

    override fun getCharPixmap(char: Char): Pixmap {
        var width = metric.charWidth(char)
        var height = metric.ascent + metric.descent
        if (width == 0) {
            height = font.size
            width = height
        }
        val bi = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
        val g = bi.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.font = font
        g.color = Color.WHITE
        g.drawString(char.toString(), 0, metric.ascent)
        val pixmap = Pixmap(bi.width, bi.height, Pixmap.Format.RGBA8888)
        val data = bi.getRGB(0, 0, bi.width, bi.height, null, 0, bi.width)
        for (i in 0 until bi.width) {
            for (j in 0 until bi.height) {
                pixmap.setColor(Integer.reverseBytes(data[i + (j * bi.width)]))
                pixmap.drawPixel(i, j)
            }
        }
        g.dispose()
        return pixmap
    }

    override fun getSystemFonts(): Sequence<FontFamilyData> {
        val cjkLanguage = " CJK " +System.getProperty("user.language").uppercase()
        return GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts.asSequence()
            .filter { " CJK " !in it.fontName || cjkLanguage in it.fontName }
            .map { FontFamilyData(it.family, it.getFamily(Locale.ROOT)) }
            .distinctBy { it.invariantName }
    }
}
