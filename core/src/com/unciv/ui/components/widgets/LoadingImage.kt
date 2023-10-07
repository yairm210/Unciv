@file:OptIn(ExperimentalTime::class)

package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
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
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Log
import kotlin.math.absoluteValue
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Animated "double arrow" loading icon.
 *
 *  * By default, shows an empty transparent square, or a circle and/or a "multiplayer" icon.
 *  * When [show] is called, the double-arrow loading icon is faded in and rotates.
 *  * When [hide] is called, the double-arrow fades out.
 *  * When [minShowTime] is set, [hide] will make sure the "busy status" can be seen even if it was very short.
 *  * When GameSettings.continuousRendering is off, fade and rotation animations are disabled.
 *  * [animated] is public and can be used to override the 'continuousRendering' setting.
 *
 *  @param size Fixed forced size: prefWidth, minWidth, maxWidth and height counterparts will all return this.
 *  @param circleColor If not CLEAR, a Circle with this Color is layered at the bottom and the icons are resized to 0.75 * [size]
 *  @param loadingColor Color for the animated "Loading" icon (drawn on top)
 *  @param multiplayerIconColor If not CLEAR, another icon is layered between circle and loading: used for MultiplayerStatusButton
 *  @param minShowTime Minimum shown time in ms including fades
 */
//region fields
class LoadingImage(
    private val size: Float = 40f,
    circleColor: Color = Color.CLEAR,
    loadingColor: Color = Color.WHITE,
    multiplayerIconColor: Color = Color.CLEAR,
    private val minShowTime: Int = 0
) : WidgetGroup(), Disposable {
    // Note: Somewhat similar to IconCircleGroup - different alpha handling
    // Also similar to Stack, but since size is fixed, done much simpler

    private val circle = if (circleColor == Color.CLEAR) null else ImageGetter.getImage(circleImageName)
    private val multiplayer = if (multiplayerIconColor == Color.CLEAR) null else ImageGetter.getImage(multiplayerImageName)
    private val loading = ImageGetter.getImage(loadingImageName)
    var animated = GUI.getSettings().continuousRendering
    private var loadingStarted: TimeMark? = null
    //endregion

    companion object {
        // If you need to customize any of these constants, copnvert them to a private val constructor parameter
        const val circleImageName = "OtherIcons/Circle"
        const val multiplayerImageName = "OtherIcons/Multiplayer"
        const val loadingImageName = "OtherIcons/Loading"

        /** Size scale for icons when a Circle is used */
        const val innerSizeFactor = 0.75f
        /** duration of fade-in and fade-out in seconds */
        const val fadeDuration = 0.2f
        /** duration of rotation - seconds per revolution */
        const val rotationDuration = 4f
        /** While loading is shown, the multiplayer icon is semitransparent */
        const val multiplayerHiddenAlpha = 0.4f

        @Suppress("unused")  // Used only temporarily for FasterUIDevelopment.DevElement
        fun getFasterUIDevelopmentTester() = Factories.getTester()
    }

    init {
        isTransform = false
        setSize(size, size)

        val innerSize = if (circle != null) {
            circle.color = circleColor
            circle.setSize(size)
            addActor(circle)
            size * innerSizeFactor
        } else size

        if (multiplayer != null) {
            multiplayer.color = multiplayerIconColor
            multiplayer.setSize(innerSize)
            multiplayer.center(this)
            addActor(multiplayer)
        }

        loading.color = loadingColor
        loading.color.a = 0f
        loading.setSize(innerSize)
        loading.setOrigin(Align.center)
        loading.center(this)
        loading.isVisible = false
        addActor(loading)
    }

    fun show() {
        loadingStarted = TimeSource.Monotonic.markNow()
        loading.isVisible = true
        actions.clear()
        if (animated) {
            actions.add(FadeoverAction(1f, 0f), SpinAction())
        } else {
            loading.color.a = 1f
            multiplayer?.color?.a = multiplayerHiddenAlpha
        }
    }

    fun hide() = if (animated) hideAnimated() else hideDelayed()

    //region Hiding helpers
    private fun hideAnimated() {
        actions.clear()
        actions.add(FadeoverAction(0f, getWaitDuration() - 2 * fadeDuration))
    }

    private fun hideDelayed() {
        val waitDuration = getWaitDuration()
        if (waitDuration == 0f) return setHidden()
        actions.clear()
        actions.add(Actions.delay(waitDuration, Actions.run { setHidden() }))
    }

    private fun setHidden() {
        actions.clear()
        loading.isVisible = false
        loading.color.a = 0f
        multiplayer?.color?.a = 1f
    }

    private fun getWaitDuration(): Float {
        val elapsed = loadingStarted?.elapsedNow()?.inWholeMilliseconds ?: 0
        if (elapsed >= minShowTime) return 0f
        return (minShowTime - elapsed) * 0.001f
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

    private inner class FadeoverAction(private val endAlpha: Float, delay: Float) : TemporalAction(fadeDuration) {
        private var startAlpha = 0f
        private var totalChange = 1f

        init {
            if (delay > 0f) time = -delay
        }

        override fun update(percent: Float) {
            if (percent < 0f) return
            val alpha = startAlpha + percent * totalChange
            loading.color.a = alpha
            if (multiplayer == null) return
            multiplayer.color.a = (1f - alpha) * (1f - multiplayerHiddenAlpha) + multiplayerHiddenAlpha
        }

        override fun begin() {
            Log.debug("FadeoverAction.begin (endAlpha=$endAlpha)")
            startAlpha = loading.color.a
            totalChange = endAlpha - startAlpha
            duration = fadeDuration * totalChange.absoluteValue
        }

        override fun end() {
            Log.debug("FadeoverAction.end (endAlpha=$endAlpha)")
            if (endAlpha == 0f) setHidden()
        }
    }

    private inner class SpinAction : TemporalAction(rotationDuration) {
        override fun update(percent: Float) {
            // The arrows point clockwise, but Actor.rotation is counterclockwise: negate.
            // Mapping to the 0..360 range is defensive, Actor itself doesn't care.
            loading.rotation = 360f * (1f - percent)
        }

        override fun end() {
            restart()
        }
    }

    private object Factories {
        fun getTester() = Table().apply {
            val testee = LoadingImage(52f, Color.NAVY, Color.SCARLET, Color.CYAN, 1500)
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
