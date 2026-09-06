package com.unciv.ui.popups

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings.LongPressIndicatorSetting
import com.unciv.ui.components.SmallButtonStyle
import com.unciv.ui.components.UncivTooltip
import com.unciv.ui.components.UncivTooltip.Companion.getEdgePoint
import com.unciv.ui.components.UncivTooltip.Companion.removeTooltips
import com.unciv.ui.components.extensions.allChildren
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.clearActivationActions
import com.unciv.ui.components.input.hasActivationHandler
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency

// Don't remove the blank line after "Clients should first implement" in the Kdoc below,
// or Android Studio will not render it as nested lists!
/**
 *  Namespace container for the Context-menu "system"
 *
 *  - Clients should first implement:
 *
 *    - [ContextMenus.IDescriptor], typically as object
 *    - [ContextMenus.IMenu], typically inheriting the standard implementation [ContextMenus.Menu] (successor to `AnimatedMenuPopup`)
 *    - [ContextMenus.IContext], depending on your needs, can be `IContext` itself when you don't need to pass any context.
 *  - In the containing UI code, call your new [IDescriptor.addContextMenu], passing as much context as your
 *    Pre-flight check [IDescriptor.isAvailable] or factory [IDescriptor.createMenu] need (typically none).
 *  - If calling code needs to change the context menu or needs to re-test its availability, simply call
 *    [IDescriptor.addContextMenu] again. If you need to remove all indicators and listeners, use [IDescriptor.removeContextMenu]
 *    (This is not necessary if your containing screen never updates or does so by clearing and rebuilding widgets).
 *  - Note you need to tell the descriptor implementation what the type of your menu is, to ensure that class meets requirements
 *    (In addition to [ContextMenus.IMenu], it currently also must be a [Popup], because the standard implementation needs its features).
 *  - You also need to explicitly tell your descriptor implementation what the type of your context is.
 *    If you don't need context, you can specify the interface for the descriptor declaration and pass
 *    `object : IContext {}` to the methods (or store a static instance of that here in the companion).
 */
interface ContextMenus {
    /**
     *  Class describing a context menu feature, instantiated before the actual UI is built.
     *  - A factory to build the UI, pre-flight checks, and potentially meta-info go here.
     *  - Meant to be implemented by an object, must remain stateless
     *  @see ContextMenus
     */
    interface IDescriptor<T, C>
    where T : IMenu, T : Popup, C : IContext {
        /** Test whether adding indicators and listener is meaningful, that is, whether showing the context at all makes sense */
        fun isAvailable(context: C): Boolean = true

        /** Factory creating the actual menu, mandatory */
        fun createMenu(context: C): T

        /** Helper to call from client code. Adds indicators and listener if [isAvailable] returns true,
         *  listener event runs [createMenu]. */
        fun Group.addContextMenu(context: C, indicatorMinSizeRelative: Float = 0.5f) {
            val hasContextMenu = hasActivationHandler(ActivationTypes.RightClick, noEquivalence = false)
            val shouldHaveContextMenu = isAvailable(context)
            if (hasContextMenu == shouldHaveContextMenu) return
            removeContextMenu()
            if (shouldHaveContextMenu) {
                Helpers.addIndicator(this, indicatorMinSizeRelative)
                onRightClick { createMenu(context) }
            }
        }

        /** Remove context menu listeners and indicators */
        fun Group.removeContextMenu() {
            clearActivationActions(ActivationTypes.RightClick, noEquivalence = false)
            removeTooltips<DesktopIndicator>()
            children.filter { it.name == AnimatedMenuPopup.indicatorAndroid }.forEach { it.remove() }
        }

        /** Helper to reposition indicators (the static ons on Android only),
         *  only needed if the layout changes between the [addContextMenu] call and UI validation. */
        fun Group.adjustContextMenuIndicators() {
            for (icon in allChildren().filter { it.name == indicatorAndroid })
                Helpers.adjustPosition(icon)
        }
    }

    /**
     *  Marker for the context you can pass to [IDescriptor] overloads.
     *  @see ContextMenus
     */
    interface IContext

    @Suppress("ConstPropertyName")
    companion object {
        const val indicatorAndroid = "OtherIcons/ContextMenuAvailableA"
        private const val indicatorDesktop = "OtherIcons/ContextMenuAvailableD"
        private const val maxIndicatorSize = 32f
        // These are partially to take up the "slack" inside the texture
        // (which is square, but the visual content for the Desktop one has a wide transparent margin)
        private const val indicatorXOffsetRelativeAndroid = 0.15f
        private const val indicatorXOffsetRelativeDesktop = 0.35f
        private val indicatorColor = Color.WHITE
        private fun Actor.getAlignedPosition(align: Int) = localToStageCoordinates(getEdgePoint(align))
    }

