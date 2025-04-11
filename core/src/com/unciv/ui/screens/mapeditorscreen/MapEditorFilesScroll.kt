package com.unciv.ui.screens.mapeditorscreen

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.files.MapSaver
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.savescreens.VerticalFileListScrollPane

class MapEditorFilesScroll(
    initWidth: Float,
    private val includeMods: Boolean = false,
    selectAction: (FileHandle) -> Unit,
    doubleClickAction: (FileHandle) -> Unit
) : VerticalFileListScrollPane() {
    private class ListEntry(val mod: String, file: FileHandle)
        : FileHandle(file.file())

    init {
        setOverscroll(false, true)
        existingSavesTable.defaults().pad(5f).maxWidth(initWidth)
        onChange(selectAction)
        onDoubleClick(doubleClickAction)
    }

    private fun getModMaps(): Sequence<ListEntry> {
        if (!includeMods) return emptySequence()
        return sequence {
            for (modFolder in RulesetCache.values.mapNotNull { it.folderLocation }) {
                val mapsFolder = modFolder.child(MapSaver.mapsFolder)
                if (mapsFolder.exists())
                    yieldAll(
                        mapsFolder.list().sortedBy { it.name() }
                            .map { ListEntry(modFolder.name(), it) }
                    )
            }
        }
    }

    fun updateMaps() {
        val sortedFiles = (
                Sequence { MapSaver.getMaps().iterator() }
                    .sortedByDescending { it.lastModified() }
                    .map { ListEntry("", it) }
                + getModMaps()
            )

        var lastMod = ""
        update(sortedFiles) {
            (it as? ListEntry)?.mod?.also { mod->
                if (mod != lastMod) {
                    // One header per Mod
                    existingSavesTable.add(Table().apply {
                        add(ImageGetter.getDot(Color.LIGHT_GRAY)).minHeight(2f).minWidth(15f)
                        add(mod.toLabel(Color.LIGHT_GRAY)).left().pad(0f,2f)
                        add(ImageGetter.getDot(Color.LIGHT_GRAY)).minHeight(2f).growX().row()
                    }).growX().row()
                    lastMod = mod
                }
            }
        }
        existingSavesTable.layout()
    }

    fun noMapsAvailable(): Boolean {
        if (MapSaver.getMaps().any()) return false
        if (!includeMods) return true
        for (modFolder in RulesetCache.values.mapNotNull { it.folderLocation }) {
            val mapsFolder = modFolder.child(MapSaver.mapsFolder)
            if (mapsFolder.exists() && mapsFolder.list().any()) return false
        }
        return true
    }
}
