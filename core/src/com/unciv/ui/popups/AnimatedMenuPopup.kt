package com.unciv.ui.popups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.unciv.ui.components.SmallButtonStyle
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency

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
 *  @param stage The stage this will be shown on, passed to Popup and used for clamping **`position`**
 *  @param position stage coordinates to show this centered over - clamped so that nothing is clipped outside the [stage]
 */
open class AnimatedMenuPopup(
    stage: Stage,
    position: Vector2
) : Popup(stage, Scrollability.None) {
    private val container: Container<Table> = Container()
    private val animationDuration = 0.33f
    private val backgroundColor = (background as NinePatchDrawable).patch.color
    private val smallButtonStyle by lazy { SmallButtonStyle() }

    /** Will be notified after this Popup is closed, the animation finished, and cleanup is done (removed from stage). */
    var afterCloseCallback: (() -> Unit)? = null

    /** Allows differentiating the close reason in [afterCloseCallback] or [closeListeners]
     *  When still `false` in a callback, then ESC/BACK or the click-behind listener closed this. */
    var anyButtonWasClicked = false
        private set

    companion object {
        /** Get stage coords of an [actor]'s right edge center, to help position an [AnimatedMenuPopup].
         *  Note the Popup will center over this point.
         */
        fun getActorTopRight(actor: Actor): Vector2 = actor.localToStageCoordinates(Vector2(actor.width, actor.height / 2))
    }

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
    open fun createContentTable(): Table? = Table().apply {
        defaults().pad(5f, 15f, 5f, 15f).growX()
        background = BaseScreen.skinStrings.getUiBackground("General/AnimatedMenu", BaseScreen.skinStrings.roundedEdgeRectangleShape, Color.DARK_GRAY)
    }

    init {
        clickBehindToClose = true
        keyShortcuts.add(KeyCharAndCode.BACK) { close() }
        innerTable.remove()

        // Decouple the content creation from object initialization so it can access its own fields
        // (initialization order super->sub - see LeakingThis)
        Concurrency.runOnGLThread { createAndShow(position) }
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
        val paddedHalfWidth = newInnerTable.width / 2 + 2f
        val paddedHalfHeight = newInnerTable.height / 2 + 2f
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

    /**
     *  Creates a button - for use in [AnimatedMenuPopup]'s `contentBuilder` parameter.
     *
     *  On activation it will set [anyButtonWasClicked], call [action], then close the Popup.
     */
    fun getButton(text: String, binding: KeyboardBinding, action: () -> Unit) =
        text.toTextButton(smallButtonStyle).apply {
            onActivation(binding = binding) {
                anyButtonWasClicked = true
                action()
                close()
            }
        }
}
