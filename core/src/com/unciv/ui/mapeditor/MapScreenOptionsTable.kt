package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.optionstable.PopupTable

class MapScreenOptionsTable(mapEditorScreen: MapEditorScreen): PopupTable(mapEditorScreen){
    init{
        val mapNameEditor = TextField(mapEditorScreen.mapName, CameraStageBaseScreen.skin)
        mapNameEditor.addListener{ mapEditorScreen.mapName=mapNameEditor.text; true }
        add(mapNameEditor).row()

        val saveMapButton = TextButton("Save".tr(), CameraStageBaseScreen.skin)
        saveMapButton.onClick {
            GameSaver().saveMap(mapEditorScreen.mapName,mapEditorScreen.tileMap)
            UnCivGame.Current.setWorldScreen()
        }
        add(saveMapButton).row()

        val loadMapButton = TextButton("Load".tr(), CameraStageBaseScreen.skin)
        loadMapButton.onClick { MapScreenLoadTable(mapEditorScreen); remove() }
        add(loadMapButton).row()

        val closeOptionsButtton = TextButton("Close".tr(), CameraStageBaseScreen.skin)
        closeOptionsButtton.onClick { remove() }
        add(closeOptionsButtton).row()

        open()
    }
}