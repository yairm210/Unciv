package com.unciv.ui.components.widgets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
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
import com.unciv.UncivGame
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.isShiftKeyPressed
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.clearActivationActions
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivSlider.Companion.formatPercent
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.popups.AskNumberPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Modified Gdx [Slider]
 *
 * - Optionally has +/- buttons at the end for easier single steps
 * - Shows a timed tip with the actual value every time it changes
 * - Disables listeners of any ScrollPanes this is nested in while dragging
 * - The snap-to feature can be dynamically disabled and re-enabled, remembering values
 * - Long-press toggles the snap-to feature - buttons turn green and every [step] can be reached (with very fine fingers)
 * - As an alternative, if a prompt is suppled with [setSnapToValues], the value can be entered as text by clicking the value tip
 *
 * Note: No attempt is made to distinguish sources of value changes, so the initial setting
 * of the value when a screen is initialized will also trigger the 'tip'. This is intentional.
 *
 * @param min           Initializes [Slider.min]
 * @param max           Initializes [Slider.max]
 * @param step          Initializes [Slider.stepSize]
 * @param vertical      Initializes [Slider.vertical]
 * @param plusMinus     Enable +/- buttons - note they will also snap to [setSnapToValues].
 * @param initial       Initializes [value]
 * @param sound         Plays _only_ on user dragging (+/- always play the normal Click sound). Consider using [UncivSound.Silent] for sliders with many steps.
 * @param tipType       None disables the tooltip, Auto animates it on change, Permanent leaves it on screen after initial fade-in.
 * @param getTipText    Formats a value for the tooltip - will not be translated, the provided method must do so. Default formats as numeric, precision depends on [stepSize]. You can also use [UncivSlider::formatPercent][formatPercent].
 * @param onChange      Optional lambda gets called with the current value on a user change (not when setting value programmatically).
 */
