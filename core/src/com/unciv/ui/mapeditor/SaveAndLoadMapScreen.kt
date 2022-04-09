package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.UncivShowableException
import com.unciv.logic.map.MapType
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.crashHandlingThread
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.popup.YesNoPopup
import com.unciv.ui.utils.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class SaveAndLoadMapScreen(mapToSave: TileMap?, save:Boolean = false, previousScreen: BaseScreen)
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
                crashHandlingThread(name = "SaveMap") {
                    try {
                        MapSaver.saveMap(mapNameTextField.text, getMapCloneForSave(mapToSave))
                        postCrashHandlingRunnable {
                            Gdx.input.inputProcessor = null // This is to stop ANRs happening here, until the map editor screen sets up.
                            game.setScreen(MapEditorScreen(mapToSave))
                            dispose()
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        postCrashHandlingRunnable {
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
                crashHandlingThread(name = "MapLoader") {
                    var popup: Popup? = null
                    var needPopup = true    // loadMap can fail faster than postRunnable runs
                    postCrashHandlingRunnable {
                        if (!needPopup) return@postCrashHandlingRunnable
                        popup = Popup(this).apply {
                            addGoodSizedLabel("Loading...")
                            open()
                        }
                    }
                    try {
                        val map = MapSaver.loadMap(chosenMap!!, checkSizeErrors = false)

                        val missingMods = map.mapParameters.mods.filter { it !in RulesetCache }.toMutableList()
                        // [TEMPORARY] conversion of old maps with a base ruleset contained in the mods
                            val newBaseRuleset = map.mapParameters.mods.filter { it !in missingMods }.firstOrNull { RulesetCache[it]!!.modOptions.isBaseRuleset }
                            if (newBaseRuleset != null) map.mapParameters.baseRuleset = newBaseRuleset
                        //

                        if (map.mapParameters.baseRuleset !in RulesetCache) missingMods += map.mapParameters.baseRuleset

                        if (missingMods.isNotEmpty()) {
                            postCrashHandlingRunnable {
                                needPopup = false
                                popup?.close()
                                ToastPopup("Missing mods: [${missingMods.joinToString()}]", this)
                            }
                        } else postCrashHandlingRunnable {
                            Gdx.input.inputProcessor = null // This is to stop ANRs happening here, until the map editor screen sets up.
                            try {
                                // For deprecated maps, set the base ruleset field if it's still saved in the mods field
                                val modBaseRuleset = map.mapParameters.mods.firstOrNull { RulesetCache[it]!!.modOptions.isBaseRuleset }
                                if (modBaseRuleset != null) {
                                    map.mapParameters.baseRuleset = modBaseRuleset
                                    map.mapParameters.mods -= modBaseRuleset
                                }

                                game.setScreen(MapEditorScreen(map))
                                dispose()
                            } catch (ex: Throwable) {
                                ex.printStackTrace()
                                needPopup = false
                                popup?.close()
                                println("Error displaying map \"$chosenMap\": ${ex.localizedMessage}")
                                Gdx.input.inputProcessor = stage
                                ToastPopup("Error loading map!", this)
                            }
                        }
                    } catch (ex: Throwable) {
                        needPopup = false
                        ex.printStackTrace()
                        postCrashHandlingRunnable {
                            popup?.close()
                            println("Error loading map \"$chosenMap\": ${ex.localizedMessage}")
                            ToastPopup("Error loading map!".tr() +
                                (if (ex is UncivShowableException) "\n" + ex.message else ""), this)
                        }
                    }
                }
            }
        }
        rightSideButton.onClick(rightSideButtonAction)
        keyPressDispatcher[KeyCharAndCode.RETURN] = rightSideButtonAction

        topTable.add(ScrollPane(mapsTable)).maxWidth(stage.width / 2)

        val rightSideTable = Table().apply { defaults().pad(10f) }

        if (save) {
            mapNameTextField.textFieldFilter = TextField.TextFieldFilter { _, char -> char != '\\' && char != '/' }
            mapNameTextField.text = if (mapToSave == null || mapToSave.mapParameters.name.isEmpty()) "My new map"
                else mapToSave.mapParameters.name
            rightSideTable.add(mapNameTextField).width(300f).pad(10f)
            stage.keyboardFocus = mapNameTextField
            mapNameTextField.selectAll()
        }

        rightSideTable.addSeparator()

        if (save) {
            val copyMapAsTextButton = "Copy to clipboard".toTextButton()
            val copyMapAsTextAction = {
                Gdx.app.clipboard.contents = MapSaver.mapToSavedString(getMapCloneForSave(mapToSave!!))
            }
            copyMapAsTextButton.onClick (copyMapAsTextAction)
            keyPressDispatcher[KeyCharAndCode.ctrl('C')] = copyMapAsTextAction
            rightSideTable.add(copyMapAsTextButton).row()
        } else {
            val loadFromClipboardButton = "Load copied data".toTextButton()
            val couldNotLoadMapLabel = "Could not load map!".toLabel(Color.RED).apply { isVisible = false }
            val loadFromClipboardAction = {
                try {
                    val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                    val loadedMap = MapSaver.mapFromSavedString(clipboardContentsString, checkSizeErrors = false)
                    game.setScreen(MapEditorScreen(loadedMap))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    couldNotLoadMapLabel.isVisible = true
                }
            }
            loadFromClipboardButton.onClick(loadFromClipboardAction)
            keyPressDispatcher[KeyCharAndCode.ctrl('V')] = loadFromClipboardAction
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
        keyPressDispatcher[KeyCharAndCode.DEL] = deleteAction
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
        val collator = UncivGame.Current.settings.getCollatorFromLocale()
        for (map in MapSaver.getMaps().sortedWith(compareBy(collator) { it.name() })) {
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

    private fun getMapCloneForSave(mapToSave: TileMap) =
        mapToSave.clone().apply {
            setTransients(setUnitCivTransients = false)
        }
}
