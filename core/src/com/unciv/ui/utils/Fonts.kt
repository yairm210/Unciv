package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.PixmapPacker
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.models.stats.Stat

interface NativeFontImplementation {
    fun getFontSize(): Int
    fun getCharPixmap(char: Char): Pixmap
}

// This class is loosely based on libgdx's FreeTypeBitmapFontData
class NativeBitmapFontData(
    private val fontImplementation: NativeFontImplementation
) : BitmapFontData(), Disposable {

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
            val charPixmap = getPixmapFromChar(ch)

            glyph = Glyph()
            glyph.id = ch.code
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
            setGlyph(ch.code, glyph)
            dirty = true
        }
        return glyph
    }

    private fun getPixmapFromChar(ch: Char): Pixmap {
        // Images must be 50*50px so they're rendered at the same height as the text - see Fonts.ORIGINAL_FONT_SIZE
        return when (ch) {
            Fonts.strength -> ImageGetter.getDrawable("StatIcons/Strength").region.toPixmap()
            Fonts.rangedStrength -> ImageGetter.getDrawable("StatIcons/RangedStrength").region.toPixmap()
            Fonts.range -> ImageGetter.getDrawable("StatIcons/Range").region.toPixmap()
            Fonts.movement -> ImageGetter.getDrawable("StatIcons/Movement").region.toPixmap()
            Fonts.turn -> ImageGetter.getDrawable("EmojiIcons/Turn").region.toPixmap()
            Fonts.production -> ImageGetter.getDrawable("EmojiIcons/Production").region.toPixmap()
            Fonts.gold -> ImageGetter.getDrawable("EmojiIcons/Gold").region.toPixmap()
            Fonts.food -> ImageGetter.getDrawable("EmojiIcons/Food").region.toPixmap()
            Fonts.science -> ImageGetter.getDrawable("EmojiIcons/Science").region.toPixmap()
            Fonts.culture -> ImageGetter.getDrawable("EmojiIcons/Culture").region.toPixmap()
            Fonts.faith -> ImageGetter.getDrawable("EmojiIcons/Faith").region.toPixmap()
            Fonts.happiness -> ImageGetter.getDrawable("EmojiIcons/Happiness").region.toPixmap()
            MayaCalendar.tun -> ImageGetter.getDrawable(MayaCalendar.tunIcon).region.toPixmap()
            MayaCalendar.katun -> ImageGetter.getDrawable(MayaCalendar.katunIcon).region.toPixmap()
            MayaCalendar.baktun -> ImageGetter.getDrawable(MayaCalendar.baktunIcon).region.toPixmap()
            in MayaCalendar.digits -> ImageGetter.getDrawable(MayaCalendar.digitIcon(ch)).region.toPixmap()
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

    /** All text is originally rendered in 50px (set in AndroidLauncher and DesktopLauncher), and thn scaled to fit the size of the text we need now.
     * This has several advantages: It means we only render each character once (good for both runtime and RAM),
     * AND it means that our 'custom' emojis only need to be once size (50px) and they'll be rescaled for what's needed. */
    const val ORIGINAL_FONT_SIZE = 50f

    lateinit var font:BitmapFont
    fun resetFont() {
        val fontData = NativeBitmapFontData(UncivGame.Current.fontImplementation!!)
        font = BitmapFont(fontData, fontData.regions, false)
        font.setOwnsTexture(true)
    }

    const val turn = '⏳'               // U+23F3 'hourglass'
    const val strength = '†'            // U+2020 'dagger'
    const val rangedStrength = '‡'      // U+2021 'double dagger'
    const val movement = '➡'            // U+27A1 'black rightwards arrow'
    const val range = '…'               // U+2026 'horizontal ellipsis'
    const val production = '⚙'          // U+2699 'gear'
    const val gold = '¤'                // U+00A4 'currency sign'
    const val food = '⁂'                // U+2042 'asterism' (to avoid 🍏 U+1F34F 'green apple' needing 2 symbols in utf-16 and 4 in utf-8)
    const val science = '⍾'             // U+237E 'bell symbol' (🧪 U+1F9EA 'test tube', 🔬 U+1F52C 'microscope')
    const val culture = '♪'             // U+266A 'eighth note' (🎵 U+1F3B5 'musical note')
    const val happiness = '⌣'           // U+2323 'smile' (😀 U+1F600 'grinning face')
    const val faith = '☮'               // U+262E 'peace symbol' (🕊 U+1F54A 'dove of peace')

    fun statToChar(stat: Stat): Char {
        return when (stat) {
            Stat.Food -> food
            Stat.Production -> production
            Stat.Gold -> gold
            Stat.Happiness -> happiness
            Stat.Culture -> culture
            Stat.Science -> science
            Stat.Faith -> faith
        }
    }
}
