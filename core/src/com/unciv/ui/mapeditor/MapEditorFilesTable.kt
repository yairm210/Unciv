package com.unciv.ui.mapeditor

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.MapSaver
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick

class MapEditorFilesTable(
    initWidth: Float,
    private val onSelect: (FileHandle) -> Unit
): Table(CameraStageBaseScreen.skin) {
    init {
        defaults().pad(5f).maxWidth(initWidth)
    }

    fun update() {
        clear()
        for (map in MapSaver.getMaps()) {
            val existingMapButton = TextButton(map.name(), CameraStageBaseScreen.skin)
            existingMapButton.onClick {
                for (cell in cells) cell.actor.color = Color.WHITE
                existingMapButton.color = Color.BLUE
                onSelect(map)
            }
            add(existingMapButton).row()
        }
    }
}
