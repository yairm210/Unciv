package com.unciv.ui.components.fonts

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.components.MayaCalendar
import com.unciv.ui.components.extensions.getReadonlyPixmap
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.Portrait
import kotlin.math.ceil

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

    // See https://en.wikipedia.org/wiki/Private_Use_Areas
    // char encodings 57344 to 63743 (U+E000-U+F8FF) are not assigned
    internal const val UNUSED_CHARACTER_CODES_START = 57344
    private const val UNUSED_CHARACTER_CODES_END = 63743

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
