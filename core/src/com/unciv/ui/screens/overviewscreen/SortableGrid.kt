@file:Suppress("MemberVisibilityCanBePrivate") // A generic Widget has public members not necessarily used elsewhere
package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel


/**
 * A generic sortable grid Widget
 *
 * @param IT Type of the data objects that provide info per row
 * @param ST Type for [parentScreen], Anything allowed, specific meaning defined only by ISortableGridContentProvider subclass
 * @param CT Type of the columns
 */
class SortableGrid<IT, ST, CT: ISortableGridContentProvider<IT, ST>> (
    /** Provides an iterator over the columns to render as [ISortableGridContentProvider] instances */
    val iterator: Iterator<CT>,
    /** Provides the actual "data" as in one object per row that can then be passed to [ISortableGridContentProvider] methods to fetch cell content */
    val data: Iterable<IT>,
    /** Passed to [ISortableGridContentProvider.getEntryActor] where it can be used to define `onClick` actions. */
    val parentScreen: ST,
    /** Sorting state will be kept here - provide your own e.g. if you want to persist it */
    val sortState: ISortState<CT> = SortState(iterator.next()),
    /** Size for header icons - if you set this too low, there is a chance that the tables will be misaligned */
    val iconSize: Float = 50f,
    /** vertical padding for all Cells */
    paddingVert: Float = 5f,
    /** horizontal padding for all Cells */
    paddingHorz: Float = 8f,
    /** When `true`, the header row isn't part of the widget but delivered through [getHeader] */
    val separateHeader: Boolean = false,
    /** Called after every update - during init and re-sort */
    val updateCallback: ((header: Table, details: Table, totals: Table) -> Unit)? = null
) : Table(BaseScreen.skin) {

    interface ISortState<CT> {
        var sortedBy: CT
        var descending: Boolean
    }
    class SortState<CT>(default: CT) : ISortState<CT> {
        override var sortedBy: CT = default
        override var descending: Boolean = false
    }

    /** Provides the header row separately if and only if [separateHeader] is true */
    fun getHeader(): Table {
        if (!separateHeader)
            throw IllegalStateException("You can't call SortableGrid.getHeader unless you override separateHeader to true")
        updateHeader()
        return headerRow
    }

    private val headerRow = Table(skin)
    private val headerIcons = hashMapOf<CT, Actor>()
    private val sortLabels = hashMapOf<Boolean, Label>()
    private val details = Table(skin)
    private val totalsRow = Table(skin)

    init {
        headerRow.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)
        details.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)
        totalsRow.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)

        for (column in iterator) {
            headerIcons[column] = column.getHeaderIcon() ?: continue
        }
        sortLabels[false] = "￪".toLabel()
        sortLabels[true] = "￬".toLabel()

        updateHeader()
        updateDetails()
        updateTotals()

        top()
        if (!separateHeader) {
            add(headerRow).row()
            addSeparator(Color.GRAY).pad(paddingVert, 0f)
        }
        add(details).row()
        addSeparator(Color.GRAY).pad(paddingVert, 0f)
        add(totalsRow)
    }

    fun updateHeader() {
        headerRow.clear()
        for (column in iterator) {
            headerRow.add(getSortIcon(column))
                .size(iconSize).align(column.align)
                .fill(column.fillX, false).expand(column.expandX, false)
        }
        headerRow.pack()
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
            for (column in iterator) {
                val actor = column.getEntryActor(item, parentScreen)
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
        // largest actual height (of the construction cell) - !! guarded by isEmpty test above
        val largestLabelHeight = cellsToEqualize.maxByOrNull{ it.prefHeight }!!.prefHeight
        for (cell in cellsToEqualize) cell.minHeight(largestLabelHeight)

        details.pack()

        updateCallback?.invoke(headerRow, details, totalsRow)
    }

    fun updateTotals() {
        totalsRow.clear()
        for (column in iterator) {
            totalsRow.add(column.getTotalsActor(data)).align(column.align)
                .fill(column.fillX, false).expand(column.expandX, false)
        }
        totalsRow.pack()
    }

    private fun getSortIcon(column: CT): Group {
        val group = Group()
        val actor = headerIcons[column] ?: return group

        group.apply {
            isTransform = false
            setSize(iconSize, iconSize)
            onClick { toggleSort(column) }
        }

        if (column == sortState.sortedBy) {
            val label = sortLabels[sortState.descending]!!
            label.setOrigin(Align.bottomRight)
            label.setPosition(iconSize - 2f, 0f)
            group.addActor(label)
        }

        actor.setSize(iconSize, iconSize)
        actor.center(this)
        actor.setOrigin(Align.center)
        if (column.headerTip.isNotEmpty())
            actor.addTooltip(column.headerTip, 18f, tipAlign = Align.center)
        group.addActor(actor)
        return group
    }

    private fun toggleSort(sortBy: CT) {
        if (sortBy == sortState.sortedBy) {
            sortState.descending = !sortState.descending
        } else {
            sortState.sortedBy = sortBy
            sortState.descending = sortBy.defaultDescending
        }
        // show sort state
        updateHeader()
        // sort the table: clear and fill with sorted data
        updateDetails()
    }
}
