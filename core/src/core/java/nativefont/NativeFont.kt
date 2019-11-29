package core.java.nativefont

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.PixmapPacker
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.GdxRuntimeException
import java.util.*

/**
 * Created by tian on 2016/10/2.
 */
class NativeFont @JvmOverloads constructor(paint: NativeFontPaint = NativeFontPaint()) : BitmapFont(BitmapFontData(), TextureRegion(), false) {
    private var charSet: MutableSet<String?>?
    private val emojiSet: HashMap<String?, EmojiDate?>
    private val magFilter: TextureFilter
    private val minFilter: TextureFilter
    private var packer: PixmapPacker?
    private var pageWidth = 512
    private var paint: NativeFontPaint?
    private var size = 0

    inner class EmojiDate(var path: String, var size: Int)

    private fun createListener() {
        var className = "core.java.nativefont.NativeFont"
        when (Gdx.app.type) {
            Application.ApplicationType.Desktop -> {
                className += "Desktop"
            }
            Application.ApplicationType.Android -> {
                className += "Android"
            }
            Application.ApplicationType.iOS -> {
                className += if (robovm) "IOS" else "IOSMoe"
            }
            Application.ApplicationType.WebGL -> {
                className += "Html"
            }
        }
        listener = try {
            val claz = Gdx.app.javaClass.classLoader.loadClass(className) as Class<out NativeFontListener>
            claz.newInstance()
        } catch (e: Exception) {
            throw GdxRuntimeException("Class Not Found:" + e.message)
        }
    }

    fun updataSize(newSize: Int) {
        size = Math.max(newSize, size)
        this.data.down = (-size).toFloat()
        this.data.ascent = (-size).toFloat()
        this.data.capHeight = size.toFloat()
        this.data.lineHeight = size.toFloat()
    }

    fun setTextColor(color: Color?): NativeFont {
        paint!!.color = color
        return this
    }

    fun setStrokeColor(color: Color?): NativeFont {
        paint!!.strokeColor = color
        return this
    }

    fun setStrokeWidth(width: Int): NativeFont {
        paint!!.strokeWidth = width
        return this
    }

    fun setSize(size: Int): NativeFont {
        paint!!.textSize = size
        return this
    }

    fun setBold(istrue: Boolean): NativeFont {
        paint!!.fakeBoldText = istrue
        return this
    }

    fun setUnderline(istrue: Boolean): NativeFont {
        paint!!.underlineText = istrue
        return this
    }

    fun setStrikeThru(istrue: Boolean): NativeFont {
        paint!!.strikeThruText = istrue
        return this
    }

    fun setPaint(paint: NativeFontPaint?): NativeFont {
        this.paint = paint
        return this
    }

    fun addEmojiPath(emojiKey: String?, imgPath: String, size: Int): NativeFont {
        emojiSet[emojiKey] = EmojiDate(imgPath, size)
        return this
    }

    fun appendEmoji(txt: String, imgname: String?, size: Int): NativeFont {
        val pixmap = Pixmap(Gdx.files.internal(imgname))
        // Pixmap.setFilter(Pixmap.Filter.BiLinear);
        val pixmap2 = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pixmap2.filter = Pixmap.Filter.BiLinear
        pixmap2.drawPixmap(pixmap, 0, 0, pixmap.width, pixmap.height, 0, 0, size, size)
        pixmap.dispose()
        appendEmoji(txt, pixmap2)
        return this
    }

    fun appendEmoji(txt: String, pixmap: Pixmap): NativeFont {
        if (charSet!!.add(txt)) {
            if (packer == null) {
                packer = PixmapPacker(pageWidth, pageWidth, Pixmap.Format.RGBA8888, 2, false)
            }
            putGlyph(txt[0], pixmap)
            updataSize(pixmap.height)
            upData()
        }
        return this
    }

    fun createText(characters: String?): NativeFont {
        if (!(characters == null || characters.length == 0)) {
            create(characters, true)
            end()
        }
        return this
    }

    fun appendText(characters: String?): NativeFont {
        if (!(characters == null || characters.length == 0)) {
            create(characters, false)
        }
        return this
    }

