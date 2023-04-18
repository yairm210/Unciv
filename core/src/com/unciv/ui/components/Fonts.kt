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
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter


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
}

// If save in `GameSettings` need use invariantFamily.
// If show to user need use localName.
// If save localName in `GameSettings` may generate garbled characters by encoding.
class FontFamilyData(
    val localName: String,
    val invariantName: String = localName,
    val filePath: String? = null
) {

    // For serialization
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

    private fun getPixmap(fileName:String) = Fonts.extractPixmapFromTextureRegion(ImageGetter.getDrawable(fileName).region)

    private fun getPixmapFromChar(ch: Char): Pixmap {
        // Images must be 50*50px so they're rendered at the same height as the text - see Fonts.ORIGINAL_FONT_SIZE
        return when (ch) {
            Fonts.strength -> getPixmap("StatIcons/Strength")
            Fonts.rangedStrength -> getPixmap("StatIcons/RangedStrength")
            Fonts.range -> getPixmap("StatIcons/Range")
            Fonts.movement -> getPixmap("StatIcons/Movement")
            Fonts.turn -> getPixmap("EmojiIcons/Turn")
            Fonts.production -> getPixmap("EmojiIcons/Production")
            Fonts.gold -> getPixmap("EmojiIcons/Gold")
            Fonts.food -> getPixmap("EmojiIcons/Food")
            Fonts.science -> getPixmap("EmojiIcons/Science")
            Fonts.culture -> getPixmap("EmojiIcons/Culture")
            Fonts.faith -> getPixmap("EmojiIcons/Faith")
            Fonts.happiness -> getPixmap("EmojiIcons/Happiness")
            Fonts.greatArtist -> getPixmap("EmojiIcons/Great Artist")
            Fonts.greatEngineer -> getPixmap("EmojiIcons/Great Engineer")
            Fonts.greatGeneral -> getPixmap("EmojiIcons/Great General")
            Fonts.greatMerchant -> getPixmap("EmojiIcons/Great Merchant")
            Fonts.greatScientist -> getPixmap("EmojiIcons/Great Scientist")
            Fonts.death -> getPixmap("EmojiIcons/Death")

            MayaCalendar.tun -> getPixmap(MayaCalendar.tunIcon)
            MayaCalendar.katun -> getPixmap(MayaCalendar.katunIcon)
            MayaCalendar.baktun -> getPixmap(MayaCalendar.baktunIcon)
            in MayaCalendar.digits -> getPixmap(MayaCalendar.digitIcon(ch))
            in Fonts.charToRulesetImageActor -> Fonts.getPixmapFromActor(
                Fonts.charToRulesetImageActor[ch]!!)
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
        val textureData = textureRegion.texture.textureData
        if (!textureData.isPrepared) {
            textureData.prepare()
        }
        val pixmap = Pixmap(
            textureRegion.regionWidth,
            textureRegion.regionHeight,
            textureData.format
        )
        val textureDataPixmap = textureData.consumePixmap()
        pixmap.drawPixmap(
                textureDataPixmap, // The other Pixmap
                0, // The target x-coordinate (top left corner)
                0, // The target y-coordinate (top left corner)
                textureRegion.regionX, // The source x-coordinate (top left corner)
                textureRegion.regionY, // The source y-coordinate (top left corner)
                textureRegion.regionWidth, // The width of the area from the other Pixmap in pixels
                textureRegion.regionHeight // The height of the area from the other Pixmap in pixels
        )
        textureDataPixmap.dispose() // Prevent memory leak.
        return pixmap
    }

    val rulesetObjectNameToChar =HashMap<String, Char>()
    val charToRulesetImageActor = HashMap<Char, Actor>()
    // See https://en.wikipedia.org/wiki/Private_Use_Areas - char encodings 57344 63743 are not assigned
    var nextUnusedCharacterNumber = 57344
    fun addRulesetImages(ruleset:Ruleset) {
        rulesetObjectNameToChar.clear()
        charToRulesetImageActor.clear()
        nextUnusedCharacterNumber = 57344

        fun addChar(objectName:String, objectActor: Actor){
            val char = Char(nextUnusedCharacterNumber)
            nextUnusedCharacterNumber++
            rulesetObjectNameToChar[objectName] = char
            charToRulesetImageActor[char] = objectActor
        }

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
    }

    fun getPixmapFromActor(actor: Actor): Pixmap {
        val spriteBatch = SpriteBatch()

        val frameBuffer =
                FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.width, Gdx.graphics.height, false)
        frameBuffer.begin()

        Gdx.gl.glClearColor(0f,0f,0f,0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        spriteBatch.begin()
        actor.draw(spriteBatch, 1f)
        spriteBatch.end()

        val w = actor.width.toInt()
        val h = actor.height.toInt()
        val pixmap = Pixmap(w, h, Pixmap.Format.RGBA8888)
        Gdx.gl.glReadPixels(0, 0, w, h, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixmap.pixels)
        frameBuffer.end()

        // These need to be disposed so they don't clog up the RAM *but not right now*
        Gdx.app.postRunnable {
            spriteBatch.dispose()
            frameBuffer.dispose()
        }


        // Pixmap is now *upside down* so we need to flip it around the y axis
        for (i in 0..w)
            for (j in 0..h/2) {
                val topPixel = pixmap.getPixel(i,j)
                val bottomPixel = pixmap.getPixel(i, h-j)
                pixmap.drawPixel(i,j,bottomPixel)
                pixmap.drawPixel(i,h-j,topPixel)
            }

        return pixmap
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
    const val greatArtist = '♬'          // U+266C 'sixteenth note'
    const val greatEngineer = '⚒'       // U+2692 'hammer'
    const val greatGeneral = '⛤'       // U+26E4 'pentagram'
    const val greatMerchant = '⚖'      // U+2696 'scale'
    const val greatScientist = '⚛'      // U+269B 'atom'
    const val death = '☠' // U+2620 'skull and crossbones'

    val allSymbols = arrayOf<Char>(
        turn,
        strength, rangedStrength, range, movement,
        production, gold, food, science, culture, happiness, faith,
        greatArtist, greatEngineer, greatGeneral, greatMerchant, greatScientist,
        death,
        *MayaCalendar.allSymbols
    )
}
