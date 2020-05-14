package com.unciv.app.desktop

import com.badlogic.gdx.graphics.Pixmap
import com.unciv.ui.utils.NativeFontImplementation
import java.awt.*
import java.awt.image.BufferedImage


class NativeFontDesktop(val size: Int) : NativeFontImplementation {
    private val font by lazy {
        Font("", Font.PLAIN, size)
    }
    private val metric by lazy {
        val bi = BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR)
        val g = bi.createGraphics()
        g.font = font
        g.fontMetrics!!
    }

    override fun getFontSize(): Int {
        return size
    }

    override fun getCharPixmap(char: Char): Pixmap {
        var width = metric.charWidth(char)
        var height = metric.ascent + metric.descent
        if (width == 0) {
            height = size
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
        return pixmap
    }
}