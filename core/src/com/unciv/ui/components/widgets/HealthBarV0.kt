package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.City
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.tilegroups.CityButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.PI
import kotlin.math.cos

@Suppress("MemberVisibilityCanBePrivate", "unused")

//TODO Since HealthBar instances can share style instances with the same values, use common ones.
//      - skin, dedicated cache, enum, or companion vals?

/**
 *  A reusable Widget to draw progress bars, health bars and the like.
 */

class HealthBarV0(
    val minValue: Float,
    val maxValue: Float,
    segmentCount: Int = 2,
    isVertical: Boolean = false,
    var style: HealthBarStyle = HealthBarStyle(isVertical)
) : Widget() {
    /**
     *  A reusable Widget to draw progress bars, health bars and the like.
     *  Convenience constructor using Int limits, and default constructor.
     *
     *  Note that a default instance without any further configuration will draw nothing and have zero pref size.
     */
    constructor(
        minValue: Int = 0,
        maxValue: Int = 100,
        segmentCount: Int = 2,
        isVertical: Boolean = false
    ) : this(minValue.toFloat(), maxValue.toFloat(), segmentCount, isVertical)

    class HealthBarStyle(var isVertical: Boolean) {
        /** The background, if any, also defines the actual bar area by its leftWidth, topHeight etc properties */
        var background: Drawable? = null
        /** In case a background fails to define leftWidth etc we can reduce the bar area with this */
        var pad: Float
            get() = (padHorizontal + padVertical) / 2
            set(value) { padHorizontal = value; padVertical = value }
        /** @see pad */
        var padVertical: Float = 0f
        /** @see pad */
        var padHorizontal: Float = 0f

        /** At layout time, there must be enough colors. One per segment, except the last one can be omitted and will mean Color.CLEAR */
        var colors: Array<Color> = arrayOf(Color.GREEN)

        var minBarWidth: Float = 0f
        var minBarHeight: Float = 0f

        var animateDuration: Float = 0f
        var flashingSegment: Int = -1
        var flashDuration: Float = 1.4f
        var flashMaxDarken: Float = 0.7f

        /** If this is set, a white Label showing values and maxValue is drawn on top */
        var fontSize = 0

        /** Sets background to a uniform color and sets padding as that background includes no margins */
        fun setBackground(color: Color, pad: Float) {
            background = ImageGetter.getDrawable(null).tint(color)
            this.pad = pad
        }

        /** Sets both minimum sizes (pad and background margins are added to this) */
        fun setBarSize(width: Float, height: Float) {
            minBarWidth = width
            minBarHeight = height
        }

        internal fun calcMinWidth() = minBarWidth + 2 * padHorizontal +
            (background?.run { leftWidth + rightWidth } ?: 0f)
        internal fun calcMinHeight() = minBarHeight + 2 * padVertical +
            (background?.run { bottomHeight + topHeight } ?: 0f)
    }

    init {
        if (segmentCount < 2)
            throw IllegalArgumentException("Less than two segments are nonsense")
        if (minValue >= maxValue)
            throw IllegalArgumentException("maxValue must be > minValue")
    }

    //region internal Fields
    private val values = FloatArray(segmentCount - 1) { 0f }

    private val drawColor = Color()
    private val segmentColor = Color()
    private val drawSegment = Rectangle()
    private val whiteDot = ImageGetter.getDrawable(null)  // == getWhiteDot().drawable

    private var flashingAction: FlashingAction? = null
    private var flashingValue = 1f

    private var label: Label? = null
    //endregion

    operator fun get(index: Int) = values[index]
    operator fun set(index: Int, value: Float) {
        values[index] = value.coerceIn(minValue, maxValue)
        invalidate()
    }

    /** Shortcut to first value */
    var value: Float
        get() = values[0]
        set(value) { values[0] = value }

    /** Set [style] value [flashingSegment][HealthBarStyle.flashingSegment] **before** turning this on */
    var allowAnimations = false
        set(value) {
            updateAnimations(value)
            field = value
        }

    /** Special case for the UnitGroup - one segment colored by value and 1f black border */
    fun setupForUnitOrCity(current: Int) {
        style.background = BaseScreen.skinStrings.getUiBackground("General/HealthBar", tintColor = Color.BLACK)
        style.padVertical = 1f
        style.padHorizontal = 1.2f
        values[0] = current.toFloat()
        style.colors[0] = when {
            current <= 33 -> Color.RED
            current <= 66 -> Color.ORANGE
            else -> Color.GREEN
        }
    }

    /** Sets all values.
     *
     *  If newValues.size is less than segmentCount-1, only a subset of the values will be updated.
     *  If newValues.size is greater than segmentCount-1, additional values will be ignored.
     */
    fun setValues(newValues: FloatArray) {
        for (i in newValues.indices) newValues[i] = newValues[i].coerceIn(minValue, maxValue)
        if (allowAnimations && style.animateDuration > 0f)
            addAction(AnimateAction(newValues))
        else
            newValues.copyInto(values, 0, 0, values.size.coerceAtMost(newValues.size))
        invalidate()
    }

    @JvmName("setValuesVarArg")
    fun setValues(vararg newValues: Float) = setValues(newValues)

    fun setValues(vararg newValues: Int) {
        val floatValues = FloatArray(newValues.size)
        for (i in newValues.indices) floatValues[i] = newValues[i].toFloat()
        setValues(floatValues)
    }

    //region Widget overrides
    override fun getPrefWidth() = style.calcMinWidth()
    override fun getPrefHeight() = style.calcMinHeight()

    override fun layout() {
        values.sort()
        if (style.fontSize == 0) return

        val labelText = (values.asSequence() + maxValue)
            .joinToString("/") { "%.0f".format(it) }
        val label = this.label ?:
        Label(null, Label.LabelStyle(Fonts.font, Color.WHITE))
            .apply { this@HealthBarV0.label = this }
        label.setFontScale(style.fontSize / Fonts.ORIGINAL_FONT_SIZE)
        label.setAlignment(Align.center)
        label.setText(labelText)
        label.validate()
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        validate()

        drawColor.set(color)
        drawColor.a *= parentAlpha
        drawSegment.set(x, y, width, height)
        style.background?.run {
            @Suppress("UsePropertyAccessSyntax") // Not a mutating setter, it copies each component
            batch.setColor(drawColor)
            draw(batch, x, y, width, height)
            drawSegment.set(x + leftWidth, y + bottomHeight, width - leftWidth - rightWidth, height - bottomHeight - topHeight)
        }
        if (style.padHorizontal > 0f || style.padVertical > 0f) drawSegment.run {
            set(x + style.padHorizontal, y + style.padVertical, width - 2 * style.padHorizontal, height - 2 * style.padVertical)
        }

        val valueScale = (if (style.isVertical) drawSegment.height else drawSegment.width) / (maxValue - minValue)
        for (index in 0..values.size) {
            if (index >= style.colors.size) break
            val segmentLength = when (index) {
                0 -> values[index] - minValue
                values.size -> maxValue - values[index - 1]
                else -> values[index] - values[index - 1]
            } * valueScale
            if (style.colors[index].a > 0f && segmentLength > 0f) {
                segmentColor.set(drawColor).mul(style.colors[index])
                if (index == style.flashingSegment) {
                    segmentColor.r *= flashingValue
                    segmentColor.g *= flashingValue
                    segmentColor.b *= flashingValue
                }
                @Suppress("UsePropertyAccessSyntax")
                batch.setColor(segmentColor)
                drawSegment.run {
                    if (style.isVertical) height = segmentLength else width = segmentLength
                    whiteDot.draw(batch, x, y, width, height)
                }
            }
            if (style.isVertical) drawSegment.y += segmentLength else drawSegment.x += segmentLength
        }

        if (style.fontSize == 0) return
        val label = this.label ?: return
        label.setPosition(x + width / 2, y + height / 2, Align.center)
        label.draw(batch, parentAlpha)
    }
    //endregion

    //region Helpers
    private inner class AnimateAction(
        newValues: FloatArray
    ) : FloatAction(0f, 1f, style.animateDuration, Interpolation.fastSlow) {
        private val startValues = values.clone()
        private val endValues = values.clone().apply {
            newValues.copyInto(this, 0, 0, size.coerceAtMost(newValues.size))
        }

        override fun act(delta: Float): Boolean {
            val complete = super.act(delta)
            val value = this.value
            for (i in values.indices) {
                values[i] = (1f - value) * startValues[i] + value * endValues[i]
            }
            return complete
        }
    }

    private inner class FlashingAction : FloatAction(0f, (2 * PI).toFloat(), style.flashDuration) {
        override fun act(delta: Float): Boolean {
            val complete = super.act(delta)
            if (complete) this.restart()
            val maxDarken = style.flashMaxDarken
            flashingValue = (cos(value) + 1f) * 0.5f * maxDarken + (1f - maxDarken)
            return false
        }
    }

    private fun updateAnimations(enable: Boolean) {
        if (!enable && flashingAction != null) {
            removeAction(flashingAction)
        }
        if (enable && style.flashingSegment >= 0 && flashingAction == null) {
            flashingAction = FlashingAction()
            addAction(flashingAction)
        }
    }
    // endregion

    companion object {
        /** Factory for a vertical bar used on the WorldScreen CityButton and CityConstructionsTable */
        fun getCityConstructionBar(
            city: City,
            constructionName: String,
            remainingColor: Color,
            hideIfNoWorkDone: Boolean = false
        ): HealthBarV0 {
            fun transparentHealthBar() = HealthBarV0().apply { style.setBarSize(4f, 30f) }

            val cityConstructions = city.cityConstructions
            val construction = cityConstructions.getConstruction(constructionName)

            val workCost: Int
            val workDone: Int
            val workNextTurn: Int
            if (construction is INonPerpetualConstruction) {
                workCost = construction.getProductionCost(city.civ).coerceAtLeast(1)
                workDone = cityConstructions.getWorkDone(construction.name)
                workNextTurn = workDone + city.cityStats.currentCityStats.production.toInt()
            } else {
                workCost = 1
                workDone = 0
                workNextTurn = 0
            }
            if (hideIfNoWorkDone && workDone == 0)
                return transparentHealthBar()

            val productionBar = HealthBarV0(0, workCost, 3, true)
            productionBar.style.apply {
                colors = arrayOf(CityButton.ColorConstruction, CityButton.ColorConstruction.darken(0.4f), remainingColor)
                setBarSize(2f, 30f)
                setBackground(Color.BLACK, 1f)
                padVertical = 0f
            }
            productionBar.setValues(workDone, workNextTurn)

            return productionBar
        }
    }
}
