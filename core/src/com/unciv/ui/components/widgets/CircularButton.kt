package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
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
    clickableSize: Float? = null,
    private val centerActors: Boolean = true,
    actors: Sequence<Actor>? = null,
    hoverCallback: ((Boolean) -> Unit)? = null
) : Group() {
    // Necessary because `hit` for input events is only checked at the stage level to determine the topmost target.
    // When scene2d bubbles the event to ascendants, it ignores `hit`. This can lead to unwanted activation when
    // `this` is an ascendant of the child that was clicked, so we don't implement our relevant `hit` on the Group itself...
    private val clickReceiver = ClickReceiver(clickableSize)

    init {
        isTransform = false
        touchable = Touchable.childrenOnly
        setSize(size, size)
        addActor(clickReceiver)
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

    fun setHoverImage(altIconName: String) {
        val icon = children.last() as? Image
            ?: throw UnsupportedOperationException("setHoverImage requires the topmost child to be an Image")
        val normalDrawable = icon.drawable
        val altDrawable = ImageGetter.getDrawable(altIconName)
        addHoverCallback { entered ->
            icon.drawable = if (entered) altDrawable else normalDrawable
        }
    }

    //region helpers

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

    private class ClickReceiver(private val clickableSize: Float?) : Actor() {
        private val center = Vector2()
        private var radius = 0f
        private var maxDst2 = 0f

        override fun setSize(width: Float, height: Float) {
            center.set(width / 2, height / 2)
            val size = clickableSize ?: width.coerceAtMost(height)
            radius = size * 0.5f
            maxDst2 = radius * radius
            super.setSize(width, height)
        }
        override fun hit(x: Float, y: Float, touchable: Boolean): Actor? =
            if (center.dst2(x, y) <= maxDst2) this else null
        override fun act(delta: Float) {}
        override fun drawDebug(shapes: ShapeRenderer) {
            if (!debug) return
            super.drawDebug(shapes)
            shapes.color = Color.DARK_GRAY
            shapes.circle(x + center.x, y + center.y, radius)
        }
    }

    //endregion
    //region overrides

    override fun setSize(width: Float, height: Float) {
        clickReceiver.setSize(width, height)
        super.setSize(width, height)
    }

    private fun centerNewActor(actor: Actor) {
        if (!centerActors) return
        actor.center(this)
        if (actor is Label) {
            actor.y -= Fonts.getDescenderHeight(Fonts.ORIGINAL_FONT_SIZE.toInt()) * actor.fontScaleY / 2
        }
        actor.setOrigin(Align.center)
    }

    override fun addActor(actor: Actor) {
        centerNewActor(actor)
        super.addActor(actor)
    }

    override fun addActorAt(index: Int, actor: Actor) {
        centerNewActor(actor)
        super.addActorAt(if (index == 0) 1 else index, actor) // leave clickReceiver at the bottom
    }

    override fun addListener(listener: EventListener?): Boolean {
        // Hijack the listener to our ClickReceiver, but also _share_ the ActorAttachments object the listener will need
        clickReceiver.userObject = this.userObject
        return clickReceiver.addListener(listener)
    }

    //endregion

    companion object {
        /** Factory using an image */
        fun fromImage(
            iconName: String,
            size: Float,
            iconScale: Float = 0.75f,
            hoverColor: Color? = defaultHoverColor(),
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
            hoverColor: Color? = defaultHoverColor(),
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

        private fun defaultHoverColor() = BaseScreen.skin.getColor("highlight")

        private fun getCircleActors(
            size: Float,
            innerCircleColor: Color?,
            outerCircleColor: Color?,
            outerCircleWidth: Float
        ) = getCircle(outerCircleColor, size) + getCircle(innerCircleColor, size - 2 * outerCircleWidth)

        private fun getIconActor(
            iconName: String,
            innerCircleSize: Float,
            iconScale: Float,
        ) = sequence<Actor> {
            val image = ImageGetter.getImage(iconName)
            image.setSize(innerCircleSize * iconScale)
            image.touchable = Touchable.disabled
            yield(image)
        }

        private fun getLabelActor(text: String, fontSize: Int) = sequence<Actor> {
            val label = text.toLabel(fontSize = fontSize)
            label.setAlignment(Align.center)
            label.touchable = Touchable.disabled
            yield(label)
        }

        private fun getCircle(color: Color?, size: Float) = sequence<Actor> {
            if (color == null) return@sequence
            val circle = ImageGetter.getCircle(color, size)
            circle.touchable = Touchable.disabled
            yield(circle)
        }
    }
}
