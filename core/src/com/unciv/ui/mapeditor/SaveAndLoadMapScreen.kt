package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Json
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.logic.map.TileMap
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.*
import kotlin.concurrent.thread
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class SaveAndLoadMapScreen(mapToSave: TileMap?, save:Boolean = false) : PickerScreen() {
    var chosenMap: FileHandle? = null
    val deleteButton = "Delete map".toTextButton()
    val mapsTable = Table().apply { defaults().pad(10f) }
    val mapNameTextField = TextField("", skin).apply { maxLength = 100 }

    init {
        if (save) {
            rightSideButton.setText("Save map".tr())
            rightSideButton.onClick {
                mapToSave!!.mapParameters.name = mapNameTextField.text
                mapToSave.mapParameters.type = MapType.custom
                thread(name = "SaveMap") {
                    try {
                        MapSaver.saveMap(mapNameTextField.text, mapToSave)
                        Gdx.app.postRunnable {
                            Gdx.input.inputProcessor = null // This is to stop ANRs happening here, until the map editor screen sets up.
                            UncivGame.Current.setScreen(MapEditorScreen(mapToSave))
                            dispose()
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        Gdx.app.postRunnable {
                            val cantLoadGamePopup = Popup(this)
                            cantLoadGamePopup.addGoodSizedLabel("It looks like your map can't be saved!").row()
                            cantLoadGamePopup.addCloseButton()
                            cantLoadGamePopup.open(force = true)
                        }
                    }
                }
            }
        } else {
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
        }

        topTable.add(ScrollPane(mapsTable)).height(stage.height * 2 / 3)
                .maxWidth(stage.width / 2)

        val rightSideTable = Table().apply { defaults().pad(10f) }

        if (save) {
            mapNameTextField.textFieldFilter = TextField.TextFieldFilter { _, char -> char != '\\' && char != '/' }
            mapNameTextField.text = "My new map"
            rightSideTable.add(mapNameTextField).width(300f).pad(10f)
        } else {
            val downloadMapButton = "Download map".toTextButton()
            downloadMapButton.onClick {
                MapDownloadPopup(this).open()
            }
            rightSideTable.add(downloadMapButton).row()
        }

        rightSideTable.addSeparator()

        if (save) {
            val copyMapAsTextButton = "Copy to clipboard".toTextButton()
            copyMapAsTextButton.onClick {
                val json = Json().toJson(mapToSave)
                val base64Gzip = Gzip.zip(json)
                Gdx.app.clipboard.contents = base64Gzip
            }
            rightSideTable.add(copyMapAsTextButton).row()
        } else {
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
        }

        deleteButton.onClick {
            YesNoPopup("Are you sure you want to delete this map?", {
                chosenMap!!.delete()
                UncivGame.Current.setScreen(SaveAndLoadMapScreen(mapToSave))
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
//        rightSideButton.setText("Load map".tr())

        mapsTable.clear()
        for (map in MapSaver.getMaps()) {
            val loadMapButton = TextButton(map.name(), skin)
            loadMapButton.onClick {
                for (cell in mapsTable.cells) cell.actor.color = Color.WHITE
                loadMapButton.color = Color.BLUE

                rightSideButton.enable()
                chosenMap = map
                mapNameTextField.text = map.name()
                deleteButton.enable()
                deleteButton.color = Color.RED
            }
            mapsTable.add(loadMapButton).row()
        }
    }

}