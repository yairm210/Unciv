import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.models.translations.tr
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

private data class DataPoint(val x: Int, val y: Int, val civ: Civilization)

class LineChart(
    data: Map<Int, Map<Civilization, Int>>,
    private val currentPlayerCiv: Civilization,
    private val chartWidth: Float,
    private val chartHeight: Float
) : WidgetGroup() {

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

    private val paddingBetweenCivs = 10f
    private val civGroupToChartPadding = 10f

    private val xLabels: List<Int>
    private val yLabels: List<Int>

    private val dataPoints: List<DataPoint> = data.flatMap { turn ->
        turn.value.map { (civ, value) ->
            DataPoint(turn.key, value, civ)
        }
    }

    init {
        xLabels = generateLabels(dataPoints.maxOf { it.x })
        yLabels = generateLabels(dataPoints.maxOf { it.y })
    }

    private fun generateLabels(maxValue: Int): List<Int> {
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
        val stepSize = maxLabelValue / maxLabels
        // `maxLabels + 1` because we want to end at `maxLabels * stepSize`.
        return (0 until maxLabels + 1).map { (it * stepSize) }
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        super.draw(batch, parentAlpha)

        // Save the current batch transformation matrix
        val oldTransformMatrix = batch.transformMatrix.cpy()
        // Set the batch transformation matrix to the local coordinates of the LineChart widget
        val transformMatrix = Matrix4()
        transformMatrix.translate(x, y, 0f)
        batch.transformMatrix = transformMatrix

        // We draw civilization labels first, because they limit the extension of the chart to the
        // right. We want to draw orientation lines together with the labels of the y axis and
        // therefore we need to know first how much space the civilization boxes took on the right.
        var yPosOfNextCiv = chartHeight
        val lastTurnDataPoints = getLastTurnDataPoints()
        val civGroups = lastTurnDataPoints.toList().sortedByDescending { (_, v) -> v.y }.map {
            getCivGroup(it.first, " - ${it.second.y}", currentPlayerCiv)
        }
        val largestCivGroupWidth = civGroups.maxOf { it.width }
        civGroups.forEach {
            it.setPosition(
                chartWidth - largestCivGroupWidth + (largestCivGroupWidth - it.width) / 2,
                yPosOfNextCiv - it.height
            )
            it.draw(batch, 1f)
            // Currently we don't really check whether y is overflowing to the bottom here.
            yPosOfNextCiv -= it.height + paddingBetweenCivs
        }

        val labelHeight = Label("123", Label.LabelStyle(Fonts.font, axisLabelColor)).height
        val yLabelsAsLabels =
                yLabels.map { Label(it.toString(), Label.LabelStyle(Fonts.font, axisLabelColor)) }
        val widestYLabelWidth = yLabelsAsLabels.maxOf { it.width }
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

        // We draw the y-axis labels second. They will take away some space on the left of the
        // widget which we need to consider when drawing the rest of the graph.
        yLabelsAsLabels.forEachIndexed { index, label ->
            val yPos = yAxisLabelMinY + index * (yAxisLabelYRange / (yLabels.size - 1))
            label.setPosition((widestYLabelWidth - label.width) / 2, yPos)
            label.draw(batch, 1f)

            // Draw y-axis orientation lines
            if (index > 0) drawLine(
                batch,
                widestYLabelWidth + axisToLabelPadding + axisLineWidth,
                yPos + labelHeight / 2,
                chartWidth - largestCivGroupWidth - civGroupToChartPadding,
                yPos + labelHeight / 2,
                orientationLineColor,
                orientationLineWidth
            )
        }

        // Draw x-axis labels
        val xLabelsAsLabels =
                xLabels.map { Label(it.toString(), Label.LabelStyle(Fonts.font, axisLabelColor)) }
        val firstXAxisLabelWidth = xLabelsAsLabels[0].width
        val lastXAxisLabelWidth = xLabelsAsLabels[xLabelsAsLabels.size - 1].width
        val xAxisLabelMinX =
                widestYLabelWidth + axisToLabelPadding + axisLineWidth / 2 - firstXAxisLabelWidth / 2
        val xAxisLabelMaxX =
                chartWidth - largestCivGroupWidth - paddingBetweenCivs - lastXAxisLabelWidth / 2
        val xAxisLabelXRange = xAxisLabelMaxX - xAxisLabelMinX
        xLabels.forEachIndexed { index, labelAsInt ->
            val label = Label(labelAsInt.toString(), Label.LabelStyle(Fonts.font, axisLabelColor))
            val xPos = xAxisLabelMinX + index * (xAxisLabelXRange / (xLabels.size - 1))
            label.setPosition(xPos - label.width / 2, 0f)
            label.draw(batch, 1f)

            // Draw x-axis orientation lines
            if (index > 0) drawLine(
                batch,
                xPos,
                labelHeight + axisToLabelPadding + axisLineWidth,
                xPos,
                chartHeight,
                orientationLineColor,
                orientationLineWidth
            )
        }

        // Draw y-axis
        val yAxisX = widestYLabelWidth + axisToLabelPadding + axisLineWidth / 2
        val xAxisY = labelHeight + axisToLabelPadding + axisLineWidth / 2
        drawLine(batch, yAxisX, xAxisY, yAxisX, chartHeight, axisColor, axisLineWidth)

        // Draw x-axis
        drawLine(
            batch,
            yAxisX,
            xAxisY,
            chartWidth - largestCivGroupWidth - civGroupToChartPadding,
            xAxisY,
            axisColor,
            axisLineWidth
        )


        // Draw line charts for each color
        val linesMinX = widestYLabelWidth + axisToLabelPadding + axisLineWidth
        val linesMaxX =
                chartWidth - largestCivGroupWidth - civGroupToChartPadding - lastXAxisLabelWidth / 2
        val linesMinY = labelHeight + axisToLabelPadding + axisLineWidth
        val linesMaxY = chartHeight - labelHeight / 2
        val scaleX = (linesMaxX - linesMinX) / xLabels.max()
        val scaleY = (linesMaxY - linesMinY) / yLabels.max()
        val sortedPoints = dataPoints.sortedBy { it.x }
        val pointsByCiv = sortedPoints.groupBy { it.civ }
        // We want the current player civ to be drawn last, so it is never overlapped by another player.
        val civIterationOrder =
        // By default the players with the highest points will be drawn last (i.e. they will
                // overlap others).
                pointsByCiv.keys.toList().sortedBy { lastTurnDataPoints[it]!!.y }
                    .toMutableList()
        // The current player might be a spectator.
        if (currentPlayerCiv in civIterationOrder) {
            civIterationOrder.remove(currentPlayerCiv)
            civIterationOrder.add(currentPlayerCiv)
        }
        for (civ in civIterationOrder) {
            val points = pointsByCiv[civ]!!
            for (i in 1 until points.size) {
                val prevPoint = points[i - 1]
                val currPoint = points[i]
                drawLine(
                    batch,
                    linesMinX + prevPoint.x * scaleX, linesMinY + prevPoint.y * scaleY,
                    linesMinX + currPoint.x * scaleX, linesMinY + currPoint.y * scaleY,
                    civ.nation.getOuterColor(), chartLineWidth
                )
            }
        }

        // Restore the previous batch transformation matrix
        batch.setTransformMatrix(oldTransformMatrix)
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

    private fun getCivGroup(
        civ: Civilization,
        afterCivNameText: String,
        currentPlayer: Civilization
    ): Table {
        val civGroup = Table()

        var labelText = "{${civ.civName.tr()}}{${afterCivNameText.tr()}}"
        var labelColor = Color.WHITE
        val backgroundColor: Color

        if (civ.isDefeated()) {
            civGroup.add(ImageGetter.getImage("OtherIcons/DisbandUnit")).size(30f)
            backgroundColor = Color.LIGHT_GRAY
            labelColor = Color.BLACK
        } else if (currentPlayer == civ // || game.viewEntireMapForDebug
                || currentPlayer.knows(civ)
                || currentPlayer.isDefeated()
                || currentPlayer.victoryManager.hasWon()
        ) {
            civGroup.add(ImageGetter.getNationPortrait(civ.nation, 30f))
            backgroundColor = civ.nation.getOuterColor()
            labelColor = civ.nation.getInnerColor()
        } else {
            civGroup.add(ImageGetter.getRandomNationPortrait(30f))
            backgroundColor = Color.DARK_GRAY
            labelText = Constants.unknownNationName
        }

        civGroup.background = BaseScreen.skinStrings.getUiBackground(
            "VictoryScreen/CivGroup",
            BaseScreen.skinStrings.roundedEdgeRectangleShape,
            backgroundColor
        )
        val label = labelText.toLabel(labelColor)
        label.setAlignment(Align.center)

        civGroup.add(label).padLeft(10f)
        civGroup.pack()
        return civGroup
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
        val shapeRenderer = ShapeRenderer()
        shapeRenderer.projectionMatrix = batch.projectionMatrix
        shapeRenderer.transformMatrix = batch.transformMatrix
        batch.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = color
        shapeRenderer.rectLine(x1, y1, x2, y2, width)
        shapeRenderer.end()

        batch.begin()
    }
}
