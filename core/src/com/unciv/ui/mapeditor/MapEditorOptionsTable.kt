package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Json
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.logic.map.RoadStatus
import com.unciv.models.gamebasics.tr
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.optionstable.PopupTable

class MapEditorOptionsTable(mapEditorScreen: MapEditorScreen): PopupTable(mapEditorScreen){
    init{
        val mapNameEditor = TextField(mapEditorScreen.mapName, skin)
        mapNameEditor.addListener{ mapEditorScreen.mapName=mapNameEditor.text; true }
        add(mapNameEditor).row()
        
        val clearCurrentMapButton = TextButton("Clear current map".tr(),skin)
        clearCurrentMapButton.onClick {
            for(tileGroup in mapEditorScreen.mapHolder.tileGroups)
            {
                val tile = tileGroup.tileInfo
                tile.baseTerrain=Constants.ocean
                tile.terrainFeature=null
                tile.resource=null
                tile.improvement=null
                tile.improvementInProgress=null
                tile.roadStatus=RoadStatus.None
                tile.setTransients()

                tileGroup.update(true,true,true)
            }
        }
        add(clearCurrentMapButton).row()

        val saveMapButton = TextButton("Save".tr(), skin)
        saveMapButton.onClick {
            GameSaver().saveMap(mapEditorScreen.mapName,mapEditorScreen.tileMap)
            UnCivGame.Current.setWorldScreen()
        }
        add(saveMapButton).row()

        val copyMapAsTextButton = TextButton("Copy to clipboard".tr(), skin)
        copyMapAsTextButton.onClick {
            val json = Json().toJson(mapEditorScreen.tileMap)
            val base64Gzip = Gzip.zip(json)
            Gdx.app.clipboard.contents =  base64Gzip
        }
        add(copyMapAsTextButton).row()

        val loadMapButton = TextButton("Load".tr(), skin)
        loadMapButton.onClick { MapScreenLoadTable(mapEditorScreen); remove() }
        add(loadMapButton).row()

        val exitMapEditorButton = TextButton("Exit map editor".tr(), skin)
        exitMapEditorButton.onClick { UnCivGame.Current.setWorldScreen(); mapEditorScreen.dispose() }
        add(exitMapEditorButton ).row()

        val closeOptionsButtton = TextButton("Close".tr(), skin)
        closeOptionsButtton.onClick { remove() }
        add(closeOptionsButtton).row()

        open()
    }
}