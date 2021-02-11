package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.TileMap
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.*
import kotlin.concurrent.thread
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class LoadMapScreen(previousMap: TileMap?) : PickerScreen() {
    var chosenMap: FileHandle? = null
    val deleteButton = "Delete map".toTextButton()
    val mapsTable = Table().apply { defaults().pad(10f) }

    init {
        rightSideButton.setText("Load map".tr())
        rightSideButton.onClick {
            thread {
                Gdx.app.postRunnable {
                    val popup = Popup(this)
                    popup.addGoodSizedLabel("Loading...")
                    popup.open()
                }
                val map = MapSaver.loadMap(chosenMap!!)
                Gdx.app.postRunnable {
                    Gdx.input.inputProcessor = null // This is to stop ANRs happening here, until the map editor screen sets up.
                    UncivGame.Current.setScreen(MapEditorScreen(map))
                    dispose()
                }
            }
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
                chosenMap!!.delete()
                UncivGame.Current.setScreen(LoadMapScreen(previousMap))
            }, this).open()
        }
        rightSideTable.add(deleteButton).row()

        topTable.add(rightSideTable)
        setDefaultCloseAction(MainMenuScreen())

        update()
    }

    fun update() {
        chosenMap = null
        deleteButton.disable()
        deleteButton.color = Color.RED

        deleteButton.setText("Delete map".tr())
        rightSideButton.setText("Load map".tr())

        mapsTable.clear()
        for (map in MapSaver.getMaps()) {
            val loadMapButton = TextButton(map.name(), skin)
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


