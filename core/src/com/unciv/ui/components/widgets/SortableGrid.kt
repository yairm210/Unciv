@file:Suppress("MemberVisibilityCanBePrivate") // A generic Widget has public members not necessarily used elsewhere
package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.ISortableGridContentProvider
import com.unciv.ui.components.NonTransformGroup
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.IconCircleGroup
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
    /** Provides the actual "data" as in one object per row that can then be passed to [ISortableGridContentProvider] methods to fetch cell content.
     *
     *  Note: If your initial [sortState] has [SortDirection.None], then enumeration order of this will determine initial presentation order.
     */
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
    private val headerElements = hashMapOf<CT, IHeaderElement>()
    private val sortSymbols = hashMapOf<Boolean, Label>()

    val details = Table(skin) // public otherwise can't be used in reified public method
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
            val element = getHeaderElement(column)
            headerElements[column] = element
            val cell = headerRow.add(element.outerActor)
            element.sizeCell(cell)
            cell.align(column.align).fill(column.fillX, false).expand(column.expandX, false)
        }
    }

    /** Calls [updateHeader] and [updateDetails] but not [updateCallback].
     *
     *  Clients can call this if some data change affects cell content and sorting.
     *  Note there is optimization potential here - a mechanism that updates one cell, and resorts only if it is in the currently sorted column
     */
    fun update() {
        updateHeader()
        updateDetails()
    }

    /** Update the sort direction icons of the header */
    fun updateHeader() {
        for (column in columns) {
            val sortDirection = if (sortState.sortedBy == column) sortState.direction else SortDirection.None
            headerElements[column]?.setSortState(sortDirection)
        }
    }

    /** Rebuild the grid cells to update and/or resort the data */
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
            else -> sortBy.defaultSort
        }
        val direction = if (sortState.sortedBy == sortBy) sortState.direction else SortDirection.None
        setSort(sortBy, direction.inverted())
    }

    fun setSort(sortBy: CT, direction: SortDirection) {
        sortState.sortedBy = sortBy
        sortState.direction = direction

        // Rebuild header content to show sort state
        // And resort the table: clear and fill with sorted data
        update()
        fireCallback()
    }

    /** Find the first Cell that contains an Actor of type [T] that matches [predicate].
     *  @return `null` if no such Actor found */
    inline fun <reified T : Actor> findCell(predicate: (T) -> Boolean): Cell<T>? =
        details.cells.asSequence()
            .filterIsInstance<Cell<T>>() // does not remove cells with null actors, still necessary so the return type is fulfilled
            .firstOrNull { it.actor is T && predicate(it.actor) }

    /** Find the first Cell that contains an Actor of type [T] with the given [name][Actor.name].
     *
     *  Not to be confused with [Group.findActor], which does a recursive search over children, does not filter by the type, and returns the actor.
     *  @return `null` if no such Actor found */
    inline fun <reified T : Actor> findCell(name: String): Cell<T>? =
        findCell { it.name == name }


    // We must be careful - the implementations of IHeaderElement are inner classes in order to have access
    // to the SortableGrid type parameters, but that also means we have access to this@SortableGrid - automatically.
    // Any unqualified method calls not implemented in this or a superclass will silently try a
    // method offered by Table! Thus make sure any Actor methods run for the contained actors.

    /** Wrap icon, label or other Actor and sort symbol for a header cell.
     *
     *  Not an Actor but a wrapper that contains [outerActor] which goes into the actual header.
     *
     *  Where/how the sort symbol is rendered depends on the type returned by [ISortableGridContentProvider.getHeaderActor].
     *  Instantiate through [getHeaderElement] - can be [EmptyHeaderElement], [LayoutHeaderElement] or [IconHeaderElement].
     */
    // Note - not an Actor because Actor is not an interface. Otherwise we *could* build a class that **is** an Actor which can be
    // implemented by any Actor subclass but also carry additional fields and methods - via delegation.
    interface IHeaderElement {
        val outerActor: Actor
        val headerActor: Actor?
        var sortShown: SortDirection

        /** Show or remove the sort symbol.
         * @param newSort None removes the symbol, Ascending shows an up arrow, Descending a down arrow */
        fun setSortState(newSort: SortDirection)

        /** Internal: Used by common implementation for [setSortState] */
        fun removeSortSymbol(sortSymbol: Label)
        /** Internal: Used by common implementation for [setSortState] */
        fun showSortSymbol(sortSymbol: Label)

        /** Override in case the implementation has specific requirements for the Table.Cell its [outerActor] is hosted in. */
        fun sizeCell(cell: Cell<Actor>) {}
    }

    private fun IHeaderElement.initActivationAndTooltip(column: CT) {
        if (column.defaultSort != SortDirection.None)
            outerActor.onClick { toggleSort(column) }
        if (column.headerTip.isNotEmpty())
            headerActor!!.addTooltip(column.headerTip, 18f, tipAlign = Align.center, hideIcons = column.headerTipHideIcons)
    }

    private fun IHeaderElement.setSortStateImpl(newSort: SortDirection) {
        if (newSort == sortShown) return
        for (symbol in sortSymbols.values)
            removeSortSymbol(symbol)  // Important: Does nothing if the actor is not our child
        sortShown = newSort
        if (newSort == SortDirection.None) return
        val sortSymbol = sortSymbols[newSort == SortDirection.Descending]!!
        showSortSymbol(sortSymbol)
    }

    private inner class EmptyHeaderElement : IHeaderElement {
        override val outerActor = Actor()
        override val headerActor = null
        override var sortShown = SortDirection.None
        override fun setSortState(newSort: SortDirection) {}
        override fun removeSortSymbol(sortSymbol: Label) {}
        override fun showSortSymbol(sortSymbol: Label) {}
    }

    /** Version of [IHeaderElement] that works fine for Image or IconCircleGroup and **overlays** the sort symbol on its lower right.
     *  Also used for all non-Layout non-null returns from [ISortableGridContentProvider.getHeaderActor].
     */
    // Note this is not a WidgetGroup and thus does not implement Layout, so all layout details are left to the container
    // - in this case, a Table.Cell.  This will knowingly place the arrow partly outside the Group bounds.
    private inner class IconHeaderElement(column: CT, override val headerActor: Actor) : IHeaderElement {
        override val outerActor = NonTransformGroup()
        override var sortShown = SortDirection.None

        init {
            outerActor.setSize(iconSize, iconSize)
            outerActor.addActor(headerActor)
            headerActor.setSize(iconSize, iconSize)
            headerActor.center(outerActor)
            initActivationAndTooltip(column)
        }

        override fun sizeCell(cell: Cell<Actor>) {
            cell.size(iconSize)
        }
        override fun setSortState(newSort: SortDirection) = setSortStateImpl(newSort)
        override fun removeSortSymbol(sortSymbol: Label) {
            outerActor.removeActor(sortSymbol)
        }
        override fun showSortSymbol(sortSymbol: Label) {
            sortSymbol.setPosition(iconSize - 2f, 0f)
            outerActor.addActor(sortSymbol)
        }
    }

    /** Version of [IHeaderElement] for all Layout returns from [ISortableGridContentProvider.getHeaderActor].
     *  Draws the sort symbol by rendering [headerActor] and the sort symbol side by side in a HorizontalGroup
     */
    private inner class LayoutHeaderElement(column: CT, override val headerActor: Actor) : IHeaderElement {
        override val outerActor = HorizontalGroup()
        override var sortShown = SortDirection.None

        init {
            outerActor.align(column.align)
            outerActor.addActor(headerActor)
            initActivationAndTooltip(column)
        }
        override fun setSortState(newSort: SortDirection) = setSortStateImpl(newSort)
        override fun removeSortSymbol(sortSymbol: Label) {
            outerActor.removeActor(sortSymbol)
        }
        override fun showSortSymbol(sortSymbol: Label) {
            outerActor.addActor(sortSymbol)
        }
    }

    private fun getHeaderElement(column: CT): IHeaderElement {
        return when (val headerActor = column.getHeaderActor(iconSize)) {
            null -> EmptyHeaderElement()
            is Image, is IconCircleGroup -> IconHeaderElement(column, headerActor) // They're also `is Layout`, but we want the overlaid header version
            is Layout -> LayoutHeaderElement(column, headerActor)
            else -> IconHeaderElement(column, headerActor)  // We haven't got a better implementation for other non-Layout Actors.
        }
    }
}
