package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.models.translations.tr
import com.unciv.ui.mapeditor.EditorMapHolder
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.setFontSize

/**
 * Creates a zoom button
 * zoomType is "in" for zooming in
 * and "out" for zooming out
 *
 * @param zoomType
 */
class ZoomButton(zoomType: String, mapHolderEditor: EditorMapHolder? = null, mapHolderWorld: WorldMapHolder? = null): TextButton("", BaseScreen.skin) {
    private val zoomType: String
    init {
        this.zoomType = zoomType
        label.setFontSize(30)
        labelCell.pad(10f)
        val action = {
            zoomForRightHolderAndType(mapHolderEditor, mapHolderWorld)
        }
        onClick(action)
    }

    private fun zoomForRightHolderAndType(mapHolderEditor: EditorMapHolder?, mapHolderWorld: WorldMapHolder?) {
        if (checkAndReturnType(zoomType)) {
            mapHolderEditor?.zoomIn()
            mapHolderWorld?.zoomIn()
        } else {
            mapHolderEditor?.zoomOut()
            mapHolderWorld?.zoomOut()
        }
    }

    private fun checkAndReturnType(zoomType: String): Boolean {
        return zoomType == "in"
    }

    fun update() {
        if (checkAndReturnType(zoomType)) {
            setText("+".tr())
        } else {
            setText("-".tr())
        }
        label.color = Color.WHITE
        pack()
    }
}
