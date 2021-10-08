package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.MapSaver
import com.unciv.logic.UncivShowableException
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.concurrent.thread

class MapEditorLoadTab(
    private val editorScreen: MapEditorScreenV2,
    headerHeight: Float
): Table(CameraStageBaseScreen.skin), TabbedPager.IPageActivation {
    private val mapFiles = MapEditorFilesTable(editorScreen.stage.width * 0.3f, this::selectFile)

    private val loadButton: TextButton
    private val deleteButton: TextButton
    private val pasteButton: TextButton

    private var chosenMap: FileHandle? = null

    init {
        val buttonTable = Table(skin)
        buttonTable.defaults().pad(10f).fillX()
        loadButton = "Load Map".toTextButton()
        loadButton.onClick(this::loadHandler)
        buttonTable.add(loadButton)
        deleteButton = "Delete map".toTextButton()
        deleteButton.onClick(this::deleteHandler)
        buttonTable.add(deleteButton)
        pasteButton = "Load copied data".toTextButton()
        pasteButton.onClick(this::pasteHandler)
        buttonTable.add(pasteButton)
        buttonTable.pack()

        val fileTableHeight = editorScreen.stage.height - headerHeight - buttonTable.height - 2f
        add(AutoScrollPane(mapFiles, skin)).height(fileTableHeight).row()
        add(buttonTable).row()
    }

    private fun loadHandler() {
        if (chosenMap == null) return
        thread(name = "MapLoader", block = this::loaderThread)
    }

    private fun deleteHandler() {
        if (chosenMap == null) return
        YesNoPopup("Are you sure you want to delete this map?", {
            chosenMap!!.delete()
            mapFiles.update()
        }, editorScreen).open()
    }

    private fun pasteHandler() {
        try {
            val clipboardContentsString = Gdx.app.clipboard.contents.trim()
            val loadedMap = MapSaver.mapFromSavedString(clipboardContentsString, checkSizeErrors = false)
            editorScreen.loadMap(loadedMap)
        } catch (ex: Exception) {
            ToastPopup("Could not load map!", editorScreen)
        }
    }

    override fun activated(index: Int) {
        //editorScreen.tabs.setScrollDisabled(true)
        mapFiles.update()
        editorScreen.keyPressDispatcher[KeyCharAndCode.RETURN] = this::loadHandler
        editorScreen.keyPressDispatcher[KeyCharAndCode.DEL] = this::deleteHandler
        editorScreen.keyPressDispatcher[KeyCharAndCode.ctrl('v')] = this::pasteHandler
        pasteButton.isEnabled = Gdx.app.clipboard.hasContents()
        selectFile(null)
    }

    override fun deactivated(newIndex: Int) {
        editorScreen.keyPressDispatcher.remove(KeyCharAndCode.RETURN)
        editorScreen.keyPressDispatcher.remove(KeyCharAndCode.DEL)
        editorScreen.keyPressDispatcher.remove(KeyCharAndCode.ctrl('v'))
        //editorScreen.tabs.setScrollDisabled(false)
    }

    fun selectFile(file: FileHandle?) {
        chosenMap = file
        loadButton.isEnabled = (file != null)
        deleteButton.isEnabled = (file != null)
        deleteButton.color = if (file != null) Color.SCARLET else Color.BROWN
    }

    fun loaderThread() {
        var popup: Popup? = null
        var needPopup = true    // loadMap can fail faster than postRunnable runs
        Gdx.app.postRunnable {
            if (!needPopup) return@postRunnable
            popup = Popup(editorScreen).apply {
                addGoodSizedLabel("Loading...")
                open()
            }
        }
        try {
            val map = MapSaver.loadMap(chosenMap!!, checkSizeErrors = false)

            val missingMods = map.mapParameters.mods.filter { it !in RulesetCache }
            if (missingMods.isNotEmpty()) {
                Gdx.app.postRunnable {
                    needPopup = false
                    popup?.close()
                    ToastPopup("Missing mods: [${missingMods.joinToString()}]", editorScreen)
                }
            } else Gdx.app.postRunnable {
                Gdx.input.inputProcessor = null // This is to stop ANRs happening here, until the map editor screen sets up.
                try {
                    editorScreen.loadMap(map)
                    needPopup = false
                    popup?.close()
                    Gdx.input.inputProcessor = stage
                } catch (ex: Throwable) {
                    needPopup = false
                    popup?.close()
                    println("Error displaying map \"$chosenMap\": ${ex.localizedMessage}")
                    Gdx.input.inputProcessor = editorScreen.stage
                    ToastPopup("Error loading map!", editorScreen)
                }
            }
        } catch (ex: Throwable) {
            needPopup = false
            Gdx.app.postRunnable {
                popup?.close()
                println("Error loading map \"$chosenMap\": ${ex.localizedMessage}")
                ToastPopup("Error loading map!".tr() +
                        (if (ex is UncivShowableException) "\n" + ex.message else ""), editorScreen)
            }
        }
    }
}