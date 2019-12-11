package core.java.nativefont

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import java.awt.*
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.AttributedString
import java.util.*
import javax.imageio.ImageIO
import javax.swing.UIManager

class NativeFontDesktop : NativeFontListener {
    private val fonts = HashMap<String, Font?>()
    private val metrics = HashMap<String, FontMetrics>()
    override fun getFontPixmap(txt: String, vpaint: NativeFontPaint): Pixmap {
        val font = getFont(vpaint)
        val fm = metrics[vpaint.name]
        var strWidth = fm!!.stringWidth(txt)
        var strHeight = fm.ascent + fm.descent
        if (strWidth == 0) {
            strHeight = vpaint.textSize
            strWidth = strHeight
        }
        val bi = BufferedImage(strWidth, strHeight,
                BufferedImage.TYPE_4BYTE_ABGR)
        val g = bi.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON)
        g.font = font
        when {
            vpaint.strokeColor != null -> { // 描边
                val v = font!!.createGlyphVector(fm.fontRenderContext,
                        txt)
                val shape = v.outline
                g.color = UIManager.getColor(vpaint.color)
                g.translate(0, fm.ascent)
                g.fill(shape)
                g.stroke = BasicStroke(vpaint.strokeWidth.toFloat())
                g.color = UIManager.getColor(vpaint.strokeColor)
                g.draw(shape)
            }
            vpaint.underlineText -> { // 下划线
                val `as` = AttributedString(txt)
                `as`.addAttribute(TextAttribute.FONT, font)
                `as`.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)
                g.color = UIManager.getColor(vpaint.color)
                g.drawString(`as`.iterator, 0, fm.ascent)
            }
            vpaint.strikeThruText -> { // 删除线
                val `as` = AttributedString(txt)
                `as`.addAttribute(TextAttribute.FONT, font)
                `as`.addAttribute(TextAttribute.STRIKETHROUGH,
                        TextAttribute.STRIKETHROUGH_ON)
                g.color = UIManager.getColor(vpaint.color)
                g.drawString(`as`.iterator, 0, fm.ascent)
            }
            else -> { // 普通
                g.color = UIManager.getColor(vpaint.color)
                g.drawString(txt, 0, fm.ascent)
            }
        }
        lateinit var pixmap: Pixmap
        try {
            val buffer = ByteArrayOutputStream()
            ImageIO.write(bi, "png", buffer)
            pixmap = Pixmap(buffer.toByteArray(), 0, buffer.toByteArray().size)
            buffer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return pixmap
    }

    private fun getFont(vpaint: NativeFontPaint): Font? {
        val isBolo = vpaint.fakeBoldText || vpaint.strokeColor != null
        var font = fonts[vpaint.name]
        if (font == null) {
            if (vpaint.tTFName == "") {
                font = Font("", if (isBolo) Font.BOLD else Font.PLAIN, vpaint.textSize)
            } else {
                try {
                    val `in` = ByteArrayInputStream(Gdx.files.internal(vpaint.tTFName + if (vpaint.tTFName
                                    .endsWith(".ttf")) "" else ".ttf").readBytes())
                    val fb = BufferedInputStream(`in`)
                    font = Font.createFont(Font.TRUETYPE_FONT, fb).deriveFont(Font.BOLD, vpaint.textSize.toFloat())
                    fb.close()
                    `in`.close()
                } catch (e: FontFormatException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            fonts[vpaint.name] = font
            val bi = BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR)
            val g = bi.createGraphics()
            g.font = font
            val fm = g.fontMetrics
            metrics[vpaint.name] = fm
        }
        return font
    }
}