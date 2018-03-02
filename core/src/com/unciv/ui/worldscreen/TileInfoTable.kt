package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.linq.Linq
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.TechPickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter

class TileInfoTable(private val worldScreen: WorldScreen, internal val civInfo: CivilizationInfo) : Table() {

    init {
        val tileTableBackground = ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(Color(0x004085bf))
        tileTableBackground.minHeight = 0f
        tileTableBackground.minWidth = 0f
        background = tileTableBackground
    }

    internal fun updateTileTable(selectedTile: TileInfo) {
        clearChildren()
        val stats = selectedTile.getTileStats(civInfo)
        pad(20f)
        columnDefaults(0).padRight(10f)

        val skin = CameraStageBaseScreen.skin

        if (selectedTile.explored) {
            add(Label(selectedTile.toString(), skin)).colspan(2)
            row()


            for (entry in stats.toHashMap().filterNot { it.value == 0f }) {
                add(ImageGetter.getStatIcon(entry.key.toString())).align(Align.right)
                add(Label(entry.value.toInt().toString(), skin)).align(Align.left)
                row()
            }
        }


        if (selectedTile.unit != null) {
            var moveUnitButton = TextButton("Move to", skin)
            if (worldScreen.tileMapHolder.unitTile == selectedTile) moveUnitButton = TextButton("Stop movement", skin)
            moveUnitButton.label.setFontScale(worldScreen.buttonScale)
            if (selectedTile.unit!!.currentMovement == 0f) {
                moveUnitButton.color = Color.GRAY
                moveUnitButton.touchable = Touchable.disabled
            }
            moveUnitButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (worldScreen.tileMapHolder.unitTile != null) {
                        worldScreen.tileMapHolder.unitTile = null
                        worldScreen.update()
                        return
                    }
                    worldScreen.tileMapHolder.unitTile = selectedTile

                    // Set all tiles transparent except those in unit range
                    for (TG in worldScreen.tileGroups.linqValues()) TG.setColor(0f, 0f, 0f, 0.3f)
                    for (tile in civInfo.gameInfo.tileMap.getDistanceToTilesWithinTurn(
                            worldScreen.tileMapHolder.unitTile!!.position,
                            worldScreen.tileMapHolder.unitTile!!.unit!!.currentMovement,
                            civInfo.tech.isResearched("Machinery")
                    ).keys) {
                        worldScreen.tileGroups[tile.position.toString()]!!.color = Color.WHITE
                    }

                    worldScreen.update()
                }
            })
            add(moveUnitButton).colspan(2)
                    .size(moveUnitButton.width * worldScreen.buttonScale, moveUnitButton.height * worldScreen.buttonScale)

            if (selectedTile.unit!!.name == "Settler") {
                addUnitAction("Found City",
                        !civInfo.gameInfo.tileMap.getTilesInDistance(selectedTile.position, 2).any { it.isCityCenter },
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

                            civInfo.addCity(selectedTile.position)
                            if (worldScreen.tileMapHolder.unitTile == selectedTile)
                                worldScreen.tileMapHolder.unitTile = null // The settler was in the middle of moving and we then founded a city with it
                            selectedTile.unit = null // Remove settler!
                            worldScreen.update()
                        })
            }

            if (selectedTile.unit!!.name == "Worker") {
                val improvementButtonText = if (selectedTile.improvementInProgress == null)
                    "Construct\r\nimprovement"
                else
                    selectedTile.improvementInProgress!! + "\r\nin progress"
                addUnitAction(improvementButtonText, !selectedTile.isCityCenter || GameBasics.TileImprovements.linqValues().any { arg0 -> selectedTile.canBuildImprovement(arg0, civInfo) },
                        { worldScreen.game.screen = com.unciv.ui.pickerscreens.ImprovementPickerScreen(selectedTile) })
                addUnitAction(if ("automation" == selectedTile.unit!!.action) "Stop automation" else "Automate", true, {
                    if ("automation" == selectedTile.unit!!.action)
                        selectedTile.unit!!.action = null
                    else {
                        selectedTile.unit!!.action = "automation"
                        selectedTile.unit!!.doAutomatedAction(selectedTile)
                    }
                    worldScreen.update()
                })
            }

            if (selectedTile.unit!!.name == "Great Scientist") {
                addUnitAction("Discover Technology", true,
                        {
                            civInfo.tech.freeTechs += 1
                            selectedTile.unit = null// destroy!
                            worldScreen.game.screen = TechPickerScreen(true, civInfo)

                        })
                addUnitAction("Construct Academy", true, {
                    selectedTile.improvement = "Academy"
                    selectedTile.unit = null// destroy!
                    worldScreen.update()
                }
                )
            }

            if (selectedTile.unit!!.name == "Great Artist") {
                addUnitAction("Start Golden Age", true,
                        {
                            civInfo.goldenAges.enterGoldenAge()
                            selectedTile.unit = null// destroy!
                            worldScreen.update()
                        }
                )
                addUnitAction("Construct Landmark", true,
                        {
                            selectedTile.improvement = "Landmark"
                            selectedTile.unit = null// destroy!
                            worldScreen.update()
                        }
                )
            }

            if (selectedTile.unit!!.name == "Great Engineer") {
                addUnitAction("Hurry Wonder", selectedTile.isCityCenter &&
                        selectedTile.city!!.cityConstructions.getCurrentConstruction() is Building &&

                        (selectedTile.city!!.cityConstructions.getCurrentConstruction() as Building).isWonder,
                        {
                            selectedTile.city!!.cityConstructions.addConstruction(300 + 30 * selectedTile.city!!.population.population) //http://civilization.wikia.com/wiki/Great_engineer_(Civ5)
                            selectedTile.unit = null // destroy!
                            worldScreen.update()
                        })
                addUnitAction("Construct Manufactory", true,
                        {
                            selectedTile.improvement = "Manufactory"
                            selectedTile.unit = null// destroy!
                            worldScreen.update()
                        })
            }
            if (selectedTile.unit!!.name == "Great Merchant") {
                addUnitAction("Conduct Trade Mission", true,
                        {
                            civInfo.gold += 350 // + 50 * era_number - todo!
                            selectedTile.unit = null // destroy!
                            worldScreen.update()
                        })
                addUnitAction("Construct Customs House", true,
                        {
                            selectedTile.improvement = "Customs House"
                            selectedTile.unit = null// destroy!
                            worldScreen.update()

                        })
            }
        }

        pack()

        setPosition(worldScreen.stage.width - 10f - width, 10f)
    }


    private fun addUnitAction(actionText: String, canAct: Boolean, action: ()->Unit) {
        val actionButton = TextButton(actionText, CameraStageBaseScreen.skin)
        actionButton.label.setFontScale(worldScreen.buttonScale)
        actionButton.addClickListener(action)
        if (worldScreen.tileMapHolder.selectedTile!!.unit!!.currentMovement == 0f || !canAct) {
            actionButton.color = Color.GRAY
            actionButton.touchable = Touchable.disabled
        }

        row()
        add(actionButton).colspan(2)
                .size(actionButton.width * worldScreen.buttonScale, actionButton.height * worldScreen.buttonScale)

    }
}
