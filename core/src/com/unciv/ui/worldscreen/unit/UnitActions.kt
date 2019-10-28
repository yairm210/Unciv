package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.pickerscreens.ImprovementPickerScreen
import com.unciv.ui.pickerscreens.PromotionPickerScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.ui.worldscreen.optionstable.YesNoPopupTable
import java.util.*

class UnitAction(var name: String, var canAct: Boolean, var currentAction: Boolean = false, var title: String = name, var action: () -> Unit = {}){
    var sound="click"
    fun sound(soundName:String): UnitAction {sound=soundName; return this}
}

class UnitActions {

    fun getUnitActions(unit:MapUnit,worldScreen: WorldScreen): List<UnitAction> {
        val tile = unit.getTile()
        val unitTable = worldScreen.bottomUnitTable
        val actionList = ArrayList<UnitAction>()

        if(unit.action!=null && unit.action!!.startsWith("moveTo")) {
            actionList += UnitAction("Stop movement", true) {unit.action = null}
        }

        val workingOnImprovement = unit.hasUnique("Can build improvements on tiles") && unit.currentTile.hasImprovementInProgress()
        if(!unit.isFortified() && (!unit.canFortify() || unit.health<100) && unit.currentMovement >0
                && !workingOnImprovement) {
            val isSleeping = unit.action == Constants.unitActionSleep
            actionList += UnitAction("Sleep", !isSleeping, isSleeping) {
                unit.action = Constants.unitActionSleep
                unitTable.selectedUnit = null
            }
        }

        if(unit.canFortify()) {
            actionList += UnitAction("Fortify", unit.currentMovement >0) {
                unit.fortify()
                unitTable.selectedUnit = null
            }.sound("fortify")
        } else if (unit.isFortified()) {
            actionList += UnitAction(
                    "Fortify",
                    false,
                    true,
                    "Fortification".tr() + " " + unit.getFortificationTurns() * 20 + "%"
            )
        }

        if(unit.type == UnitType.Scout){
            if(unit.action != Constants.unitActionExplore)
                actionList += UnitAction("Explore",true) {
                    UnitAutomation().automatedExplore(unit)
                    unit.action = Constants.unitActionExplore
                }
            else
                actionList += UnitAction("Stop exploration", true) { unit.action = null }
        }

        if(!unit.type.isCivilian() && unit.promotions.canBePromoted()) {
            // promotion does not consume movement points, so we can do it always
            actionList += UnitAction("Promote", true) {
                UnCivGame.Current.setScreen(PromotionPickerScreen(unit))
            }.sound("promote")
        }

        if(unit.baseUnit().upgradesTo!=null && tile.getOwner()==unit.civInfo) {

            if (unit.canUpgrade()) {
                val goldCostOfUpgrade = unit.getCostOfUpgrade()
                val upgradedUnit = unit.getUnitToUpgradeTo()

                actionList += UnitAction("Upgrade to [${upgradedUnit.name}] ([$goldCostOfUpgrade] gold)",
                        unit.civInfo.gold >= goldCostOfUpgrade
                                && !unit.isEmbarked()
                                && unit.currentMovement == unit.getMaxMovement().toFloat()
                ) {
                    unit.civInfo.gold -= goldCostOfUpgrade
                    val unitTile = unit.getTile()
                    unit.destroy()
                    val newunit = unit.civInfo.placeUnitNearTile(unitTile.position, upgradedUnit.name)!!
                    newunit.health = unit.health
                    newunit.promotions = unit.promotions

                    for(promotion in unit.baseUnit.promotions)
                        if(promotion !in newunit.promotions.promotions)
                            newunit.promotions.addPromotion(promotion, true)

                    newunit.updateUniques()
                    newunit.updateVisibleTiles()
                    newunit.currentMovement = 0f
                    worldScreen.shouldUpdate = true
                }.sound("upgrade")
            }
        }

        if(!unit.type.isCivilian() && tile.improvement !=null){
            actionList += UnitAction("Pillage", unit.currentMovement>0 && canPillage(unit,tile))
            {
                // http://well-of-souls.com/civ/civ5_improvements.html says that naval improvements are destroyed upon pilllage
                //    and I can't find any other sources so I'll go with that
                if(tile.isLand) {
                    tile.improvementInProgress = tile.improvement
                    tile.turnsToImprovement = 2
                }
                tile.improvement = null
                if(!unit.hasUnique("No movement cost to pillage")) unit.useMovementPoints(1f)
                unit.healBy(25)
            }
        }

        if(unit.hasUnique("Must set up to ranged attack") && !unit.isEmbarked()) {
            val setUp = unit.action == "Set Up"
            actionList+=UnitAction("Set up", unit.currentMovement >0 && !setUp, currentAction = setUp ) {
                unit.action=Constants.unitActionSetUp
                unit.useMovementPoints(1f)
            }.sound("setup")
        }

        if (unit.hasUnique("Founds a new city") && !unit.isEmbarked()) {
            actionList += UnitAction("Found city",
                    unit.currentMovement >0 &&
                            !tile.getTilesInDistance(3).any { it.isCityCenter() })
            {
                unit.civInfo.addCity(tile.position)
                tile.improvement = null
                unit.destroy()
            }.sound("chimes")
        }

        if (unit.hasUnique("Can build improvements on tiles") && !unit.isEmbarked()) {
            actionList += UnitAction("Construct improvement",
                    unit.currentMovement > 0
                            && !tile.isCityCenter()
                            && GameBasics.TileImprovements.values.any { tile.canBuildImprovement(it, unit.civInfo) },
                    currentAction = unit.currentTile.hasImprovementInProgress()
            ) { worldScreen.game.setScreen(ImprovementPickerScreen(tile) { unitTable.selectedUnit = null }) }

            if (Constants.unitActionAutomation == unit.action) {
                actionList += UnitAction("Stop automation", true) { unit.action = null }
            } else {
                actionList += UnitAction("Automate", unit.currentMovement > 0)
                {
                    unit.action = Constants.unitActionAutomation
                    WorkerAutomation(unit).automateWorkerAction()
                }
            }
        }

        if(unit.hasUnique("Can construct roads")
                && tile.roadStatus==RoadStatus.None
                && tile.improvementInProgress != "Road"
                && tile.isLand
                && unit.civInfo.tech.isResearched(GameBasics.TileImprovements["Road"]!!.techRequired!!))
            actionList+=UnitAction("Construct road", unit.currentMovement >0){
                tile.improvementInProgress="Road"
                tile.turnsToImprovement=4
            }

        for(improvement in listOf("Fishing Boats","Oil well")) {
            if (unit.hasUnique("May create improvements on water resources") && tile.resource != null
                    && tile.isWater // because fishing boats can enter cities, and if there's oil in the city... ;)
                    && tile.improvement==null
                    && tile.getTileResource().improvement == improvement
                    && unit.civInfo.tech.isResearched(GameBasics.TileImprovements[improvement]!!.techRequired!!)
            )
                actionList += UnitAction("Create [$improvement]", unit.currentMovement >0) {
                    tile.improvement = improvement
                    unit.destroy()
                }
        }

        for(unique in unit.getUniques().filter { it.startsWith("Can build improvement: ") }){
            val improvementName = unique.replace("Can build improvement: ","")
            actionList += UnitAction("Create [$improvementName]",
                    unit.currentMovement >0f && !tile.isWater && !tile.isCityCenter() && !tile.getLastTerrain().unbuildable
            ) {
                unit.getTile().terrainFeature=null // remove forest/jungle/marsh
                unit.getTile().improvement = improvementName
                unit.getTile().improvementInProgress = null
                unit.getTile().turnsToImprovement = 0
                unit.destroy()
            }.sound("chimes")
        }


        if (unit.name == "Great Scientist" && !unit.isEmbarked()) {
            actionList += UnitAction("Discover Technology", unit.currentMovement >0
            ) {
                unit.civInfo.tech.freeTechs += 1
                unit.destroy()
                worldScreen.game.setScreen(TechPickerScreen(true, unit.civInfo))
            }.sound("chimes")
        }

        if (unit.hasUnique("Can start an 8-turn golden age") && !unit.isEmbarked()) {
            actionList += UnitAction("Start Golden Age", unit.currentMovement >0
            ) {
                unit.civInfo.goldenAges.enterGoldenAge()
                unit.destroy()
            }.sound("chimes")
        }

        if (unit.name == "Great Engineer" && !unit.isEmbarked()) {
            val canHurryWonder = if(unit.currentMovement==0f || !tile.isCityCenter()) false
            else {
                val currentConstruction = tile.getCity()!!.cityConstructions.getCurrentConstruction()
                if(currentConstruction !is Building) false
                else currentConstruction.isWonder || currentConstruction.isNationalWonder
            }
            actionList += UnitAction("Hurry Wonder",
                    canHurryWonder
            ) {
                tile.getCity()!!.cityConstructions.apply {
                    addProductionPoints(300 + 30 * tile.getCity()!!.population.population) //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                    constructIfEnough()
                }
                unit.destroy()
            }.sound("chimes")
        }

        if (unit.name == "Great Merchant" && !unit.isEmbarked()) {
            actionList += UnitAction("Conduct Trade Mission", unit.currentMovement >0
            ) {
                // http://civilization.wikia.com/wiki/Great_Merchant_(Civ5)
                val goldGained = 350 + 50 * unit.civInfo.getEra().ordinal
                unit.civInfo.gold += goldGained
                unit.civInfo.addNotification("Your trade mission has earned you [$goldGained] gold!",null, Color.GOLD)
                unit.destroy()
            }.sound("chimes")
        }

        actionList += UnitAction("Disband unit", unit.currentMovement >0
        ) {
            val disbandText = if(unit.currentTile.getOwner()==unit.civInfo)
                "Disband this unit for [${unit.baseUnit.getDisbandGold()}] gold?".tr()
            else "Do you really want to disband this unit?".tr()
            YesNoPopupTable(disbandText,
                    {unit.disband(); worldScreen.shouldUpdate=true} )
        }

        return actionList
    }

    fun canPillage(unit: MapUnit, tile: TileInfo): Boolean {
        if(tile.improvement==null || tile.improvement==Constants.barbarianEncampment
                || tile.improvement=="City ruins") return false
        val tileOwner = tile.getOwner()
        // Can't pillage friendly tiles, just like you can't attack them - it's an 'act of war' thing
        return tileOwner==null || tileOwner==unit.civInfo || unit.civInfo.isAtWarWith(tileOwner)
    }

}