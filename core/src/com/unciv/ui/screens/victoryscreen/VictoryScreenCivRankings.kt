package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.equalizeColumns
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class VictoryScreenCivRankings(
    worldScreen: WorldScreen
) : Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val header = Table()

    init {
        align(Align.topLeft)
        header.align(Align.topLeft)
        defaults().pad(10f)

        val majorCivs = worldScreen.gameInfo.civilizations.filter { it.isMajorCiv() }

        for (category in RankingType.entries) {
            val textAndIcon = Table()
            val columnImage = category.getImage()
            if (columnImage != null)
                textAndIcon.add(columnImage).size(Constants.defaultFontSize.toFloat() * 0.75f)
                    .padRight(2f).padTop(-2f)
            textAndIcon.add(category.label.toLabel()).row()
            header.add(textAndIcon).pad(10f)

            val column = Table().apply { defaults().space(10f) }
            val civData = majorCivs
                .map { VictoryScreen.CivWithStat(it, category) }
                .sortedBy { it.civ.civName }
                .sortedByDescending { if(it.civ.isDefeated()) Int.MIN_VALUE else it.value }
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
