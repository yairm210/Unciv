@file:Suppress("MemberVisibilityCanBePrivate") // A generic Widget has public members not necessarily used elsewhere
package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.ISortableGridContentProvider
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.basescreen.BaseScreen


/**
 * A generic sortable grid Widget
 *
 * Note this only remembers one sort criterion. Sorts like compareBy(type).thenBy(name) aren't supported.
 *
 * @param IT Type of the data objects that provide info per row
 * @param ACT Type for [actionContext], Anything allowed, specific meaning defined only by ISortableGridContentProvider subclass
 * @param CT Type of the columns
 */
class SortableGrid<IT, ACT, CT: ISortableGridContentProvider<IT, ACT>> (
    /** Provides the columns to render as [ISortableGridContentProvider] instances */
    private val columns: Iterable<CT>,
    /** Provides the actual "data" as in one object per row that can then be passed to [ISortableGridContentProvider] methods to fetch cell content */
    private val data: Iterable<IT>,
    /** Passed to [ISortableGridContentProvider.getEntryActor] where it can be used to define `onClick` actions. */
    private val actionContext: ACT,
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
    /** The direction a column may be sorted in */
    // None is the Natural order of underlying data - only available before using any sort-click
    enum class SortDirection { None, Ascending, Descending }

    /** Defines what is needed to remember the sorting state of the grid. */
    // Abstract to allow easier implementation outside this class
    // Note this does not automatically enforce this CT to be te same as SortableGrid's CT - not here.
    // The _client_ will get the compilation errors when passing a custom SortState with a mismatch in CT.
    interface ISortState<CT> {
        /** Stores the column this grid is currently sorted by */
        var sortedBy: CT
        /** Stores the direction column [sortedBy] is sorted in */
        var direction: SortDirection
    }

    /** Default implementation used as default for the [sortState] parameter
     *  - unused if the client provides that parameter */
    private class SortState<CT>(default: CT) : ISortState<CT> {
        override var sortedBy: CT = default
        override var direction: SortDirection = SortDirection.None
    }

    /** Provides the header row separately if and only if [separateHeader] is true,
     * e.g. to allow scrolling the content but leave the header fixed
     * (which will need some column width equalization method).
     * @see com.unciv.ui.components.extensions.equalizeColumns
     */
    fun getHeader(): Table {
        if (!separateHeader)
            throw IllegalStateException("You can't call SortableGrid.getHeader unless you set separateHeader to true")
        return headerRow
    }

    private val headerRow = Table(skin)
    private val headerIcons = hashMapOf<CT, HeaderGroup>()
    private val sortSymbols = hashMapOf<Boolean, Label>()

    private val details = Table(skin)
    private val totalsRow = Table(skin)

    init {
        require (!separateHeader || columns.none { it.expandX }) {
            "SortableGrid currently does not support separateHeader combined with expanding columns"
        }

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
        // Note: These will scale with GameSettings.fontSizeMultiplier - could be *partly* countered
        // with `toLabel(fontSize = (Constants.defaultFontSize / GUI.getSettings().fontSizeMultiplier).toInt())`
        sortSymbols[false] = Fonts.sortUpArrow.toString().toLabel()
        sortSymbols[true] = Fonts.sortDownArrow.toString().toLabel()

        for (column in columns) {
            val group = HeaderGroup(column)
            headerIcons[column] = group
            headerRow.add(group).size(iconSize).align(column.align)
                .fill(column.fillX, false).expand(column.expandX, false)
        }
    }

    fun updateHeader() {
        for (column in columns) {
            val sortDirection = if (sortState.sortedBy == column) sortState.direction else SortDirection.None
            headerIcons[column]?.setSortState(sortDirection)
        }
    }

    fun updateDetails() {
        details.clear()
        if (data.none()) return

        val comparator = sortState.sortedBy.getComparator()
        val sortedData = when(sortState.direction) {
            SortDirection.None -> data.asSequence()
            SortDirection.Ascending -> data.asSequence().sortedWith(comparator)
            SortDirection.Descending -> data.asSequence().sortedWith(comparator.reversed())
        }

        val cellsToEqualize = mutableListOf<Cell<Actor>>()
        for (item in sortedData) {
            for (column in columns) {
                val actor = column.getEntryActor(item, iconSize, actionContext)
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
        // Could be a SortDirection method, but it's single use here
        fun SortDirection.inverted() = when {
            this == SortDirection.Ascending -> SortDirection.Descending
            this == SortDirection.Descending -> SortDirection.Ascending
            sortBy.defaultDescending -> SortDirection.Descending
            else -> SortDirection.Ascending
        }

        sortState.run {
            if (sortedBy == sortBy) {
                direction = direction.inverted()
            } else {
                sortedBy = sortBy
                direction = SortDirection.None.inverted()
            }
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
    // This will knowingly place the arrow partly outside the Group bounds.
    /** Wrap icon and sort symbol for a header cell */
    inner class HeaderGroup(column: CT) : Group() {
        private val icon = column.getHeaderIcon(iconSize)
        private var sortShown: SortDirection = SortDirection.None

        init {
            this.isTransform = false
            this.setSize(iconSize, iconSize)
            if (icon != null) {
                this.onClick { toggleSort(column) }
                icon.setSize(iconSize, iconSize)
                icon.center(this)
                if (column.headerTip.isNotEmpty())
                    icon.addTooltip(column.headerTip, 18f, tipAlign = Align.center, hideIcons = column.headerTipHideIcons)
                this.addActor(icon)
            }
        }

        /** Show or remove the sort symbol.
         * @param showSort None removes the symbol, Ascending shows an up arrow, Descending a down arrow */
        fun setSortState(showSort: SortDirection) {
            if (showSort == sortShown) return
            for (symbol in sortSymbols.values)
                removeActor(symbol)  // Important: Does nothing if the actor is not our child
            sortShown = showSort
            if (showSort == SortDirection.None) return
            val sortSymbol = sortSymbols[showSort == SortDirection.Descending]!!
            sortSymbol.setPosition(iconSize - 2f, 0f)
            addActor(sortSymbol)
        }
    }
}
