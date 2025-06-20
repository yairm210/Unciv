package com.unciv.ui.components.fonts

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.ui.components.MayaCalendar
import com.unciv.ui.components.extensions.getReadonlyPixmap
import com.unciv.ui.components.fonts.Fonts.extractPixmapFromTextureRegion
import com.unciv.ui.components.fonts.Fonts.font
import com.unciv.ui.components.fonts.Fonts.fontImplementation
import kotlin.math.ceil

/**
 *  The "Font manager"
 *
 *  * We have one global [font], held by this object.
 *  * Platform-dependent code is linked through [fontImplementation].
 *  * Most of the work happens in [getGlyph][NativeBitmapFontData.getGlyph]. It dispatches to one of three handlers:
 *        - Normal text goes to the platform specific implemenation to fetch a glyph as pixels from the system.
 *        - A set of "symbols" (for strength, movement, death, war, gold and some others)
 *          comes from the texture atlas and is implemented in `extractPixmapFromTextureRegion`.
 *          They use Unicode code points which normally hold related symbols.
 *        - Icons for Ruleset objects are pre-build as Actors then drawn as pixels in `FontRulesetIcons`.
 *          They use code points from the 'Private use' range - see comments over there.
 *
 *  @see NativeBitmapFontData
 *  @see com.unciv.app.desktop.DesktopFont
 *  @see com.unciv.app.AndroidFont
 *  @see extractPixmapFromTextureRegion
 *  @see FontRulesetIcons
 */
object Fonts {

    /** All text is originally rendered in one size, and then scaled to fit the size of the text we need now.
     * This has several advantages: It means we only render each character once (good for both runtime and RAM),
     * AND it means that our 'custom' emojis only need to be once size (50px) and they'll be rescaled for what's needed. */
    const val ORIGINAL_FONT_SIZE = 100f
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
     * Helper for v-centering the text of Icon – Label -type components:
     *
     * Normal vertical centering uses the entire font height. In reality,
     * it is customary to align the centre from the baseline to the ascent
     * with the centre of the other element.  This function estimates the
     * correct amount to shift the text element.
     */
    fun getDescenderHeight(fontSize: Int): Float {
        val ratio = fontImplementation.getMetrics().run {
            descent / height }
        // For whatever reason, undershooting the adjustment slightly
        // causes rounding to work better
        return ratio * fontSize.toFloat() + 2.25f
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

    //region Symbols added to font from atlas textures
    const val turn = '⏳'               // U+23F3 'hourglass'
    const val strength = '†'            // U+2020 'dagger'
    const val rangedStrength = '‡'      // U+2021 'double dagger'
    const val movement = '➡'            // U+27A1 'black rightwards arrow'
    const val range = '…'               // U+2026 'horizontal ellipsis'
    const val health = '♡'              // U+2661 'white heart suit'
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

    //region Symbols that can be optionally added to the font from atlas textures
    // (a mod can override these, otherwise the font supplies the glyph)
    const val infinity = '∞'            // U+221E
    const val clock = '⌚'               // U+231A 'watch'
    const val star = '✯'                // U+272F 'pinwheel star'
    const val status = '◉'              // U+25C9 'fisheye'
    // The following two are used for sort visualization.
    // They may disappear (show as placeholder box) on Linux if you clean out asian fonts.
    // Alternatives: "↑" U+2191, "↓" U+2193 - much wider and weird spacing in some fonts (e.g. Verdana).
    // These are possibly the highest codepoints in use in Unciv -
    // Taken into account when limiting FontRulesetIcons codepoints (it respects the private area ending at U+F8FF)
    const val sortUpArrow = '￪'         // U+FFEA 'half wide upward arrow'
    const val sortDownArrow = '￬'       // U+FFEC 'half wide downward arrow'
    const val rightArrow = '→'          // U+2192, e.g. Battle table or event-based tutorials
    //endregion

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
        infinity to "EmojiIcons/Infinity",
        clock to "EmojiIcons/SortedByTime",
        star to "EmojiIcons/Star",
        status to "EmojiIcons/SortedByStatus",
        sortUpArrow to "EmojiIcons/SortedAscending",
        sortDownArrow to "EmojiIcons/SortedDescending",
        rightArrow to "EmojiIcons/RightArrow",
        *MayaCalendar.allSymbols
    )
    //endregion

}
