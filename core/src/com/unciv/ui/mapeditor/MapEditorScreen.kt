package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.GameParameters
import com.unciv.logic.GameSaver
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.tr
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.TileGroupMap

class MapEditorScreen(): CameraStageBaseScreen(){
    var tileMap = TileMap(GameParameters())
    var mapName = "My first map"
    lateinit var mapHolder: TileGroupMap<TileGroup>
    val tileEditorOptions = TileEditorOptionsTable(this)


    constructor(mapNameToLoad:String?):this(){
        var mapToLoad = mapNameToLoad
        if (mapToLoad == null) {
            val existingSaves = GameSaver().getMaps()
            if(existingSaves.isNotEmpty())
                mapToLoad = existingSaves.first()
        }
        if(mapToLoad!=null){
            mapName=mapToLoad
            tileMap=GameSaver().loadMap(mapName)
        }
        initialize()
    }

    constructor(map: TileMap):this(){
        tileMap = map
        initialize()
    }

    fun initialize(){
        tileMap.setTransients()
        val mapHolder = getMapHolder(tileMap)

        stage.addActor(mapHolder)

        stage.addActor(tileEditorOptions)


        val saveMapButton = TextButton("Options".tr(),skin)
        saveMapButton.onClick {
            MapEditorOptionsTable(this)
        }
        stage.addActor(saveMapButton)
    }

    private fun getMapHolder(tileMap: TileMap): ScrollPane {
        val tileGroups = tileMap.values.map { TileGroup(it) }
        for (tileGroup in tileGroups) {
            tileGroup.showEntireMap = true
            tileGroup.update(true, true, true)
            tileGroup.onClick {
                val tileInfo = tileGroup.tileInfo

                tileEditorOptions.updateTileWhenClicked(tileInfo)
                tileGroup.tileInfo.setTransients()
                tileGroup.update(true, true, true)
            }
        }

        mapHolder = TileGroupMap(tileGroups, 300f)
        val scrollPane = ScrollPane(mapHolder)
        scrollPane.setSize(stage.width, stage.height)
        scrollPane.layout()
        scrollPane.scrollPercentX=0.5f
        scrollPane.scrollPercentY=0.5f
        scrollPane.updateVisualScroll()
        return scrollPane
    }


}

