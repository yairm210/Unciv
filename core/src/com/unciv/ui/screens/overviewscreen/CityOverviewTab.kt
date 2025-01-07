package com.unciv.ui.screens.overviewscreen

import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.extensions.equalizeColumns
import com.unciv.ui.components.widgets.SortableGrid
import com.unciv.ui.components.widgets.TabbedPager


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
    class CityTabPersistableData : EmpireOverviewTabPersistableData(), SortableGrid.ISortState<CityOverviewTabColumn> {
        override var sortedBy: CityOverviewTabColumn = CityOverviewTabColumn.CityColumn
        override var direction: SortableGrid.SortDirection = SortableGrid.SortDirection.None
        override fun isEmpty() = sortedBy == CityOverviewTabColumn.CityColumn && direction != SortableGrid.SortDirection.Descending
    }

    override val persistableData = (persistedData as? CityTabPersistableData) ?: CityTabPersistableData()

    private val grid = SortableGrid(
        columns = CityOverviewTabColumn.entries.asIterable(),
        data = viewingPlayer.cities,
        actionContext = overviewScreen,
        sortState = persistableData,
        iconSize = 50f,  //if you set this too low, there is a chance that the tables will be misaligned
        paddingVert = 5f,
        paddingHorz = 8f,
        separateHeader = true
    ) {
        header, details, totals ->
        // Notes: header.parent is the LinkedScrollPane of TabbedPager. Its linked twin is details.parent.parent.parent however!
        // horizontal "slack" if available width > content width is taken up between SortableGrid and CityOverviewTab for the details,
        // but not so for the header. We must force the LinkedScrollPane somehow (no? how?) to do so - or the header Table itself.

        equalizeColumns(details, header, totals)
        // todo Kludge! Positioning and alignment of the header Table within its parent has quirks when content width < stage width
        //      This code should likely be included in SortableGrid anyway?
        if (header.width < this.width) header.width = this.width
        this.validate()
    }

    override fun getFixedContent() = grid.getHeader()

    init {
        top()
        add(grid)
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        super.activated(index, caption, pager)
        // Being here can mean the user closed a CityScreen we opened from the first column - or the overview was just opened.
        // To differentiate, the EmpireOverviewScreen.resume code lies and passes an empty caption - a kludge, but a clean
        // callback architecture would mean a lot more work
        if (caption.isEmpty())
            grid.updateDetails()
    }
}
