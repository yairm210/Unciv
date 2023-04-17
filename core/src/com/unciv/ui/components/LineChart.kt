package com.unciv.ui.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.screens.victoryscreen.VictoryScreenCivGroup
import com.unciv.ui.screens.victoryscreen.VictoryScreenCivGroup.DefeatedPlayerStyle
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

private data class DataPoint<T>(val x: T, val y: T, val civ: Civilization)

class LineChart(
    data: Map<Int, Map<Civilization, Int>>,
    private val viewingCiv: Civilization,
    private val selectedCiv: Civilization,
    private val chartWidth: Float,
    private val chartHeight: Float
) : Widget() {

    private val shapeRenderer = ShapeRenderer()

    private val axisLineWidth = 2f
    private val axisColor = Color.WHITE
    private val axisLabelColor = axisColor
    private val axisToLabelPadding = 5f
    private val chartLineWidth = 3f
    private val orientationLineWidth = 0.5f
    private val orientationLineColor = Color.LIGHT_GRAY

    /** This should not be changed lightly. There's code (e.g. for generating the labels) that
     * assumes multiples of 10. Also please note, that the real number of labels is `maxLabels + 1`
     * as `0` is not counted. */
    private val maxLabels = 10

    private val xLabels: List<Int>
    private val yLabels: List<Int>

    private val hasNegativeYValues: Boolean
    private val negativeYLabel: Int

    private val dataPoints: List<DataPoint<Int>> = data.flatMap { turn ->
        turn.value.map { (civ, value) ->
            DataPoint(turn.key, value, civ)
        }
    }

    init {
        hasNegativeYValues = dataPoints.any { it.y < 0 }
        xLabels = generateLabels(dataPoints.maxOf { it.x })
        yLabels = generateLabels(dataPoints.maxOf { it.y })
        val lowestValue = dataPoints.minOf { it.y }
        negativeYLabel = if (hasNegativeYValues) -getNextNumberDivisibleByPowOfTen(-lowestValue) else 0
    }

    private fun generateLabels(maxValue: Int): List<Int> {
        val maxLabelValue = getNextNumberDivisibleByPowOfTen(maxValue)
        val stepSize = ceil(maxLabelValue.toFloat() / maxLabels).toInt()
        // `maxLabels + 1` because we want to end at `maxLabels * stepSize`.
        return (0 until maxLabels + 1).map { (it * stepSize) }
    }

    private fun getNextNumberDivisibleByPowOfTen(maxValue: Int): Int {
        val numberOfDigits = ceil(log10(maxValue.toDouble())).toInt()
        val maxLabelValue = when {
            numberOfDigits <= 0 -> 1
            else -> {
                // Some examples:
                // If `maxValue = 97` => `oneWithZeros = 10^(2-1) = 10 => ceil(97/10) * 10 = 100
                // If `maxValue = 567` => `oneWithZeros = 10^(3-1) = 100 => ceil(567/100) * 100 = 600
                val oneWithZeros = 10.0.pow(numberOfDigits - 1)
                ceil(maxValue.toDouble() / oneWithZeros).toInt() * oneWithZeros.toInt()
            }
        }
        return maxLabelValue
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        super.draw(batch, parentAlpha)

        // Save the current batch transformation matrix
        val oldTransformMatrix = batch.transformMatrix.cpy()
        // Set the batch transformation matrix to the local coordinates of the LineChart widget
        val stageCoords = localToStageCoordinates(Vector2(0f, 0f))
        batch.transformMatrix = Matrix4().translate(stageCoords.x, stageCoords.y, 0f)

        val lastTurnDataPoints = getLastTurnDataPoints()

        val labelHeight = Label("123", Label.LabelStyle(Fonts.font, axisLabelColor)).height
        val yLabelsAsLabels =
                yLabels.map { Label(it.toString(), Label.LabelStyle(Fonts.font, axisLabelColor)) }
        val negativeYLabelAsLabel =
                Label(negativeYLabel.toString(), Label.LabelStyle(Fonts.font, axisLabelColor))
        val widestYLabelWidth = max(yLabelsAsLabels.maxOf { it.width }, negativeYLabelAsLabel.width)
        // We assume here that all labels have the same height. We need to deduct the height of
        // a label from the available height, because otherwise the label on the top would
        // overrun the height since the (x,y) coordinates define the bottom left corner of the
        // Label.
        val yAxisLabelMaxY = chartHeight - labelHeight
        // This is to account for the x axis and its labels which are below the lowest point
        val xAxisLabelsHeight = labelHeight
        val zeroYAxisLabelHeight = labelHeight
        val yAxisLabelMinY =
                xAxisLabelsHeight + axisToLabelPadding + axisLineWidth / 2 - zeroYAxisLabelHeight / 2
        val yAxisLabelYRange = yAxisLabelMaxY - yAxisLabelMinY

        // We draw the y-axis labels first. They will take away some space on the left of the
        // widget which we need to consider when drawing the rest of the graph.
        var yAxisYPosition = 0f
        val negativeOrientationLineYPosition = yAxisLabelMinY + labelHeight / 2
        val yLabelsToDraw = if (hasNegativeYValues) listOf(negativeYLabelAsLabel) + yLabelsAsLabels else yLabelsAsLabels
        yLabelsToDraw.forEachIndexed { index, label ->
            val yPos = yAxisLabelMinY + index * (yAxisLabelYRange / (yLabelsToDraw.size - 1))
            label.setPosition((widestYLabelWidth - label.width) / 2, yPos)
            label.draw(batch, 1f)

            // Draw y-axis orientation lines and x-axis
            val zeroIndex = if (hasNegativeYValues) 1 else 0
            drawLine(
                batch,
                widestYLabelWidth + axisToLabelPadding + axisLineWidth,
                yPos + labelHeight / 2,
                chartWidth,
                yPos + labelHeight / 2,
                if (index != zeroIndex) orientationLineColor else axisColor,
                if (index != zeroIndex) orientationLineWidth else axisLineWidth
            )
            if (index == zeroIndex) {
                yAxisYPosition = yPos + labelHeight / 2
            }
        }

        // Draw x-axis labels
        val xLabelsAsLabels =
                xLabels.map { Label(it.toString(), Label.LabelStyle(Fonts.font, axisLabelColor)) }
        val lastXAxisLabelWidth = xLabelsAsLabels[xLabelsAsLabels.size - 1].width
        val xAxisLabelMinX =
                widestYLabelWidth + axisToLabelPadding + axisLineWidth / 2
        val xAxisLabelMaxX = chartWidth - lastXAxisLabelWidth / 2
        val xAxisLabelXRange = xAxisLabelMaxX - xAxisLabelMinX
        xLabels.forEachIndexed { index, labelAsInt ->
            val label = Label(labelAsInt.toString(), Label.LabelStyle(Fonts.font, axisLabelColor))
            val xPos = xAxisLabelMinX + index * (xAxisLabelXRange / (xLabels.size - 1))
            label.setPosition(xPos - label.width / 2, 0f)
            label.draw(batch, 1f)

            // Draw x-axis orientation lines and y-axis
            drawLine(
                batch,
                xPos,
                labelHeight + axisToLabelPadding + axisLineWidth,
                xPos,
                chartHeight,
                if (index > 0) orientationLineColor else axisColor,
                if (index >0) orientationLineWidth else axisLineWidth
            )
        }

        // Draw line charts for each color
        val linesMinX = widestYLabelWidth + axisToLabelPadding + axisLineWidth
        val linesMaxX = chartWidth - lastXAxisLabelWidth / 2
        val linesMinY = yAxisYPosition
        val linesMaxY = chartHeight - labelHeight / 2
        val scaleX = (linesMaxX - linesMinX) / xLabels.max()
        val scaleY = (linesMaxY - linesMinY) / yLabels.max()
        val negativeScaleY = if (hasNegativeYValues) (linesMinY - negativeOrientationLineYPosition) / -negativeYLabel else 0f
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
                val yScale = if (it.y < 0f) negativeScaleY else scaleY
                DataPoint(linesMinX + it.x * scaleX, linesMinY + it.y * yScale, it.civ)
            }
            // Probably nobody can tell the difference of one pixel, so that seems like a reasonable epsilon.
            val simplifiedScaledPoints = douglasPeucker(scaledPoints, 1f)
            // Draw a background line for the selected civ. We need to do this before all other
            // lines of the selected civ, but after all lines of other civs.
            if (civ == selectedCiv) {
                for (i in 1 until simplifiedScaledPoints.size) {
                    val a = simplifiedScaledPoints[i - 1]
                    val b = simplifiedScaledPoints[i]
                    drawLine(
                        batch, a.x, a.y, b.x, b.y,
                        civ.nation.getInnerColor(), chartLineWidth * 3
                    )
                }
            }
            for (i in 1 until simplifiedScaledPoints.size) {
                val a = simplifiedScaledPoints[i - 1]
                val b = simplifiedScaledPoints[i]
                drawLine(batch, a.x, a.y, b.x, b.y, civ.nation.getOuterColor(), chartLineWidth)

                // Draw the selected Civ icon on its last datapoint
                if (i == simplifiedScaledPoints.size - 1 && selectedCiv == civ && selectedCiv in lastTurnDataPoints) {
                    val selectedCivIcon =
                            VictoryScreenCivGroup(
                                selectedCiv,
                                "",
                                viewingCiv,
                                DefeatedPlayerStyle.REGULAR
                            ).children[0].run {
                                (this as? Image)?.surroundWithCircle(30f, color = Color.LIGHT_GRAY)
                                    ?: this
                            }
                    selectedCivIcon.run {
                        setPosition(b.x, b.y, Align.center)
                        setSize(33f, 33f) // Dead Civs need this
                        draw(batch, parentAlpha)
                    }
                }
            }
        }

        // Restore the previous batch transformation matrix
        batch.transformMatrix = oldTransformMatrix
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

    private fun drawLine(
        batch: Batch,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Color,
        width: Float
    ) {
        shapeRenderer.projectionMatrix = batch.projectionMatrix
        shapeRenderer.transformMatrix = batch.transformMatrix
        batch.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = color
        shapeRenderer.rectLine(x1, y1, x2, y2, width)
        // Draw a circle at the beginning and end points of the line to make consecutive lines
        // (which might point in different directions) connect nicely.
        shapeRenderer.circle(x1, y1, width / 2)
        shapeRenderer.circle(x2, y2, width / 2)
        shapeRenderer.end()

        batch.begin()
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

    override fun getMinWidth() = chartWidth
    override fun getMinHeight() = chartHeight
    override fun getPrefWidth() = chartWidth
    override fun getPrefHeight() = chartHeight
    override fun getMaxWidth() = chartWidth
    override fun getMaxHeight() = chartHeight
}
