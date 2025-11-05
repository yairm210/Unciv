package com.unciv.ui.components.widgets

import com.badlogic.gdx.Gdx
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
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.BaseScreen.Companion.skinStrings
import yairm210.purity.annotations.Pure


/**
 *  ### Features:
 *  * Fires click events only when the cursor is over that circular area
 *  * Fires enter/exit events only when the cursor enters/exits that circular area
 *  * Labels get a correction applied after centering so they actually look centered (using the font descender metric).
 *  * Does not implement [Layout], not resizable after instantiation (a WidgetGroup version was shelved @SomeTroglodyte).
 *
 *  ### Factories:
 *  * For the typical circled icon usecase, use [fromImage]
 *  * For a circled text symbol or similar, use [fromText]
 *  * For best flexibility, use the [builder][build]
 *
 *  ### Related:
 *  * [ClickableCircle][com.unciv.ui.components.input.ClickableCircle]
 *  * [toImageButton][com.unciv.ui.components.extensions.toImageButton]
 *  * [surroundWithCircle][com.unciv.ui.components.extensions.surroundWithCircle]
 *
 *  ### TODO:
 *  * Migrate [com.unciv.ui.screens.overviewscreen.GlobalPoliticsOverviewTable.DiplomacyGroup] (ball-of-yarn)
 *  * Migrate [com.unciv.ui.screens.worldscreen.worldmap.WorldMapHolder.unitActionOverlays] (move-to etc)
 */
open class CircularButton(size: Float) : Group() {
    // Necessary because `hit` for input events is only checked at the stage level to determine the topmost target.
    // When scene2d bubbles the event to ascendants, it ignores `hit`. This can lead to unwanted activation when
    // `this` is an ascendant of the child that was clicked, so we don't implement our relevant `hit` on the Group itself...
    private val clickReceiver = ClickReceiver()

    // These exist for subclasses, normal instantiation should prefer the builder.
    var centerActors: Boolean = true
    var clickableSize by clickReceiver::clickableSize

    init {
        isTransform = false
        touchable = Touchable.childrenOnly
        setSize(size, size)
        addActor(clickReceiver)
    }

    //region helpers

    private fun addHoverCallback(hoverCallback: (Boolean) -> Unit) {
        addListener(EnterExitListener(hoverCallback))
    }

    private fun hasHoverCallback() = listeners.any { it is EnterExitListener }

    private fun setHoverImage(altIconName: String) {
        val icon = children.last() as? Image
            ?: throw UnsupportedOperationException("setHoverImage requires the topmost child to be an Image")
        val normalDrawable = icon.drawable
        val altDrawable = ImageGetter.getDrawable(altIconName)
        addHoverCallback { entered ->
            icon.drawable = if (entered) altDrawable else normalDrawable
        }
    }

