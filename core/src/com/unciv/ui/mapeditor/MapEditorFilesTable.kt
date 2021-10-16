package com.unciv.ui.mapeditor

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.MapSaver
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick

class MapEditorFilesTable(
    initWidth: Float,
    private val onSelect: (FileHandle) -> Unit
): Table(CameraStageBaseScreen.skin) {
    var selectedIndex = -1
        private set
    private lateinit var sortedFiles: List<FileHandle>

    init {
        defaults().pad(5f).maxWidth(initWidth)
    }

    private fun markSelection(button: TextButton) {
        for ((index, cell) in cells.withIndex()) {
            if (cell.actor == button) {
                cell.actor.color = Color.BLUE
                selectedIndex = index
                onSelect(sortedFiles[index])
            } else {
                cell.actor.color = Color.WHITE
            }
        }
    }

    fun moveSelection(delta: Int) {
        selectedIndex = when {
            selectedIndex + delta in sortedFiles.indices ->
                selectedIndex + delta
            selectedIndex + delta < 0 ->
                sortedFiles.size - 1
            else -> 0
        }
        val button = cells[selectedIndex].actor as TextButton
        (parent as? ScrollPane)?.let {
            it.scrollY = (height - button.y) - (it.height - button.height) / 2
        }
        markSelection(button)
    }

    fun update() {
        clear()
        sortedFiles = MapSaver.getMaps().sortedByDescending { it.lastModified() }
        for (mapFile in sortedFiles) {
            val mapButton = TextButton(mapFile.name(), CameraStageBaseScreen.skin)
            mapButton.onClick {
                markSelection(mapButton)
            }
            add(mapButton).row()
        }
    }
}
