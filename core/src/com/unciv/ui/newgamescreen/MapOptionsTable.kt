package com.unciv.ui.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onChange
import com.unciv.ui.utils.toLabel

class MapOptionsTable(val newGameScreen: NewGameScreen): Table() {

    val mapParameters = newGameScreen.gameSetupInfo.mapParameters
    private var mapTypeSpecificTable = Table()
    private val generatedMapOptionsTable = MapParametersTable(mapParameters)
    private val savedMapOptionsTable = Table()
    lateinit var mapTypeSelectBox: TranslatedSelectBox

    init {
        defaults().pad(5f)
        add("Map Options".toLabel(fontSize = 24)).top().padBottom(20f).colspan(2).row()
        addMapTypeSelection()
    }

    private fun addMapTypeSelection() {
        add("{Map Type}:".toLabel())
        val mapTypes = arrayListOf("Generated")
        if (MapSaver.getMaps().isNotEmpty()) mapTypes.add(MapType.custom)
        mapTypeSelectBox = TranslatedSelectBox(mapTypes, "Generated", CameraStageBaseScreen.skin)

        val mapFileSelectBox = getMapFileSelectBox()
        savedMapOptionsTable.defaults().pad(5f)
        savedMapOptionsTable.add("{Map file}:".toLabel()).left()
        // because SOME people gotta give the hugest names to their maps
        savedMapOptionsTable.add(mapFileSelectBox).maxWidth(newGameScreen.stage.width / 2)
                .right().row()



        fun updateOnMapTypeChange() {
            mapTypeSpecificTable.clear()
            if (mapTypeSelectBox.selected.value == MapType.custom) {
                mapParameters.type = MapType.custom
                mapParameters.name = mapFileSelectBox.selected.toString()
                mapTypeSpecificTable.add(savedMapOptionsTable)
                newGameScreen.unlockTables()
            } else { // generated map
                mapParameters.name = ""
                mapParameters.type = generatedMapOptionsTable.mapTypeSelectBox.selected.value
                mapTypeSpecificTable.add(generatedMapOptionsTable)
                newGameScreen.unlockTables()
            }
            newGameScreen.gameSetupInfo.gameParameters.godMode = false
            newGameScreen.updateTables()
        }

        // activate once, so when we had a file map before we'll have the right things set for another one
        updateOnMapTypeChange()

        mapTypeSelectBox.onChange { updateOnMapTypeChange() }

        add(mapTypeSelectBox).row()
        add(mapTypeSpecificTable).colspan(2).row()
    }

    private fun getMapFileSelectBox(): SelectBox<FileHandleWrapper> {
        val mapFileSelectBox = SelectBox<FileHandleWrapper>(CameraStageBaseScreen.skin)
        val mapFiles = Array<FileHandleWrapper>()
        for (mapFile in MapSaver.getMaps())
            mapFiles.add(FileHandleWrapper(mapFile))
        for (mod in Gdx.files.local("mods").list()) {
            val mapsFolder = mod.child("maps")
            if (mapsFolder.exists())
                for (map in mapsFolder.list())
                    mapFiles.add(FileHandleWrapper(map))
        }
        mapFileSelectBox.items = mapFiles
        val selectedItem = mapFiles.firstOrNull { it.fileHandle.name() == mapParameters.name }
        if (selectedItem != null) {
            mapFileSelectBox.selected = selectedItem
            newGameScreen.gameSetupInfo.mapFile = mapFileSelectBox.selected.fileHandle
        } else if (!mapFiles.isEmpty) {
            mapFileSelectBox.selected = mapFiles.first()
            newGameScreen.gameSetupInfo.mapFile = mapFileSelectBox.selected.fileHandle
        }

        mapFileSelectBox.onChange {
            val mapFile = mapFileSelectBox.selected.fileHandle
            mapParameters.name = mapFile.name()
            newGameScreen.gameSetupInfo.mapFile = mapFile
            val map = MapSaver.loadMap(mapFile)
            newGameScreen.gameSetupInfo.gameParameters.mods = map.mapParameters.mods
            newGameScreen.updateRuleset()
            newGameScreen.updateTables()
        }
        return mapFileSelectBox
    }


    // The SelectBox auto displays the text a object.toString(), which on the FileHandle itself includes the folder path.
    //  So we wrap it in another object with a custom toString()
    class FileHandleWrapper(val fileHandle: FileHandle) {
        override fun toString() = fileHandle.name()
    }
}