package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import kotlin.math.roundToInt
import yairm210.purity.annotations.Readonly

class VictoryScreenDemographics(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin) {
    private val playerCiv = worldScreen.viewingCiv

    private enum class RankLabels { Rank, Value, Best, Average, Worst }

    init {
        defaults().pad(5f)
        val majorCivs = worldScreen.gameInfo.civilizations.filter { it.isMajorCiv() }

        buildDemographicsHeaders()

        for (rankLabel in RankLabels.entries)   {
            if (rankLabel == RankLabels.Value) {
                // playerCiv is not necessarily alive nor major, and the `first` below would throw
                if (playerCiv.isDefeated() || !playerCiv.isMajorCiv()) continue
            }
            row()
            add(rankLabel.name.toLabel())

            for (category in RankingType.entries) {
                // Use turn-start snapshots so mid-turn army/economy changes cannot be used to
                // probe Best/Worst values of other civilizations (see linked issue).
                val aliveMajorCivsSorted = majorCivs.filter { it.isAlive() || it == playerCiv }
                    .map { VictoryScreen.CivWithStat(it, it.getStatForDemographics(category)) }
                    .sortedByDescending { it.value }

                fun addRankCivGroup(civEntry: VictoryScreen.CivWithStat) {
                    add(VictoryScreenCivGroup(civEntry, playerCiv)).fillX()
                }

                @Suppress("NON_EXHAUSTIVE_WHEN") // RankLabels.Demographic treated above
                when (rankLabel) {
                    RankLabels.Rank -> add((aliveMajorCivsSorted.indexOfFirst { it.civ == playerCiv } + 1).toLabel())
                    RankLabels.Value -> addRankCivGroup(aliveMajorCivsSorted.first { it.civ == playerCiv })
                    RankLabels.Best -> addRankCivGroup(aliveMajorCivsSorted.first())
                    RankLabels.Average -> add((aliveMajorCivsSorted.sumOf { it.value }.toFloat() / aliveMajorCivsSorted.size).roundToInt().toLabel())
                    RankLabels.Worst -> addRankCivGroup(aliveMajorCivsSorted.last())
                }
            }
        }
    }

    /**
     * Ranking value frozen at that civilization's last turn-start recording in [Civilization.statsHistory].
     * Falls back to a live value only when no history exists yet (e.g. very early game).
     */
    @Readonly
    private fun Civilization.getStatForDemographics(category: RankingType): Int {
        val history = statsHistory
        val snapshot = history[gameInfo.turns] ?: history.maxByOrNull { it.key }?.value
        return snapshot?.get(category) ?: getStatForRanking(category)
    }

    private fun buildDemographicsHeaders() {
        val demoLabel = Table().apply { defaults().pad(5f) }

        demoLabel.add("Demographic".toLabel()).row()
        demoLabel.addSeparator().fillX()
        add(demoLabel)

        for (category in RankingType.entries) {
            val headers = Table().apply { defaults().pad(5f) }
            val textAndIcon = Table().apply { defaults() }
            val columnImage = category.getImage()
            if (columnImage != null)
                textAndIcon.add(columnImage).center()
                    .size(Constants.defaultFontSize.toFloat() * 0.75f)
                    .padRight(2f).padTop(-2f)
            textAndIcon.add(category.label.toLabel()).row()
            headers.add(textAndIcon)
            headers.addSeparator()
            add(headers)
        }
    }
}
