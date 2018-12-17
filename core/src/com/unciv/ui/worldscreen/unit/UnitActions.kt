package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.unciv.UnCivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.map.MapUnit
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
import kotlin.math.max

class UnitAction(var name: String, var canAct:Boolean, var action:()->Unit){
    var sound="click"
    fun sound(soundName:String): UnitAction {sound=soundName; return this}
}

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

        if(unit.action!=null && unit.action!!.startsWith("moveTo")) {
            actionList +=
                    UnitAction("Stop movement", true) {
                        unitTable.currentlyExecutingAction = null
                        unit.action = null
                    }
        }

        if(!unit.type.isCivilian() && !unit.isEmbarked() && !unit.type.isWaterUnit()
                && !unit.hasUnique("No defensive terrain bonus") && !unit.isFortified()) {
            actionList += UnitAction("Fortify", unit.currentMovement != 0f)
                { unit.action = "Fortify 0" }.sound("fortify")
        }

        if(!unit.isFortified() && actionList.none{it.name=="Fortify"} && unit.action!="Sleep") {
            actionList += UnitAction("Sleep",unit.currentMovement != 0f) { unit.action = "Sleep" }
        }

        if(unit.type == UnitType.Scout){
            if(unit.action != "explore")
                actionList += UnitAction("Explore",unit.currentMovement != 0f)
                    { UnitAutomation().automatedExplore(unit); unit.action = "explore" }
            else
                actionList += UnitAction("Stop exploration", true) { unit.action = null }
        }

        if(!unit.type.isCivilian() && unit.promotions.canBePromoted()) {
            actionList += UnitAction("Promote", unit.currentMovement != 0f)
            { UnCivGame.Current.screen = PromotionPickerScreen(unit) }.sound("promote")
        }

        if(unit.baseUnit().upgradesTo!=null && tile.getOwner()==unit.civInfo) {
            var upgradedUnit = unit.baseUnit().getUpgradeUnit(unit.civInfo)

            // Go up the upgrade tree until you find the first one which isn't obsolete
            while (upgradedUnit.obsoleteTech!=null && unit.civInfo.tech.isResearched(upgradedUnit.obsoleteTech!!))
                upgradedUnit = upgradedUnit.getUpgradeUnit(unit.civInfo)

            if (upgradedUnit.isBuildable(unit.civInfo)) {
                var goldCostOfUpgrade = (upgradedUnit.cost - unit.baseUnit().cost) * 2 + 10
                if (unit.civInfo.policies.isAdopted("Professional Army")) goldCostOfUpgrade = (goldCostOfUpgrade * 0.66f).toInt()
                actionList += UnitAction("Upgrade to [${upgradedUnit.name}] ([$goldCostOfUpgrade] gold)",
                        unit.civInfo.gold >= goldCostOfUpgrade
                                && !unit.isEmbarked()
                                && unit.currentMovement == unit.getMaxMovement().toFloat()
                ) {
                    unit.civInfo.gold -= goldCostOfUpgrade
                    val unitTile = unit.getTile()
                    unit.destroy()
                    val newunit = unit.civInfo.placeUnitNearTile(unitTile.position, upgradedUnit.name)
                    newunit.health = unit.health
                    newunit.promotions = unit.promotions
                    newunit.updateUniques()
                    newunit.currentMovement = 0f
                }.sound("promote")
            }
        }

        if(unit.hasUnique("Must set up to ranged attack") && unit.action != "Set Up" && !unit.isEmbarked())
            actionList+=UnitAction("Set up",unit.currentMovement != 0f)
                {unit.action="Set Up"; unit.currentMovement = max(0f, unit.currentMovement-1)}

        if (unit.hasUnique("Founds a new city") && !unit.isEmbarked()) {
            actionList += UnitAction("Found city",
                    unit.currentMovement != 0f &&
                            !tile.getTilesInDistance(3).any { it.isCityCenter() })
            {
                worldScreen.displayTutorials("CityFounded")

                unit.civInfo.addCity(tile.position)
                tile.improvement = null
                unitTable.currentlyExecutingAction = null // In case the settler was in the middle of doing something and we then founded a city with it
                unit.destroy()
            }.sound("chimes")
        }
        
        if (unit.hasUnique("Can build improvements on tiles") && !unit.isEmbarked()) {
            actionList += UnitAction("Construct improvement",
                    unit.currentMovement != 0f
                            && !tile.isCityCenter()
                            && GameBasics.TileImprovements.values.any { tile.canBuildImprovement(it, unit.civInfo) }
            ) { worldScreen.game.screen = ImprovementPickerScreen(tile) }

            if("automation" == unit.action){
                actionList += UnitAction("Stop automation",true) {unit.action = null}
            }
            else {
                actionList += UnitAction("Automate", unit.currentMovement != 0f)
                {
                    unit.action = "automation"
                    WorkerAutomation(unit).automateWorkerAction()
                }
            }
        }

        for(improvement in listOf("Fishing Boats","Oil well")) {
            if (unit.hasUnique("May create improvements on water resources") && tile.resource != null
                    && tile.getTileResource().improvement == improvement
                    && unit.civInfo.tech.isResearched(GameBasics.TileImprovements[improvement]!!.techRequired!!)
            )
                actionList += UnitAction("Create [$improvement]", unit.currentMovement != 0f) {
                    tile.improvement = improvement
                    unit.destroy()
                }
        }

        for(unique in unit.getUniques().filter { it.startsWith("Can build improvement: ") }){
            val improvementName = unique.replace("Can build improvement: ","")
            actionList += UnitAction("Create [$improvementName]",
                    unit.currentMovement != 0f && !tile.isCityCenter(),
                    constructImprovementAndDestroyUnit(unit, improvementName)).sound("chimes")
        }


        if (unit.name == "Great Scientist" && !unit.isEmbarked()) {
            actionList += UnitAction( "Discover Technology",unit.currentMovement != 0f
            ) {
                unit.civInfo.tech.freeTechs += 1
                unit.destroy()
                worldScreen.game.screen = TechPickerScreen(true, unit.civInfo)
            }.sound("chimes")
        }

        if (unit.name == "Great Artist" && !unit.isEmbarked()) {
            actionList += UnitAction( "Start Golden Age",unit.currentMovement != 0f
            ) {
                unit.civInfo.goldenAges.enterGoldenAge()
                unit.destroy()
            }.sound("chimes")
        }

        if (unit.name == "Great Engineer" && !unit.isEmbarked()) {
            actionList += UnitAction( "Hurry Wonder",
                    unit.currentMovement != 0f &&
                            tile.isCityCenter() &&
                            tile.getCity()!!.cityConstructions.getCurrentConstruction() is Building &&
                            (tile.getCity()!!.cityConstructions.getCurrentConstruction() as Building).isWonder
            ) {
                tile.getCity()!!.cityConstructions.addProduction(300 + 30 * tile.getCity()!!.population.population) //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                unit.destroy()
            }.sound("chimes")
        }

        if (unit.name == "Great Merchant" && !unit.isEmbarked()) {
            actionList += UnitAction("Conduct Trade Mission", unit.currentMovement != 0f
            ) {
                // http://civilization.wikia.com/wiki/Great_Merchant_(Civ5)
                val goldGained = 350 + 50 * unit.civInfo.getEra().ordinal
                unit.civInfo.gold += goldGained
                unit.civInfo.addNotification("Your trade mission has earned you [$goldGained] gold!",null, Color.GOLD)
                unit.destroy()
            }.sound("chimes")
        }

        actionList += UnitAction("Disband unit",unit.currentMovement != 0f
        ) {
            YesNoPopupTable("Do you really want to disband this unit?".tr(),
                    {unit.destroy(); worldScreen.shouldUpdate=true} )
        }

        return actionList
    }

}