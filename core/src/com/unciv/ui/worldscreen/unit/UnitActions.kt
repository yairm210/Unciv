package com.unciv.ui.worldscreen.unit

import com.unciv.UnCivGame
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.map.MapUnit
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.pickerscreens.ImprovementPickerScreen
import com.unciv.ui.pickerscreens.PromotionPickerScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.utils.tr
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.ui.worldscreen.optionstable.YesNoPopupTable
import java.util.*
import kotlin.math.max

class UnitAction(var name: String, var action:()->Unit, var canAct:Boolean)

class UnitActions {

    private fun constructImprovementAndDestroyUnit(unit:MapUnit, improvementName: String): () -> Unit {
        return {
            unit.getTile().improvement = improvementName
            unit.destroy()
        }
    }


    fun getUnitActions(unit:MapUnit,worldScreen: WorldScreen): List<UnitAction> {
        val tile = unit.getTile()
        val unitTable = worldScreen.bottomBar.unitTable
        val actionList = ArrayList<UnitAction>()

        if (unitTable.currentlyExecutingAction != "moveTo"
                && (unit.action==null || !unit.action!!.startsWith("moveTo") )){
            actionList += UnitAction("Move unit", {
                unitTable.currentlyExecutingAction = "moveTo"
            }, unit.currentMovement != 0f )
        }

        else {
            actionList +=
                    UnitAction("Stop movement", {
                unitTable.currentlyExecutingAction = null
                unit.action=null
            },true)
        }

        if(unit.baseUnit().unitType!= UnitType.Civilian
                && !unit.hasUnique("No defensive terrain bonus") && !unit.isFortified()) {
            actionList += UnitAction("Fortify", { unit.action = "Fortify 0" }, unit.currentMovement != 0f)
        }

        if(unit.baseUnit().unitType!= UnitType.Civilian && unit.promotions.canBePromoted()){
                actionList += UnitAction("Promote",
                        {UnCivGame.Current.screen = PromotionPickerScreen(unit)},
                        unit.currentMovement != 0f)
        }

        if(unit.baseUnit().upgradesTo!=null && tile.getOwner()==unit.civInfo) {
            var upgradedUnit = unit.baseUnit().getUpgradeUnit(unit.civInfo)

            // Go up the upgrade tree until you find the first one which isn't obsolete
            while (upgradedUnit.obsoleteTech!=null && unit.civInfo.tech.isResearched(upgradedUnit.obsoleteTech!!))
                upgradedUnit = upgradedUnit.getUpgradeUnit(unit.civInfo)

            if (upgradedUnit.isBuildable(unit.civInfo)) {
                var goldCostOfUpgrade = (upgradedUnit.cost - unit.baseUnit().cost) * 2 + 10
                if(unit.civInfo.policies.isAdopted("Professional Army")) goldCostOfUpgrade = (goldCostOfUpgrade* 0.66f).toInt()
                actionList += UnitAction("Upgrade to [${upgradedUnit.name}] ([$goldCostOfUpgrade] gold)",
                        {
                            unit.civInfo.gold -= goldCostOfUpgrade
                            val unitTile = unit.getTile()
                            unit.destroy()
                            val newunit = unit.civInfo.placeUnitNearTile(unitTile.position, upgradedUnit.name)
                            newunit.health = unit.health
                            newunit.promotions = unit.promotions
                            newunit.currentMovement=0f
                        },
                        unit.civInfo.gold >= goldCostOfUpgrade
                                && unit.currentMovement == unit.getMaxMovement().toFloat()  )
            }
        }

        if(unit.hasUnique("Must set up to ranged attack") && unit.action != "Set Up")
            actionList+=UnitAction("Set up",
                    {unit.action="Set Up"; unit.currentMovement = max(0f, unit.currentMovement-1)},
                    unit.currentMovement != 0f)

        if (unit.name == "Settler") {
            actionList += UnitAction("Found city",
                    {
                        worldScreen.displayTutorials("CityFounded")

                        unit.civInfo.addCity(tile.position)
                        tile.improvement=null
                        unitTable.currentlyExecutingAction = null // In case the settler was in the middle of doing something and we then founded a city with it
                        unit.destroy()
                    },
                    unit.currentMovement != 0f &&
                            !tile.getTilesInDistance(2).any { it.isCityCenter() })
        }
        
        if (unit.name == "Worker") {
            actionList += UnitAction("Construct improvement",
                    { worldScreen.game.screen = ImprovementPickerScreen(tile) },
                    unit.currentMovement != 0f
                            && !tile.isCityCenter()
                            && GameBasics.TileImprovements.values.any { tile.canBuildImprovement(it, unit.civInfo) })

            if("automation" == unit.action){
                actionList += UnitAction("Stop automation",
                        {unit.action = null},true)
            }
            else {
                actionList += UnitAction("Automate",
                        {
                            unit.action = "automation"
                            WorkerAutomation(unit).automateWorkerAction()
                        },unit.currentMovement != 0f
                )
            }
        }

        if (unit.name == "Great Scientist") {
            actionList += UnitAction( "Discover Technology",
                    {
                        unit.civInfo.tech.freeTechs += 1
                        unit.destroy()
                        worldScreen.game.screen = TechPickerScreen(true, unit.civInfo)

                    },unit.currentMovement != 0f)
            actionList += UnitAction("Construct Academy",
                    constructImprovementAndDestroyUnit(unit, "Academy"),
                    unit.currentMovement != 0f && !tile.isCityCenter())
        }

        if (unit.name == "Great Artist") {
            actionList += UnitAction( "Start Golden Age",
                    {
                        unit.civInfo.goldenAges.enterGoldenAge()
                        unit.destroy()
                    },unit.currentMovement != 0f
            )
            actionList += UnitAction("Construct Landmark",
                    constructImprovementAndDestroyUnit(unit, "Landmark"),
                    unit.currentMovement != 0f && !tile.isCityCenter())
        }

        if (unit.name == "Great Engineer") {
            actionList += UnitAction( "Hurry Wonder",
                    {
                        tile.getCity()!!.cityConstructions.addConstruction(300 + 30 * tile.getCity()!!.population.population) //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                        unit.destroy()
                    },
                    unit.currentMovement != 0f &&
                    tile.isCityCenter() &&
                    tile.getCity()!!.cityConstructions.getCurrentConstruction() is Building &&
                    (tile.getCity()!!.cityConstructions.getCurrentConstruction() as Building).isWonder)

            actionList += UnitAction("Construct Manufactory",
                    constructImprovementAndDestroyUnit(unit, "Manufactory"),
                    unit.currentMovement != 0f && !tile.isCityCenter())
        }

        if (unit.name == "Great Merchant") {
            actionList += UnitAction("Conduct Trade Mission",
                    {
                        unit.civInfo.gold += 350 // + 50 * era_number - todo!
                        unit.destroy()
                    },unit.currentMovement != 0f)
            actionList += UnitAction( "Construct Customs House",
                    constructImprovementAndDestroyUnit(unit, "Customs house"),
                    unit.currentMovement != 0f && !tile.isCityCenter())
        }

        actionList += UnitAction("Disband unit",
                {
                    YesNoPopupTable("Do you really want to disband this unit?".tr(),
                            {unit.destroy(); worldScreen.update()} )
                },unit.currentMovement != 0f)

        return actionList
    }

}