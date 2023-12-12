package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Victory
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.equalizeColumns
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenGlobalVictory(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val header = Table()

    init {
        align(Align.top)

        val gameInfo = worldScreen.gameInfo
        val majorCivs = gameInfo.civilizations.asSequence().filter { it.isMajorCiv() }
        val victoriesToShow = gameInfo.getEnabledVictories()

        defaults().pad(10f)
        for ((victoryName, victory) in victoriesToShow) {
            header.add("[$victoryName] Victory".toLabel()).pad(10f)
            add(getColumn(majorCivs, victory, worldScreen.viewingCiv))
        }
        header.addSeparator(Color.GRAY)
    }

    private fun getColumn(
        majorCivs: Sequence<Civilization>,
        victory: Victory,
        playerCiv: Civilization
    ) = Table().apply {
        defaults().pad(10f)
        val sortedCivs = majorCivs.sortedWith(
            compareBy<Civilization> { it.isDefeated() }
            .thenBy { it.victoryManager.amountMilestonesCompleted(victory) }
        )
        for (civ in sortedCivs) {
            val buttonText = civ.victoryManager.getNextMilestone(victory)
                ?.getVictoryScreenButtonHeaderText(false, civ)
                ?: "Done!"
            add(VictoryScreenCivGroup(civ, buttonText, playerCiv)).fillX().row()
        }
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        equalizeColumns(header, this)
    }

    override fun getFixedContent() = header
}
