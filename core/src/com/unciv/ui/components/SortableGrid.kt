@file:Suppress("MemberVisibilityCanBePrivate") // A generic Widget has public members not necessarily used elsewhere
package com.unciv.ui.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.*
import com.unciv.ui.screens.basescreen.BaseScreen


/**
 * A generic sortable grid Widget
 *
 * @param IT Type of the data objects that provide info per row
 * @param ST Type for [parentScreen], Anything allowed, specific meaning defined only by ISortableGridContentProvider subclass
 * @param CT Type of the columns
 */
class SortableGrid<IT, ST, CT: ISortableGridContentProvider<IT, ST>> (
    /** Provides the columns to render as [ISortableGridContentProvider] instances */
    private val columns: Iterable<CT>,
    /** Provides the actual "data" as in one object per row that can then be passed to [ISortableGridContentProvider] methods to fetch cell content */
    private val data: Iterable<IT>,
    /** Passed to [ISortableGridContentProvider.getEntryActor] where it can be used to define `onClick` actions. */
    private val parentScreen: ST,
    /** Sorting state will be kept here - provide your own e.g. if you want to persist it */
    private val sortState: ISortState<CT> = SortState(columns.first()),
    /** Size for header icons - if you set this too low, there is a chance that the tables will be misaligned */
    private val iconSize: Float = 50f,
    /** vertical padding for all Cells */
    paddingVert: Float = 5f,
    /** horizontal padding for all Cells */
    paddingHorz: Float = 8f,
    /** When `true`, the header row isn't part of the widget but delivered through [getHeader] */
    private val separateHeader: Boolean = false,
    /** Called after every update - during init and re-sort */
    private val updateCallback: ((header: Table, details: Table, totals: Table) -> Unit)? = null
) : Table(BaseScreen.skin) {

    interface ISortState<CT> {
        var sortedBy: CT
        var descending: Boolean
    }
    class SortState<CT>(default: CT) : ISortState<CT> {
        override var sortedBy: CT = default
        override var descending: Boolean = false
    }

    /** Provides the header row separately if and only if [separateHeader] is true,
     * e.g. to allow scrolling the content but leave the header fixed
     * (which will need some column width equalization method).
     * @see com.unciv.ui.screens.overviewscreen.EmpireOverviewTab.equalizeColumns
     */
    fun getHeader(): Table {
        if (!separateHeader)
            throw IllegalStateException("You can't call SortableGrid.getHeader unless you override separateHeader to true")
        return headerRow
    }

    private val headerRow = Table(skin)
    private val headerIcons = hashMapOf<CT, HeaderGroup>()
    private val sortSymbols = hashMapOf<Boolean, Label>()

    private val details = Table(skin)
    private val totalsRow = Table(skin)

    init {
        headerRow.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)
        details.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)
        totalsRow.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)

        initHeader()
        updateHeader()
        updateDetails()
        initTotals()
        fireCallback()

        top()
        if (!separateHeader) {
            add(headerRow).row()
            addSeparator(Color.GRAY).pad(paddingVert, 0f)
        }
        add(details).row()
        addSeparator(Color.GRAY).pad(paddingVert, 0f)
        add(totalsRow)
    }

    private fun fireCallback() {
        if (updateCallback == null) return
        headerRow.pack()
        details.pack()
        totalsRow.pack()
        updateCallback.invoke(headerRow, details, totalsRow)
    }

    private fun initHeader() {
        sortSymbols[false] = "￪".toLabel().apply { setOrigin(Align.bottomRight) }
        sortSymbols[true] = "￬".toLabel().apply { setOrigin(Align.bottomRight) }

        for (column in columns) {
            val group = HeaderGroup(column)
            headerIcons[column] = group
            headerRow.add(group).size(iconSize).align(column.align)
                .fill(column.fillX, false).expand(column.expandX, false)
        }
    }

    fun updateHeader() {
        for (column in columns) {
            headerIcons[column]?.setSortState(sortState.descending.takeIf { column == sortState.sortedBy })
        }
    }

    fun updateDetails() {
        details.clear()
        if (data.none()) return

        val sorter = sortState.sortedBy.getComparator()
        var sortedData = data.sortedWith(sorter)
        if (sortState.descending)
            sortedData = sortedData.reversed()

        val cellsToEqualize = mutableListOf<Cell<Actor>>()
        for (item in sortedData) {
            for (column in columns) {
                val actor = column.getEntryActor(item, iconSize, parentScreen)
                if (actor == null) {
                    details.add()
                    continue
                }
                val cell = details.add(actor).align(column.align)
                    .fill(column.fillX, false).expand(column.expandX, false)
                if (column.equalizeHeight) cellsToEqualize.add(cell)
            }
            details.row()
        }

        // row heights may diverge - fix it by setting minHeight to
        // largest actual height (of the cells marked `equalizeHeight`)
        if (cellsToEqualize.isNotEmpty()) {
            val largestLabelHeight = cellsToEqualize.maxByOrNull { it.prefHeight }!!.prefHeight
            for (cell in cellsToEqualize) cell.minHeight(largestLabelHeight)
        }
    }

    private fun initTotals() {
        for (column in columns) {
            totalsRow.add(column.getTotalsActor(data)).align(column.align)
                .fill(column.fillX, false).expand(column.expandX, false)
        }
    }

    private fun toggleSort(sortBy: CT) {
        if (sortBy == sortState.sortedBy) {
            sortState.descending = !sortState.descending
        } else {
            sortState.sortedBy = sortBy
            sortState.descending = sortBy.defaultDescending
        }
        // Rebuild header content to show sort state
        updateHeader()
        // Sort the table: clear and fill with sorted data
        updateDetails()
        fireCallback()
    }

    // We must be careful - this is an inner class in order to have access to the SortableGrid
    // type parameters, but that also means we have access to this@SortableGrid - automatically.
    // Any unqualified method calls not implemented in this or a superclass will silently try a
    // method offered by Table! Thus all the explicit `this` - to be really safe.
    //
    // Using Group to overlay an optional sort symbol on top of the icon - we could also
    // do HorizontalGroup to have them side by side. Also, note this is not a WidgetGroup
    // so all layout details are left to the container - in this case, a Table.Cell
    /** Wrap icon and sort symbol for a header cell */
    inner class HeaderGroup(column: CT) : Group() {
        private val icon = column.getHeaderIcon(iconSize)
        private var sortShown: Boolean? = null

        init {
            this.isTransform = false
            this.setSize(iconSize, iconSize)
            if (icon != null) {
                this.onClick { toggleSort(column) }
                icon.setSize(iconSize, iconSize)
                icon.setOrigin(Align.center)
                icon.center(this)
                if (column.headerTip.isNotEmpty())
                    icon.addTooltip(column.headerTip, 18f, tipAlign = Align.center)
                this.addActor(icon)
            }
        }

        /** Show or remove the sort symbol.
         * @param showSort null removes the symbol, `false` shows an up arrow, `true` a down arrow */
        fun setSortState(showSort: Boolean?) {
            if (showSort == sortShown) return
            for (symbol in sortSymbols.values)
                symbol.remove()
            sortShown = showSort
            if (showSort == null) return
            val sortSymbol = sortSymbols[showSort]!!
            sortSymbol.setOrigin(Align.bottomRight)
            sortSymbol.setPosition(iconSize - 2f, 0f)
            this.addActor(sortSymbol)
        }
    }
}
