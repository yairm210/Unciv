package com.unciv.ui.screens.overviewscreen

import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.SortableGrid
import com.unciv.ui.components.extensions.equalizeColumns


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

    private val grid = SortableGrid(
        columns = CityOverviewTabColumn.values().asIterable(),
        data = viewingPlayer.cities,
        parentScreen = overviewScreen,
        sortState = persistableData,
        iconSize = 50f,  //if you set this too low, there is a chance that the tables will be misaligned
        paddingVert = 5f,
        paddingHorz = 8f,
        separateHeader = false
    ) {
        header, details, totals ->
        this.name
        equalizeColumns(details, header, totals)
        this.layout()
    }

//     override fun getFixedContent() = Table().apply {
//         add("Cities".toLabel(fontSize = Constants.headingFontSize)).padTop(10f).row()
//         add(grid.getHeader()).padBottom(paddingVert).row()
//         addSeparator(Color.GRAY)
//     }

    init {
        add(grid)
    }
}
