package com.unciv.ui.components.fonts

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.PixmapPacker
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.unciv.Constants
import com.unciv.ui.images.ImageGetter
import kotlin.math.roundToInt

// This class is loosely based on libgdx's FreeTypeBitmapFontData
class NativeBitmapFontData(
    private val fontImplementation: FontImplementation
) : BitmapFont.BitmapFontData(), Disposable {

    val regions: Array<TextureRegion>

    private var dirty = false
    private val packer: PixmapPacker

    private val filter = Texture.TextureFilter.Linear

    private companion object {
        /** How to get the alpha channel in a Pixmap.getPixel return value (Int) - it's the LSB */
        const val alphaChannelMask = 255
        /** Where to test circle for transparency */
        // The center of a squared circle's corner wedge would be at (1-PI/4)/2 ≈ 0.1073
        const val nearCornerRelativeOffset = 0.1f
        /** Where to test circle for opacity */
        // arbitrary choice just off-center
        const val nearCenterRelativeOffset = 0.4f
        /** Width multiplier to get extra advance after a ruleset icon, empiric */
        const val relativeAdvanceExtra = 0.039f
        /** Multiplier to get default kerning between a ruleset icon and 'open' characters */
        const val relativeKerning = -0.055f
        /** Which follower characters receive how much kerning relative to [relativeKerning] */
        val kerningMap = mapOf('A' to 1f, 'T' to 0.6f, 'V' to 1f, 'Y' to 1.2f)
    }

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

        setScale(Constants.defaultFontSize / Fonts.ORIGINAL_FONT_SIZE)
    }

    override fun getGlyph(ch: Char): BitmapFont.Glyph = super.getGlyph(ch) ?: createAndCacheGlyph(ch)

    private fun createAndCacheGlyph(ch: Char): BitmapFont.Glyph {
        val charPixmap = getPixmapFromChar(ch)

        val glyph = BitmapFont.Glyph()
        glyph.id = ch.code
        glyph.width = charPixmap.width
        glyph.height = charPixmap.height
        glyph.xadvance = glyph.width

        // Check alpha to guess whether this is a round icon
        // Needs to be done before disposing charPixmap, and we want to do that soon
        val isFontRulesetIcon = ch.code >= FontRulesetIcons.UNUSED_CHARACTER_CODES_START && ch <= DiacriticSupport.getCurrentFreeCode()
        val assumeRoundIcon = isFontRulesetIcon && charPixmap.guessIsRoundSurroundedByTransparency()

        val rect = packer.pack(charPixmap)
        charPixmap.dispose()
        glyph.page = packer.pages.size - 1 // Glyph is always packed into the last page for now.
        glyph.srcX = rect.x.toInt()
        glyph.srcY = rect.y.toInt()

        if (isFontRulesetIcon)
            glyph.setRulesetIconGeometry(assumeRoundIcon)

        // If a page was added, create a new texture region for the incrementally added glyph.
        if (regions.size <= glyph.page)
            packer.updateTextureRegions(regions, filter, filter, false)

        setGlyphRegion(glyph, regions.get(glyph.page))
        setGlyph(ch.code, glyph)
        dirty = true

        return glyph
    }

    private fun Pixmap.guessIsRoundSurroundedByTransparency(): Boolean {
        // If a pixel near the center is opaque...
        val nearCenterOffset = (width * nearCenterRelativeOffset).toInt()
        if ((getPixel(nearCenterOffset, nearCenterOffset) and alphaChannelMask) == 0) return false
        // ... and one near a corner is transparent ...
        val nearCornerOffset = (width * nearCornerRelativeOffset).toInt()
        return (getPixel(nearCornerOffset, nearCornerOffset) and alphaChannelMask) == 0
        // ... then assume it's a circular icon surrounded by transparency - for kerning
    }

    private fun BitmapFont.Glyph.setRulesetIconGeometry(assumeRoundIcon: Boolean) {
        // This is a Ruleset object icon - first avoid "glue"'ing them to the next char..
        // ends up 2px for default font scale, 1px for min, 3px for max
        xadvance += (width * relativeAdvanceExtra).roundToInt()

        if (!assumeRoundIcon) return

        // Now, if we guessed it's round, do some kerning, only for the most conspicuous combos.
        // Will look ugly for very unusual Fonts - should we limit this to only default fonts?

        // Kerning is a sparse 2D array of up to 2^16 hints, each stored as byte, so this is
        // costly: kerningMap.size * Fonts.charToRulesetImageActor.size * 512 bytes
        // Which is 1.76MB for vanilla G&K rules.

        // Ends up -3px for default font scale, -2px for minimum, -4px for max
        val defaultKerning = (width * relativeKerning)
        for ((char, kerning) in kerningMap)
            setKerning(char.code, (defaultKerning * kerning).roundToInt())
    }

    private fun getPixmapForTextureName(regionName: String) =
        Fonts.extractPixmapFromTextureRegion(ImageGetter.getDrawable(regionName).region)

    private fun getPixmapFromChar(ch: Char): Pixmap {
        val textureName = Fonts.allSymbols[ch]
        if (textureName != null && ImageGetter.imageExists(textureName))
            return getPixmapForTextureName(textureName)
        val actor = FontRulesetIcons.charToRulesetImageActor[ch]
        if (actor != null)
            return try {
                    // This sometimes fails with a "Frame buffer couldn't be constructed: incomplete attachment" error, unclear why
                    FontRulesetIcons.getPixmapFromActor(actor)
                } catch (_: Exception) {
                    Pixmap(0, 0, Pixmap.Format.RGBA8888) // Empty space
                }
        if (DiacriticSupport.isEmpty())
            return fontImplementation.getCharPixmap(ch)
        return fontImplementation.getCharPixmap(DiacriticSupport.getStringFor(ch))
    }

    override fun getGlyphs(run: GlyphLayout.GlyphRun, str: CharSequence, start: Int, end: Int, lastGlyph: BitmapFont.Glyph?) {
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
