package com.unciv.ui.mapeditor

import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.TileMap
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.*

class LoadMapScreen(previousMap: TileMap?) : PickerScreen(){
    var chosenMap = ""
    val deleteMapButton = TextButton("Delete map".tr(),skin)

    init {
        setAcceptButtonAction("Load map") {
            UncivGame.Current.setScreen(MapEditorScreen(chosenMap))
            dispose()
        }

        val mapsTable = Table().apply { defaults().pad(10f) }
        for (map in MapSaver.getMaps()) {
            val selectMapButton = TextButton(map, skin)
            val action = {
                rightSideButton.enable()
                chosenMap = map
                deleteMapButton.enable()
                deleteMapButton.color = Color.RED
                descriptionLabel.setText(map)
            }
            selectMapButton.onClick (action)
            registerKeyHandler (map, action)
            mapsTable.add(selectMapButton).row()
        }
        topTable.add(ScrollPane(mapsTable)).height(stage.height * 2 / 3)
                .maxWidth(stage.width/2)

        val rightSideTable = Table().apply { defaults().pad(10f) }

        val downloadMapButton = TextButton("Download map".tr(), skin)
        downloadMapButton.onClick {
            MapDownloadPopup(this).open()
        }
        rightSideTable.add(downloadMapButton).row()

        rightSideTable.addSeparator()


        val loadFromClipboardButton = TextButton("Load copied data".tr(), skin)
        val couldNotLoadMapLabel = "Could not load map!".toLabel(Color.RED).apply { isVisible=false }
        val loadFromClipboardAction = {
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
        loadFromClipboardButton.onClick (loadFromClipboardAction)
        rightSideTable.add(loadFromClipboardButton).row()
        rightSideTable.add(couldNotLoadMapLabel).row()
        registerKeyHandler (Constants.asciiCtrlV, loadFromClipboardAction)

        val deleteAction = {
            YesNoPopup("Are you sure you want to delete this map?", {
                MapSaver.deleteMap(chosenMap)
                UncivGame.Current.setScreen(LoadMapScreen(previousMap))
            }, this).open()
        }
        deleteMapButton.onClick (deleteAction)
        deleteMapButton.disable()
        deleteMapButton.color = Color.RED
        rightSideTable.add(deleteMapButton).row()
        val checkedDelAction = { if (deleteMapButton.touchable == Touchable.enabled) deleteAction() }
        registerKeyHandler (Constants.asciiDelete, checkedDelAction)

        topTable.add(rightSideTable)
        if(previousMap==null) closeButton.isVisible=false
        else setCloseAction { UncivGame.Current.setScreen(MapEditorScreen(previousMap)) }
    }
}


