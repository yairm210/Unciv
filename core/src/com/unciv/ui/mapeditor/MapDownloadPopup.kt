package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.Popup
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.mainmenu.DropBox
import kotlin.concurrent.thread

class MapDownloadPopup(loadMapScreen: LoadMapScreen): Popup(loadMapScreen) {
    val contentTable = Table()
    init {
        thread(name="LoadMapList") { loadContent() }
        add(contentTable).row()
        addCloseButton()
    }

    fun loadContent() {
        try {
            val folderList = DropBox().getFolderList("/Maps")
            Gdx.app.postRunnable {
                val scrollableMapTable = Table().apply { defaults().pad(10f) }
                for (downloadableMap in folderList.entries) {
                    val downloadMapButton = TextButton(downloadableMap.name, CameraStageBaseScreen.skin)
                    downloadMapButton.onClick {
                        thread(name = "MapDownload") { loadMap(downloadableMap) }
                    }
                    scrollableMapTable.add(downloadMapButton).row()
                }
                contentTable.add(ScrollPane(scrollableMapTable)).height(screen.stage.height * 2 / 3).row()
            }
        } catch (ex: Exception) {
            addGoodSizedLabel("Could not get list of maps!").row()
        }
    }

    fun loadMap(downloadableMap: DropBox.FolderListEntry) {

        try {
            val mapJsonGzipped = DropBox().downloadFileAsString(downloadableMap.path_display)
            val decodedMapJson = Gzip.unzip(mapJsonGzipped)
            val mapObject = MapSaver().mapFromJson(decodedMapJson)
            MapSaver().saveMap(downloadableMap.name, mapObject)

            // creating a screen is a GL task
            Gdx.app.postRunnable { UncivGame.Current.setScreen(MapEditorScreen(mapObject)) }
        } catch (ex: Exception) {
            print(ex)

            // Yes, even creating popups.
            Gdx.app.postRunnable {
                val couldNotDownloadMapPopup = Popup(screen)
                couldNotDownloadMapPopup.addGoodSizedLabel("Could not download map!").row()
                couldNotDownloadMapPopup.addCloseButton()
                couldNotDownloadMapPopup.open()
            }
        }
    }
}
