package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils.lerp
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.victoryscreen.VictoryScreenCivGroup.DefeatedPlayerStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class DataPoint<T>(val x: T, val y: T, val civ: Civilization)

class LineChart(
    private val viewingCiv: Civilization
) : WidgetGroup() {

    private val axisLineWidth = 2f
    private val axisColor = Color.WHITE
    private val axisLabelColor = axisColor
    private val axisToLabelPadding = 5f
    private val chartLineWidth = 3f
    private val orientationLineWidth = 1f
    private val orientationLineColor = Color.LIGHT_GRAY

    /** This should not be changed lightly. There's code (e.g. for generating the labels) that
     * assumes multiples of 10. Also please note, that the real number of labels is `maxLabels + 1`
     * as `0` is not counted. */
    private val maxLabels = 10

    private var xLabels = emptyList<Int>()
    private var yLabels = emptyList<Int>()
    private var xLabelsAsLabels = emptyList<Label>()
    private var yLabelsAsLabels = emptyList<Label>()

    private var dataPoints = emptyList<DataPoint<Int>>()
    private var selectedCiv = Civilization()



    fun getTurnAt(x: Float): IntRange? {
        if (xLabels.isEmpty() || xLabelsAsLabels.isEmpty() || yLabelsAsLabels.isEmpty()) return null
        val widestYLabelWidth = yLabelsAsLabels.maxOf { it.width }
        val linesMinX = widestYLabelWidth + axisToLabelPadding + axisLineWidth
        val linesMaxX = width - xLabelsAsLabels.last().width / 2
        if (linesMinX.compareTo(linesMaxX) == 0) return (xLabels.first()..xLabels.last())
        val ratio = (x - linesMinX) / (linesMaxX - linesMinX)
        val turn = max(1, lerp(xLabels.first().toFloat(), xLabels.last().toFloat(), ratio).toInt())
        return (getPrevNumberDivisibleByPowOfTen(turn-1)..getNextNumberDivisibleByPowOfTen(turn+1))
    }

    fun update(newData: List<DataPoint<Int>>, newSelectedCiv: Civilization) {
        selectedCiv = newSelectedCiv

        dataPoints = newData
        updateLabels(dataPoints)
        prepareForDraw()
    }

    private fun updateLabels(newData: List<DataPoint<Int>>) {
        xLabels = generateLabels(newData, false)
        yLabels = generateLabels(newData, true)

        xLabelsAsLabels =
            xLabels.map { Label(it.tr(), Label.LabelStyle(Fonts.font, axisLabelColor)) }
        yLabelsAsLabels =
            yLabels.map { Label(it.tr(), Label.LabelStyle(Fonts.font, axisLabelColor)) }
    }

    fun generateLabels(value: List<DataPoint<Int>>, yAxis: Boolean): List<Int> {
        if (value.isEmpty()) return listOf(0)
        val minLabelValue = getPrevNumberDivisibleByPowOfTen(value.minOf { if (yAxis) it.y else it.x })
        val maxLabelValue = getNextNumberDivisibleByPowOfTen(value.maxOf { if (yAxis) it.y else it.x })
        var stepSizePositive = ceil(maxLabelValue.toFloat() / maxLabels).toInt()

        return when {
            minLabelValue < 0 -> {
                var stepSizeNegative = ceil(-minLabelValue.toFloat() / maxLabels).toInt()
                val maxStep = max(stepSizePositive, stepSizeNegative)
                val stepCountNegative = floor(minLabelValue / maxStep.toDouble()).toInt()
                stepSizeNegative = if (abs(stepCountNegative) < 2) abs(minLabelValue) else maxStep
                val stepCountPositive = ceil(maxLabelValue / maxStep.toDouble()).toInt()
                stepSizePositive = if (abs(stepCountPositive) < 2) abs(maxLabelValue) else maxStep

                (stepCountNegative until 0).map { (it * stepSizeNegative) } +
                    if (maxLabelValue != 0)
                    (0 until stepCountPositive + 1).map { (it * stepSizePositive) }
                    else listOf(0)
            }
            maxLabelValue != 0 -> {
                // `maxLabels + 1` because we want to end at `maxLabels * stepSize`.
                if (minLabelValue < stepSizePositive)
                    (0 until maxLabels + 1).map { (it * stepSizePositive) }
                else {
                    stepSizePositive = ceil((maxLabelValue-minLabelValue).toFloat() / maxLabels).toInt()
                    (0 until maxLabels + 1).map { minLabelValue + (it * stepSizePositive) }
                }
            }
            else -> listOf(0, 1) // line along 0
        }
    }

    /**
     *  Returns the next number of power 10, with maximal step <= 100.
     *  Examples: 0 => 0, 3 => 10, 97 => 100, 567 => 600, 123321 => 123400
     */
    private fun getNextNumberDivisibleByPowOfTen(value: Int): Int {
        if (value == 0) return 0
        val numberOfDigits = max(2, min(ceil(log10(abs(value).toDouble())).toInt(), 3))
        val oneWithZeros = 10.0.pow(numberOfDigits - 1)
        // E.g., 3 => 10^(2-1) = 10 ; ceil(3 / 10) * 10 = 10
        //     567 => 10^(3-1) = 100 ; ceil(567 / 100) * 100 = 600
        //  123321 => 10^(3-1) = 100 ; ceil(123321 / 100) * 100 = 123400
        return (ceil(value / oneWithZeros) * oneWithZeros).toInt()
    }

    /**
     *  Returns the previous number of power 10, with maximal step <= 100.
     *  Examples: 0 => 0, -3 => -10, 97 => 90, 567 => 500, 123321 => 123300
     */
    private fun getPrevNumberDivisibleByPowOfTen(value: Int): Int {
        if (value == 0) return 0
        val numberOfDigits = ceil(log10(abs(value).toDouble())).toInt().coerceIn(2,3)
        val oneWithZeros = 10.0.pow(numberOfDigits - 1)
        // E.g., 3 => 10^(2-1) = 10 ; floor(3 / 10) * 10 = 0
        //     567 => 10^(3-1) = 100 ; floor(567 / 100) * 100 = 500
        //  123321 => 10^(3-1) = 100 ; floor(123321 / 100) * 100 = 123300
        return (floor(value / oneWithZeros) * oneWithZeros).toInt()
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
    }

    private fun prepareForDraw() {

        clearChildren()

        if (xLabels.isEmpty() || yLabels.isEmpty()) return

        val lastTurnDataPoints = getLastTurnDataPoints()

        val labelHeight = yLabelsAsLabels.first().height
        val widestYLabelWidth = yLabelsAsLabels.maxOf { it.width }
        // We assume here that all labels have the same height. We need to deduct the height of
        // a label from the available height, because otherwise the label on the top would
        // overrun the height since the (x,y) coordinates define the bottom left corner of the
        // Label.
        val yAxisLabelMaxY = height - labelHeight
        // This is to account for the x axis and its labels which are below the lowest point
        val xAxisLabelsHeight = labelHeight
        val zeroYAxisLabelHeight = labelHeight
        val yAxisLabelMinY =
                xAxisLabelsHeight + axisToLabelPadding + axisLineWidth / 2 - zeroYAxisLabelHeight / 2
        val yAxisLabelYRange = yAxisLabelMaxY - yAxisLabelMinY

        // We draw the y-axis labels first. They will take away some space on the left of the
        // widget which we need to consider when drawing the rest of the graph.
        var yAxisLowestOrientationLinePosition = 0f // Lowest orientation line in pixels
        var zeroAxisYPosition = 0f // Pixel position of zero axis
        yLabels.forEachIndexed { index, value ->
            val label = yLabelsAsLabels[index] // we assume yLabels.size == yLabelsAsLabels.size
            val yPos = yAxisLabelMinY + index * (yAxisLabelYRange / (yLabels.size - 1))
            label.setPosition((widestYLabelWidth - label.width) / 2, yPos)
            addActor(label)

            // Draw y-axis orientation lines and x-axis
            val zeroIndex = value == 0
            val labelAdjustedYPos = yPos + labelHeight / 2
            drawLine(
                widestYLabelWidth + axisToLabelPadding + axisLineWidth,
                labelAdjustedYPos,
                width,
                labelAdjustedYPos,
                if (zeroIndex) axisColor else orientationLineColor,
                if (zeroIndex) axisLineWidth else orientationLineWidth
            )
            if (value <= 0) {
                if (zeroIndex) zeroAxisYPosition = labelAdjustedYPos
                yAxisLowestOrientationLinePosition = min(yAxisLowestOrientationLinePosition, labelAdjustedYPos)
            }
        }

        // Draw x-axis labels
        val lastXAxisLabelWidth = xLabelsAsLabels.last().width
        val xAxisLabelMinX =
                widestYLabelWidth + axisToLabelPadding + axisLineWidth / 2
        val xAxisLabelMaxX = width - lastXAxisLabelWidth / 2
        val xAxisLabelXRange = xAxisLabelMaxX - xAxisLabelMinX
        xLabelsAsLabels.forEachIndexed { index, label ->
            val xPos = xAxisLabelMinX + index * (xAxisLabelXRange / (xLabels.size - 1))
            label.setPosition(xPos - label.width / 2, 0f)
            addActor(label)

            // Draw x-axis orientation lines and y-axis
            drawLine(
                xPos,
                labelHeight + axisToLabelPadding + axisLineWidth,
                xPos,
                height,
                if (index > 0) orientationLineColor else axisColor,
                if (index > 0) orientationLineWidth else axisLineWidth
            )
        }

        // Draw line charts for each color
        val linesMinX = widestYLabelWidth + axisToLabelPadding + axisLineWidth
        val linesMaxX = width - lastXAxisLabelWidth / 2
        val linesMinY = yAxisLowestOrientationLinePosition + labelHeight + axisToLabelPadding + axisLineWidth
        val linesMaxY = height - labelHeight / 2
        val minXLabel = xLabels.min()
        val minYLabel = yLabels.min()
        val scaleX = (linesMaxX - linesMinX) / (xLabels.max() - minXLabel)
        val scaleY = (linesMaxY - linesMinY) / (yLabels.max() - minYLabel)
        val negativeOrientationLineYPosition = yAxisLabelMinY + labelHeight / 2
        val negativeScaleY = (negativeOrientationLineYPosition - zeroAxisYPosition) / if (minYLabel < 0) minYLabel else 1
        val sortedPoints = dataPoints.sortedBy { it.x }
        val pointsByCiv = sortedPoints.groupBy { it.civ }
        // We want the current player civ to be drawn last, so it is never overlapped by another player.
        val civIterationOrder =
                 // By default the players with the highest points will be drawn last (i.e. they will
                // overlap others).
                pointsByCiv.keys.toList().sortedBy { lastTurnDataPoints[it]!!.y }
                    .toMutableList()
        // The current player might be a spectator.
        if (selectedCiv in civIterationOrder) {
            civIterationOrder.remove(selectedCiv)
            civIterationOrder.add(selectedCiv)
        }
        for (civ in civIterationOrder) {
            val points = pointsByCiv[civ]!!
            val scaledPoints : List<DataPoint<Float>> = points.map {
                if (it.y < 0f)
                    DataPoint(linesMinX + (it.x - minXLabel) * scaleX, zeroAxisYPosition + it.y * negativeScaleY, it.civ)
                else
                    DataPoint(linesMinX + (it.x - minXLabel) * scaleX, linesMinY + (it.y - minYLabel) * scaleY, it.civ)
            }
            // Probably nobody can tell the difference of one pixel, so that seems like a reasonable epsilon.
            val simplifiedScaledPoints = douglasPeucker(scaledPoints, 1f)
            // Draw a background line for the selected civ. We need to do this before all other
            // lines of the selected civ, but after all lines of other civs.
            if (civ == selectedCiv) {
                for (i in 1 until simplifiedScaledPoints.size) {
                    val a = simplifiedScaledPoints[i - 1]
                    val b = simplifiedScaledPoints[i]
                    val selectedCivBackgroundColor =
                        if (useActualColor(civ)) civ.nation.getInnerColor() else Color.LIGHT_GRAY
                    drawLine(
                        a.x, a.y, b.x, b.y,
                        selectedCivBackgroundColor, chartLineWidth * 3
                    )
                }
            }
            for (i in 1 until simplifiedScaledPoints.size) {
                val a = simplifiedScaledPoints[i - 1]
                val b = simplifiedScaledPoints[i]
                val civLineColor = if (useActualColor(civ)) civ.nation.getOuterColor() else Color.DARK_GRAY
                drawLine(a.x, a.y, b.x, b.y, civLineColor, chartLineWidth)

                // Draw the selected Civ icon on its last datapoint
                if (i == simplifiedScaledPoints.size - 1 && selectedCiv == civ && selectedCiv in lastTurnDataPoints) {
                    val selectedCivIcon = VictoryScreenCivGroup.getCivImageAndColors(selectedCiv, viewingCiv, DefeatedPlayerStyle.REGULAR).first
                    selectedCivIcon.apply {
                        setPosition(b.x, b.y, Align.center)
                        setSize(33f, 33f) // Dead Civs need this
                    }
                    addActor(selectedCivIcon)
                }
            }
        }
    }

    private fun useActualColor(civ: Civilization) : Boolean {
        return viewingCiv.isSpectator() ||
            viewingCiv.isDefeated() ||
            viewingCiv.victoryManager.hasWon() ||
            viewingCiv == civ ||
            viewingCiv.knows(civ) ||
            civ.isDefeated()
    }

    private fun getLastTurnDataPoints(): MutableMap<Civilization, DataPoint<Int>> {
        val lastDataPoints = mutableMapOf<Civilization, DataPoint<Int>>()
        for (dataPoint in dataPoints) {
            if (!lastDataPoints.containsKey(dataPoint.civ) || lastDataPoints[dataPoint.civ]!!.x < dataPoint.x) {
                lastDataPoints[dataPoint.civ] = dataPoint
            }
        }
        return lastDataPoints
    }

    private fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, lineColor: Color, width: Float) {

        val line = ImageGetter.getLine(x1, y1, x2, y2, width)
        line.color = lineColor
        addActor(line)

        val edgeRounding = ImageGetter.getCircle(lineColor, width)
        edgeRounding.setPosition(x1 - width / 2f, y1 - width / 2f)
        addActor(edgeRounding)
    }

    private fun douglasPeucker(points: List<DataPoint<Float>>, epsilon: Float): List<DataPoint<Float>> {
        if (points.size < 3) {
            return points
        }

        val dMax = FloatArray(points.size)
        var index = 0
        var maxDistance = 0.0f

        // Find the point with the maximum distance from the line segment
        for (i in 1 until points.lastIndex) {
            val distance = perpendicularDistance(points[i], points[0], points.last())
            dMax[i] = distance

            if (distance > maxDistance) {
                index = i
                maxDistance = distance
            }
        }

        // If the maximum distance is greater than epsilon, recursively simplify
        val resultList: MutableList<DataPoint<Float>> = mutableListOf()
        if (maxDistance > epsilon) {
            val recursiveList1 = douglasPeucker(points.subList(0, index + 1), epsilon)
            val recursiveList2 = douglasPeucker(points.subList(index, points.size), epsilon)

            resultList.addAll(recursiveList1.subList(0, recursiveList1.lastIndex))
            resultList.addAll(recursiveList2)
        } else {
            resultList.add(points.first())
            resultList.add(points.last())
        }

        return resultList
    }

    // Calculates the perpendicular distance between a point and a line segment
    private fun perpendicularDistance(
        point: DataPoint<Float>,
        start: DataPoint<Float>,
        end: DataPoint<Float>
    ): Float {
        val x = point.x
        val y = point.y
        val x1 = start.x
        val y1 = start.y
        val x2 = end.x
        val y2 = end.y

        val a = x - x1
        val b = y - y1
        val c = x2 - x1
        val d = y2 - y1

        val dot = a * c + b * d
        val lenSq = c * c + d * d
        val param = if (lenSq == 0.0f) 0.0f else dot / lenSq

        val xx = if (param < 0) x1 else if (param > 1) x2 else x1 + param * c
        val yy = if (param < 0) y1 else if (param > 1) y2 else y1 + param * d

        val dx = x - xx
        val dy = y - yy

        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

}
