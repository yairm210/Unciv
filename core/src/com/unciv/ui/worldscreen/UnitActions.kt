package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.linq.Linq
import com.unciv.ui.UnCivGame
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import java.util.ArrayList

public class UnitActions {

    private fun constructImprovementAndDestroyUnit(tileInfo: TileInfo, improvementName: String): () -> Unit {
        return {
            tileInfo.improvement = improvementName
            tileInfo.unit = null// destroy!
        }
    }

    fun getUnitActions(unit: MapUnit, tile: TileInfo, civInfo: CivilizationInfo): List<TextButton> {

        val worldScreen = UnCivGame.Current.worldScreen!!

        val actionList = ArrayList<TextButton>()

        if (unit.name == "Settler") {
            actionList += getUnitActionButton(unit, "Found City",
                    !civInfo.gameInfo.tileMap.getTilesInDistance(tile.position, 2).any { it.isCityCenter },
                    {
                        val tutorial = Linq<String>()
                        tutorial.add("You have founded a city!" +
                                "\r\nCities are the lifeblood of your empire," +
                                "\r\n  providing gold and science empire-wide," +
                                "\r\n  which are displayed on the top bar.")
                        tutorial.add("Science is used to research technologies." +
                                "\r\nYou can enter the technology screen by clicking" +
                                "\r\n  on the button on the top-left, underneath the bar")
                        tutorial.add("You can click the city name to enter" +
                                "\r\n  the city screen to assign population," +
                                "\r\n  choose production, and see information on the city")

                        worldScreen.displayTutorials("CityFounded", tutorial)

                        civInfo.addCity(tile.position)
                        if (worldScreen.tileMapHolder.unitTile == tile)
                            worldScreen.tileMapHolder.unitTile = null // The settler was in the middle of moving and we then founded a city with it
                        tile.unit = null // Remove settler!
                        worldScreen.update()
                    })
        }
        
        if (unit.name == "Worker") {
            val improvementButtonText =
                    if (tile.improvementInProgress == null) "Construct\r\nimprovement"
                    else tile.improvementInProgress!! + "\r\nin progress"
            actionList += getUnitActionButton(unit, improvementButtonText, !tile.isCityCenter || GameBasics.TileImprovements.linqValues().any { arg0 -> tile.canBuildImprovement(arg0, civInfo) },
                    { worldScreen.game.screen = com.unciv.ui.pickerscreens.ImprovementPickerScreen(tile) })
            actionList += getUnitActionButton(unit, if ("automation" == tile.unit!!.action) "Stop automation" else "Automate", true, {
                if ("automation" == tile.unit!!.action)
                    tile.unit!!.action = null
                else {
                    tile.unit!!.action = "automation"
                    tile.unit!!.doAutomatedAction(tile)
                }
                worldScreen.update()
            })
        }

        if (unit.name == "Great Scientist") {
            actionList += getUnitActionButton(unit, "Discover Technology", true,
                    {
                        civInfo.tech.freeTechs += 1
                        tile.unit = null// destroy!
                        worldScreen.game.screen = TechPickerScreen(true, civInfo)

                    })
            actionList += getUnitActionButton(unit, "Construct Academy", true,
                    constructImprovementAndDestroyUnit(tile, "Academy"))
        }

        if (unit.name == "Great Artist") {
            actionList += getUnitActionButton(unit, "Start Golden Age", true,
                    {
                        civInfo.goldenAges.enterGoldenAge()
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
                        civInfo.gold += 350 // + 50 * era_number - todo!
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
        if (unit.currentMovement == 0f || !canAct) {
            actionButton.color = Color.GRAY
            actionButton.touchable = Touchable.disabled
        }
        return actionButton
    }
}