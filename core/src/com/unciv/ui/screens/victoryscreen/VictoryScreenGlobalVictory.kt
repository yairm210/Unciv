package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenGlobalVictory(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin) {
    private val gameInfo = worldScreen.gameInfo
    private val playerCiv = worldScreen.viewingCiv

    init {
        defaults().pad(10f)
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }
        val enabledVictoryTypes = gameInfo.gameParameters.victoryTypes
        val victoriesToShow = gameInfo.ruleset.victories.filter {
            !it.value.hiddenInVictoryScreen && enabledVictoryTypes.contains(it.key)
        }

        for (victory in victoriesToShow) {
            add(getGlobalVictoryColumn(majorCivs, victory.key))
        }

    }

    private fun getGlobalVictoryColumn(majorCivs: List<Civilization>, victory: String): Table {
        val victoryColumn = Table().apply { defaults().pad(10f) }

        victoryColumn.add("[$victory] Victory".toLabel()).row()
        victoryColumn.addSeparator()

        for (civ in majorCivs.filter { !it.isDefeated() }.sortedByDescending { it.victoryManager.amountMilestonesCompleted(victory) }) {
            val buttonText = civ.victoryManager.getNextMilestone(victory)?.getVictoryScreenButtonHeaderText(false, civ) ?: "Done!"
            victoryColumn.add(VictoryScreenCivGroup(civ, buttonText, playerCiv)).fillX().row()
        }

        for (civ in majorCivs.filter { it.isDefeated() }.sortedByDescending { it.victoryManager.amountMilestonesCompleted(victory) }) {
            val buttonText = civ.victoryManager.getNextMilestone(victory)?.getVictoryScreenButtonHeaderText(false, civ) ?: "Done!"
            victoryColumn.add(VictoryScreenCivGroup(civ, buttonText, playerCiv)).fillX().row()
        }

        return victoryColumn
    }

}
