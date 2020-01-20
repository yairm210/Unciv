package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.TileMap
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.*
import com.unciv.ui.utils.YesNoPopup

class LoadMapScreen(previousMap: TileMap?) : PickerScreen(){
    var chosenMap = ""
    val deleteMapButton = TextButton("Delete map",skin)

    init {
        rightSideButton.setText("Load map".tr())
        rightSideButton.onClick {
            UncivGame.Current.setScreen(MapEditorScreen(chosenMap))
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
                .maxWidth(stage.width/2)

        val rightSideTable = Table().apply { defaults().pad(10f) }

        val downloadMapButton = TextButton("Download map".tr(), skin)
        downloadMapButton.onClick {
            MapDownloadPopup(this)
        }
        rightSideTable.add(downloadMapButton).row()

        rightSideTable.addSeparator()


        val loadFromClipboardButton = TextButton("Load copied data".tr(), skin)
        val couldNotLoadMapLabel = "Could not load map!".toLabel(Color.RED).apply { isVisible=false }
        loadFromClipboardButton.onClick {
            try {
                val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                val decoded = Gzip.unzip(clipboardContentsString)
                val loadedMap = MapSaver().mapFromJson(decoded)
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
                MapSaver().deleteMap(chosenMap)
                UncivGame.Current.setScreen(LoadMapScreen(previousMap))
            }, this)
        }
        deleteMapButton.disable()
        deleteMapButton.color = Color.RED
        rightSideTable.add(deleteMapButton).row()

        topTable.add(rightSideTable)
        if(previousMap==null) closeButton.isVisible=false
        else closeButton.onClick { UncivGame.Current.setScreen(MapEditorScreen(previousMap)) }
    }
}


