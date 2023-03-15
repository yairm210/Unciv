package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel


class CityOverviewTab(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class CityTabPersistableData(
        override var sortedBy: CityOverviewTabColumn = CityOverviewTabColumn.CityColumn,
        override var descending: Boolean = false
    ) : EmpireOverviewTabPersistableData(), SortableGrid.ISortState<CityOverviewTabColumn> {
        override fun isEmpty() = sortedBy == CityOverviewTabColumn.CityColumn
    }

    override val persistableData = (persistedData as? CityTabPersistableData) ?: CityTabPersistableData()

    companion object {
        const val iconSize = 50f  //if you set this too low, there is a chance that the tables will be misaligned
        const val paddingVert = 5f      // vertical padding
        const val paddingHorz = 8f      // horizontal padding
    }

    private val grid = SortableGrid(
        iterator = CityOverviewTabColumn.values().iterator(),
        data = viewingPlayer.cities,
        parentScreen = overviewScreen,
        sortState = persistableData,
        iconSize, paddingVert, paddingHorz,
        separateHeader = true
    ) {
        header, details, totals ->
        equalizeColumns(details, header, totals)
        layout()
    }

    override fun getFixedContent() = Table().apply {
        add("Cities".toLabel(fontSize = Constants.headingFontSize)).padTop(10f).row()
        add(grid.getHeader()).padBottom(paddingVert).row()
        addSeparator(Color.GRAY)
    }

    init {
        add(grid)
    }
}
