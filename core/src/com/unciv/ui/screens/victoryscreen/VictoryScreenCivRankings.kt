package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenCivRankings(
    private val worldScreen: WorldScreen
) : VictoryScreen.VictoryScreenTab(worldScreen) {

    init {
        defaults().pad(5f)

        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }
        if (UncivGame.Current.settings.useDemographics) buildDemographicsTable(majorCivs)
        else buildRankingsTable(majorCivs)
    }

    enum class RankLabels { Rank, Value, Best, Average, Worst}
    private fun buildDemographicsTable(majorCivs: List<Civilization>) {
        buildDemographicsHeaders()

        for (rankLabel in RankLabels.values())   {
            row()
            add(rankLabel.name.toLabel())

            for (category in RankingType.values()) {
                val aliveMajorCivsSorted = majorCivs.filter{ it.isAlive() }.sortedByDescending { it.getStatForRanking(category) }

                fun addRankCivGroup(civ: Civilization) { // local function for reuse of getting and formatting civ stats
                    add(getCivGroup(civ, ": " + civ.getStatForRanking(category).toString(), playerCivInfo)).fillX()
                }

                @Suppress("NON_EXHAUSTIVE_WHEN") // RankLabels.Demographic treated above
                when (rankLabel) {
                    RankLabels.Rank -> add((aliveMajorCivsSorted.indexOfFirst { it == worldScreen.viewingCiv } + 1).toLabel())
                    RankLabels.Value -> addRankCivGroup(worldScreen.viewingCiv)
                    RankLabels.Best -> addRankCivGroup(aliveMajorCivsSorted.firstOrNull()!!)
                    RankLabels.Average -> add((aliveMajorCivsSorted.sumOf { it.getStatForRanking(category) } / aliveMajorCivsSorted.size).toLabel())
                    RankLabels.Worst -> addRankCivGroup(aliveMajorCivsSorted.lastOrNull()!!)
                }
            }
        }
    }

    private fun buildDemographicsHeaders() {
        val demoLabel = Table().apply { defaults().pad(5f) }

        demoLabel.add("Demographic".toLabel()).row()
        demoLabel.addSeparator().fillX()
        add(demoLabel)

        for (category in RankingType.values()) {
            val headers = Table().apply { defaults().pad(5f) }
            val textAndIcon = Table().apply { defaults() }
            val columnImage = category.getImage()
            if (columnImage != null) textAndIcon.add(columnImage).center().size(Constants.defaultFontSize.toFloat() * 0.75f).padRight(2f).padTop(-2f)
            textAndIcon.add(category.name.replace('_', ' ').toLabel()).row()
            headers.add(textAndIcon)
            headers.addSeparator()
            add(headers)
        }
    }

    private fun buildRankingsTable(majorCivs: List<Civilization>) {
        for (category in RankingType.values()) {
            val column = Table().apply { defaults().pad(5f) }
            val textAndIcon = Table().apply { defaults() }
            val columnImage = category.getImage()
            if (columnImage != null) textAndIcon.add(columnImage).size(Constants.defaultFontSize.toFloat() * 0.75f).padRight(2f).padTop(-2f)
            textAndIcon.add(category.name.replace('_' , ' ').toLabel()).row()
            column.add(textAndIcon)
            column.addSeparator()

            for (civ in majorCivs.sortedByDescending { it.getStatForRanking(category) }) {
                column.add(getCivGroup(civ, ": " + civ.getStatForRanking(category).toString(), playerCivInfo)).fillX().row()
            }

            add(column)
        }
    }

}
