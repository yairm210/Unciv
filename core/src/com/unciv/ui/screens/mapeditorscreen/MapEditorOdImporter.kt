package com.unciv.ui.screens.mapeditorscreen

import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.UncivShowableException
import com.unciv.logic.files.FileChooser
import com.unciv.logic.map.OdMapImport
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.popups.ToastPopup
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job

/**
 * The map editor's "Import an Open Doctrines map" button.
 *
 * The reading itself is [OdMapImport]; this is the file dialog, the background
 * job and the error toast, shaped after [MapEditorWesnothImporter].
 */
class MapEditorOdImporter(private val editorScreen: MapEditorScreen) : DisposableHandle {
    companion object {
        var lastFileFolder: FileHandle? = null
    }

    private val ruleset by lazy { RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!! }
    private var importJob: Job? = null

    override fun dispose() {
        importJob?.cancel()
    }

    fun onImportButtonClicked() {
        editorScreen.askIfDirtyForLoad(::openFileDialog)
    }

    private fun openFileDialog() {
        FileChooser.createLoadDialog(editorScreen.stage, "Choose an Open Doctrines map", lastFileFolder) {
            success: Boolean, file: FileHandle ->
            if (!success) return@createLoadDialog
            startImport(file)
            lastFileFolder = file.parent()
        }.apply {
            filter = FileChooser.createExtensionFilter("odmap")
        }.open()
    }

    private fun startImport(file: FileHandle) {
        dispose()
        importJob = Concurrency.run("Open Doctrines map import") {
            try {
                val map = OdMapImport.read(file, ruleset)
                Concurrency.runOnGLThread { editorScreen.loadMap(map) }
            } catch (ex: UncivShowableException) {
                Log.error("Could not load map", ex)
                Concurrency.runOnGLThread { ToastPopup(ex.message, editorScreen, 4000L) }
            } catch (ex: Throwable) {
                Log.error("Could not load map", ex)
                Concurrency.runOnGLThread { ToastPopup("Could not load map!", editorScreen) }
            }
        }
    }
}
