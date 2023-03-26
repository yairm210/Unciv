package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Civilization
import com.unciv.models.translations.tr
import com.unciv.ui.components.CivGroup
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenGlobalVictory(
    worldScreen: WorldScreen
) : VictoryScreen.VictoryScreenTab(worldScreen) {

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
            victoryColumn.add(CivGroup(civ, "\n" + buttonText.tr(), playerCivInfo)).fillX().row()
        }

        for (civ in majorCivs.filter { it.isDefeated() }.sortedByDescending { it.victoryManager.amountMilestonesCompleted(victory) }) {
            val buttonText = civ.victoryManager.getNextMilestone(victory)?.getVictoryScreenButtonHeaderText(false, civ) ?: "Done!"
            victoryColumn.add(CivGroup(civ, "\n" + buttonText.tr(), playerCivInfo)).fillX().row()
        }

        return victoryColumn
    }

}
