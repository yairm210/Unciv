package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.unciv.logic.GameInfo
import java.util.Comparator


/**
 * This defines all behaviour of a sortable Grid per column through overridable parts:
 * - [isVisible] can hide a column
 * - [align], [fillX], [expandX], [equalizeHeight] control geometry
 * - [getComparator] or [getEntryValue] control sorting, [defaultDescending] the initial order
 * - [headerTip] and [getHeaderIcon] define how the header row looks
 * - [getEntryValue] or [getEntryActor] define what the cells display
 * - [getEntryValue] or [getTotalsActor] define what the totals row displays
 */
interface ISortableGridContentProvider<T> {
    /** tooltip for the column header, defaults to enum name, will be auto-translated */
    val headerTip: String

    /** [Cell.align] - used on header, entry and total cells */
    val align: Int

    /** [Cell.fillX] - used on header, entry and total cells */
    val fillX: Boolean

    /** [Cell.expandX] - used on header, entry and total cells */
    val expandX: Boolean

    /** When overridden `true`, the entry cells of this column will be equalized to their max height */
    val equalizeHeight: Boolean

    /** When `true` the column will be sorted descending initially - relevant for visual representation too */
    val defaultDescending: Boolean

    /** @return whether the column should be rendered */
    fun isVisible(gameInfo: GameInfo): Boolean = true

    /** [Comparator] Factory used for sorting.
     * @return positive to sort second lambda argument before first lambda argument
     */
    fun getComparator(): Comparator<T>

    /** Factory for the header cell [Actor] */
    fun getHeaderIcon(): Actor?

    /** A getter for the numeric value to display in a cell */
    fun getEntryValue(item: T): Int

    /** Factory for entry cell [Actor]
     * - By default displays the (numeric) result of [getEntryValue].
     * - [overviewScreen] can be used to define `onClick` actions.
     */
    fun getEntryActor(item: T, overviewScreen: EmpireOverviewScreen): Actor?

    /** Factory for totals cell [Actor]
     * - By default displays the sum over [getEntryValue].
     * - Note a count may be meaningful even if entry cells display something other than a number,
     *   In that case _not_ overriding this and supply a meaningful [getEntryValue] may be easier.
     * - On the other hand, a sum may not be meaningful even if the cells are numbers - to leave
     *   the total empty override to return `null`.
     */
    fun getTotalsActor(items: Iterable<T>): Actor?
}