    /**
     *  A popup menu that animates on open/close, centered on a given Position (unlike other [Popup]s which are always stage-centered).
     *
     *  You must provide content by overriding [createContentTable] - see its doc.
     *
     *  The Popup opens automatically once created.
     *  **Meant to be used for small menus.** - otherwise use [ScrollableAnimatedMenuPopup].
     *  No default close button - recommended to simply use "click-behind".
     *
     *  The "click-behind" semi-transparent covering of the rest of the stage is much darker than a normal
     *  Popup (give the impression to take away illumination and spotlight the menu) and fades in together
     *  with the AnimatedMenuPopup itself. Closing the menu in any of the four ways will fade out everything
     *  inverting the fade-and-scale-in. Callbacks registered with [Popup.closeListeners] will run before the animation starts.
     *  Use [afterCloseCallback] instead if you need a notification after the animation finishes and the Popup is cleaned up.
     *
     *  Using the new `onRightClick` replacement [addContextMenu][IDescriptor.addContextMenu],
     *  the target actor will receive a decoration symbol over its bottom right, permanent on Android but on hover only on desktop.
     */
    interface IMenu {
        /** Will be notified after this Popup is closed, the animation finished, and cleanup is done (removed from stage). */
        var afterCloseCallback: (() -> Unit)?

        /** Allows differentiating the close reason in [afterCloseCallback] or [Popup.closeListeners]
         *  When still `false` in a callback, then ESC/BACK or the click-behind listener closed this. */
        val anyButtonWasClicked: Boolean

        /**
         *  Provides the Popup content.
         *
         *  Call super to fetch an empty default with prepared padding and background.
         *  You can use [getButton], which produces TextButtons slightly smaller than Unciv's default ones.
         *  The content adding functions offered by [Popup] or [Table] won't work.
         *  The content needs to be complete when the method finishes, it will be `pack()`ed and measured immediately.
         *
         *  Return `null` to abort the menu creation - nothing will be shown and the instance should be discarded.
         *  Useful if you need full context first to determine if any entry makes sense.
         */
        fun createContentTable(): Table?

        /**
         *  Creates a button - for use in your override of [AnimatedMenuPopup]'s [createContentTable].
         *
         *  On activation, it will set [anyButtonWasClicked], call [action], then close the Popup.
         */
        fun getButton(text: String, binding: KeyboardBinding, action: () -> Unit): TextButton
    }

    abstract class Menu private constructor (stage: Stage) : IMenu, Popup(stage, Scrollability.None) {
        /** Constructor taking a target Actor for alignment
         *  @param stage The stage this will be shown on, passed to Popup and used for clamping **`position`**
         *  @param actor Target [Actor]. This popup will be shown centered relative to it, and it will get a visual indicator.
         */
        constructor(stage: Stage, actor: Actor, align: Int = Align.topRight) : this(stage) {
            clickBehindToClose = true
            keyShortcuts.add(KeyCharAndCode.BACK) { close() }
            innerTable.remove()

            // Decouple the content creation from object initialization so it can access its own fields
            // (initialization order super->sub - see LeakingThis)
            Concurrency.runOnGLThread { createAndShow(actor.getAlignedPosition(align)) }
        }

        private val container: Container<Table> = Container()
        private val animationDuration = 0.33f
        private val backgroundColor = (background as NinePatchDrawable).patch.color
        private val smallButtonStyle by lazy { SmallButtonStyle() }

        override var afterCloseCallback: (() -> Unit)? = null
        final override var anyButtonWasClicked = false

        override fun createContentTable(): Table? = Table().apply {
            defaults().pad(5f, 15f, 5f, 15f).growX()
            background = BaseScreen.skinStrings.getUiBackground("General/AnimatedMenu", BaseScreen.skinStrings.roundedEdgeRectangleShape, Color.DARK_GRAY)
        }

        override fun getButton(text: String, binding: KeyboardBinding, action: () -> Unit) =
            text.toTextButton(smallButtonStyle).apply {
                onActivation(binding = binding) {
                    anyButtonWasClicked = true
                    action()
                    close()
                }
            }

