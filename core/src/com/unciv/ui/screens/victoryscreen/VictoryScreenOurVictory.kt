package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.Victory
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenOurVictory(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin) {
    private val gameInfo = worldScreen.gameInfo
    private val playerCiv = worldScreen.viewingCiv

    init {
        defaults().pad(10f)
        val victoriesToShow = gameInfo.getEnabledVictories()

        for (victory in victoriesToShow) {
            add("[${victory.key}] Victory".toLabel())
        }
        row()

        for (victory in victoriesToShow) {
            add(getOurVictoryColumn(victory.key))
        }
        row()

        for (victory in victoriesToShow) {
            add(victory.value.victoryScreenHeader.toLabel())
        }

    }

    private fun getOurVictoryColumn(victory: String): Table {
        val victoryObject = gameInfo.ruleset.victories[victory]!!
        val table = Table()
        table.defaults().pad(5f)
        var firstIncomplete = true
        for (milestone in victoryObject.milestoneObjects) {
            val completionStatus =
                    when {
                        milestone.hasBeenCompletedBy(playerCiv) -> Victory.CompletionStatus.Completed
                        firstIncomplete -> {
                            firstIncomplete = false
                            Victory.CompletionStatus.Partially
                        }
                        else -> Victory.CompletionStatus.Incomplete
                    }
            for (button in milestone.getVictoryScreenButtons(completionStatus, playerCiv)) {
                table.add(button).row()
            }
        }
        return table
    }

}
