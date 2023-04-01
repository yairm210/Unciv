package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenCivRankings(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val header = Table()

    init {
        defaults().pad(10f)

        val majorCivs = worldScreen.gameInfo.civilizations.filter { it.isMajorCiv() }

        for (category in RankingType.values()) {
            val textAndIcon = Table()
            val columnImage = category.getImage()
            if (columnImage != null)
                textAndIcon.add(columnImage).size(Constants.defaultFontSize.toFloat() * 0.75f)
                    .padRight(2f).padTop(-2f)
            textAndIcon.add(category.name.replace('_' , ' ').toLabel()).row()
            header.add(textAndIcon).pad(10f)

            val column = Table().apply { defaults().space(10f) }
            val civData = majorCivs
                .map { VictoryScreen.CivWithStat(it, category) }
                .sortedByDescending { it.value }
            for (civEntry in civData) {
                column.add(VictoryScreenCivGroup(civEntry, worldScreen.viewingCiv)).fillX().row()
            }
            add(column)
        }
        header.addSeparator(Color.GRAY)
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        equalizeColumns(header, this)
    }

    override fun getFixedContent() = header
}
