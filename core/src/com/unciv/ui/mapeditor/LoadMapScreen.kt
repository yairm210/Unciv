package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.TileMap
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class LoadMapScreen(previousMap: TileMap?) : PickerScreen(){
    var chosenMap = ""
    val deleteButton = "Delete map".toTextButton()
    var scenarioMap = false
    val mapsTable = Table().apply { defaults().pad(10f) }

    init {
        if(UncivGame.Current.settings.extendedMapEditor) {
            val toggleButton = "Toggle Scenario Map".toTextButton()
            toggleButton.onClick {
                scenarioMap = !scenarioMap
                update()
            }
            toggleButton.centerX(stage)
            toggleButton.setY(stage.height - 10f, Align.top)
            stage.addActor(toggleButton)
        }

        rightSideButton.setText("Load map".tr())
        rightSideButton.onClick {
            val mapEditorScreen = if (scenarioMap) MapEditorScreen(MapSaver.loadScenario(chosenMap), chosenMap)
            else MapEditorScreen(chosenMap)
            UncivGame.Current.setScreen(mapEditorScreen)
            dispose()
        }

        topTable.add(ScrollPane(mapsTable)).height(stage.height * 2 / 3)
                .maxWidth(stage.width / 2)

        val rightSideTable = Table().apply { defaults().pad(10f) }

        val downloadMapButton = "Download map".toTextButton()
        downloadMapButton.onClick {
            MapDownloadPopup(this).open()
        }
        rightSideTable.add(downloadMapButton).row()

        rightSideTable.addSeparator()


        val loadFromClipboardButton = "Load copied data".toTextButton()
        val couldNotLoadMapLabel = "Could not load map!".toLabel(Color.RED).apply { isVisible = false }
        loadFromClipboardButton.onClick {
            try {
                val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                val decoded = Gzip.unzip(clipboardContentsString)
                val loadedMap = MapSaver.mapFromJson(decoded)
                UncivGame.Current.setScreen(MapEditorScreen(loadedMap))
            } catch (ex: Exception) {
                couldNotLoadMapLabel.isVisible = true
            }
        }
        rightSideTable.add(loadFromClipboardButton).row()
        rightSideTable.add(couldNotLoadMapLabel).row()

        deleteButton.onClick {
            YesNoPopup("Are you sure you want to delete this map?", {
                if (scenarioMap) MapSaver.deleteScenario(chosenMap)
                else MapSaver.deleteMap(chosenMap)
                UncivGame.Current.setScreen(LoadMapScreen(previousMap))
            }, this).open()
        }
        rightSideTable.add(deleteButton).row()

        topTable.add(rightSideTable)
        if (previousMap != null)
            closeButton.onClick { UncivGame.Current.setScreen(MapEditorScreen(previousMap)) }

        update()
    }

    fun update() {
        chosenMap = ""
        deleteButton.disable()
        deleteButton.color = Color.RED

        if (scenarioMap) {
            deleteButton.setText("Delete Scenario Map")
            rightSideButton.setText("Load Scenario Map")

            mapsTable.clear()
            for (scenario in MapSaver.getScenarios()) {
                val loadScenarioButton = TextButton(scenario, skin)
                loadScenarioButton.onClick {
                    rightSideButton.enable()
                    chosenMap = scenario
                    deleteButton.enable()
                    deleteButton.color = Color.RED
                }
                mapsTable.add(loadScenarioButton).row()
            }
        } else {
            deleteButton.setText("Delete map")
            rightSideButton.setText("Load map")

            mapsTable.clear()
            for (map in MapSaver.getMaps()) {
                val loadMapButton = TextButton(map, skin)
                loadMapButton.onClick {
                    rightSideButton.enable()
                    chosenMap = map
                    deleteButton.enable()
                    deleteButton.color = Color.RED
                }
                mapsTable.add(loadMapButton).row()
            }
        }
    }
}


