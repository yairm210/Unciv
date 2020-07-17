package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.Scenario
import com.unciv.logic.map.TileMap
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.*

/**
 * [PickerScreen] used for simple load/delete scenario. Called from [MapEditorScreen]
 * and returns to that [Screen] type.
 * @param previousMap [TileMap] to return when no scenario chosen
 */
class LoadScenarioScreen(previousMap: TileMap?): PickerScreen(){
    var chosenScenario = ""
    val deleteScenarioButton = "Delete scenario".toTextButton()

    init {
        rightSideButton.setText("Load scenario".tr())
        rightSideButton.onClick {
            val mapEditorScreen = MapEditorScreen(MapSaver.loadScenario(chosenScenario), chosenScenario)
            UncivGame.Current.setScreen(mapEditorScreen)
            dispose()
        }

        val scenariosTable = Table().apply { defaults().pad(10f) }
        for (scenario in MapSaver.getScenarios()) {
            val loadScenarioButton = TextButton(scenario, skin)
            loadScenarioButton.onClick {
                rightSideButton.enable()
                chosenScenario = scenario
                deleteScenarioButton.enable()
                deleteScenarioButton.color = Color.RED
            }
            scenariosTable.add(loadScenarioButton).row()
        }
        topTable.add(AutoScrollPane(scenariosTable)).height(stage.height * 2 / 3)
                .maxWidth(stage.width/2)

        val rightSideTable = Table().apply { defaults().pad(10f) }

//        val downloadMapButton = "Download map".toTextButton()
//        downloadMapButton.onClick {
//            MapDownloadPopup(this).open()
//        }
//        rightSideTable.add(downloadMapButton).row()
//
//        rightSideTable.addSeparator()


//        val loadFromClipboardButton = "Load copied data".toTextButton()
//        val couldNotLoadMapLabel = "Could not load map!".toLabel(Color.RED).apply { isVisible=false }
//        loadFromClipboardButton.onClick {
//            try {
//                val clipboardContentsString = Gdx.app.clipboard.contents.trim()
//                val decoded = Gzip.unzip(clipboardContentsString)
//                val loadedMap = MapSaver.mapFromJson(decoded)
//                UncivGame.Current.setScreen(MapEditorScreen(loadedMap))
//            }
//            catch (ex:Exception){
//                couldNotLoadMapLabel.isVisible=true
//            }
//        }
//        rightSideTable.add(loadFromClipboardButton).row()
//        rightSideTable.add(couldNotLoadMapLabel).row()

        deleteScenarioButton.onClick {
            YesNoPopup("Are you sure you want to delete this scenario?", {
                MapSaver.deleteScenario(chosenScenario)
                UncivGame.Current.setScreen(LoadScenarioScreen(previousMap))
            }, this).open()
        }
        deleteScenarioButton.disable()
        deleteScenarioButton.color = Color.RED
        rightSideTable.add(deleteScenarioButton).row()

        topTable.add(rightSideTable)
        if(previousMap!=null) closeButton.onClick { UncivGame.Current.setScreen(MapEditorScreen(previousMap)) }
    }

}