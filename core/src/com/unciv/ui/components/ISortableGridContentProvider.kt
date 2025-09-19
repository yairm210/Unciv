package com.unciv.ui.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.SortableGrid
import com.unciv.ui.images.ImageGetter

/**
 * This defines all behaviour of a sortable Grid per column through overridable parts:
 * - [isVisible] can hide a column
 * - [align], [fillX], [expandX], [equalizeHeight] control geometry
 * - [getComparator] or [getEntryValue] control sorting, [defaultSort] the initial order
 * - [getHeaderActor], [headerTip] and [headerTipHideIcons] define how the header row looks
 * - [getEntryValue] or [getEntryActor] define what the cells display
 * - [getEntryValue] or [getTotalsActor] define what the totals row displays
 * @param IT The item type - what defines the row
 * @param ACT Action context type - The Type of any object you need passed to [getEntryActor] for potential OnClick calls
 */
interface ISortableGridContentProvider<IT, ACT> {
    /** tooltip for the column header, typically overridden to default to enum name, will be auto-translated */
    val headerTip: String

    /** Passed to addTooltip(hideIcons) - override to true to prevent autotranslation from inserting icons */
    val headerTipHideIcons get() = false

    /** [Cell.align] - used on header, entry and total cells */
    val align: Int

    /** [Cell.fillX] - used on header, entry and total cells */
    val fillX: Boolean

    /** [Cell.expandX] - used on header, entry and total cells */
    val expandX: Boolean

    /** When overridden `true`, the entry cells of this column will be equalized to their max height */
    val equalizeHeight: Boolean

    /** Default sort direction when a column is first sorted - can be None to disable sorting entirely for this column. */
    // Relevant for visuals (simply inverting the comparator would leave the displayed arrow not matching)
    val defaultSort: SortableGrid.SortDirection

    /** @return whether the column should be rendered */
    fun isVisible(gameInfo: GameInfo): Boolean = true

    /** [Comparator] Factory used for sorting.
     * - The default will sort by [getEntryValue] ascending.
     * @return positive to sort second lambda argument before first lambda argument
     */
    fun getComparator(): Comparator<IT> = compareBy { item: IT -> getEntryValue(item) }

    /** Factory for the header cell [Actor]
     *  @param iconSize Suggestion for icon size passed down from [SortableGrid] constructor, intended to scale the grid header. If the actor is not an icon, treat as height.
     */
    fun getHeaderActor(iconSize: Float): Actor?

    /** A getter for the numeric value to display in a cell */
    fun getEntryValue(item: IT): Int

    /** Factory for entry cell [Actor]
     * - By default displays the (numeric) result of [getEntryValue].
     * - [actionContext] can be used to define `onClick` actions.
     */
    fun getEntryActor(item: IT, iconSize: Float, actionContext: ACT): Actor? =
        getEntryValue(item).toCenteredLabel()

    /** Factory for totals cell [Actor]
     * - By default displays the sum over [getEntryValue].
     * - Note a count may be meaningful even if entry cells display something other than a number,
     *   In that case _not_ overriding this and supply a meaningful [getEntryValue] may be easier.
     * - On the other hand, a sum may not be meaningful even if the cells are numbers - to leave
     *   the total empty override to return `null`.
     */
    fun getTotalsActor(items: Iterable<IT>): Actor? =
        items.sumOf { getEntryValue(it) }.toCenteredLabel()

    companion object {
        val collator by lazy {UncivGame.Current.settings.getCollatorFromLocale()}

        @JvmStatic
        fun getCircledIcon(path: String, iconSize: Float, circleColor: Color = Color.LIGHT_GRAY) =
            ImageGetter.getImage(path)
                .apply { color = ImageGetter.CHARCOAL }
                .surroundWithCircle(iconSize, color = circleColor)

        @JvmStatic
        fun Int.toCenteredLabel(): Label =
            this.toLabel().apply { setAlignment(Align.center) }
    }
}
