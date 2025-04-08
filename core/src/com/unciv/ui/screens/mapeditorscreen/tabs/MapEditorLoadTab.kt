package com.unciv.ui.screens.mapeditorscreen.tabs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.MissingModsException
import com.unciv.logic.UncivShowableException
import com.unciv.logic.files.MapSaver
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.widgets.TabbedPager
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.LoadingPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.mapeditorscreen.MapEditorFilesScroll
import com.unciv.ui.screens.mapeditorscreen.MapEditorScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive

class MapEditorLoadTab(
    private val editorScreen: MapEditorScreen,
    headerHeight: Float
): Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val mapFiles = MapEditorFilesScroll(
        initWidth = editorScreen.getToolsWidth() - 20f,
        includeMods = true,
        this::selectFile,
        this::loadHandler
    )

    private val loadButton = "Load map".toTextButton()
    private val deleteButton = "Delete map".toTextButton()

    private var chosenMap: FileHandle? = null

    init {
        val buttonTable = Table(skin)
        buttonTable.defaults().pad(10f).fillX()
        loadButton.onActivation { chosenMap?.let { loadHandler(it) } }
        loadButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        buttonTable.add(loadButton)
        deleteButton.onActivation { deleteHandler() }
        deleteButton.keyShortcuts.add(KeyCharAndCode.DEL)
        buttonTable.add(deleteButton)
        buttonTable.pack()

        val fileTableHeight = editorScreen.stage.height - headerHeight - buttonTable.height - 2f
        add(mapFiles).size(editorScreen.getToolsWidth() - 20f, fileTableHeight).padTop(10f).row()
        add(buttonTable).row()
    }

    private fun loadHandler(mapFile: FileHandle) {
        editorScreen.askIfDirtyForLoad {
            editorScreen.startBackgroundJob("MapLoader") { loaderThread(mapFile) }
        }
    }

    private fun deleteHandler() {
        if (chosenMap == null) return
        ConfirmPopup(
            editorScreen,
            "Are you sure you want to delete this map?",
            "Delete map",
        ) {
            chosenMap!!.delete()
            mapFiles.updateMaps()
        }.open()
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
        mapFiles.updateMaps()
        selectFile(null)
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(false)
    }

    private fun selectFile(file: FileHandle?) {
        chosenMap = file
        loadButton.isEnabled = (file != null)
        deleteButton.isEnabled = (file != null)
        deleteButton.color = if (file != null) Color.SCARLET else Color.BROWN
    }

    private fun CoroutineScope.loaderThread(mapFile: FileHandle) {
        var popup: Popup? = null
        var needPopup = true    // loadMap can fail faster than postRunnable runs
        Concurrency.runOnGLThread {
            if (!needPopup) return@runOnGLThread
            popup = LoadingPopup(editorScreen)
        }
        try {
            val map = MapSaver.loadMap(mapFile)
            if (!isActive) return

            // For deprecated maps, set the base ruleset field if it's still saved in the mods field
            val modBaseRuleset = map.mapParameters.mods.firstOrNull { RulesetCache[it]?.modOptions?.isBaseRuleset == true }
            if (modBaseRuleset != null) {
                map.mapParameters.baseRuleset = modBaseRuleset
                map.mapParameters.mods -= modBaseRuleset
            }

            val missingMods = (setOf(map.mapParameters.baseRuleset) + map.mapParameters.mods)
                .filterNot { it in RulesetCache }
            if (missingMods.isNotEmpty())
                throw MissingModsException(missingMods)

            Concurrency.runOnGLThread {
                Gdx.input.inputProcessor = null // This is to stop ANRs happening here, until the map editor screen sets up.
                try {
                    val ruleset = RulesetCache.getComplexRuleset(map.mapParameters)
                    val rulesetIncompatibilities = map.getRulesetIncompatibility(ruleset)
                    if (rulesetIncompatibilities.isNotEmpty()) {
                        map.removeMissingTerrainModReferences(ruleset)
                        val message = "{This map has errors:}\n\n" +
                                rulesetIncompatibilities.sorted().joinToString("\n") { it.tr() } +
                                "\n\n{The incompatible elements have been removed.}"
                        ToastPopup(message, editorScreen, 4000L)
                    }

                    editorScreen.loadMap(map, ruleset)
                    needPopup = false
                    popup?.close()
                } catch (ex: Throwable) {
                    needPopup = false
                    popup?.close()
                    Log.error("Error displaying map \"$mapFile\"", ex)
                    Gdx.input.inputProcessor = editorScreen.stage
                    ToastPopup("Error loading map!", editorScreen)
                }
            }
        } catch (ex: Throwable) {
            needPopup = false
            Concurrency.runOnGLThread {
                popup?.close()
                Log.error("Error loading map \"$mapFile\"", ex)

                @Suppress("InstanceOfCheckForException") // looks cleaner like this than having 2 catch statements
                ToastPopup("{Error loading map!}" +
                        (if (ex is UncivShowableException) "\n{${ex.message}}" else ""), editorScreen)
            }
        }
    }

    fun noMapsAvailable() = mapFiles.noMapsAvailable()
}
