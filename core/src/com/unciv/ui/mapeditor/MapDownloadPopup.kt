package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.mainmenu.DropBox
import kotlin.concurrent.thread
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class MapDownloadPopup(loadMapScreen: LoadMapScreen): Popup(loadMapScreen) {
    private val contentTable = Table()
    private val header = Table()
    private val listOfMaps = mutableListOf<TextButton>()
    private val scrollableMapTable = Table()
    private val loadingLabel = "Loading...".toLabel()

    init {
        add(header).row()
        add(loadingLabel).row()
        thread(name="LoadMapList") { loadContent() }
        add(contentTable).row()
        addCloseButton()
    }

    private fun createHeader() {
        header.defaults().pad(5f)
        header.add("Filter:".toLabel())
        val filter = TextField("", skin)
        val listener = TextField.TextFieldListener{ textField: TextField, _: Char -> updateList(textField.text) }
        filter.setTextFieldListener(listener)
        header.add(filter).row()
        header.addSeparator().row()
        pack()
    }

    private fun updateList(filterText : String) {
        scrollableMapTable.clear()
        listOfMaps.forEach { if (it.text.contains(filterText)) scrollableMapTable.add(it).row() }
        contentTable.pack()
    }

    private fun loadContent() {
        try {
            val folderList = DropBox.getFolderList("/Maps")
            Gdx.app.postRunnable {
                scrollableMapTable.apply { defaults().pad(10f) }
                for (downloadableMap in folderList.entries) {
                    val downloadMapButton = downloadableMap.name.toTextButton()
                    listOfMaps.add(downloadMapButton)
                    downloadMapButton.onClick {
                        thread(name = "MapDownload") { loadMap(downloadableMap) }
                    }
                    scrollableMapTable.add(downloadMapButton).row()
                }
                contentTable.add(ScrollPane(scrollableMapTable)).height(screen.stage.height * 2 / 3).row()
                pack()
                close()
                // the list is loaded and ready to be shown
                removeActor(loadingLabel)
                // create the header with a filter tool
                createHeader()
                open()
            }
        } catch (ex: Exception) {
            Gdx.app.postRunnable { addGoodSizedLabel("Could not get list of maps!").row() }
        }
    }

    private fun loadMap(downloadableMap: DropBox.FolderListEntry) {

        try {
            val mapJsonGzipped = DropBox.downloadFileAsString(downloadableMap.path_display)
            val decodedMapJson = Gzip.unzip(mapJsonGzipped)
            val mapObject = MapSaver.mapFromJson(decodedMapJson)
            MapSaver.saveMap(downloadableMap.name, mapObject)

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
