package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.unciv.UncivGame
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.FontImplementation
import com.unciv.ui.components.fonts.FontMetricsCommon
import com.unciv.ui.components.fonts.Fonts
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Locale
import org.lwjgl.opengl.GL14


class DesktopFont : FontImplementation {

    override val useMipMaps = true

    override fun configureFontTexture(texture: Texture) {
        // Prefer slightly finer mip levels to sharpen text while retaining trilinear filtering.
        texture.bind()
        Gdx.gl.glTexParameterf(texture.glTarget, GL14.GL_TEXTURE_LOD_BIAS, -0.5f)
    }

    private lateinit var font: Font
    private lateinit var metric: FontMetrics
    private lateinit var fallbackMetric: FontMetrics

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
        fallbackMetric = graphics.getFontMetrics(Font(Font.DIALOG, Font.PLAIN, size))
        graphics.dispose()
    }

    private fun createFontFromFile(path: String, size: Int): Font {
        var font: Font
        try
        {
            // Try to create and register new font
            val fontFile = UncivGame.Current.files.getLocalFile(path).file()
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            font = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(size.toFloat())
            ge.registerFont(font)
        }
        catch (_: Exception)
        {
            // Fallback to default, if failed.
            font = Font(Fonts.DEFAULT_FONT_FAMILY, Font.PLAIN, size)
        }
        return font
    }

    override fun getFontSize(): Int {
        return font.size
    }

    override fun getCharPixmap(char: Char) = getCharPixmapCommon(char.toString(), metric.charWidth(char))

    override fun getCharPixmap(symbolString: String) = getCharPixmapCommon(symbolString, metric.stringWidth(symbolString))

    private fun getCharPixmapCommon(symbolString: String, measuredWidth: Int): Pixmap {
        // Physical fonts such as Microsoft YaHei may lack typographic spaces used by
        // the UI (U+2004, U+2009). Their missing-glyph box is neither blank nor the right
        // width. Borrow the logical font's spacing, without changing visible glyphs.
        val unsupportedSpace = symbolString.isNotEmpty() &&
            symbolString.all { Character.isSpaceChar(it) } && font.canDisplayUpTo(symbolString) != -1
        var width = if (unsupportedSpace) fallbackMetric.stringWidth(symbolString) else measuredWidth
        var height = metric.height
        if (width == 0) {
            // This happens e.g. for the Tab character
            height = font.size
            width = height
        }

        val bi = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
        val g = bi.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.font = font
        g.color = Color.WHITE
        if (!unsupportedSpace)
            g.drawString(symbolString, 0, metric.leading + metric.ascent)

        val pixmap = Pixmap(bi.width, bi.height, Pixmap.Format.RGBA8888)
        // Mipmaps average RGB as well as alpha. Transparent black around white glyphs
        // would darken their edges, then alpha blending would attenuate them again.
        // Keep white RGB even at zero coverage, and copy without blending to preserve it.
        pixmap.blending = Pixmap.Blending.None
        val data = bi.getRGB(0, 0, bi.width, bi.height, null, 0, bi.width)
        for (i in 0 until bi.width) {
            for (j in 0 until bi.height) {
                val pixel = data[i + (j * bi.width)]
                val rgba = 0xffffff00.toInt() or (pixel ushr 24)
                pixmap.setColor(rgba)
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

    // Note: AWT uses the FontDesignMetrics implementation in our case, which has more precise
    // float fields but rounds to integers to satisfy the interface.
    // Additionally, the rounding is weird: x.049 rounds down, x.051 rounds up.
    // There is no way around the privacy crap: FontUtilities.getFont2D(metric.font).getStrike(metric.font, metric.fontRenderContext).getFontMetrics() would work if that last method wasn't private too...
    // Reflection is out too, since java.desktop refuses to open sun.font - we must die with rounding errors!
    override fun getMetrics() = FontMetricsCommon(
        ascent = metric.ascent.toFloat(),
        descent = metric.descent.toFloat(),
        height = metric.height.toFloat(),
        leading = metric.leading.toFloat()
    )
}
