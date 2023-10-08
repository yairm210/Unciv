package com.unciv.ui.components

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.PixmapPacker
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.getReadonlyPixmap
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.Portrait
import kotlin.math.ceil
import kotlin.math.roundToInt


// See https://en.wikipedia.org/wiki/Private_Use_Areas
// char encodings 57344 to 63743 (U+E000-U+F8FF) are not assigned
private const val UNUSED_CHARACTER_CODES_START = 57344
private const val UNUSED_CHARACTER_CODES_END = 63743

/** Implementations of FontImplementation will use different FontMetrics - AWT or Android.Paint,
 *  both have a class of that name, no other common point: thus we create an abstraction.
 *
 *  This is used by [Fonts.getPixmapFromActor] for vertical positioning.
 */
class FontMetricsCommon(
    /** (positive) distance from the baseline up to the recommended top of normal text */
    val ascent: Float,
    /** (positive) distance from the baseline down to the recommended bottom of normal text */
    val descent: Float,
    /** (positive) maximum distance from top to bottom of any text,
     *  including potentially empty space above ascent or below descent */
    val height: Float,
    /** (positive) distance from the bounding box top (as defined by [height])
     *  to the highest possible top of any text */

    // Note: This is NOT what typographical leading actually is, but redefined as extra empty space
    // on top, to make it easier to sync desktop and android. AWT has some leading but no measures
    // outside ascent+descent+leading, while Android has its leading always 0 but typically top
    // above ascent and bottom below descent.
    // I chose to map AWT's spacing to the top as I found the calculations easier to visualize.
    /** Space from the bounding box top to the top of the ascenders - includes line spacing and
     *  room for unusually high ascenders, as [ascent] is only a recommendation. */
    val leading: Float
)

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

// If save in `GameSettings` need use invariantFamily.
// If show to user need use localName.
// If save localName in `GameSettings` may generate garbled characters by encoding.
class FontFamilyData(
    val localName: String,
    val invariantName: String = localName,
    val filePath: String? = null
) {

    @Suppress("unused") // For serialization
    constructor() : this(default.localName, default.invariantName)

    // Implement kotlin equality contract such that _only_ the invariantName field is compared.
    override fun equals(other: Any?): Boolean {
        return if (other is FontFamilyData) invariantName == other.invariantName
        else super.equals(other)
    }

    override fun hashCode() = invariantName.hashCode()

    /** For SelectBox usage */
    override fun toString() = localName.tr()

    companion object {
        val default = FontFamilyData("Default Font", Fonts.DEFAULT_FONT_FAMILY)
    }
}

