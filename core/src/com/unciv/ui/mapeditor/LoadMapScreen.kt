package com.unciv.ui.mapeditor

import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.TileMap
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.*

class LoadMapScreen(previousMap: TileMap?) : PickerScreen(){
    var chosenMap = ""
    val deleteMapButton = "Delete map".toTextButton()

    init {
        rightSideButton.setText("Load map".tr())
        rightSideButton.onClick {
            UncivGame.Current.setScreen(MapEditorScreen(chosenMap))
            dispose()
        }

        val mapsTable = Table().apply { defaults().pad(10f) }
        for (map in MapSaver.getMaps()) {
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
                .maxWidth(stage.width/2)

        val rightSideTable = Table().apply { defaults().pad(10f) }

        val downloadMapButton = "Download map".toTextButton()
        downloadMapButton.onClick {
            MapDownloadPopup(this).open()
        }
        rightSideTable.add(downloadMapButton).row()

        rightSideTable.addSeparator()


        val loadFromClipboardButton = "Load copied data".toTextButton()
        val couldNotLoadMapLabel = "Could not load map!".toLabel(Color.RED).apply { isVisible=false }
        loadFromClipboardButton.onClick {
            try {
                val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                val decoded = Gzip.unzip(clipboardContentsString)
                val loadedMap = MapSaver.mapFromJson(decoded)
                UncivGame.Current.setScreen(MapEditorScreen(loadedMap))
            }
            catch (ex:Exception){
                couldNotLoadMapLabel.isVisible=true
            }
        }
        rightSideTable.add(loadFromClipboardButton).row()
        rightSideTable.add(couldNotLoadMapLabel).row()

        deleteMapButton.onClick {
            YesNoPopup("Are you sure you want to delete this map?", {
                MapSaver.deleteMap(chosenMap)
                UncivGame.Current.setScreen(LoadMapScreen(previousMap))
            }, this).open()
        }
        deleteMapButton.disable()
        deleteMapButton.color = Color.RED
        rightSideTable.add(deleteMapButton).row()

        topTable.add(rightSideTable)
        if(previousMap==null) closeButton.isVisible=false
        else closeButton.onClick { UncivGame.Current.setScreen(MapEditorScreen(previousMap)) }
    }
}


