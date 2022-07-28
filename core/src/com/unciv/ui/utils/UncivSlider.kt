package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.unciv.Constants
import com.unciv.models.UncivSound
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import kotlin.math.abs
import kotlin.math.sign

/**
 * Modified Gdx [Slider]
 *
 * Has +/- buttons at the end for easier single steps
 * Shows a timed tip with the actual value every time it changes
 * Disables listeners of any ScrollPanes this is nested in while dragging
 *
 * Note: No attempt is made to distinguish sources of value changes, so the initial setting
 * of the value when a screen is initialized will also trigger the 'tip'. This is intentional.
 *
 * @param min           Initializes [Slider.min]
 * @param max           Initializes [Slider.max]
 * @param step          Initializes [Slider.stepSize]
 * @param vertical      Initializes [Slider.vertical]
 * @param plusMinus     Enable +/- buttons
 * @param onChange      Optional lambda gets called with the current value on change
 */
class UncivSlider (
    min: Float,
    max: Float,
    step: Float,
    vertical: Boolean = false,
    plusMinus: Boolean = true,
    initial: Float,
    sound: UncivSound = UncivSound.Slider,
    private val getTipText: ((Float) -> String)? = null,
    onChange: ((Float) -> Unit)? = null
): Table(BaseScreen.skin) {
    companion object {
        /** Can be passed directly to the [getTipText] constructor parameter */
        fun formatPercent(value: Float): String {
            return (value * 100f + 0.5f).toInt().toString() + "%"
        }
        // constants for geometry tuning
        const val plusMinusFontSize = Constants.defaultFontSize
        const val plusMinusCircleSize = 20f
        const val padding = 5f                  // padding around the Slider, doubled between it and +/- buttons
        const val hideDelay = 3f                // delay in s to hide tooltip
        const val tipAnimationDuration = 0.2f   // tip show/hide duration in s
    }

    // component widgets
    private val slider = Slider(min, max, step, vertical, BaseScreen.skin)
    private val minusButton: IconCircleGroup?
    private val plusButton: IconCircleGroup?
    private val tipLabel = "".toLabel(Color.LIGHT_GRAY)
    private val tipContainer: Container<Label> = Container(tipLabel)
    private val tipHideTask: Timer.Task

    // copies of maliciously protected Slider members
    private var snapToValues: FloatArray? = null
    private var snapThreshold: Float = 0f

    // Compatibility with default Slider
    val minValue: Float
        get() = slider.minValue
    @Suppress("unused") // Part of the Slider API
    val maxValue: Float
        get() = slider.maxValue
    var value: Float
        get() = slider.value
        set(newValue) {
            slider.value = newValue
            valueChanged()
        }
    var stepSize: Float
        get() = slider.stepSize
        set(value) {
            slider.stepSize = value
            stepChanged()
        }
    /** Returns true if the slider is being dragged. */
    @Suppress("unused") // Part of the Slider API
    val isDragging: Boolean
        get() = slider.isDragging
    /** Disables the slider - visually (if the skin supports it) and blocks interaction */
    var isDisabled: Boolean
        get() = slider.isDisabled
        set(value) {
            slider.isDisabled = value
            setPlusMinusEnabled()
        }
    /** Sets the range of this slider. The slider's current value is clamped to the range. */
    fun setRange(min: Float, max: Float) {
        slider.setRange(min, max)
        setPlusMinusEnabled()
    }
    /** Will make this slider snap to the specified values, if the knob is within the threshold. */
    fun setSnapToValues(values: FloatArray?, threshold: Float) {
        snapToValues = values       // make a copy so our plus/minus code can snap
        snapThreshold = threshold
        slider.setSnapToValues(values, threshold)
    }

    // java format string for the value tip, set by changing stepSize
    private var tipFormat = "%.1f"

    /** Prevents hiding the value tooltip over the slider knob */
    var permanentTip = false

    // Detect changes in isDragging
    private var hasFocus = false

    init {
        tipLabel.setOrigin(Align.center)
        tipContainer.touchable = Touchable.disabled
        tipHideTask = object : Timer.Task() {
            override fun run() {
                hideTip()
            }
        }

        stepChanged()   // Initialize tip formatting

        if (plusMinus) {
            minusButton = "-".toLabel(Color.BLACK, plusMinusFontSize)
                .apply { setAlignment(Align.center) }
                .surroundWithCircle(plusMinusCircleSize)
            minusButton.onClick {
                addToValue(-stepSize)
            }
            add(minusButton).apply {
                if (vertical) padBottom(padding) else padLeft(padding)
            }
            if (vertical) row()
        } else minusButton = null

        add(slider).pad(padding).fill()

        if (plusMinus) {
            if (vertical) row()
            plusButton = "+".toLabel(Color.BLACK, plusMinusFontSize)
                .apply { setAlignment(Align.center) }
                .surroundWithCircle(plusMinusCircleSize)
            plusButton.onClick {
                addToValue(stepSize)
            }
            add(plusButton).apply {
                if (vertical) padTop(padding) else padRight(padding)
            }
        } else plusButton = null

        row()
        value = initial  // set initial value late so the tooltip can work with the layout

        // Add the listener late so the setting of the initial value is silent
        slider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (slider.isDragging != hasFocus) {
                    hasFocus = slider.isDragging
                    if (hasFocus)
                        killScrollPanes()
                    else
                        resurrectScrollPanes()
                }
                valueChanged()
                onChange?.invoke(slider.value)
                SoundPlayer.play(sound)
            }
        })
    }

    // Helper for plus/minus button onClick, non-trivial only if setSnapToValues is used
    private fun addToValue(delta: Float) {
        // un-snapping with Shift is taken from Slider source, and the loop mostly as well
        // with snap active, plus/minus buttons will go to the next snap position regardless of stepSize
        // this could be shorter if Slider.snap(), Slider.snapValues and Slider.threshold weren't protected
        if (snapToValues?.isEmpty() != false ||
                Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ||
                Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
        ) {
            value += delta
            return
        }
        var bestDiff = -1f
        var bestIndex = -1
        for ((i, snapValue) in snapToValues!!.withIndex()) {
            val diff = abs(value - snapValue)
            if (diff <= snapThreshold) {
                if (bestIndex == -1 || diff < bestDiff) {
                    bestDiff = diff
                    bestIndex = i
                }
            }
        }
        bestIndex += delta.sign.toInt()
        if (bestIndex !in snapToValues!!.indices) return
        value = snapToValues!![bestIndex]
    }

    // Visual feedback
    private fun valueChanged() {
        if (getTipText == null)
            tipLabel.setText(tipFormat.format(slider.value))
        else
            @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") // warning wrong, without !! won't compile
            tipLabel.setText(getTipText!!(slider.value))
        if (!tipHideTask.isScheduled) showTip()
        tipHideTask.cancel()
        if (!permanentTip)
            Timer.schedule(tipHideTask, hideDelay)
        setPlusMinusEnabled()
    }

    private fun setPlusMinusEnabled() {
        val enableMinus = slider.value > slider.minValue && !isDisabled
        minusButton?.touchable = if(enableMinus) Touchable.enabled else Touchable.disabled
        minusButton?.apply {circle.color.a = if(enableMinus) 1f else 0.5f}
        val enablePlus = slider.value < slider.maxValue && !isDisabled
        plusButton?.touchable = if(enablePlus) Touchable.enabled else Touchable.disabled
        plusButton?.apply {circle.color.a = if(enablePlus) 1f else 0.5f}
    }

    private fun stepChanged() {
        tipFormat = when {
            stepSize > 0.99f -> "%.0f"
            stepSize > 0.099f -> "%.1f"
            stepSize > 0.0099f -> "%.2f"
            else -> "%.3f"
        }
        if (getTipText == null)
            tipLabel.setText(tipFormat.format(slider.value))
    }

    // Attempt to prevent ascendant ScrollPane(s) from stealing our focus
    private val killedListeners: MutableMap<ScrollPane, List<EventListener>> = mutableMapOf()
    private val killedCaptureListeners: MutableMap<ScrollPane, List<EventListener>> = mutableMapOf()

    private fun killScrollPanes() {
        var widget: Group = this
        while (widget.parent != null) {
            widget = widget.parent
            if (widget !is ScrollPane) continue
            if (widget.listeners.size != 0)
                killedListeners[widget] = widget.listeners.toList()
            if (widget.captureListeners.size != 0)
                killedCaptureListeners[widget] = widget.captureListeners.toList()
            widget.clearListeners()
        }
    }

    private fun resurrectScrollPanes() {
        var widget: Group = this
        while (widget.parent != null) {
            widget = widget.parent
            if (widget !is ScrollPane) continue
            killedListeners[widget]?.forEach {
                widget.addListener(it)
            }
            killedListeners.remove(widget)
            killedCaptureListeners[widget]?.forEach {
                widget.addCaptureListener(it)
            }
            killedCaptureListeners.remove(widget)
        }
    }

    // Helpers to manage the light-weight "tooltip" showing the value
    private fun showTip() {
        if (tipContainer.hasParent()) return
        tipContainer.pack()
        if (needsLayout()) pack()
        val pos = slider.localToParentCoordinates(Vector2(slider.width / 2, slider.height))
        tipContainer.run {
            setOrigin(Align.bottom)
            setPosition(pos.x, pos.y, Align.bottom)
            isTransform = true
            color.a = 0.2f
            setScale(0.05f)
        }
        addActor(tipContainer)
        tipContainer.addAction(
            Actions.parallel(
                Actions.fadeIn(tipAnimationDuration, Interpolation.fade),
                Actions.scaleTo(1f, 1f, 0.2f, Interpolation.fade)
            )
        )
    }

    private fun hideTip() {
        tipContainer.addAction(
            Actions.sequence(
                Actions.parallel(
                    Actions.alpha(0.2f, 0.2f, Interpolation.fade),
                    Actions.scaleTo(0.05f, 0.05f, 0.2f, Interpolation.fade)
                ),
                Actions.removeActor()
            )
        )
    }
}
