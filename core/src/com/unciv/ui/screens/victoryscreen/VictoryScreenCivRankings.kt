package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenCivRankings(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin) {
    private val gameInfo = worldScreen.gameInfo
    private val playerCiv = worldScreen.viewingCiv

    init {
        defaults().pad(5f)

        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }

        for (category in RankingType.values()) {
            val column = Table().apply { defaults().pad(5f) }
            val textAndIcon = Table().apply { defaults() }
            val columnImage = category.getImage()
            if (columnImage != null) textAndIcon.add(columnImage).size(Constants.defaultFontSize.toFloat() * 0.75f).padRight(2f).padTop(-2f)
            textAndIcon.add(category.name.replace('_' , ' ').toLabel()).row()
            column.add(textAndIcon)
            column.addSeparator()

            val civData = majorCivs
                .map { VictoryScreen.CivWithStat(it, category) }
                .sortedByDescending { it.value }
            for (civEntry in civData) {
                column.add(VictoryScreenCivGroup(civEntry, playerCiv)).fillX().row()
            }

            add(column)
        }
    }

}
