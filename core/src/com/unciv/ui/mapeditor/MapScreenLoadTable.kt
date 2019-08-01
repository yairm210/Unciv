package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Array
import com.unciv.UnCivGame
import com.unciv.logic.MapSaver
import com.unciv.models.gamebasics.tr
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.optionstable.PopupTable

class MapScreenLoadTable(mapEditorScreen: MapEditorScreen): PopupTable(mapEditorScreen){
    init{
        val mapFileSelectBox = SelectBox<String>(CameraStageBaseScreen.skin)
        val mapNames = Array<String>()
        for (mapName in MapSaver().getMaps()) mapNames.add(mapName)
        mapFileSelectBox.items = mapNames
        add(mapFileSelectBox).row()

        val loadMapButton = TextButton("Load".tr(), CameraStageBaseScreen.skin)
        loadMapButton.onClick {
            UnCivGame.Current.screen = MapEditorScreen(mapFileSelectBox.selected)
        }
        add(loadMapButton).row()

        val loadFromClipboardButton = TextButton("Load copied data".tr(), CameraStageBaseScreen.skin)
        loadFromClipboardButton .onClick {
            val clipboardContentsString = Gdx.app.clipboard.contents.trim()
            val decoded = Gzip.unzip(clipboardContentsString)
            val loadedMap = MapSaver().mapFromJson(decoded)
            UnCivGame.Current.screen = MapEditorScreen(loadedMap)
        }
        add(loadFromClipboardButton).row()

        val closeOptionsButtton = TextButton("Close".tr(), CameraStageBaseScreen.skin)
        closeOptionsButtton.onClick { remove() }
        add(closeOptionsButtton).row()

        open()
    }
}