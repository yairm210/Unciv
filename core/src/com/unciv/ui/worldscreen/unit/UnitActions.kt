package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.automation.WorkerAutomation
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.ImprovementPickerScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.worldscreen.WorldScreen
import java.util.*

class UnitAction(var name: String, var action:()->Unit, var canAct:Boolean)

class UnitActions {

    private fun constructImprovementAndDestroyUnit(tileInfo: TileInfo, improvementName: String): () -> Unit {
        return {
            tileInfo.improvement = improvementName
            tileInfo.unit = null// destroy!
        }
    }

    fun getUnitActionButtons(unit:MapUnit,worldScreen: WorldScreen): List<TextButton> {
        return getUnitActions(unit, worldScreen).map { getUnitActionButton(it) }
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

        if (unit.name == "Settler") {
            actionList += UnitAction("Found city",
                    {
                        worldScreen.displayTutorials("CityFounded")

                        unit.civInfo.addCity(tile.position)
                        unitTable.currentlyExecutingAction = null // In case the settler was in the middle of doing something and we then founded a city with it
                        tile.unit = null // Remove settler!
                    },
                    unit.currentMovement != 0f &&
                            !tile.getTilesInDistance(2).any { it.isCityCenter() })
        }
        
        if (unit.name == "Worker") {
            val improvementButtonText: String
            if (tile.improvementInProgress == null) improvementButtonText = "Construct\r\nimprovement"
            else improvementButtonText = tile.improvementInProgress!! + "\r\nin progress"
            actionList += UnitAction(improvementButtonText,
                    { worldScreen.game.screen = ImprovementPickerScreen(tile) },
                    unit.currentMovement != 0f &&
                            !tile.isCityCenter() || GameBasics.TileImprovements.values.any { tile.canBuildImprovement(it, unit.civInfo) })

            if("automation" == unit.action){
                actionList += UnitAction("Stop automation",
                        {unit.action = null},true)
            }
            else {
                actionList += UnitAction("Automate",
                        {
                            tile.unit!!.action = "automation"
                            WorkerAutomation().automateWorkerAction(tile.unit!!)
                        },unit.currentMovement != 0f
                )
            }
        }

        if (unit.name == "Great Scientist") {
            actionList += UnitAction( "Discover Technology",
                    {
                        unit.civInfo.tech.freeTechs += 1
                        tile.unit = null// destroy!
                        worldScreen.game.screen = TechPickerScreen(true, unit.civInfo)

                    },unit.currentMovement != 0f)
            actionList += UnitAction("Construct Academy",
                    constructImprovementAndDestroyUnit(tile, "Academy"),unit.currentMovement != 0f)
        }

        if (unit.name == "Great Artist") {
            actionList += UnitAction( "Start Golden Age",
                    {
                        unit.civInfo.goldenAges.enterGoldenAge()
                        tile.unit = null// destroy!
                    },unit.currentMovement != 0f
            )
            actionList += UnitAction("Construct Landmark",
                    constructImprovementAndDestroyUnit(tile, "Landmark"),unit.currentMovement != 0f)
        }

        if (unit.name == "Great Engineer") {
            actionList += UnitAction( "Hurry Wonder",
                    {
                        tile.getCity()!!.cityConstructions.addConstruction(300 + 30 * tile.getCity()!!.population.population) //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                        tile.unit = null // destroy!
                    },
                    unit.currentMovement != 0f &&
                    tile.isCityCenter() &&
                    tile.getCity()!!.cityConstructions.getCurrentConstruction() is Building &&
                    (tile.getCity()!!.cityConstructions.getCurrentConstruction() as Building).isWonder)

            actionList += UnitAction("Construct Manufactory",
                    constructImprovementAndDestroyUnit(tile, "Manufactory"),unit.currentMovement != 0f)
        }

        if (unit.name == "Great Merchant") {
            actionList += UnitAction("Conduct Trade Mission",
                    {
                        unit.civInfo.gold += 350 // + 50 * era_number - todo!
                        tile.unit = null // destroy!
                    },unit.currentMovement != 0f)
            actionList += UnitAction( "Construct Customs House",
                    constructImprovementAndDestroyUnit(tile, "Customs house"),
                    unit.currentMovement != 0f)
        }

        return actionList
    }

    private fun getUnitActionButton(unitAction: UnitAction): TextButton {
        val actionButton = TextButton(unitAction.name, CameraStageBaseScreen.skin)
        actionButton.addClickListener({ unitAction.action(); UnCivGame.Current.worldScreen!!.update() })
        if (!unitAction.canAct) actionButton.disable()
        return actionButton
    }
}

