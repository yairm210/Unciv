package com.unciv.ui.screens.overviewscreen

import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.SortableGrid
import com.unciv.ui.components.extensions.equalizeColumns


/**
 *  Provides the Cities tab for Empire Overview.
 *
 *  Uses [SortableGrid], so all actual content implementation is in the [CityOverviewTabColumn] enum.
 */
class CityOverviewTab(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class CityTabPersistableData(
        override var sortedBy: CityOverviewTabColumn = CityOverviewTabColumn.CityColumn,

    ) : EmpireOverviewTabPersistableData(), SortableGrid.ISortState<CityOverviewTabColumn> {
        override fun isEmpty() = sortedBy == CityOverviewTabColumn.CityColumn
        override var direction = SortableGrid.SortDirection.None
    }

    override val persistableData = (persistedData as? CityTabPersistableData) ?: CityTabPersistableData()

    private val grid = SortableGrid(
        columns = CityOverviewTabColumn.values().asIterable(),
        data = viewingPlayer.cities,
        actionContext = overviewScreen,
        sortState = persistableData,
        iconSize = 50f,  //if you set this too low, there is a chance that the tables will be misaligned
        paddingVert = 5f,
        paddingHorz = 8f,
        separateHeader = true
    ) {
        header, details, totals ->
        equalizeColumns(details, header, totals)
        this.validate()
    }

    override fun getFixedContent() = grid.getHeader()

    init {
        add(grid)
    }
}
