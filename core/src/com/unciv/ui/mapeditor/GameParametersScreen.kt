package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.logic.map.Scenario
import com.unciv.ui.newgamescreen.GameOptionsTable
import com.unciv.ui.newgamescreen.GameSetupInfo
import com.unciv.ui.newgamescreen.PlayerPickerTable
import com.unciv.ui.newgamescreen.PreviousScreenInterface
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import kotlin.concurrent.thread

class GameParametersScreen(var mapEditorScreen: MapEditorScreen): PickerScreen() {
    var playerPickerTable = PlayerPickerTable(mapEditorScreen, mapEditorScreen.gameSetupInfo.gameParameters)
    var gameOptionsTable = GameOptionsTable(mapEditorScreen) { desiredCiv: String -> playerPickerTable.update(desiredCiv) }

    init {
        setDefaultCloseAction(mapEditorScreen)
        scrollPane.setScrollingDisabled(true, true)

        playerPickerTable.apply { locked = true }.update()
        val scenarioNameEditor = TextField(mapEditorScreen.scenarioName, skin)

        topTable.add(playerPickerTable)
        topTable.addSeparatorVertical()
        topTable.add(gameOptionsTable).row()
        topTable.add(scenarioNameEditor)
        rightSideButton.setText("Save scenario")
        rightSideButton.onClick {
            thread(name = "SaveScenario") {
                try {
                    mapEditorScreen.tileMap.mapParameters.type = MapType.scenario
                    mapEditorScreen.scenario = Scenario(mapEditorScreen.tileMap, mapEditorScreen.gameSetupInfo.gameParameters)
                    mapEditorScreen.scenarioName = scenarioNameEditor.text
                    MapSaver.saveScenario(scenarioNameEditor.text, mapEditorScreen.scenario!!)

                    game.setScreen(mapEditorScreen)
                    Gdx.app.postRunnable {
                        ResponsePopup("Scenario saved", mapEditorScreen) // todo - add this text to translations
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    Gdx.app.postRunnable {
                        val cantLoadGamePopup = Popup(mapEditorScreen)
                        cantLoadGamePopup.addGoodSizedLabel("It looks like your scenario can't be saved!").row()
                        cantLoadGamePopup.addCloseButton()
                        cantLoadGamePopup.open(force = true)
                    }
                }
            }
        }

        rightSideButton.isEnabled = scenarioNameEditor.text.isNotEmpty()
        scenarioNameEditor.addListener {
            mapEditorScreen.scenarioName = scenarioNameEditor.text
            rightSideButton.isEnabled = scenarioNameEditor.text.isNotEmpty()
            true
        }
    }
}