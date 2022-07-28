package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.MapSaver
import com.unciv.logic.UncivShowableException
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.utils.AutoScrollPane
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.TabbedPager
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.Log
import kotlin.concurrent.thread

class MapEditorLoadTab(
    private val editorScreen: MapEditorScreen,
    headerHeight: Float
): Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val mapFiles = MapEditorFilesTable(
        initWidth = editorScreen.getToolsWidth() - 20f,
        includeMods = true,
        this::selectFile)

    private val loadButton = "Load map".toTextButton()
    private val deleteButton = "Delete map".toTextButton()

    private var chosenMap: FileHandle? = null

    init {
        val buttonTable = Table(skin)
        buttonTable.defaults().pad(10f).fillX()
        loadButton.onActivation { loadHandler() }
        loadButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        buttonTable.add(loadButton)
        deleteButton.onActivation { deleteHandler() }
        deleteButton.keyShortcuts.add(KeyCharAndCode.DEL)
        buttonTable.add(deleteButton)
        buttonTable.pack()

        val fileTableHeight = editorScreen.stage.height - headerHeight - buttonTable.height - 2f
        val scrollPane = AutoScrollPane(mapFiles, skin)
        scrollPane.setOverscroll(false, true)
        add(scrollPane).height(fileTableHeight).width(editorScreen.getToolsWidth() - 20f).row()
        add(buttonTable).row()
    }

    private fun loadHandler() {
        if (chosenMap == null) return
        editorScreen.askIfDirty(
            "Do you want to load another map without saving the recent changes?",
            "Load map"
        ) {
            thread(name = "MapLoader", isDaemon = true, block = this::loaderThread)
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
            mapFiles.update()
        }.open()
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
        mapFiles.update()
        selectFile(null)
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(false)
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
                addGoodSizedLabel(Constants.loading)
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
                Gdx.app.postRunnable {
                    needPopup = false
                    popup?.close()
                    ToastPopup("Missing mods: [${missingMods.joinToString()}]", editorScreen)
                }
            } else Gdx.app.postRunnable {
                Gdx.input.inputProcessor = null // This is to stop ANRs happening here, until the map editor screen sets up.
                try {
                    // For deprecated maps, set the base ruleset field if it's still saved in the mods field
                    val modBaseRuleset = map.mapParameters.mods.firstOrNull { RulesetCache[it]!!.modOptions.isBaseRuleset }
                    if (modBaseRuleset != null) {
                        map.mapParameters.baseRuleset = modBaseRuleset
                        map.mapParameters.mods -= modBaseRuleset
                    }

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
                    Log.error("Error displaying map \"$chosenMap\"", ex)
                    Gdx.input.inputProcessor = editorScreen.stage
                    ToastPopup("Error loading map!", editorScreen)
                }
            }
        } catch (ex: Throwable) {
            needPopup = false
            Gdx.app.postRunnable {
                popup?.close()
                Log.error("Error loading map \"$chosenMap\"", ex)
                ToastPopup("{Error loading map!}" +
                        (if (ex is UncivShowableException) "\n{${ex.message}}" else ""), editorScreen)
            }
        }
    }

    fun noMapsAvailable() = mapFiles.noMapsAvailable()
}
