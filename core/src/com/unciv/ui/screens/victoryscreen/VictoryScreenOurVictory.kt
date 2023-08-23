package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.MilestoneType
import com.unciv.models.ruleset.Victory
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenOurVictory(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val header = Table()
    private val stageWidth = worldScreen.stage.width

    init {
        align(Align.top)

        val gameInfo = worldScreen.gameInfo
        val victoriesToShow = gameInfo.getEnabledVictories()

        defaults().pad(10f)
        for ((victoryName, victory) in victoriesToShow) {
            header.add("[$victoryName] Victory".toLabel()).pad(10f)
            add(getColumn(victory, worldScreen.viewingCiv)).top()
        }

        row()
        for (victory in victoriesToShow.values) {
            add(victory.victoryScreenHeader.toLabel())
        }

        header.addSeparator(Color.GRAY)
    }

    private fun getColumn(victory: Victory, playerCiv: Civilization): Table {
        val table = Table()
        table.defaults().space(10f)
        var firstIncomplete = true
        for (milestone in victory.milestoneObjects) {
            val completionStatus = when {
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
