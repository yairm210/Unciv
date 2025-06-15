package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.packIfNeeded
import com.unciv.ui.components.input.OnClickListener
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.ui.screens.victoryscreen.VictoryScreenCivGroup.DefeatedPlayerStyle
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenCharts(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val gameInfo = worldScreen.gameInfo

    private var rankingType = RankingType.Score
    private var selectedCiv = worldScreen.selectedCiv
    private val viewingCiv = worldScreen.viewingCiv

    private val rankingTypeSelect = TranslatedSelectBox(RankingType.entries.map { it.label }, rankingType.name)
    private val civButtonsTable = Table()
    private val civButtonsScroll = AutoScrollPane(civButtonsTable)
    private val controlsColumn = Table()
    private val markerIcon = ImageGetter.getImage("OtherIcons/Star").apply {
        color = Color.GOLD
        align = Align.center
    }

    private var lineChart = LineChart(viewingCiv)
    // if it is negative - no zoom, if positive - zoom at turn X
    private var zoomAtX : IntRange? = null

    init {
        civButtonsScroll.setScrollingDisabled(true, false)
        civButtonsTable.defaults().space(20f).fillX()
        controlsColumn.defaults().space(20f).fillX()
        controlsColumn.add(rankingTypeSelect).right().row()
        controlsColumn.add(civButtonsScroll).fillY()
        defaults().fill().pad(20f)
        add(controlsColumn)
        updateControls()
        add(lineChart).growX().top().padLeft(0f)

        val onChartClick = OnClickListener(function = { _ , x, _ ->
            zoomAtX = if (zoomAtX == null) lineChart.getTurnAt(x) else null
            updateChart()
        })
        lineChart.addListener(onChartClick)

        rankingTypeSelect.onChange {
            rankingType = RankingType.entries
                .firstOrNull { it.label == rankingTypeSelect.selected.value }
                ?: RankingType.Score
            update()
        }
    }

    private fun update() {
        updateControls()
        updateChart()
    }

    private fun updateControls() {
        civButtonsTable.clear()
        val sortedCivs = gameInfo.civilizations.asSequence()
            .filter { it.isMajorCiv() }
            .map { VictoryScreen.CivWithStat(it, rankingType) }
            .sortedBy { it.civ.civName }
            .sortedByDescending { if(it.civ.isDefeated()) Int.MIN_VALUE else it.value }
        for (civEntry in sortedCivs) {
            if (civEntry.civ != selectedCiv) civButtonsTable.add()
            else civButtonsTable.add(markerIcon).size(24f).right()
            val button = VictoryScreenCivGroup(civEntry, viewingCiv, DefeatedPlayerStyle.REGULAR)
            button.touchable = Touchable.enabled
            civButtonsTable.add(button).row()
            button.onClick {
                selectedCiv = civEntry.civ
                update()
            }
        }
        civButtonsTable.add().padBottom(20f).row()
        civButtonsTable.pack()
        civButtonsScroll.layout()
    }

    private fun updateChart() {
        lineChart.update(getLineChartData(rankingType), selectedCiv)
        packIfNeeded()
    }

    private fun getLineChartData(rankingType: RankingType): List<DataPoint<Int>> {
        val dataPoints = gameInfo.civilizations.asSequence()
            .filter { it.isMajorCiv() }
            .flatMap { civ ->
                civ.statsHistory
                    .filterKeys { zoomAtX == null || it in zoomAtX!!  }
                    .filterValues { it.containsKey(rankingType) }
                    .map { (turn, data) -> Pair(turn, Pair(civ, data.getValue(rankingType))) }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { group -> group.value.toMap() }
            .flatMap { turn ->
                turn.value.map { (civ, value) -> DataPoint(turn.key, value, civ) }
            }.toMutableList()

        // Historical data does not include data for current turn except for civ that played turn 0 first,
        // so we append missing stat for current turn to the data for each other civ
        val pointsByCiv = dataPoints.sortedBy { it.x }.groupBy { it.civ }
        val actualTurn = dataPoints.maxOf { it.x }
        for (civ in pointsByCiv.keys.filterNot { it.isDefeated() })
            if (pointsByCiv[civ]!!.last().x != actualTurn)
                dataPoints += DataPoint(actualTurn, civ.getStatForRanking(rankingType), civ)
        
        return dataPoints
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
        controlsColumn.height = parent.height
        lineChart.height = parent.height
        update()
        civButtonsTable.invalidateHierarchy()
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(false)
    }
}
