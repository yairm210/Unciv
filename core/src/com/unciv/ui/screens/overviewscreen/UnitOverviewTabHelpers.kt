package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.UnitUpgradeMenu
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade
import yairm210.purity.annotations.Readonly

/**
 *  Helper library for [UnitOverviewTabColumn]
 *
 *  Note - this will be made into a companion object by simply inheriting it, so do treat it as singleton
 */
open class UnitOverviewTabHelpers {

    private fun showWorldScreenAt(position: Vector2, unit: MapUnit?) {
        GUI.resetToWorldScreen()
        GUI.getMap().setCenterPosition(position, forceSelectUnit = unit)
    }

    protected fun showWorldScreenAt(unit: MapUnit) = showWorldScreenAt(unit.currentTile.position, unit)
    protected fun showWorldScreenAt(tile: Tile) = showWorldScreenAt(tile.position, null)

    @Readonly
    private fun getWorkerActionText(unit: MapUnit): String? = when {
        // See UnitTurnManager.endTurn, if..workOnImprovement or UnitGroup.getActionImage: similar logic
        !unit.cache.hasUniqueToBuildImprovements -> null
        !unit.hasMovement() -> null
        unit.currentTile.improvementInProgress == null -> null
        !unit.canBuildImprovement(unit.getTile().getTileImprovementInProgress()!!) -> null
        else -> unit.currentTile.improvementInProgress
    }

    @Readonly
    protected fun getActionText(unit: MapUnit): String? {
        val workerText by lazy { getWorkerActionText(unit) }
        return when {
            unit.action == null -> workerText
            unit.isFortified() -> UnitActionType.Fortify.value
            unit.isGuarding() -> UnitActionType.Guard.value
            unit.isMoving() -> "Moving"
            unit.isAutomated() && workerText != null -> "[$workerText] ${Fonts.automate}"
            else -> unit.action
        }
    }

    protected fun getUpgradeTable(unit: MapUnit, actionContext: UnitOverviewTab): Table? {
        val table = Table()
        val unitActions = UnitActionsUpgrade.getUpgradeActionAnywhere(unit)
        if (unitActions.none()) return null
        val canEnable = actionContext.viewingPlayer.isCurrentPlayer() && GUI.isAllowedChangeState()

        for (unitAction in unitActions) {
            val enable = canEnable && unitAction.action != null
            val unitToUpgradeTo = (unitAction as UpgradeUnitAction).unitToUpgradeTo
            val selectKey = unit.id.toString()
            val upgradeIcon = ImageGetter.getUnitIcon(unitToUpgradeTo,
                if (enable) Color.GREEN else Color.GREEN.darken(0.5f))
            upgradeIcon.onClick {
                UnitUpgradeMenu(actionContext.overviewScreen.stage, upgradeIcon, unit, unitAction, enable) {
                    actionContext.update()
                    actionContext.overviewScreen.select(EmpireOverviewCategories.Units, selectKey) // actionContext.select skips setting scrollY
                }
            }
            upgradeIcon.addTooltip(unitToUpgradeTo.name, 24f, tipAlign = Align.bottomLeft)
            table.add(upgradeIcon).size(28f)
        }
        return table
    }

    @Readonly @Suppress("purity") // Calls action
    protected fun getUpgradeSortString(unit: MapUnit): String? {
        val upgrade = UnitActionsUpgrade.getUpgradeActionAnywhere(unit).firstOrNull()
            ?: return null
        return (upgrade as UpgradeUnitAction).unitToUpgradeTo.name.tr(hideIcons = true)
    }

    protected fun getPromotionsTable(unit: MapUnit, actionContext: UnitOverviewTab): Table {
        // This was once designed to be redrawn in place without rebuilding the grid.
        // That created problems with sorting - and determining when the state would allow minimal updating is complex.
        // But the old way also had the mini-bug that PromotionPicker allows unit rename which wasn't reflected on the grid...
        // Now it always does rebuild all rows (as simple as actionContext.update instead of updatePromotionsTable).
        val promotionsTable = Table()
        val canEnable = actionContext.viewingPlayer.isCurrentPlayer() && GUI.isAllowedChangeState()
        updatePromotionsTable(promotionsTable, unit, canEnable)
        val selectKey = unit.id.toString()

        fun onPromotionsTableClick() {
            val canPromote = canEnable && unit.promotions.canBePromoted()
            if (!canPromote && unit.promotions.promotions.isEmpty()) return
            // We can either add a promotion or at least view existing ones.
            // PromotionPickerScreen is reponsible for checking viewingPlayer.isCurrentPlayer and isAllowedChangeState **again**.
            actionContext.overviewScreen.game.pushScreen(
                PromotionPickerScreen(unit) {
                    // Todo seems the picker does not call this if only the unit rename was used
                    actionContext.update()
                    actionContext.overviewScreen.select(EmpireOverviewCategories.Units, selectKey) // actionContext.select skips setting scrollY
                }
            )
        }
        promotionsTable.onClick(::onPromotionsTableClick)
        return promotionsTable
    }

    private fun updatePromotionsTable(table: Table, unit: MapUnit, canEnable: Boolean) {
        table.clearChildren()

        // getPromotions goes by json order on demand - so this is the same sorting as on UnitTable,
        // but not same as on PromotionPickerScreen (which e.g. tries to respect prerequisite proximity)
        val promotions = unit.promotions.getPromotions(true)
        val showPromoteStar = unit.promotions.canBePromoted()
        if (promotions.any()) {
            val iconCount = promotions.count() + (if (showPromoteStar) 1 else 0)
            val numberOfLines = (iconCount - 1) / 8 + 1  // Int math: -1,/,+1 means divide rounding *up*
            val promotionsPerLine = (iconCount - 1) / numberOfLines + 1
            for (linePromotions in promotions.chunked(promotionsPerLine)) {
                for (promotion in linePromotions) {
                    table.add(ImageGetter.getPromotionPortrait(promotion.name))
                }
                if (linePromotions.size == promotionsPerLine) table.row()
            }
        }

        if (!showPromoteStar) return
        table.add(
            ImageGetter.getImage("OtherIcons/Star").apply {
                color = if (canEnable && unit.hasMovement() && unit.attacksThisTurn == 0)
                    Color.GOLDENROD
                else Color.GOLDENROD.darken(0.25f)
            }
        ).size(24f).padLeft(8f)
    }
}
