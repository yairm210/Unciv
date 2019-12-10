package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.optionstable.DropBox
import com.unciv.ui.worldscreen.optionstable.PopupTable

class MapDownloadTable(loadMapScreen: LoadMapScreen): PopupTable(loadMapScreen) {
    init {
        val folderList: DropBox.FolderList
        try {
            folderList = DropBox().getFolderList("/Maps")
            val scrollableMapTable = Table().apply { defaults().pad(10f) }
            for (downloadableMap in folderList.entries) {
                val downloadMapButton = TextButton(downloadableMap.name, CameraStageBaseScreen.skin)
                downloadMapButton.onClick {
                    try{
                        val mapJsonGzipped = DropBox().downloadFileAsString(downloadableMap.path_display)
                        val decodedMapJson = Gzip.unzip(mapJsonGzipped)
                        val mapObject = MapSaver().mapFromJson(decodedMapJson)
                        MapSaver().saveMap(downloadableMap.name, mapObject)
                        UncivGame.Current.setScreen(MapEditorScreen(mapObject))
                    }
                    catch(ex:Exception){
                        val couldNotDownloadMapPopup = PopupTable(screen)
                        couldNotDownloadMapPopup.addGoodSizedLabel("Could not download map!").row()
                        couldNotDownloadMapPopup.addCloseButton()
                    }
                }
                scrollableMapTable.add(downloadMapButton).row()
            }
            add(ScrollPane(scrollableMapTable)).height(screen.stage.height * 2 / 3).row()
        } catch (ex: Exception) {
            addGoodSizedLabel("Could not get list of maps!").row()
        }
        addCloseButton()
        open()
    }
}