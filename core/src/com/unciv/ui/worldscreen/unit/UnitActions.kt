package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.UnCivGame
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.ImprovementPickerScreen
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.worldscreen.WorldScreen
import java.util.*

class UnitActions {

    private fun constructImprovementAndDestroyUnit(tileInfo: TileInfo, improvementName: String): () -> Unit {
        return {
            tileInfo.improvement = improvementName
            tileInfo.unit = null// destroy!
        }
    }

    fun getUnitActions(tile: TileInfo, worldScreen: WorldScreen): List<TextButton> {

        val unit = tile.unit!!
        val tileMapHolder = worldScreen.tileMapHolder
        val unitTable = worldScreen.unitTable

        val actionList = ArrayList<TextButton>()

        if (unitTable.currentlyExecutingAction != "moveTo"
                && (unit.action==null || !unit.action!!.startsWith("moveTo") )){
            actionList += getUnitActionButton(unit, "Move unit", true, {
                unitTable.currentlyExecutingAction = "moveTo"
                // Set all tiles transparent except those in unit range
                for (TG in tileMapHolder.tileGroups.values) TG.setColor(0f, 0f, 0f, 0.3f)

                val distanceToTiles = tileMapHolder.tileMap.getDistanceToTilesWithinTurn(
                        unitTable.selectedUnitTile!!.position,
                        unitTable.getSelectedUnit().currentMovement,
                        unit.civInfo.tech.isResearched("Machinery"))

                for (tileInRange in distanceToTiles.keys) {
                    tileMapHolder.tileGroups[tileInRange.position.toString()]!!.color = Color.WHITE
                }
            })
        }

        else {
            val stopUnitAction =getUnitActionButton(unit, "Stop movement", true, {
                unitTable.currentlyExecutingAction = null
                unit.action=null
                tileMapHolder.updateTiles()
            })
            stopUnitAction.enable() // Stopping automation is always enabled;
            actionList += stopUnitAction
        }

        if (unit.name == "Settler") {
            actionList += getUnitActionButton(unit, "Found City",
                    !tileMapHolder.tileMap.getTilesInDistance(tile.position, 2).any { it.isCityCenter },
                    {
                        worldScreen.displayTutorials("CityFounded")

                        unit.civInfo.addCity(tile.position)
                        unitTable.currentlyExecutingAction = null // In case the settler was in the middle of doing something and we then founded a city with it
                        tile.unit = null // Remove settler!
                        worldScreen.update()
                    })
        }
        
        if (unit.name == "Worker") {
            val improvementButtonText =
                    if (tile.improvementInProgress == null) "Construct\r\nimprovement"
                    else tile.improvementInProgress!! + "\r\nin progress"
            actionList += getUnitActionButton(unit, improvementButtonText,
                    !tile.isCityCenter || GameBasics.TileImprovements.values.any { tile.canBuildImprovement(it, unit.civInfo) },
                    { worldScreen.game.screen = ImprovementPickerScreen(tile) })

            if("automation" == tile.unit!!.action){
                val automationAction = getUnitActionButton(unit,"Stop automation",true,
                        {tile.unit!!.action = null})
                automationAction.enable() // Stopping automation is always enabled;
                actionList += automationAction
            }
            else {
                actionList += getUnitActionButton(unit, "Automate", true,
                        {
                            tile.unit!!.action = "automation"
                            tile.unit!!.doAutomatedAction(tile)
                        }
                )
            }
        }

        if (unit.name == "Great Scientist") {
            actionList += getUnitActionButton(unit, "Discover Technology", true,
                    {
                        unit.civInfo.tech.freeTechs += 1
                        tile.unit = null// destroy!
                        worldScreen.game.screen = TechPickerScreen(true, unit.civInfo)

                    })
            actionList += getUnitActionButton(unit, "Construct Academy", true,
                    constructImprovementAndDestroyUnit(tile, "Academy"))
        }

        if (unit.name == "Great Artist") {
            actionList += getUnitActionButton(unit, "Start Golden Age", true,
                    {
                        unit.civInfo.goldenAges.enterGoldenAge()
                        tile.unit = null// destroy!
                        worldScreen.update()
                    }
            )
            actionList += getUnitActionButton(unit, "Construct Landmark", true,
                    constructImprovementAndDestroyUnit(tile, "Landmark"))
        }

        if (unit.name == "Great Engineer") {
            actionList += getUnitActionButton(unit, "Hurry Wonder", tile.isCityCenter &&
                    tile.city!!.cityConstructions.getCurrentConstruction() is Building &&
                    (tile.city!!.cityConstructions.getCurrentConstruction() as Building).isWonder,
                    {
                        tile.city!!.cityConstructions.addConstruction(300 + 30 * tile.city!!.population.population) //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                        tile.unit = null // destroy!
                    })
            actionList += getUnitActionButton(unit, "Construct Manufactory", true,
                    constructImprovementAndDestroyUnit(tile, "Manufactory"))
        }

        if (unit.name == "Great Merchant") {
            actionList += getUnitActionButton(unit, "Conduct Trade Mission", true,
                    {
                        unit.civInfo.gold += 350 // + 50 * era_number - todo!
                        tile.unit = null // destroy!
                    })
            actionList += getUnitActionButton(unit, "Construct Customs House", true,
                    constructImprovementAndDestroyUnit(tile, "Customs House"))
        }

        return actionList
    }


    private fun getUnitActionButton(unit: MapUnit, actionText: String, canAct: Boolean, action: () -> Unit): TextButton {
        val actionButton = TextButton(actionText, CameraStageBaseScreen.skin)
        actionButton.addClickListener({ action(); UnCivGame.Current.worldScreen!!.update() })
        if (unit.currentMovement == 0f || !canAct) actionButton.disable()
        return actionButton
    }
}

