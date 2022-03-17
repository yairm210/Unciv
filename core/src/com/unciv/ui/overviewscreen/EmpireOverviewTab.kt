package com.unciv.ui.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.packIfNeeded
import com.unciv.ui.utils.toLabel

abstract class EmpireOverviewTab (
    val viewingPlayer: CivilizationInfo,
    val overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : Table(BaseScreen.skin) {
    open class EmpireOverviewTabPersistableData {
        open fun isEmpty() = true
    }
    open val persistableData = persistedData ?: EmpireOverviewTabPersistableData()
    /** Override if your Tab needs to do stuff on activation. @return non-null to scroll the Tab vertically within the TabbedPager. */
    open fun activated(): Float? = null
    /** Override if your Tab needs to do housekeeping when it loses focus. [scrollY] is the Tab's current vertical scroll position. */
    open fun deactivated(scrollY: Float) {}
    /** Override to supply content not participating in scrolling */
    open fun getFixedContent(): WidgetGroup? = null

    val gameInfo = viewingPlayer.gameInfo

    /** Sets first row cell's minWidth to the max of the widths of that column over all given tables
     *
     * Notes:
     * - This aligns columns only if the tables are arranged vertically with equal X coordinates.
     * - first table determines columns processed, all others must have at least the same column count.
     * - Tables are left as needsLayout==true, so while equal width is ensured, you may have to pack if you want to see the value before this is rendered.
     */
    protected fun equalizeColumns(vararg tables: Table) {
        for (table in tables)
            table.packIfNeeded()
        val columns = tables.first().columns
        if (tables.any { it.columns < columns })
            throw IllegalStateException("EmpireOverviewTab.equalizeColumns needs all tables to have at least the same number of columns as the first one")
        val widths = (0 until columns)
            .mapTo(ArrayList(columns)) { column ->
                tables.maxOf { it.getColumnWidth(column) }
            }
        for (table in tables) {
            for (column in 0 until columns)
                table.cells[column].run {
                    if (actor == null)
                        // Empty cells ignore minWidth, so just doing Table.add() for an empty cell in the top row will break this. Fix!
                        setActor<Label>("".toLabel())
                    else if (Align.isCenterHorizontal(align)) (actor as? Label)?.run {
                        // minWidth acts like fillX, so Labels will fill and then left-align by default. Fix!
                        if (!Align.isCenterHorizontal(labelAlign))
                            setAlignment(Align.center)
                    }
                    minWidth(widths[column] - padLeft - padRight)
                }
            table.invalidate()
        }
    }
}
