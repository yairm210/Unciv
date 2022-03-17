package com.unciv.ui.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.utils.BaseScreen

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

    protected fun equalizeColumns(vararg tables: Table) {
        val columns = tables.first().columns
        val widths = (0 until columns)
            .mapTo(ArrayList(columns)) { column ->
                tables.maxOf { it.getColumnWidth(column) }
            }
        for (table in tables) {
            for (column in 0 until columns)
                table.cells[column].run {
                    minWidth(widths[column] - padLeft - padRight)
                }
            table.invalidate()
        }
    }
}
