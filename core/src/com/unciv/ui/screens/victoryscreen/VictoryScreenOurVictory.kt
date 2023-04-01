package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.Victory
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenOurVictory(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val gameInfo = worldScreen.gameInfo
    private val playerCiv = worldScreen.viewingCiv
    private val header = Table()

    init {
        val victoriesToShow = gameInfo.getEnabledVictories()

        for (victory in victoriesToShow) {
            header.add("[${victory.key}] Victory".toLabel()).pad(10f)
        }
        header.addSeparator(Color.GRAY)

        defaults().pad(10f)
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
        table.defaults().space(10f)
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

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        equalizeColumns(header, this)
    }

    override fun getFixedContent() = header
}
