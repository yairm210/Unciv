package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen.Companion.skinStrings


/**
 *  ### Features:
 *  * Fires click events only when the cursor is over that circular area
 *  * Fires enter/exit events only when the cursor enters/exits that circular area
 *  * Labels get a correction applied after centering so they actually look centered (using the font descender metric).
 *  * Does not implement [Layout], not resizable after instantiation (a WidgetGroup version was shelved @SomeTroglodyte).
 *
 *  ### Constructor or factories:
 *  * The default constructor allows stacking arbitrary actors and any kind of hover callback.
 *  * For the typical circled icon usecase, use [fromImage]
 *  * For a circled text symbol or similar, use [fromText]
 *
 *  ### Related:
 *  * [ClickableCircle][com.unciv.ui.components.input.ClickableCircle]
 *  * [toImageButton][com.unciv.ui.components.extensions.toImageButton]
 *  * [surroundWithCircle][com.unciv.ui.components.extensions.surroundWithCircle]
 *
 *  ### TODO:
 *  * Migrate [com.unciv.ui.screens.overviewscreen.GlobalPoliticsOverviewTable.DiplomacyGroup] (ball-of-yarn)
 *  * Migrate [com.unciv.ui.screens.worldscreen.worldmap.WorldMapHolder.unitActionOverlays] (move-to etc)
 *
 *  @param size Initial (square) size, actors added before any resize will be measured relative to this
 *  @param clickableSize If set, overrides the diameter of the clickable area, even if the widget is resized later
 *  @param centerActors If unset, [addActor] won't move anything, by default all actors are centered
 *  @param actors Provide initial actors - the first goes to the bottom of the z-order, the last to the top
 *  @param hoverCallback Optional callback fired when the mouse enters or exits the circular clickable area
 */
open class CircularButton(
    size: Float,
    private val clickableSize: Float? = null,
    private val centerActors: Boolean = true,
    actors: Sequence<Actor>? = null,
    hoverCallback: ((Boolean) -> Unit)? = null
) : Group() {
    private val center = Vector2(size / 2, size / 2)
    private val maxDst2 = (clickableSize ?: size).let { it * it } / 4f

    init {
        isTransform = false
        touchable = Touchable.enabled
        setSize(size, size)
        if (actors != null)
            addActors(actors)
        if (hoverCallback != null)
            addHoverCallback(hoverCallback)
    }

    /** Add several actors */
    fun addActors(actors: Sequence<Actor>) {
        for (actor in actors)
            addActor(actor)
    }

    /** Add a circle, filling the container by default */
    fun addCircle(color: Color, size: Float = width) = ImageGetter.getCircle(color, size).apply { 
        addActor(this)
    }

    /** Adds a listener that notifies your [hoverCallback] when the cursor enters (argument is `true`) or leaves (`false`) the circle */
    fun addHoverCallback(hoverCallback: (Boolean) -> Unit) {
        addListener(EnterExitListener(hoverCallback))
    }

    private fun addHoverColorCallback(hoverColor: Color?) {
        if (hoverColor == null) return
        val iconOrLabel = children.last()
        val originalColor = iconOrLabel.color.cpy()
        addHoverCallback { entered ->
            iconOrLabel.color = if (entered) hoverColor else originalColor
        }
    }

    private class EnterExitListener(private val hoverCallback: ((Boolean) -> Unit)) : InputListener() {
        override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
            hoverCallback(true)
        }
        override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
            hoverCallback(false)
        }
    }

    override fun addActor(actor: Actor) {
        if (centerActors) {
            actor.center(this)
            if (actor is Label) {
                actor.y -= Fonts.getDescenderHeight(Fonts.ORIGINAL_FONT_SIZE.toInt()) * actor.fontScaleY / 2
            }
            actor.setOrigin(Align.center)
        }
        super.addActor(actor)
    }

    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? {
        // ask children first, then check whether inside the circle
        val child = super.hit(x, y, touchable)
        if (child != null && child != this) return child
        if (touchable && this.touchable != Touchable.enabled) return null
        if (!isVisible) return null
        return if (center.dst2(x, y) <= maxDst2) this else null
    }

    companion object {
        /** Factory using an image */
        fun fromImage(
            iconName: String,
            size: Float,
            iconScale: Float = 0.75f,
            hoverColor: Color? = Color.RED,
            innerCircleColor: Color? = skinStrings.skinConfig.baseColor,
            outerCircleColor: Color? = Color.WHITE,
            outerCircleWidth: Float = 1f
        ) = CircularButton(size,
            actors = getCircleActors(size, innerCircleColor, outerCircleColor, outerCircleWidth) +
            getIconActor(iconName, if (outerCircleColor == null) size else size - 2 * outerCircleWidth, iconScale)
        ).apply {
            name = "${CircularButton::class.simpleName} (\"$iconName\")"
            addHoverColorCallback(hoverColor)
        }

        /** Factory using a Label */
        fun fromText(
            text: String,
            size: Float,
            fontSize: Int = Constants.headingFontSize,
            hoverColor: Color? = Color.RED,
            innerCircleColor: Color? = skinStrings.skinConfig.baseColor,
            outerCircleColor: Color? = Color.WHITE,
            outerCircleWidth: Float = 1f
        ) = CircularButton(size,
            actors = getCircleActors(size, innerCircleColor, outerCircleColor, outerCircleWidth) +
            getLabelActor(text, fontSize)
        ).apply {
            name = "${CircularButton::class.simpleName} (\"$text\")"
            addHoverColorCallback(hoverColor)
        }

        private fun getCircleActors(
            size: Float,
            innerCircleColor: Color?,
            outerCircleColor: Color?,
            outerCircleWidth: Float
        ) = sequence<Actor> {
            if (outerCircleColor != null)
                yield(ImageGetter.getCircle(outerCircleColor, size))
            if (innerCircleColor != null)
                yield(ImageGetter.getCircle(innerCircleColor, size - 2 * outerCircleWidth))
        }

        private fun getIconActor(
            iconName: String,
            innerCircleSize: Float,
            iconScale: Float,
        ) = sequence<Actor> {
            val image = ImageGetter.getImage(iconName)
            image.setSize(innerCircleSize * iconScale)
            yield(image)
        }

        private fun getLabelActor(text: String, fontSize: Int) = sequence<Actor> {
            val label = text.toLabel(fontSize = fontSize)
                .apply { setAlignment(Align.center) }
            yield(label)
        }
    }
}
