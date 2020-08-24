package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.PixmapPacker
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame

interface NativeFontImplementation {
    fun getFontSize(): Int
    fun getCharPixmap(char: Char): Pixmap
}

// This class is loosely based on libgdx's FreeTypeBitmapFontData
class NativeBitmapFontData(val fontImplementation: NativeFontImplementation) : BitmapFontData(), Disposable {
    val regions: Array<TextureRegion>

    private var dirty = false
    private val packer: PixmapPacker

    private val filter = Texture.TextureFilter.Linear

    init {
        // set general font data
        flipped = false
        lineHeight = fontImplementation.getFontSize().toFloat()
        capHeight = lineHeight
        ascent = -lineHeight
        down = -lineHeight

        // Create a packer.
        val size = 1024
        val packStrategy = PixmapPacker.GuillotineStrategy()
        packer = PixmapPacker(size, size, Pixmap.Format.RGBA8888, 1, false, packStrategy)
        packer.transparentColor = Color.WHITE
        packer.transparentColor.a = 0f

        // Generate texture regions.
        regions = Array()
        packer.updateTextureRegions(regions, filter, filter, false)

        // Set space glyph.
        val spaceGlyph = getGlyph(' ')
        spaceXadvance = spaceGlyph.xadvance.toFloat()
    }

    override fun getGlyph(ch: Char): Glyph {
        var glyph: Glyph? = super.getGlyph(ch)
        if (glyph == null) {
            if(ch == '\uD83D' || ch == '\uD83C' ) return Glyph() // This is the 'first character' of an emoji - empty space
            val charPixmap = getPixmapFromChar(ch)

            glyph = Glyph()
            glyph.id = ch.toInt()
            glyph.width = charPixmap.width
            glyph.height = charPixmap.height
            glyph.xadvance = glyph.width

            val rect = packer.pack(charPixmap)
            charPixmap.dispose()
            glyph.page = packer.pages.size - 1 // Glyph is always packed into the last page for now.
            glyph.srcX = rect.x.toInt()
            glyph.srcY = rect.y.toInt()

            // If a page was added, create a new texture region for the incrementally added glyph.
            if (regions.size <= glyph.page)
                packer.updateTextureRegions(regions, filter, filter, false)

            setGlyphRegion(glyph, regions.get(glyph.page))
            setGlyph(ch.toInt(), glyph)
            dirty = true
        }
        return glyph
    }

    private fun getPixmapFromChar(ch: Char): Pixmap {
        return when (ch) {
            Fonts.strength[1] -> Fonts.extractPixmapFromTextureRegion(ImageGetter.getDrawable("StatIcons/Strength").region)
            Fonts.rangedStrength[1] -> Fonts.extractPixmapFromTextureRegion(ImageGetter.getDrawable("StatIcons/RangedStrength").region)
            Fonts.range[1] -> Fonts.extractPixmapFromTextureRegion(ImageGetter.getDrawable("StatIcons/Range").region)
            Fonts.movement -> Fonts.extractPixmapFromTextureRegion(ImageGetter.getDrawable("StatIcons/Movement").region)
            Fonts.turn -> Fonts.extractPixmapFromTextureRegion(ImageGetter.getDrawable("EmojiIcons/Turn").region)
            else -> fontImplementation.getCharPixmap(ch)
        }
    }

    override fun getGlyphs(run: GlyphLayout.GlyphRun, str: CharSequence, start: Int, end: Int, lastGlyph: Glyph?) {
        packer.packToTexture = true // All glyphs added after this are packed directly to the texture.
        super.getGlyphs(run, str, start, end, lastGlyph)
        if (dirty) {
            dirty = false
            packer.updateTextureRegions(regions, filter, filter, false)
        }
    }

    override fun dispose() {
        packer.dispose()
    }
}

object Fonts {
    val font by lazy {
        val fontData = NativeBitmapFontData(UncivGame.Current.fontImplementation!!)
        val font = BitmapFont(fontData, fontData.regions, false)
        font.setOwnsTexture(true)
        font
    }

    // From https://stackoverflow.com/questions/29451787/libgdx-textureregion-to-pixmap
    fun extractPixmapFromTextureRegion(textureRegion:TextureRegion):Pixmap {
        val textureData = textureRegion.texture.textureData
        if (!textureData.isPrepared) {
            textureData.prepare()
        }
        val pixmap = Pixmap(
                textureRegion.regionWidth,
                textureRegion.regionHeight,
                textureData.format
        )
        pixmap.drawPixmap(
                textureData.consumePixmap(), // The other Pixmap
                0, // The target x-coordinate (top left corner)
                0, // The target y-coordinate (top left corner)
                textureRegion.regionX, // The source x-coordinate (top left corner)
                textureRegion.regionY, // The source y-coordinate (top left corner)
                textureRegion.regionWidth, // The width of the area from the other Pixmap in pixels
                textureRegion.regionHeight // The height of the area from the other Pixmap in pixels
        )
        return pixmap
    }

    const val food = "\uD83C\uDF4E"
    const val gold = "\uD83D\uDCB0"
    const val turn = '⏳'
    const val strength = "\uD83D\uDCAA"
    const val rangedStrength = "\uD83C\uDFF9"
    const val movement = '➡'
    const val range = "\uD83D\uDCCF"

//    const val production = '⚙'
}