    private fun addHoverColorCallback(hoverColor: Color) {
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

    private class ClickReceiver : Actor() {
        var clickableSize: Float? = null
            set(value) {
                field = value
                calculate()
            }
        private val center = Vector2()
        private var radius = 0f
        private var maxDst2 = 0f

        private fun calculate() {
            val size = clickableSize ?: width.coerceAtMost(height)
            radius = size * 0.5f
            maxDst2 = radius * radius
        }

        override fun setSize(width: Float, height: Float) {
            super.setSize(width, height)
            center.set(width / 2, height / 2)
            calculate()
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
        /** Factory using an image.
         *  Custom configuration using [builderAction] is optional and can override [hoverColor] by defining any other [hover][CircularButtonBuilder.hover] method.
         */
        fun fromImage(
            iconName: String,
            size: Float,
            iconScale: Float = 0.75f,
            hoverColor: Color? = CircularButtonBuilder.highlightColor,
            innerCircleColor: Color? = CircularButtonBuilder.defaultColor,
            outerCircleColor: Color? = Color.WHITE,
            outerCircleWidth: Float = 1f,
            builderAction: (CircularButtonBuilder.() -> Unit)? = null
        ) = build(size) {
            circles(outerCircleColor, innerCircleColor, width = outerCircleWidth)
            image(iconName, (if (outerCircleColor == null) size else size - 2 * outerCircleWidth) * iconScale)
            if (builderAction != null) builderAction()
            if (!hasHover) hover(hoverColor)
            name(iconName)
        }

        /** Factory using a Label.
         *  Custom configuration using [builderAction] is optional and can override [hoverColor] by defining any other [hover][CircularButtonBuilder.hover] method.
         */
        fun fromText(
            text: String,
            size: Float,
            fontSize: Int = Constants.headingFontSize,
            hoverColor: Color? = CircularButtonBuilder.highlightColor,
            innerCircleColor: Color? = CircularButtonBuilder.defaultColor,
            outerCircleColor: Color? = Color.WHITE,
            outerCircleWidth: Float = 1f,
            builderAction: (CircularButtonBuilder.() -> Unit)? = null
        ) = build(size) {
            circles(outerCircleColor, innerCircleColor, width = outerCircleWidth)
            label(text, fontSize)
            if (builderAction != null) builderAction()
            if (!hasHover) hover(hoverColor)
            name(text)
        }

        /** Create a [CircularButton] using typical builder [syntax][CircularButtonBuilder]. */
        @Pure
        fun build(size: Float, builderAction: CircularButtonBuilder.() -> Unit) =
            CircularButtonBuilder(size).apply { builderAction() }.result()
    }

    /**
     *  Flexible builder for a [CircularButton].
     *
     *  Variables [clickableSize] and [centerActors] can be modified if required.
     *
     *  [defaultColor] and [highlightColor] are supplied for convenience and map to [baseColor][com.unciv.models.skins.SkinConfig.baseColor] from the SkinConfig and "highlight" from the [Skin][BaseScreen.skin], respectively.
     *
     *  All actor methods ([circle], [circles], [image], [label], [actor]) must be called in order, from bottom in Z-order to top.
     *
     *  All other methods ([hover], [name]) must be called after all actor methods.
     */
    class CircularButtonBuilder(private val size: Float) {
        private val result = CircularButton(size)
        internal val hasHover get() = result.hasHoverCallback()

        /** If set, overrides the diameter of the clickable area, even if the widget is resized later */
        var clickableSize by result.clickReceiver::clickableSize

        /** By default all actors are centered upon adding them. Turn this off or back on at any time during the build to prevent or reenable that. */
        var centerActors by result::centerActors

        companion object {
            val defaultColor by skinStrings.skinConfig::baseColor
            val highlightColor: Color get() = BaseScreen.skin.getColor("highlight")
        }

        /** Add an image by its texture path ([name]), sized to the widget by default. */
        fun image(name: String, size: Float = this.size): Image {
            val image = ImageGetter.getImage(name)
            image.setSize(size)
            image.touchable = Touchable.disabled
            actor(image)
            return image
        }

        /** Add a circle, sized to the widget by default. */
        fun circle(color: Color = defaultColor, size: Float = this.size): Image {
            val circle = ImageGetter.getCircle(color, size)
            circle.touchable = Touchable.disabled
            actor(circle)
            return circle
        }

        /** Add concentric circles with the given [colors], each [width] units smaller than the last. */
        fun circles(vararg colors: Color?, width: Float = 1f) {
            var size = this.size
            for (color in colors) {
                if (color == null) continue
                if (size <= 0f) throw IllegalArgumentException("More circles than there's space for")
                circle(color, size)
                size -= width
            }
        }

        /** Add a label, not scaled to the widget's size but by [fontSize] only. Centering ignores descenders. */
        fun label(text: String, fontSize: Int = Constants.headingFontSize, fontColor: Color = Color.WHITE): Label {
            val label = text.toLabel(fontColor, fontSize, Align.center)
            label.touchable = Touchable.disabled
            actor(label)
            return label
        }

        /** Adds any [actor] */
        fun actor(actor: Actor) = result.addActor(actor)

        /** Adds a listener that swaps the top most actor's color when the cursor enters the circle and restores it when leaving. */
        fun hover(color: Color?) {
            if (color == null) return
            result.addHoverColorCallback(color)
        }

        /** Adds a listener that swaps the icon drawable when the cursor enters the circle and restores it when leaving.
         *  Topmost actor must be an [Image].
         *  @param name A texture path recognized by [ImageGetter.getDrawable]
         */
        fun hover(name: String) {
            result.setHoverImage(name)
        }

        /** Adds a listener that notifies your [callback] when the cursor enters (argument is `true`) or leaves (`false`) the circle */
        fun hover(callback: (Boolean) -> Unit) {
            result.addHoverCallback(callback)
        }

        /** Adds a click handler opening an external link in a browser */
        fun link(uri: String) {
            result.onActivation { Gdx.net.openURI(uri) }
        }

        /** Marks the widget with a name recognizable in Scene2D debug mode */
        fun name(text: String) {
            result.name = "${CircularButton::class.simpleName} (\"$text\")"
        }

        // Exists so we could add any finalization when required
        internal fun result() = result
    }
}
