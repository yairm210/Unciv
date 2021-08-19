package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PromotionPickerScreen
import com.unciv.ui.utils.*
import java.text.DecimalFormat
import kotlin.math.abs

/**
 * Supplies the Unit sub-table for the Empire Overview
 */
class UnitOverviewTable(
    private val viewingPlayer: CivilizationInfo,
    private val overviewScreen: EmpireOverviewScreen
) : Table(CameraStageBaseScreen.skin) {

    init {
        add(getUnitSupplyTable()).top().padRight(25f)
        add(getUnitListTable())
        pack()
    }

    private fun getUnitSupplyTable(): Table {
        val unitSupplyTable = Table(CameraStageBaseScreen.skin)
        unitSupplyTable.defaults().pad(5f)
        unitSupplyTable.apply {
            add("Unit Supply".tr()).colspan(2).center().row()
            addSeparator()
            add("Base Supply".tr()).left()
            add(viewingPlayer.stats().getBaseUnitSupply().toLabel()).right().row()
            add("Cities".tr()).left()
            add(viewingPlayer.stats().getUnitSupplyFromCities().toLabel()).right().row()
            add("Population".tr()).left()
            add(viewingPlayer.stats().getUnitSupplyFromPop().toLabel()).right().row()
            addSeparator()
            add("Total Supply".tr()).left()
            add(viewingPlayer.stats().getUnitSupply().toLabel()).right().row()
            add("In Use".tr()).left()
            add(viewingPlayer.getCivUnitsSize().toLabel()).right().row()
            addSeparator()
            add("Supply Deficit".tr()).left()
            add(viewingPlayer.stats().getUnitSupplyDeficit().toLabel()).right().row()
            add("Production Penalty".tr()).left()
            add((viewingPlayer.stats().getUnitSupplyProductionMalus()).toInt().toString()+"%").right().row()
        }
        return unitSupplyTable
    }

    private fun getUnitListTable(): Table {
        val game = overviewScreen.game
        val unitListTable = Table(CameraStageBaseScreen.skin)
        unitListTable.defaults().pad(5f)
        unitListTable.apply {
            add("Name".tr())
            add("Action".tr())
            add(Fonts.strength.toString())
            add(Fonts.rangedStrength.toString())
            add(Fonts.movement.toString())
            add("Closest city".tr())
            add("Promotions".tr())
            add("Health".tr())
            row()
            addSeparator()

            for (unit in viewingPlayer.getCivUnits().sortedWith(
                compareBy({ it.displayName() },
                    { !it.due },
                    { it.currentMovement < 0.1f },
                    { abs(it.currentTile.position.x) + abs(it.currentTile.position.y) })
            )) {
                val baseUnit = unit.baseUnit()

                val button = Button(skin)
                button.add(UnitGroup(unit, 20f)).padRight(5f)
                button.add(unit.displayName().toLabel())
                button.onClick {
                    game.setWorldScreen()
                    game.worldScreen.mapHolder.setCenterPosition(unit.currentTile.position)
                }
                add(button).left()
                if (unit.action == null) add()
                else add(unit.getActionLabel().tr())
                if (baseUnit.strength > 0) add(baseUnit.strength.toString()) else add()
                if (baseUnit.rangedStrength > 0) add(baseUnit.rangedStrength.toString()) else add()
                add(DecimalFormat("0.#").format(unit.currentMovement) + "/" + unit.getMaxMovement())
                val closestCity =
                    unit.getTile().getTilesInDistance(3).firstOrNull { it.isCityCenter() }
                if (closestCity != null) add(closestCity.getCity()!!.name.tr()) else add()
                val promotionsTable = Table()
                val promotionsForUnit = unit.civInfo.gameInfo.ruleSet.unitPromotions.values.filter {
                    unit.promotions.promotions.contains(it.name)
                }     // force same sorting as on picker (.sorted() would be simpler code, but...)
                for (promotion in promotionsForUnit)
                    promotionsTable.add(ImageGetter.getPromotionIcon(promotion.name))
                if (unit.promotions.canBePromoted()) promotionsTable.add(
                    ImageGetter.getImage("OtherIcons/Star").apply { color = Color.GOLDENROD })
                    .size(24f).padLeft(8f)
                if (unit.canUpgrade()) promotionsTable.add(
                    ImageGetter.getUnitIcon(
                        unit.getUnitToUpgradeTo().name,
                        Color.GREEN
                    )
                ).size(28f).padLeft(8f)
                promotionsTable.onClick {
                    if (unit.promotions.canBePromoted() || unit.promotions.promotions.isNotEmpty()) {
                        game.setScreen(PromotionPickerScreen(unit))
                    }
                }
                add(promotionsTable)
                if (unit.health < 100) add(unit.health.toString()) else add()
                row()
            }
        }
        return unitListTable
    }
}
