package com.unciv.ui.popups

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.widgets.AutoScrollPane

/**
 *  Adds (partial) scrollability to [AnimatedMenuPopup]. See its doc for details.
 *
 *  Provide content by implementing [createScrollableContent] and [createFixedContent].
 *  If you need to modify outer wrapper styling, override [createWrapperTable].
 */
abstract class ScrollableAnimatedMenuPopup(
    stage: Stage,
    position: Vector2
) : AnimatedMenuPopup(stage, position) {

    /** The API of this Widget is moved to [createScrollableContent], [createFixedContent], [createWrapperTable]. */
    final override fun createContentTable(): Table? {
        val top = createScrollableContent()
            ?: return null

        // Build content by wrapping scrollable and fixed parts
        val table = createWrapperTable()
        val scroll = AutoScrollPane(top)
        val scrollCell = table.add(scroll).growX()
        table.row()
        val bottom = createFixedContent()
        if (bottom != null) table.add(bottom)

        // ScrollBars need to be told their size
        val paddedMaxHeight = maxPopupHeight()
        val desiredTotalHeight = table.prefHeight
        val desiredScrollHeight = table.getRowPrefHeight(0)
        val scrollHeight = if (desiredTotalHeight <= paddedMaxHeight) desiredScrollHeight
            else paddedMaxHeight - (desiredTotalHeight - desiredScrollHeight)

        val paddedMaxWidth = maxPopupWidth()
        val desiredTotalWidth = table.prefWidth
        val desiredContentWidth = table.getColumnPrefWidth(0)
        val scrollWidth = if (desiredTotalWidth <= paddedMaxHeight) desiredContentWidth
            else paddedMaxWidth - (desiredTotalWidth - desiredContentWidth)

        scrollCell.size(scrollWidth, scrollHeight)

        return table
    }

    /** Provides an empty wrapper Table.
     *
     *  Override only to change styling.
     *  By default, a rounded edge dark gray background and 5f vertical / 15f horizontal padding for the two halves is used. */
    open fun createWrapperTable(): Table = super.createContentTable()!!

    /** Provides the scrollable top part
     *  @return `null` to abort opening the entire Popup */
    abstract fun createScrollableContent(): Table?

    /** Provides the fixed bottom part
     *  @return `null` to make the entire Popup scrollable (so the fixed part takes no vertical space, not even the default padding) */
    abstract fun createFixedContent(): Table?

    /** Determines maximum usable width
     *
     *  Use [stageToShowOn] to measure the Stage (from the underlying [Popup]).
     *  Do not use [Actor.stage][stage], it is uninitialized at this point.
     */
    open fun maxPopupWidth() = 0.95f * stageToShowOn.width - 5f

    /** Determines maximum usable height
     *
     *  Use [stageToShowOn] to measure the Stage (from the underlying [Popup]).
     *  Do not use [Actor.stage][stage], it is uninitialized at this point.
     */
    open fun maxPopupHeight() = 0.95f * stageToShowOn.height - 5f
}
