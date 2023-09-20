package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.components.SortableGrid
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.extensions.equalizeColumns
import com.unciv.ui.components.extensions.toPrettyString
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.screens.overviewscreen.UnitSupplyTable.getUnitSupplyTable

/**
 * Supplies the Unit sub-table for the Empire Overview
 */
class UnitOverviewTab(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class UnitTabPersistableData(
        var scrollY: Float? = null,
        override var sortedBy: UnitOverviewTabColumn = UnitOverviewTabColumn.Name
    ) : EmpireOverviewTabPersistableData(), SortableGrid.ISortState<UnitOverviewTabColumn> {
        override fun isEmpty() = scrollY == null && sortedBy == UnitOverviewTabColumn.Name
        override var direction = SortableGrid.SortDirection.None
    }
    override val persistableData = (persistedData as? UnitTabPersistableData) ?: UnitTabPersistableData()

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        if (persistableData.scrollY != null)
            pager.setPageScrollY(index, persistableData.scrollY!!)
        super.activated(index, caption, pager)
    }
    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        persistableData.scrollY = pager.getPageScrollY(index)
        removeBlinkAction()
    }

    private val fixedContent = Table()

    // used for select()
    private var blinkAction: Action? = null
    private var blinkActor: Actor? = null
    private fun removeBlinkAction() {
        if (blinkAction == null || blinkActor == null) return
        blinkActor!!.removeAction(blinkAction)
        blinkAction = null
        blinkActor = null
    }

    override fun getFixedContent() = fixedContent

    private val grid = SortableGrid(
        columns = UnitOverviewTabColumn.values().asIterable(),
        data = viewingPlayer.units.getCivUnits().asIterable(),
        actionContext = this,
        sortState = persistableData,
        iconSize = 25f
    ) { header, details, totals ->
        equalizeColumns(details, header, totals)
        this.layout()
    }

    init {
        fixedContent.add(getUnitSupplyTable(this)).align(Align.top).padBottom(10f).row()
        //fixedContent.add(grid.getHeader())
        top()
        add(grid)
    }

    companion object {
        /** Constructs an ID that allows recognizing a specific MapUnit without resorting to instance identity.
         *
         *  This allows for changes affecting the unit that choose to implement via re-creation,
         *  or recognizing an upgrade via the [unitToUpgradeTo] parameter.
         *  It uses name (ignoring user renames) and tile position, and therefore it is not valid across
         *  any user interaction involving movement or next-turn - internal UnitOverview use is fine.
         *
         *  * Example: `Bomber@(2,9).a3` means the Bomber unit in the (2,9) tile, 4th air unit slot
         */
        fun getUnitIdentifier(unit: MapUnit, unitToUpgradeTo: BaseUnit? = null): String {
            val name = unitToUpgradeTo?.name ?: unit.name
            val tile = unit.getTile()
            val posInTile = when (unit) {
                tile.militaryUnit -> "m"
                tile.civilianUnit -> "c"
                else -> "a${tile.airUnits.indexOf(unit)}"
            }
            return "$name@${tile.position.toPrettyString()}.$posInTile"
        }
    }

    override fun select(selection: String): Float? {
        val cell = grid.cells.asSequence()
                .filter { it.actor is IconTextButton && it.actor.name == selection }
                .firstOrNull() ?: return null
        val button = cell.actor as IconTextButton
        val scrollY = (0 until cell.row)
            .map { grid.getRowHeight(it) }.sum() -
                (parent.height - grid.getRowHeight(cell.row)) / 2

        removeBlinkAction()
        blinkAction = Actions.repeat(3, Actions.sequence(
            Actions.fadeOut(0.17f),
            Actions.fadeIn(0.17f)
        ))
        blinkActor = button
        button.addAction(blinkAction)
        return scrollY
    }

    internal fun updateAndSelect(selection: String) {
        grid.updateDetails()
        select(selection)
    }
}
