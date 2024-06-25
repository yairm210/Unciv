@file:OptIn(ExperimentalTime::class)

package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.unciv.GUI
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.LoadingImage.Style
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.absoluteValue
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Animated "double arrow" loading icon.
 *
 *  * By default, shows an empty transparent square, or a circle and/or an "idle" icon.
 *  * When [show] is called, the double-arrow loading icon is faded in and rotates.
 *  * When [hide] is called, the double-arrow fades out.
 *  * When [Style.minShowTime] is set, [hide] will make sure the "busy status" can be seen even if it was very short.
 *  * When GameSettings.continuousRendering is off, fade and rotation animations are disabled.
 *  * [animated] is public and can be used to override the 'continuousRendering' setting.
 *
 *  @param size Fixed forced size: prefWidth, minWidth, maxWidth and height counterparts will all return this.
 *  @param style Contains the visual and behavioral parameters
 */
//region fields
class LoadingImage(
    private val size: Float = 40f,
    private val style: Style = Style(),
) : WidgetGroup(), Disposable {
    // Note: Somewhat similar to IconCircleGroup - different alpha handling
    // Also similar to Stack, but since size is fixed, done much simpler

    private val circle: Image?
    private val idleIcon: Image?
    private val loadingIcon: Image
    var animated = GUI.getSettings().continuousRendering
    private var loadingStarted: TimeMark? = null
    //endregion

    data class Style(
        /** If not CLEAR, a Circle with this Color is layered at the bottom and the icons are resized to [innerSizeFactor] * `size` */
        val circleColor: Color = Color.CLEAR,
        /** Color for the animated "Loading" icon (drawn on top) */
        val loadingColor: Color = Color.WHITE,
        /** If not CLEAR, another icon is layered between circle and loading, e.g. symbolizing 'idle' or 'done' */
        val idleIconColor: Color = Color.CLEAR,
        /** Minimum shown time in ms including fades */
        val minShowTime: Int = 0,

        /** Texture name for the circle */
        val circleImageName: String = ImageGetter.circleLocation,
        /** Texture name for the idle icon */
        val idleImageName: String = ImageGetter.whiteDotLocation,
        /** Texture name for the loading icon */
        val loadingImageName: String = "OtherIcons/Loading",

        /** Size scale for icons when a Circle is used */
        val innerSizeFactor: Float = 0.75f,
        /** duration of fade-in and fade-out in seconds */
        val fadeDuration: Float = 0.2f,
        /** duration of rotation - seconds per revolution */
        val rotationDuration: Float = 4f,
        /** While loading is shown, the idle icon is semitransparent */
        val idleIconHiddenAlpha: Float = 0.4f
    )

    init {
        isTransform = false
        setSize(size, size)

        val innerSize: Float
        if (style.circleColor == Color.CLEAR) {
            circle = null
            innerSize = size
        } else {
            circle = ImageGetter.getImage(style.circleImageName)
            circle.color = style.circleColor
            circle.setSize(size)
            addActor(circle)
            innerSize = size * style.innerSizeFactor
        }

        if (style.idleIconColor == Color.CLEAR) {
            idleIcon = null
        } else {
            idleIcon = ImageGetter.getImage(style.idleImageName)
            idleIcon.color = style.idleIconColor
            idleIcon.setSize(innerSize)
            idleIcon.center(this)
            addActor(idleIcon)
        }

        loadingIcon = ImageGetter.getImage(style.loadingImageName).apply {
            color = style.loadingColor
            color.a = 0f
            setSize(innerSize)
            setOrigin(Align.center)
            isVisible = false
        }
        loadingIcon.center(this)
        addActor(loadingIcon)
    }

    fun show() {
        loadingStarted = TimeSource.Monotonic.markNow()
        loadingIcon.isVisible = true
        actions.clear()
        if (animated) {
            actions.add(FadeoverAction(1f, 0f), SpinAction())
        } else {
            loadingIcon.color.a = 1f
            idleIcon?.color?.a = style.idleIconHiddenAlpha
        }
    }

    fun hide(onComplete: (() -> Unit)? = null) =
        if (animated) hideAnimated(onComplete)
        else hideDelayed(onComplete)

    fun isShowing() = loadingIcon.isVisible && actions.isEmpty

    //region Hiding helpers
    private fun hideAnimated(onComplete: (() -> Unit)?) {
        actions.clear()
        actions.add(FadeoverAction(0f, getWaitDuration() - 2 * style.fadeDuration, onComplete))
    }

    private fun hideDelayed(onComplete: (() -> Unit)?) {
        val waitDuration = getWaitDuration()
        if (waitDuration == 0f) return setHidden()
        actions.clear()
        actions.add(Actions.delay(waitDuration, Actions.run {
            setHidden()
            onComplete?.invoke()
        }))
    }

    private fun setHidden() {
        actions.clear()
        loadingIcon.isVisible = false
        loadingIcon.color.a = 0f
        idleIcon?.color?.a = 1f
    }

    private fun getWaitDuration(): Float {
        val elapsed = loadingStarted?.elapsedNow()?.inWholeMilliseconds ?: 0
        if (elapsed >= style.minShowTime) return 0f
        return (style.minShowTime - elapsed) * 0.001f
    }
    //endregion

    //region Widget API
    override fun getPrefWidth() = size
    override fun getPrefHeight() = size
    override fun getMaxWidth() = size
    override fun getMaxHeight() = size
    //endregion

    override fun dispose() {
        clearActions()
    }

    private inner class FadeoverAction(
        private val endAlpha: Float,
        delay: Float,
        private val onComplete: (() -> Unit)? = null
    ) : TemporalAction(style.fadeDuration) {
        private var startAlpha = 0f
        private var totalChange = 1f

        init {
            if (delay > 0f) time = -delay
        }

        override fun update(percent: Float) {
            if (percent < 0f) return
            val alpha = startAlpha + percent * totalChange
            loadingIcon.color.a = alpha
            if (idleIcon == null) return
            idleIcon.color.a = (1f - alpha) * (1f - style.idleIconHiddenAlpha) + style.idleIconHiddenAlpha
        }

        override fun begin() {
            startAlpha = loadingIcon.color.a
            totalChange = endAlpha - startAlpha
            duration = style.fadeDuration * totalChange.absoluteValue
        }

        override fun end() {
            if (endAlpha == 0f) setHidden()
            onComplete?.invoke()
        }
    }

    private inner class SpinAction : TemporalAction(style.rotationDuration) {
        override fun update(percent: Float) {
            // The arrows point clockwise, but Actor.rotation is counterclockwise: negate.
            // Mapping to the 0..360 range is defensive, Actor itself doesn't care.
            loadingIcon.rotation = 360f * (1f - percent)
        }

        override fun end() {
            restart()
        }
    }

    @Suppress("unused")  // Used only temporarily for FasterUIDevelopment.DevElement
    object Testing {
        fun getFasterUIDevelopmentTester() = Table().apply {
            val testee = LoadingImage(52f, Style(
                circleColor = Color.NAVY,
                loadingColor = Color.SCARLET,
                idleIconColor = Color.CYAN,
                idleImageName = "OtherIcons/Multiplayer",
                minShowTime = 1500))
            defaults().pad(10f).center()
            add(testee)
            add(TextButton("Start", BaseScreen.skin).onClick {
                testee.show()
            })
            add(TextButton("Stop", BaseScreen.skin).onClick {
                testee.hide()
            })
            row()
            val check = CheckBox(" animated ", BaseScreen.skin)
            check.isChecked = testee.animated
            check.onChange { testee.animated = check.isChecked }
            add(check).colspan(3)
            pack()
        }
    }
}