class UncivSlider (
    min: Float,
    max: Float,
    step: Float,
    vertical: Boolean = false,
    plusMinus: Boolean = true,
    initial: Float,
    sound: UncivSound = UncivSound.Slider,
    private val tipType: TipType = TipType.Permanent,
    private val getTipText: ((Float) -> String)? = null,
    private val onChange: ((Float) -> Unit)? = null
): Table(BaseScreen.skin) {
    enum class TipType { None, Auto, Permanent }

    @Suppress("ConstPropertyName")
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
    private val slider = Slider(min, max, step, vertical, BaseScreen.skin)
    private val minusButton: IconCircleGroup?
    private val plusButton: IconCircleGroup?
    private val tipLabel = "".toLabel(Color.LIGHT_GRAY)
    private val tipContainer: Container<Label> = Container(tipLabel)
    private val tipHideTask = object : Timer.Task() {
        override fun run() {
            hideTip()
        }
    }

    // copies of maliciously protected Slider members
    private var snapToValues: FloatArray? = null
    private var snapThreshold: Float = 0f

    /** Enable/disable snap-to independently of [setSnapToValues] (disabling remembers values) */
    var snapEnabled = true
        get() = field && snapToValues != null
        set(value) {
            val values = snapToValues ?: return
            field = value
            if (value)
                slider.setSnapToValues(snapThreshold, *values)
            else
                slider.setSnapToValues(0f, Float.NaN) // As Gdx doesn't allow a clean workaround to call this with a null array
            setPlusMinusEnabled()
        }

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
    @Suppress("unused") // Part of the Slider API
    fun setRange(min: Float, max: Float) {
        slider.setRange(min, max)
        setPlusMinusEnabled()
    }

    /** Will make this slider snap to the specified values, if the knob is within the threshold.
     *  @param editLabel if set, allows clicking the value tip (best make it permanent) to edit the value manually - passed to [AskNumberPopup].
     *  @see snapEnabled */
    fun setSnapToValues(threshold: Float, vararg values: Float, editLabel: String? = null) {
        snapToValues = values       // make a copy so our plus/minus code can snap
        snapThreshold = threshold
        this.editLabel = editLabel
        if (snapEnabled) {
            slider.setSnapToValues(threshold, *values)
            onActivation(ActivationTypes.Longpress, noEquivalence = true, allowEventPropagation = false, halfTapSquareSize = getHalfTapSize(stage)) {
                snapEnabled = !snapEnabled
            }
            setEditable(true)
        }
    }

    // java formatter for the value tip, configured by changing stepSize - respects language setting, result should NOT be passed through tr()
    private var tipFormat = UncivGame.Current.settings.getAndModifyCurrentNumberFormat {
        isGroupingUsed = false
    }

    // Detect changes in isDragging
    private var hasFocus = false
    // Help value set not to trigger change listener events
    private var blockListener = false

    private val buttonCircleColorDefault = BaseScreen.skin.getColor("color")
    private val buttonCircleColorDisabled = BaseScreen.skin.getColor("disabled")
    private val buttonCircleColorSnapDisabled = BaseScreen.skin.getColor("highlight")

    private var editLabel: String? = null

    init {
        tipLabel.setOrigin(Align.center)
        tipContainer.touchable = Touchable.disabled

        stepChanged()   // Initialize tip formatting

        fun addPlusOrMinusButton(text: String, isMinus: Boolean, onClick: ()->Unit): IconCircleGroup? {
            if (!plusMinus) return null
            if (vertical && !isMinus) row()
            val button = text.toLabel(Color.WHITE, plusMinusFontSize)
                .apply { setAlignment(Align.center) }
                .surroundWithCircle(plusMinusCircleSize, true, buttonCircleColorDefault)
            button.onClick(onClick)
            add(button).apply {
                if (isMinus)
                    if (vertical) padBottom(padding) else padLeft(padding)
                else
                    if (vertical) padTop(padding) else padRight(padding)
            }
            if (vertical && isMinus) row()
            return button
        }

        minusButton = addPlusOrMinusButton("-", true) { addToValue(-stepSize) }
        add(slider).pad(padding).fillY().growX()
        plusButton = addPlusOrMinusButton("+", false) { addToValue(stepSize) }
        row()

        value = initial  // set initial value late so the tooltip can work with the layout

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
    }

    // Helper for plus/minus button onClick, non-trivial only if setSnapToValues is used
    private fun addToValue(delta: Float) {
        // un-snapping with Shift is taken from Slider source, and the loop mostly as well
        // with snap active, plus/minus buttons will go to the next snap position regardless of stepSize
        // this could be shorter if Slider.snap(), Slider.snapValues and Slider.threshold weren't protected
        if (!snapEnabled || Gdx.input.isShiftKeyPressed()) {
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
        setTipText()
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

    private fun formatDefaultTip(value: Float) = tipFormat.format(value)

    private fun setTipText() {
        if (tipType == TipType.None) return
        val formatted = (getTipText ?: ::formatDefaultTip)(slider.value) +
            if (isEditable()) " ${Fonts.pencil}" else ""
        tipLabel.setText(formatted)
    }

    private fun showSnapDisabled() = !snapEnabled && snapToValues != null

    private fun IconCircleGroup.setEnabled(enabled: Boolean) {
        touchable = if (enabled) Touchable.enabled else Touchable.disabled
        circle.color = when {
            !enabled -> buttonCircleColorDisabled
            showSnapDisabled() -> buttonCircleColorSnapDisabled
            else -> buttonCircleColorDefault
        }
    }
    private fun setPlusMinusEnabled() {
        minusButton?.setEnabled(slider.value > slider.minValue && !isDisabled)
        plusButton?.setEnabled(slider.value < slider.maxValue && !isDisabled)
    }

    private fun stepChanged() {
        val fractionDigits = when {
            stepSize > 0.99f -> 0
            stepSize > 0.099f -> 1
            stepSize > 0.0099f -> 2
            else -> 3
        }
        tipFormat.minimumFractionDigits = fractionDigits
        tipFormat.maximumFractionDigits = fractionDigits
        if (tipType != TipType.None && getTipText == null)
            setTipText()
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
        setEditable(true)
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
        setEditable(false)
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

    private fun setEditable(onOff: Boolean) {
        tipContainer.touchable = Touchable.disabled
        tipContainer.clearActivationActions(ActivationTypes.Tap)
        if (!onOff || editLabel == null) return
        tipContainer.touchable = Touchable.enabled
        tipContainer.onActivation(sound = UncivSound.Silent) {
            AskNumberPopup(
                stage,
                editLabel!!,
                defaultValue = value.roundToInt(),
                bounds = minValue.toInt() .. maxValue.toInt()
            ) {
                setValueExact(it.toFloat())
            }.open(true)
        }
        setTipText()
    }
    private fun isEditable() = tipContainer.touchable == Touchable.enabled

    /** This is for the pedants out there - [slider] would snap the new value to a multiple of [stepSize],
     *  and the user would see the rounded value not the one they entered. [snapToValues] are irrelevant too. */
    private fun setValueExact(newValue: Float) {
        slider.stepSize = 0.001f
        try { value = newValue } finally { slider.stepSize = this.stepSize }
    }

    /** Try to translate 4dp (Android's touch slop is about 8dp) into stage coordinates. */
    private fun getHalfTapSize(stage: Stage?): Float {
        val stage = stage ?: UncivGame.Current.screen?.stage ?: return 2f
        return 4f * Gdx.graphics.density * stage.width / Gdx.graphics.width
    }
}