        private fun createAndShow(position: Vector2) {
            val newInnerTable = createContentTable()
                ?: return  // Special case - we don't want the context menu after all. If cleanup should become necessary in that case, add here.
            newInnerTable.pack()
            container.actor = newInnerTable
            container.touchable = Touchable.childrenOnly
            container.isTransform = true
            container.setScale(0.05f)
            container.color.a = 0f

            open(true)  // this only does the screen-covering "click-behind" portion - and ensures this.stage is set

            // Note that coerceIn throws if min>max, so we defend against newInnerTable being bigger than the stage,
            // and padding helps the rounded edges to look more natural:
            val paddedHalfWidth = newInnerTable.width / 2 + newInnerTable.padLeft + newInnerTable.background.leftWidth + 5f
            val paddedHalfHeight = newInnerTable.height / 2 + newInnerTable.padTop + newInnerTable.background.topHeight + 5f
            container.setPosition(
                if (paddedHalfWidth * 2 > stage.width) stage.width / 2
                else position.x.coerceIn(paddedHalfWidth, stage.width - paddedHalfWidth),
                if (paddedHalfHeight * 2 > stage.height) stage.height / 2
                else position.y.coerceIn(paddedHalfHeight, stage.height - paddedHalfHeight)
            )
            super.addActor(container)

            // This "zoomfades" the container "in"
            container.addAction(
                Actions.parallel(
                    Actions.scaleTo(1f, 1f, animationDuration, Interpolation.fade),
                    Actions.fadeIn(animationDuration, Interpolation.fade)
                ))

            // This gradually darkens the "outside" at the same time
            backgroundColor.set(0)
            super.addAction(Actions.alpha(0.35f, animationDuration, Interpolation.fade).apply {
                color = backgroundColor
            })
        }

        override fun close() {
            val toNotify = closeListeners.toList()
            closeListeners.clear()
            for (listener in toNotify) listener()

            addAction(Actions.alpha(0f, animationDuration, Interpolation.fade).apply {
                color = backgroundColor
            })
            container.addAction(
                Actions.sequence(
                    Actions.parallel(
                        Actions.scaleTo(0.05f, 0.05f, animationDuration, Interpolation.fade),
                        Actions.fadeOut(animationDuration, Interpolation.fade)
                    ),
                    Actions.run {
                        container.remove()
                        super.close()
                        afterCloseCallback?.invoke()
                    }
                )
            )
        }
    }

    private object Helpers {
        fun addIndicator(actor: Group, indicatorMinSizeRelative: Float) {
            val setting = UncivGame.Current.settings.showLongPressIndicators
            if (setting == LongPressIndicatorSetting.Off) return
            if (setting == LongPressIndicatorSetting.Debug || Gdx.app.type == Application.ApplicationType.Android)
                addIndicatorAndroid(actor, indicatorMinSizeRelative)
            else addIndicatorDesktop(actor, indicatorMinSizeRelative)
        }

        fun adjustPosition(actor: Actor) {
            val setting = UncivGame.Current.settings.showLongPressIndicators
            if (setting == LongPressIndicatorSetting.Off) return
            if (setting != LongPressIndicatorSetting.Debug && Gdx.app.type == Application.ApplicationType.Android) return
            val x = actor.parent.width + actor.width * indicatorXOffsetRelativeAndroid
            actor.setPosition(x , 0f, Align.bottomRight)
        }

        private fun getIndicatorSize(actor: Group, indicatorMinSizeRelative: Float): Float {
            val actorHeight = (actor as? Layout)?.prefHeight ?: actor.height
            return maxIndicatorSize.coerceAtMost(actorHeight * indicatorMinSizeRelative)
        }

        private fun addIndicatorAndroid(actor: Group, indicatorMinSizeRelative: Float) {
            val icon = ImageGetter.getImage(indicatorAndroid, indicatorColor)
            icon.name = indicatorAndroid
            val size = getIndicatorSize(actor, indicatorMinSizeRelative)
            icon.setSize(size)
            val x = ((actor as? Layout)?.prefWidth ?: actor.width) + size * indicatorXOffsetRelativeAndroid
            icon.setPosition(x , 0f, Align.bottomRight)
            actor.addActor(icon)
        }

        private fun addIndicatorDesktop(actor: Group, indicatorMinSizeRelative: Float) {
            val icon = ImageGetter.getImage(indicatorDesktop, indicatorColor)
            val size = getIndicatorSize(actor, indicatorMinSizeRelative)
            icon.setSize(size)
            val offset = size * indicatorXOffsetRelativeDesktop
            actor.addListener(DesktopIndicator(actor, icon, offset))
        }
    }

    /** A subclass so [IDescriptor.removeContextMenu] can test for it */
    private class DesktopIndicator(
        actor: Group,
        icon: Image,
        offset: Float
    ) : UncivTooltip<Image>(
        target = actor,
        content = icon,
        targetAlign = Align.bottomRight,
        tipAlign = Align.bottomRight,
        offset = Vector2(offset, 0f)
    )
}
