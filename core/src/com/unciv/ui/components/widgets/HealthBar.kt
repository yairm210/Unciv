package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.HealthBar.Segment
import com.unciv.ui.components.widgets.HealthBar.Style
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.BackgroundActor
import kotlin.math.PI
import kotlin.math.sin

/**
 * A dynamic Healthbar/Progressbar Widget
 *
 * Features:
 * - All visual properties not related to data encapsulated in [style], with the [Style] class offering some static reusable predefined ones.
 * - Horizontal left to right or right to left, Vertical bottom to top or top to bottom ([Style.vertical]).
 * - Order can be inverted (right-left / top-bottom) by toggling [Style.inverted].
 * - Min/Max (as [range]) and segment value properties (accessed using the indexing operator on a HealthBar instance), relative proportions for display calculated internally.
 * - Any number of segments (>=2 of course).
 * - Each segment has its own style (static or dynamic color, or skinnable background drawable: [Segment]).
 * - (N-1) segment-change positions (last segment always has end-position == Max).
 * - Optional background behind segments as border staying inside the Widget bounds.
 */
class HealthBar(
    val range: ClosedRange<Float> = 0f..100f,
    val style: Style = Style.default
) : Table() {
    // The data we represent
    private val values: Array<Float>

    // Convenience shortcuts
    private val vertical get() = style.vertical
    private val segments get() = style.segments

    init {
        if (segments.size < 2)
            throw IllegalArgumentException("HealthBar must have at least 2 segments")
        values = Array(segments.size - 1) { range.start }

        background = BaseScreen.skinStrings.getUiBackground(style.backgroundPath, tintColor = style.backgroundTint)
        pad(style.borderSize)

        for (i in segments.indices) {
            val segment = segments[i]
            if (segment.dynamicColor != null && segment.color != Color.WHITE)
                throw IllegalArgumentException("HealthBar segment with dynamicColor must leave color at WHITE")

            val backgroundPath = "General/HealthBar" + (segment.backgroundPath ?.let { "/$it" } ?: "Segment")
            if (BaseScreen.skinStrings.hasUiBackground(backgroundPath)) {
                val background = BaseScreen.skinStrings.getUiBackground(backgroundPath, tintColor = segment.color)
                add(BackgroundActor(background, Align.center))
            } else {
                // This is a kludge to reduce visual impact of Table quirks - shouldn't be necessary, but using BackgroundActor will paint cells into the padding
                val image = ImageGetter.getWhiteDot()
                image.color = segment.color
                add(image)
            }

            if (vertical) row()
        }
    }

    /** Retrieve a segment's value determining the end of its bar (as measured relative to [range]).
     *  When index is out of bounds, the nearest end of [range] is returned, this does not throw.
     */
    operator fun get(index: Int) = when {
        index < 0 -> range.start
        index >= segments.size - 1 -> range.endInclusive
        else -> values[index]
    }

    /** Set a segment's value determining the end of its bar (as measured relative to [range]).
     *  [index] must be 0 to [segments].size **minus 1** as you can't change the [range] parameter after instantiation.
     *  @throws IndexOutOfBoundsException
     */
    operator fun set(index: Int, value: Float) {
        values[index] = value
        invalidate()
    }

    override fun act(delta: Float) {
        if (!style.animated) return
        super.act(delta)
    }

    override fun layout() {
        val totalLength = (
            when {
                vertical && height > 0f -> height
                vertical && prefHeight > 0f -> prefHeight
                vertical -> 100f
                width > 0f -> width
                prefWidth > 0f -> prefWidth
                else -> 100f
            } - style.borderSize * 2
        ).coerceAtLeast(0f)
        val across = (
            when {
                vertical && width > 0f -> width
                vertical && prefWidth > 0f -> prefWidth
                vertical -> 10f
                height > 0f -> height
                prefHeight > 0f -> prefHeight
                else -> 10f
            } - style.borderSize * 2
        ).coerceAtLeast(0f)

        var lastValue = range.start
        for (i in segments.indices) {
            val cell = cells[if (style.inverted) segments.size - i - 1 else i]
            val value = get(i)
            val delta = (value - lastValue).coerceAtLeast(0f)
            val percent = delta / (range.endInclusive - range.start)
            val along = percent * totalLength
            if (vertical) cell.size(across, along) else cell.size(along, across)
            if (value <= lastValue) continue
            lastValue = value
            segments[i].dynamicColor?.let {
                cell.actor.color = it.invoke(percent)
            }
        }
        super.layout()
    }

    /**
     * Visual styling for a [HealthBar] instance
     *
     * Static during a [HealthBar] instance lifetime, shareable between instances.
     * @property vertical as the name says.
     * @property inverted logical segment order and order of cells in the Table are inverted. On by default for vertical (as the first value should correspond to the bottom cell).
     * @property animated **Not supported yet**
     * @property borderSize padding between the outer Widget bounds and the area painted with segments.
     * @property backgroundPath Background for entire Widget, ony visible when a segment is transparent or [borderSize] > 0. See [BaseScreen.skinStrings.getUiBackground][com.unciv.models.skins.SkinStrings.getUiBackground]. Defaults to "General/HealthBar", which has no texture by default, resulting in a uniform background.
     * @property backgroundTint Color used as tint with [backgroundPath]. Defaults to null, meaning untinted white.
     * @property segments At least two [Segment] instances.
     */
    class Style(
        val vertical: Boolean = false,
        val inverted: Boolean = vertical,
        val animated: Boolean = false,
        val borderSize: Float = 0f,
        val backgroundPath: String = "General/HealthBar",
        val backgroundTint: Color? = null,
        val segments: Array<Segment>
    ) {
        companion object {
            private val defaultSegments = arrayOf(
                Segment(),
                Segment(Color.BLACK)
            )
            private val unitOrCitySegments = arrayOf(
                Segment { percent ->
                    when {
                        percent > 0.6666f -> Color.GREEN
                        percent > 0.3333f -> Color.ORANGE
                        else -> Color.RED
                    }
                },
                Segment(Color.BLACK)
            )

            val default = Style(segments = defaultSegments)
            val unitOrCity = Style(borderSize = 1f, backgroundTint = Color.BLACK, segments = unitOrCitySegments)
        }
    }

    /**
     * Styling for one [HealthBar] segment
     *
     * @property color The static segment color, also passed as tint to `getUiBackground` if [backgroundPath] is used
     * @property backgroundPath see [BaseScreen.skinStrings.getUiBackground][com.unciv.models.skins.SkinStrings.getUiBackground]. Note that a skin mod can define "General/HealthBarSegment" to apply to all segments where this is null.
     * @property dynamicColor A lambda returning a Color for a given percentage
     */
    class Segment(
        val color: Color = Color.WHITE,
        val backgroundPath: String? = null,
        val dynamicColor: ((percent: Float) -> Color)? = null
    )

    @Suppress("unused")  // Used only temporarily for FasterUIDevelopment.DevElement
    object Testing {
        fun getFasterUIDevelopmentTester() = Table(BaseScreen.skin).apply {
            defaults().pad(10f)
            background = ImageGetter.getDrawable(null).tint(Color.GRAY)
            add("Old:").uniformX()
            val oldBarCell = add().colspan(2)
            fun replaceOldBar(value: Float) {
                oldBarCell.setActor<Table>(ImageGetter.getHealthBar(value, 100f, 100f, 10f))
            }
            replaceOldBar(50f)
            add().uniformX().row()

            val ourBar = HealthBar(0f..100f, style = Style.unitOrCity)
            ourBar[0] = 50f

            val slider = UncivSlider(0f, 100f, 1f, initial = 50f) {
                ourBar[0] = it
                replaceOldBar(it)
            }

            val action = object : FloatAction() {
                init {
                    duration = 4f
                }
                override fun end() {
                    restart()
                }
                override fun update(percent: Float) {
                    val value = ((sin(percent * 2 * PI) + 1.0) * 50).toFloat()
                    slider.value = value
                    ourBar[0] = value
                }
            }

            add("New:").uniformX()
            add(ourBar).size(102f, 12f).colspan(2) // ImageGetter.getHealthBar pads the dimensions it gets, this one has the padding inside its bounds
            add().uniformX().row()

            add(slider).colspan(4).row()

            val startButton = TextButton("Start", skin)
            val stopButton = TextButton("Stop", skin)
            startButton.onClick {
                startButton.disable()
                addAction(action)
                stopButton.enable()
            }
            stopButton.onClick {
                stopButton.disable()
                clearActions()
                startButton.enable()
                replaceOldBar(slider.value)
            }
            stopButton.disable()
            add()
            add(startButton)
            add(stopButton)
            add().row()
            pack()
        }
    }
}
