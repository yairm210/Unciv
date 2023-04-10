package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.LineChart
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.extensions.onChange
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.packIfNeeded
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.newgamescreen.TranslatedSelectBox

class VictoryScreenCharts(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val gameInfo = worldScreen.gameInfo

    private var rankingType = RankingType.Score
    private var selectedCiv = worldScreen.selectedCiv
    private val viewingCiv = worldScreen.viewingCiv

    private val rankingTypeSelect = TranslatedSelectBox(RankingType.values().map { it.name }, rankingType.name, skin)
    private val civButtonsTable = Table()
    private val civButtonsScroll = AutoScrollPane(civButtonsTable)
    private val controlsColumn = Table()
    private val markerIcon = ImageGetter.getImage("OtherIcons/Star").apply {
        color = Color.GOLD
        align = Align.center
    }

    private val chartHolder = Container<LineChart?>(null)

    init {
        civButtonsScroll.setScrollingDisabled(true, false)
        civButtonsTable.defaults().space(20f).fillX()
        controlsColumn.defaults().space(20f).fillX()
        controlsColumn.add(rankingTypeSelect).right().row()
        controlsColumn.add(civButtonsScroll).fillY()
        defaults().fill().pad(20f)
        add(controlsColumn)
        add(chartHolder).growX().top().padLeft(0f)

        rankingTypeSelect.onChange {
            rankingType = RankingType.values()
                .firstOrNull { it.name == rankingTypeSelect.selected.value }
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
            .sortedByDescending { it.value }
        for (civEntry in sortedCivs) {
            if (civEntry.civ != selectedCiv) civButtonsTable.add()
            else civButtonsTable.add(markerIcon).size(24f).right()
            val button = VictoryScreenCivGroup(civEntry, viewingCiv)
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
        // LineChart does not "cooperate" in Layout - the size we set here is final.
        // These values seem to fit the cell it'll be in - we subtract padding and some extra manually
        packIfNeeded()
        chartHolder.actor = LineChart(
            getLineChartData(rankingType),
            viewingCiv,
            selectedCiv,
            parent.width - getColumnWidth(0) - 60f,
            parent.height - 60f
        )
        chartHolder.invalidateHierarchy()
    }

    private fun getLineChartData(
        rankingType: RankingType
    ): Map<Int, Map<Civilization, Int>> {
        return gameInfo.civilizations.asSequence()
            .filter { it.isMajorCiv() }
            .flatMap { civ ->
                civ.statsHistory
                    .filterValues { it.containsKey(rankingType) }
                    .map { (turn, data) -> Pair(turn, Pair(civ, data.getValue(rankingType))) }
            }.groupBy({ it.first }, { it.second })
            .mapValues { group -> group.value.toMap() }
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
        getCell(controlsColumn).height(parent.height)
        getCell(chartHolder).height(parent.height)
        if (chartHolder.actor == null) update()
        civButtonsTable.invalidateHierarchy()
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(false)
    }
}
