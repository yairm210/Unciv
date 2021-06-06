package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.Input
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.logic.map.TileMap
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.*
import kotlin.concurrent.thread
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class SaveAndLoadMapScreen(mapToSave: TileMap?, save:Boolean = false, previousScreen: CameraStageBaseScreen)
        : PickerScreen(disableScroll = true) {
    private var chosenMap: FileHandle? = null
    val deleteButton = "Delete map".toTextButton()
    val mapsTable = Table().apply { defaults().pad(10f) }
    private val mapNameTextField = TextField("", skin).apply { maxLength = 100 }

    init {
        val rightSideButtonAction: ()->Unit
        if (save) {
            rightSideButton.enable()
            rightSideButton.setText("Save map".tr())
            rightSideButtonAction = {
                mapToSave!!.mapParameters.name = mapNameTextField.text
                mapToSave.mapParameters.type = MapType.custom
                thread(name = "SaveMap") {
                    try {
                        MapSaver.saveMap(mapNameTextField.text, mapToSave)
                        Gdx.app.postRunnable {
                            Gdx.input.inputProcessor = null // This is to stop ANRs happening here, until the map editor screen sets up.
                            game.setScreen(MapEditorScreen(mapToSave))
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
            rightSideButtonAction = {
                thread {
                    Gdx.app.postRunnable {
                        val popup = Popup(this)
                        popup.addGoodSizedLabel("Loading...")
                        popup.open()
                    }
                    val map = MapSaver.loadMap(chosenMap!!)
                    Gdx.app.postRunnable {
                        Gdx.input.inputProcessor = null // This is to stop ANRs happening here, until the map editor screen sets up.
                        game.setScreen(MapEditorScreen(map))
                        dispose()
                    }
                }
            }
        }
        rightSideButton.onClick(rightSideButtonAction)
        keyPressDispatcher['\r'] = rightSideButtonAction

        topTable.add(ScrollPane(mapsTable)).maxWidth(stage.width / 2)

        val rightSideTable = Table().apply { defaults().pad(10f) }

        if (save) {
            mapNameTextField.textFieldFilter = TextField.TextFieldFilter { _, char -> char != '\\' && char != '/' }
            mapNameTextField.text = if (mapToSave == null || mapToSave.mapParameters.name.isEmpty()) "My new map"
                else mapToSave.mapParameters.name
            rightSideTable.add(mapNameTextField).width(300f).pad(10f)
            stage.keyboardFocus = mapNameTextField
            mapNameTextField.selectAll()
        } else {
            val downloadMapButton = "Download map".toTextButton()
            val downloadAction = {
                MapDownloadPopup(this).open()
            }
            downloadMapButton.onClick(downloadAction)
            keyPressDispatcher['\u0004'] = downloadAction   // Ctrl-D
            rightSideTable.add(downloadMapButton).row()
        }

        rightSideTable.addSeparator()

        if (save) {
            val copyMapAsTextButton = "Copy to clipboard".toTextButton()
            val copyMapAsTextAction = {
                val json = Json().toJson(mapToSave)
                val base64Gzip = Gzip.zip(json)
                Gdx.app.clipboard.contents = base64Gzip
            }
            copyMapAsTextButton.onClick (copyMapAsTextAction)
            keyPressDispatcher['\u0003'] = copyMapAsTextAction   // Ctrl-C
            rightSideTable.add(copyMapAsTextButton).row()
        } else {
            val loadFromClipboardButton = "Load copied data".toTextButton()
            val couldNotLoadMapLabel = "Could not load map!".toLabel(Color.RED).apply { isVisible = false }
            val loadFromClipboardAction = {
                try {
                    val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                    val decoded = Gzip.unzip(clipboardContentsString)
                    val loadedMap = MapSaver.mapFromJson(decoded)
                    game.setScreen(MapEditorScreen(loadedMap))
                } catch (ex: Exception) {
                    couldNotLoadMapLabel.isVisible = true
                }
            }
            loadFromClipboardButton.onClick(loadFromClipboardAction)
            keyPressDispatcher['\u0016'] = loadFromClipboardAction   // Ctrl-V
            rightSideTable.add(loadFromClipboardButton).row()
            rightSideTable.add(couldNotLoadMapLabel).row()
        }

        val deleteAction = {
            YesNoPopup("Are you sure you want to delete this map?", {
                chosenMap!!.delete()
                game.setScreen(SaveAndLoadMapScreen(mapToSave, save, previousScreen))
            }, this).open()
        }
        deleteButton.onClick(deleteAction)
        keyPressDispatcher['\u007f'] = deleteAction     // Input.Keys.DEL but ascii has precedence
        rightSideTable.add(deleteButton).row()

        topTable.add(rightSideTable)
        setDefaultCloseAction(previousScreen)

        update()
    }

    fun update() {
        chosenMap = null
        deleteButton.disable()
        deleteButton.color = Color.RED

        deleteButton.setText("Delete map".tr())

        mapsTable.clear()
        for (map in MapSaver.getMaps()) {
            val existingMapButton = TextButton(map.name(), skin)
            existingMapButton.onClick {
                for (cell in mapsTable.cells) cell.actor.color = Color.WHITE
                existingMapButton.color = Color.BLUE

                rightSideButton.enable()
                chosenMap = map
                mapNameTextField.text = map.name()
                mapNameTextField.setSelection(Int.MAX_VALUE,Int.MAX_VALUE)  // sets caret to end of text
                
                deleteButton.enable()
                deleteButton.color = Color.RED
            }
            mapsTable.add(existingMapButton).row()
        }
    }

}
