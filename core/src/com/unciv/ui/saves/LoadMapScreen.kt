package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.tr
import com.unciv.ui.mapeditor.MapEditorScreen
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.YesNoPopupTable

class LoadMapScreen(previousMap: TileMap) : PickerScreen(){
    var chosenMap = ""
    val deleteMapButton = TextButton("Delete map",skin)

    init {
        rightSideButton.setText("Load map".tr())
        rightSideButton.onClick {
            UnCivGame.Current.screen = MapEditorScreen(chosenMap)
            dispose()
        }

        val mapsTable = Table().apply { defaults().pad(10f) }
        for (map in MapSaver().getMaps()) {
            val loadMapButton = TextButton(map, skin)
            loadMapButton.onClick {
                rightSideButton.enable()
                chosenMap = map
                deleteMapButton.enable()
                deleteMapButton.color = Color.RED
            }
            mapsTable.add(loadMapButton).row()
        }
        topTable.add(ScrollPane(mapsTable)).height(stage.height * 2 / 3)

        val rightSideTable = Table().apply { defaults().pad(10f) }
        val loadFromClipboardButton = TextButton("Load copied data".tr(), skin)
        val couldNotLoadMapLabel = "Could not load map!".toLabel().setFontColor(Color.RED).apply { isVisible=false }
        loadFromClipboardButton.onClick {
            val clipboardContentsString = Gdx.app.clipboard.contents.trim()
            try {
                val decoded = Gzip.unzip(clipboardContentsString)
                val loadedMap = MapSaver().mapFromJson(decoded)
                UnCivGame.Current.screen = MapEditorScreen(loadedMap)
            }
            catch (ex:Exception){
                couldNotLoadMapLabel.isVisible=true
            }
        }
        rightSideTable.add(loadFromClipboardButton).row()
        rightSideTable.add(couldNotLoadMapLabel).row()

        deleteMapButton.onClick {
            YesNoPopupTable("Are you sure you want to delete this map?", {
                MapSaver().deleteMap(chosenMap)
                UnCivGame.Current.screen = LoadMapScreen(previousMap)
            }, this)
        }
        deleteMapButton.disable()
        deleteMapButton.color = Color.RED
        rightSideTable.add(deleteMapButton).row()

        topTable.add(rightSideTable)
        closeButton.onClick { UnCivGame.Current.screen = MapEditorScreen(previousMap) }
    }
}