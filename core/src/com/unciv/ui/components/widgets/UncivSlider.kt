package com.unciv.ui.components.widgets

import com.badlogic.gdx.Gdx
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
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.isShiftKeyPressed
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.widgets.UncivSlider.Companion.formatPercent
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.abs
import kotlin.math.sign

/**
 * Modified Gdx [Slider]
 *
 * Optionally has +/- buttons at the end for easier single steps
 * Shows a timed tip with the actual value every time it changes
 * Disables listeners of any ScrollPanes this is nested in while dragging
 *
 * Note: No attempt is made to distinguish sources of value changes, so the initial setting
 * of the value when a screen is initialized will also trigger the 'tip'. This is intentional.
 *
 * @param label         A string describing what the slider controls.
 * @param min           Initializes [Slider.min]
 * @param max           Initializes [Slider.max]
 * @param step          Initializes [Slider.stepSize]
 * @param vertical      Initializes [Slider.vertical]
 * @param plusMinus     Enable +/- buttons - note they will also snap to [setSnapToValues].
 * @param initial       Initializes [value]
 * @param sound         Plays _only_ on user dragging (+/- always play the normal Click sound). Consider using [UncivSound.Silent] for sliders with many steps.
 * @param tipType       None disables the tooltip, Auto animates it on change, Permanent leaves it on screen after initial fade-in.
 * @param getTipText    Formats a value for the tooltip. Default formats as numeric, precision depends on [stepSize]. You can also use [UncivSlider::formatPercent][formatPercent].
 * @param onChange      Optional lambda gets called with the current value on a user change (not when setting value programmatically).
 */
class UncivSlider (
    text: String,
    min: Float,
    max: Float,
    step: Float,
    initial: Float,
    vertical: Boolean = false,
    plusMinus: Boolean = true,
    sound: UncivSound = UncivSound.Slider,
    private val tipType: TipType = TipType.Permanent,
    private val getTipText: ((Float) -> String)? = null,
    private val onChange: ((Float) -> Unit)? = null
): Table(BaseScreen.skin) {
    enum class TipType { None, Auto, Permanent }

    companion object {
        /** Can be passed directly to the [getTipText] constructor parameter */
        fun formatPercent(value: Float): String {
            return (value * 100f + 0.5f).toInt().tr() + "%"
        }
        // constants for geometry tuning
        const val plusMinusFontSize = Constants.defaultFontSize
        const val plusMinusCircleSize = 20f
        const val padding = 5f                  // padding around the Slider, doubled between it and +/- buttons
        const val hideDelay = 3f                // delay in s to hide tooltip
        const val tipAnimationDuration = 0.2f   // tip show/hide duration in s
    }

    // component widgets
    private val label = text.toLabel()
    private val tipLabel = "".toLabel()
    private val tipContainer: Container<Label> = Container(tipLabel)
    private val tipHideTask = object : Timer.Task() {
        override fun run() {
            hideTip()
        }
    }

    private val sliderWrapper = Table(BaseScreen.skin)
    private val slider = Slider(min, max, step, vertical, BaseScreen.skin)
    private val minusButton: IconCircleGroup?
    private val plusButton: IconCircleGroup?


    // copies of maliciously protected Slider members
    private var snapToValues: FloatArray? = null
    private var snapThreshold: Float = 0f

    // Compatibility with default Slider
    @Suppress("unused") // Part of the Slider API
    val minValue: Float
        get() = slider.minValue
    @Suppress("unused") // Part of the Slider API
    val maxValue: Float
        get() = slider.maxValue
    var value: Float
        get() = slider.value
        set(newValue) {
            blockListener = true
            slider.value = newValue
            blockListener = false
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
    @Suppress("unused") // Part of the Slider API
    /** Sets the range of this slider. The slider's current value is clamped to the range. */
    fun setRange(min: Float, max: Float) {
        slider.setRange(min, max)
        setPlusMinusEnabled()
    }
    /** Will make this slider snap to the specified values, if the knob is within the threshold. */
    fun setSnapToValues(threshold: Float, vararg values: Float) {
        snapToValues = values       // make a copy so our plus/minus code can snap
        snapThreshold = threshold
        slider.setSnapToValues(threshold, *values)
    }

    // java format string for the value tip, set by changing stepSize
    private var tipFormat = "%.1f"

    // Detect changes in isDragging
    private var hasFocus = false
    // Help value set not to trigger change listener events
    private var blockListener = false

    init {
        tipContainer.touchable = Touchable.disabled
        stepChanged()   // Initialize tip formatting

        add(label).expandX().left()
        value = initial // this implicitly adds the value label, see showTip()

        // Add the listener late so the setting of the initial value is silent
        slider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                if (blockListener) return
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

        if (plusMinus) {
            minusButton = "-".toLabel(Color.WHITE, plusMinusFontSize)
                .apply { setAlignment(Align.center) }
                .surroundWithCircle(plusMinusCircleSize, true, BaseScreen.skin.getColor("base-40"))
            minusButton.onClick {
                addToValue(-stepSize)
            }
            sliderWrapper.add(minusButton).padRight(padding);
            if (vertical) row()
        } else minusButton = null

        sliderWrapper.add(slider).pad(padding).growX()

        if (plusMinus) {
            if (vertical) row()
            plusButton = "+".toLabel(Color.WHITE, plusMinusFontSize)
                .apply { setAlignment(Align.center) }
                .surroundWithCircle(plusMinusCircleSize, true, BaseScreen.skin.getColor("base-40"))
            plusButton.onClick {
                addToValue(stepSize)
            }
            sliderWrapper.add(plusButton).padLeft(padding)
        } else plusButton = null

        add(sliderWrapper).colspan(3).growX()

    }

    // Helper for plus/minus button onClick, non-trivial only if setSnapToValues is used
    private fun addToValue(delta: Float) {
        // un-snapping with Shift is taken from Slider source, and the loop mostly as well
        // with snap active, plus/minus buttons will go to the next snap position regardless of stepSize
        // this could be shorter if Slider.snap(), Slider.snapValues and Slider.threshold weren't protected
        if (snapToValues?.isEmpty() != false || Gdx.input.isShiftKeyPressed()) {
            value += delta
            onChange?.invoke(value)
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
        onChange?.invoke(value)
    }

    // Visual feedback
    private fun valueChanged() {
        when {
            tipType == TipType.None -> Unit
            getTipText == null ->
                tipLabel.setText(tipFormat.format(slider.value))
            else ->
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION") // warning wrong, without !! won't compile
                tipLabel.setText(getTipText!!(slider.value))
        }
        when(tipType) {
            TipType.None -> Unit
            TipType.Auto -> {
                if (!tipHideTask.isScheduled) showTip()
                tipHideTask.cancel()
                Timer.schedule(tipHideTask, hideDelay)
            }
            TipType.Permanent -> showTip()
        }
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
        if (tipType != TipType.None && getTipText == null)
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
        add(tipContainer).right().row()
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
