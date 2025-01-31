package com.unciv.ui.components.fonts

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.UncivGame
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.tilesets.TileSetCache
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.fonts.FontRulesetIcons.getPixmapFromActor
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.Portrait
import com.unciv.ui.screens.civilopediascreen.CivilopediaImageGetters
import kotlin.math.ceil

/** Map all or most Ruleset icons as Actors to unused Char codepoints,
 *  [NativeBitmapFontData.getGlyph] can then paint them onto a PixMap,
 *  on demand, by calling [getPixmapFromActor]. */
object FontRulesetIcons {
    // See https://en.wikipedia.org/wiki/Private_Use_Areas
    // char encodings 57344 to 63743 (U+E000-U+F8FF) are not assigned
    internal const val UNUSED_CHARACTER_CODES_START = 57344
    private const val UNUSED_CHARACTER_CODES_END = 63743

    val rulesetObjectNameToChar = HashMap<String, Char>()
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
            addChar(resourceName, ImageGetter.getResourcePortrait(resourceName, Fonts.ORIGINAL_FONT_SIZE))

        for (buildingName in ruleset.buildings.keys)
            addChar(buildingName, ImageGetter.getConstructionPortrait(buildingName, Fonts.ORIGINAL_FONT_SIZE))

        for (unitName in ruleset.units.keys)
            addChar(unitName, ImageGetter.getConstructionPortrait(unitName, Fonts.ORIGINAL_FONT_SIZE))

        for (promotionName in ruleset.unitPromotions.keys)
            addChar(promotionName, ImageGetter.getPromotionPortrait(promotionName, Fonts.ORIGINAL_FONT_SIZE))

        for (improvementName in ruleset.tileImprovements.keys)
            addChar(improvementName, ImageGetter.getImprovementPortrait(improvementName, Fonts.ORIGINAL_FONT_SIZE))

        for (techName in ruleset.technologies.keys)
            addChar(techName, ImageGetter.getTechIconPortrait(techName, Fonts.ORIGINAL_FONT_SIZE))

        for (nation in ruleset.nations.values)
            addChar(nation.name, ImageGetter.getNationPortrait(nation, Fonts.ORIGINAL_FONT_SIZE))

        for (policy in ruleset.policies.values) {
            val fileLocation = if (policy.name in ruleset.policyBranches)
                "PolicyBranchIcons/" + policy.name else "PolicyIcons/" + policy.name
            if (!ImageGetter.imageExists(fileLocation)) continue
            addChar(policy.name, ImageGetter.getImage(fileLocation).apply { setSize(Fonts.ORIGINAL_FONT_SIZE) })
        }
        
        // Upon *game initialization* we can get here without the tileset being loaded yet
        //  in which case we can't add terrain icons
        if (TileSetCache.containsKey(UncivGame.Current.settings.tileSet)) {
            val tileSetStrings = TileSetStrings(ruleset, UncivGame.Current.settings)
            for (terrain in ruleset.terrains.values) {
                // These ensure that the font icons are correctly sized - tilegroup rendering works differently than others, to account for clickability vs rendered areas

                val tileGroup = CivilopediaImageGetters.terrainImage(terrain, ruleset, Fonts.ORIGINAL_FONT_SIZE, tileSetStrings)
                tileGroup.width *= 1.5f
                tileGroup.height *= 1.5f
                for (layer in tileGroup.children) layer.center(tileGroup)

                addChar(terrain.name, tileGroup)
            }
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

        return getPixmapFromActorBase(actor, boxWidth, boxHeight)
    }
    
    // Also required for dynamically generating pixmaps for pixmappacker
    fun getPixmapFromActorBase(actor: Actor, boxWidth: Int, boxHeight: Int): Pixmap {
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
        val metrics = Fonts.fontImplementation.getMetrics()
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
}