    private fun create(characters: String, haveMinPageSize: Boolean) {
        var characters = characters
        characters = characters.replace("[\\t\\n\\x0B\\f\\r]".toRegex(), "")
        val arrayOfCharsAsStrings = Array<String>()
        for (c2 in characters.toCharArray()) {
            if (charSet!!.add(c2.toString())) {
                arrayOfCharsAsStrings.add(c2.toString())
            }
        }
        if (haveMinPageSize) {
            pageWidth = (paint!!.textSize + 2) * (Math.sqrt(arrayOfCharsAsStrings.size.toDouble()) + 1.0).toInt()
        }
        if (packer == null) {
            packer = PixmapPacker(pageWidth, pageWidth, Pixmap.Format.RGBA8888, 2, false)
        }

        val putGlyphStartTime = System.currentTimeMillis()
        for (i in 0 until arrayOfCharsAsStrings.size) {
            val txt = arrayOfCharsAsStrings[i]
            val c2 = txt[0]
            val css = c2.toString()
            if (emojiSet[css] != null) {
                charSet!!.remove(css)
                val date = emojiSet[css]
                appendEmoji(c2.toString() + "", date!!.path, date.size)
            } else {
                putGlyph(c2, listener!!.getFontPixmap(txt, paint))
            }
        }

        val putGlyphTime = System.currentTimeMillis() - putGlyphStartTime
        println("Putting glyphs - "+putGlyphTime+"ms")

        updataSize(size)
        upData()

        if (regions.size == 1) {
            setOwnsTexture(true)
        } else {
            setOwnsTexture(false)
        }
    }

    private fun putGlyph(c: Char, pixmap: Pixmap) {
        val rect = packer!!.pack(c.toString(), pixmap)
        pixmap.dispose()
        val pIndex = packer!!.getPageIndex(c.toString())
        val glyph = Glyph()
        glyph.id = c.toInt()
        glyph.page = pIndex
        glyph.srcX = rect.x.toInt()
        glyph.srcY = rect.y.toInt()
        glyph.width = rect.width.toInt()
        glyph.height = rect.height.toInt()
        glyph.xadvance = glyph.width
        this.data!!.setGlyph(c.toInt(), glyph)
    }

    private fun upData() {
        var spaceGlyph = this.data!!.getGlyph(' ')
        if (spaceGlyph == null) {
            spaceGlyph = Glyph()
            var xadvanceGlyph = this.data!!.getGlyph('l')
            if (xadvanceGlyph == null) {
                xadvanceGlyph = this.data!!.firstGlyph
            }
            spaceGlyph.xadvance = xadvanceGlyph!!.xadvance
            spaceGlyph.id = 32
            this.data!!.setGlyph(32, spaceGlyph)
        }
        this.data!!.spaceXadvance = (spaceGlyph.xadvance + spaceGlyph.width).toFloat()
        val pages = packer!!.pages
        val regions = regions
        val regSize = regions.size - 1
        for (i in 0 until pages.size) {
            val p = pages[i]
            if (i > regSize) {
                p.updateTexture(minFilter, magFilter, false)
                regions.add(TextureRegion(p.texture))
            } else {
                if (p.updateTexture(minFilter, magFilter, false)) {
                    regions[i] = TextureRegion(p.texture)
                }
            }
        }
        for (page in this.data!!.glyphs) {
            if (page == null) continue
            for (glyph in page) {
                if (glyph != null) {
                    val region = getRegions()[glyph.page]
                            ?: throw IllegalArgumentException("BitmapFont texture region array cannot contain null elements.")
                    this.data!!.setGlyphRegion(glyph, region)
                }
            }
        }
    }

    fun end(): NativeFont {
        paint = null
        charSet!!.clear()
        charSet = null
        packer!!.dispose()
        packer = null
        return this
    }

    override fun dispose() {
        end()
        super.dispose()
    }

    companion object {
        var listener: NativeFontListener? = null
            private set
        private var robovm = false
        fun setRobovm() {
            robovm = true
        }

    }

    init {
        this.paint = NativeFontPaint()
        charSet = HashSet()
        packer = null
        minFilter = TextureFilter.Linear
        magFilter = TextureFilter.Linear
        emojiSet = HashMap()
        updataSize(paint.textSize)
        if (listener == null) createListener()
        this.paint = paint
    }
}