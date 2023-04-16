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

private data class DataPoint(val x: Int, val y: Int, val civ: Civilization)

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

    private val dataPoints: List<DataPoint> = data.flatMap { turn ->
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
            for (i in 1 until points.size) {
                val prevPoint = points[i - 1]
                val currPoint = points[i]
                val prevPointYScale = if (prevPoint.y < 0f) negativeScaleY else scaleY
                val currPointYScale = if (currPoint.y < 0f) negativeScaleY else scaleY
                drawLine(
                    batch,
                    linesMinX + prevPoint.x * scaleX, linesMinY + prevPoint.y * prevPointYScale,
                    linesMinX + currPoint.x * scaleX, linesMinY + currPoint.y * currPointYScale,
                    civ.nation.getOuterColor(), chartLineWidth
                )

                // Draw the selected Civ icon on its last datapoint
                if (i == points.size - 1 && selectedCiv == civ && selectedCiv in lastTurnDataPoints) {
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
                        setPosition(
                            linesMinX + currPoint.x * scaleX,
                            linesMinY + currPoint.y * currPointYScale,
                            Align.center
                        )
                        setSize(33f, 33f) // Dead Civs need this
                        draw(batch, parentAlpha)
                    }
                }
            }
        }

        // Restore the previous batch transformation matrix
        batch.transformMatrix = oldTransformMatrix
    }

    private fun getLastTurnDataPoints(): MutableMap<Civilization, DataPoint> {
        val lastDataPoints = mutableMapOf<Civilization, DataPoint>()
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
        shapeRenderer.end()

        batch.begin()
    }

    override fun getMinWidth() = chartWidth
    override fun getMinHeight() = chartHeight
    override fun getPrefWidth() = chartWidth
    override fun getPrefHeight() = chartHeight
    override fun getMaxWidth() = chartWidth
    override fun getMaxHeight() = chartHeight
}