// This class is loosely based on libgdx's FreeTypeBitmapFontData
class NativeBitmapFontData(
    private val fontImplementation: FontImplementation
) : BitmapFontData(), Disposable {

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

    override fun getGlyph(ch: Char): Glyph = super.getGlyph(ch) ?: createAndCacheGlyph(ch)

    private fun createAndCacheGlyph(ch: Char): Glyph {
        val charPixmap = getPixmapFromChar(ch)

        val glyph = Glyph()
        glyph.id = ch.code
        glyph.width = charPixmap.width
        glyph.height = charPixmap.height
        glyph.xadvance = glyph.width

        // Check alpha to guess whether this is a round icon
        // Needs to be done before disposing charPixmap, and we want to do that soon
        val assumeRoundIcon = charPixmap.guessIsRoundSurroundedByTransparency()

        val rect = packer.pack(charPixmap)
        charPixmap.dispose()
        glyph.page = packer.pages.size - 1 // Glyph is always packed into the last page for now.
        glyph.srcX = rect.x.toInt()
        glyph.srcY = rect.y.toInt()

        if (ch.code >= UNUSED_CHARACTER_CODES_START)
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

    private fun Glyph.setRulesetIconGeometry(assumeRoundIcon: Boolean) {
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
        return when (ch) {
            in Fonts.allSymbols -> getPixmapForTextureName(Fonts.allSymbols[ch]!!)
            in Fonts.charToRulesetImageActor ->
                try {
                    // This sometimes fails with a "Frame buffer couldn't be constructed: incomplete attachment" error, unclear why
                    Fonts.getPixmapFromActor(Fonts.charToRulesetImageActor[ch]!!)
                } catch (_: Exception) {
                    Pixmap(0,0, Pixmap.Format.RGBA8888) // Empty space
                }
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

    /** All text is originally rendered in 50px (set in AndroidLauncher and DesktopLauncher), and then scaled to fit the size of the text we need now.
     * This has several advantages: It means we only render each character once (good for both runtime and RAM),
     * AND it means that our 'custom' emojis only need to be once size (50px) and they'll be rescaled for what's needed. */
    const val ORIGINAL_FONT_SIZE = 50f
    const val DEFAULT_FONT_FAMILY = ""

    lateinit var fontImplementation: FontImplementation
    lateinit var font: BitmapFont

    /** This resets all cached font data in object Fonts.
     *  Do not call from normal code - reset the Skin instead: `BaseScreen.setSkin()`
     */
    fun resetFont() {
        val settings = GUI.getSettings()
        fontImplementation.setFontFamily(settings.fontFamilyData, settings.getFontSize())
        font = fontImplementation.getBitmapFont()
        font.data.markupEnabled = true
    }

    /** Reduce the font list returned by platform-specific code to font families (plain variant if possible) */
    fun getSystemFonts(): Sequence<FontFamilyData> {
        return fontImplementation.getSystemFonts()
            .sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.localName })
    }

    /**
     * Turn a TextureRegion into a Pixmap.
     *
     * .dispose() must be called on the returned Pixmap when it is no longer needed, or else it will leave a memory leak behind.
     *
     * @return New Pixmap with all the size and pixel data from this TextureRegion copied into it.
     */
    // From https://stackoverflow.com/questions/29451787/libgdx-textureregion-to-pixmap
    fun extractPixmapFromTextureRegion(textureRegion: TextureRegion): Pixmap {
        val metrics = fontImplementation.getMetrics()
        val boxHeight = ceil(metrics.height).toInt()
        val boxWidth = ceil(metrics.ascent * textureRegion.regionWidth / textureRegion.regionHeight).toInt()
        // In case the region's aspect isn't 1:1, scale the rounded-up width back to a height with unrounded aspect ratio
        // (using integer math only, do the equivalent of float math rounded to closest integer)
        val drawHeight = (2 * textureRegion.regionHeight * boxWidth + 1) / textureRegion.regionWidth / 2
        // place region from top of bounding box down
        // Adding half the descent is empiric - should theoretically be leading only
        val drawY = ceil(metrics.leading + metrics.descent * 0.5f).toInt()

        val textureData = textureRegion.texture.textureData
        val textureDataPixmap = textureData.getReadonlyPixmap()

        val pixmap = Pixmap(boxWidth, boxHeight, textureData.format)

        // We're using the scaling drawPixmap so pixmap.filter is relevant - it defaults to BiLinear
        pixmap.drawPixmap(
            textureDataPixmap,              // The source Pixmap
            textureRegion.regionX,          // The source x-coordinate (top left corner)
            textureRegion.regionY,          // The source y-coordinate (top left corner)
            textureRegion.regionWidth,      // The width of the area from the other Pixmap in pixels
            textureRegion.regionHeight,     // The height of the area from the other Pixmap in pixels
            0,                         // The target x-coordinate (top left corner)
            drawY,                          // The target y-coordinate (top left corner)
            boxWidth,                       // The target width
            drawHeight,                     // The target height
        )

        return pixmap
    }

    val rulesetObjectNameToChar =HashMap<String, Char>()
    val charToRulesetImageActor = HashMap<Char, Actor>()
    private var nextUnusedCharacterNumber = UNUSED_CHARACTER_CODES_START

    fun addRulesetImages(ruleset: Ruleset) {
        rulesetObjectNameToChar.clear()
        charToRulesetImageActor.clear()
        nextUnusedCharacterNumber = UNUSED_CHARACTER_CODES_START

        fun addChar(objectName: String, objectActor: Actor) {
            if (nextUnusedCharacterNumber > UNUSED_CHARACTER_CODES_END) return
            val char = Char(nextUnusedCharacterNumber)
            nextUnusedCharacterNumber++
            rulesetObjectNameToChar[objectName] = char
            charToRulesetImageActor[char] = objectActor
        }

        // Note: If an image is missing, these will insert a white square in the font - acceptable in
        // most cases as these white squares will be visible elsewhere anyway. "Policy branch Complete"
        // is an exception, and therefore gets an existence test.

        for (resourceName in ruleset.tileResources.keys)
            addChar(resourceName, ImageGetter.getResourcePortrait(resourceName, ORIGINAL_FONT_SIZE))

        for (buildingName in ruleset.buildings.keys)
            addChar(buildingName, ImageGetter.getConstructionPortrait(buildingName, ORIGINAL_FONT_SIZE))

        for (unitName in ruleset.units.keys)
            addChar(unitName, ImageGetter.getConstructionPortrait(unitName, ORIGINAL_FONT_SIZE))

        for (promotionName in ruleset.unitPromotions.keys)
            addChar(promotionName, ImageGetter.getPromotionPortrait(promotionName, ORIGINAL_FONT_SIZE))

        for (improvementName in ruleset.tileImprovements.keys)
            addChar(improvementName, ImageGetter.getImprovementPortrait(improvementName, ORIGINAL_FONT_SIZE))

        for (techName in ruleset.technologies.keys)
            addChar(techName, ImageGetter.getTechIconPortrait(techName, ORIGINAL_FONT_SIZE))

        for (nation in ruleset.nations.values)
            addChar(nation.name, ImageGetter.getNationPortrait(nation, ORIGINAL_FONT_SIZE))

        for (policy in ruleset.policies.values) {
            val fileLocation = if (policy.name in ruleset.policyBranches)
                "PolicyBranchIcons/" + policy.name else "PolicyIcons/" + policy.name
            if (!ImageGetter.imageExists(fileLocation)) continue
            addChar(policy.name, ImageGetter.getImage(fileLocation).apply { setSize(ORIGINAL_FONT_SIZE) })
        }
    }

    private val frameBuffer by lazy {
        // Size here is way too big, but it's hard to know in advance how big it needs to be.
        // Gdx world coords, not pixels.
        FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.width, Gdx.graphics.height, false)
    }
    private val spriteBatch by lazy { SpriteBatch() }
    private val transform = Matrix4()  // for repeated reuse without reallocation

    /** Get a Pixmap for a "show ruleset icons as part of text" actor.
     *
     *  Draws onto an offscreen frame buffer and copies the pixels.
     *  Caller becomes owner of the returned Pixmap and is responsible for disposing it.
     *
     *  Size is such that the actor's height is mapped to the font's ascent (close to
     *  ORIGINAL_FONT_SIZE * GameSettings.fontSizeMultiplier), the actor is placed like a letter into
     *  the total height as given by the font's metrics, and width scaled to maintain aspect ratio.
     */
    fun getPixmapFromActor(actor: Actor): Pixmap {
        val (boxWidth, boxHeight) = scaleAndPositionActor(actor)

        val pixmap = Pixmap(boxWidth, boxHeight, Pixmap.Format.RGBA8888)

        frameBuffer.begin()

        Gdx.gl.glClearColor(0f,0f,0f,0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        spriteBatch.begin()
        actor.draw(spriteBatch, 1f)
        spriteBatch.end()
        Gdx.gl.glReadPixels(0, 0, boxWidth, boxHeight, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixmap.pixels)
        frameBuffer.end()

        return pixmap
    }

    /** Does the Actor scaling and positioning using metrics for [getPixmapFromActor]
     *  @return boxWidth to boxHeight
     */
    private fun scaleAndPositionActor(actor: Actor): Pair<Int, Int> {
        // We want our - mostly circular - icon to match a typical large uppercase letter in height
        // The drawing bounding box should have room, however for the font's leading and descent
        val metrics = fontImplementation.getMetrics()
        // Empiric slight size reduction - "correctly calculated" they just look a bit too big
        val scaledActorHeight = metrics.ascent * 0.93f
        val scaledActorWidth = actor.width * (scaledActorHeight / actor.height)
        val boxHeight = ceil(metrics.height).toInt()
        val boxWidth = ceil(scaledActorWidth).toInt()

        // Nudge down by the border size if it's a Portrait having one, so the "core" sits on the baseline
        val border = (actor as? Portrait)?.borderSize ?: 0f

        // Scale to desired font dimensions - modifying the actor this way is OK as the decisions are
        // the same each repetition, and size in the Group case or aspect ratio otherwise is preserved
        if (actor is Group) {
            // We can't just actor.setSize - a Group won't scale its children that way
            actor.isTransform = true
            val scale = scaledActorWidth / actor.width
            actor.setScale(scale, -scale)
            // Now the Actor is scaled, we need to position it at the baseline, Y from top of the box
            // The +1f is empirical because the result still looked off.
            actor.setPosition(0f, metrics.leading + metrics.ascent + border * scale + 1f)
        } else {
            // Assume it's an Image obeying Actor size, but needing explicit Y flipping
            // place actor from top of bounding box down
            // (don't think the Gdx (Y is upwards) way - due to the transformMatrix below)
            actor.setPosition(0f, metrics.leading + border)
            actor.setSize(scaledActorWidth, scaledActorHeight)
            transform.idt().scl(1f, -1f, 1f).trn(0f, boxHeight.toFloat(), 0f)
            spriteBatch.transformMatrix = transform
            // (copies matrix, not a set-by-reference, ignored when actor isTransform is on)
        }

        return boxWidth to boxHeight
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
    @Suppress("MemberVisibilityCanBePrivate") // offer for mods
    const val greatArtist = '♬'         // U+266C 'sixteenth note'
    @Suppress("MemberVisibilityCanBePrivate") // offer for mods
    const val greatEngineer = '⚒'       // U+2692 'hammer'
    @Suppress("MemberVisibilityCanBePrivate") // offer for mods
    const val greatGeneral = '⛤'        // U+26E4 'pentagram'
    @Suppress("MemberVisibilityCanBePrivate") // offer for mods
    const val greatMerchant = '⚖'       // U+2696 'scale'
    @Suppress("MemberVisibilityCanBePrivate") // offer for mods
    const val greatScientist = '⚛'      // U+269B 'atom'
    const val death = '☠'               // U+2620 'skull and crossbones'
    const val automate = '⛏'            // U+26CF 'pick'

    val allSymbols = mapOf(
        turn to "EmojiIcons/Turn",
        strength to "StatIcons/Strength",
        rangedStrength to "StatIcons/RangedStrength",
        range to "StatIcons/Range",
        movement to "StatIcons/Movement",
        production to "EmojiIcons/Production",
        gold to "EmojiIcons/Gold",
        food to "EmojiIcons/Food",
        science to "EmojiIcons/Science",
        culture to "EmojiIcons/Culture",
        happiness to "EmojiIcons/Happiness",
        faith to "EmojiIcons/Faith",
        greatArtist to "EmojiIcons/Great Artist",
        greatEngineer to "EmojiIcons/Great Engineer",
        greatGeneral to "EmojiIcons/Great General",
        greatMerchant to "EmojiIcons/Great Merchant",
        greatScientist to "EmojiIcons/Great Scientist",
        death to "EmojiIcons/Death",
        automate to "EmojiIcons/Automate",
        *MayaCalendar.allSymbols
    )
}
