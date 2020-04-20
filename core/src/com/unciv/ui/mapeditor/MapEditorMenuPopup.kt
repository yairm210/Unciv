package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Json
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.logic.map.RoadStatus
import com.unciv.models.translations.tr
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.mainmenu.DropBox
import kotlin.concurrent.thread

class MapEditorMenuPopup(mapEditorScreen: MapEditorScreen): Popup(mapEditorScreen){
    init{
        val mapNameEditor = TextField(mapEditorScreen.mapName, skin)
        add(mapNameEditor).fillX().row()
        mapNameEditor.selectAll()
        mapNameEditor.maxLength = 240       // A few under max for most filesystems
        mapEditorScreen.stage.keyboardFocus = mapNameEditor

        val newMapButton = "New map".toTextButton()
        newMapButton.onClick {
            UncivGame.Current.setScreen(NewMapScreen())
        }
        add(newMapButton).row()

        val clearCurrentMapButton = "Clear current map".toTextButton()
        clearCurrentMapButton.onClick {
            for(tileGroup in mapEditorScreen.mapHolder.tileGroups.values)
            {
                val tile = tileGroup.tileInfo
                tile.baseTerrain=Constants.ocean
                tile.terrainFeature=null
                tile.naturalWonder=null
                tile.resource=null
                tile.improvement=null
                tile.improvementInProgress=null
                tile.roadStatus=RoadStatus.None
                tile.setTransients()

                tileGroup.update()
            }
        }
        add(clearCurrentMapButton).row()

        val saveMapButton = "Save map".toTextButton()
        saveMapButton.onClick {
            mapEditorScreen.tileMap.mapParameters.name=mapEditorScreen.mapName
            mapEditorScreen.tileMap.mapParameters.type=MapType.custom
            thread ( name = "SaveMap" ) {
                try {
                    MapSaver.saveMap(mapEditorScreen.mapName, mapEditorScreen.tileMap)
                    UncivGame.Current.setWorldScreen()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    Gdx.app.postRunnable {
                        val cantLoadGamePopup = Popup(mapEditorScreen)
                        cantLoadGamePopup.addGoodSizedLabel("It looks like your map can't be saved!").row()
                        cantLoadGamePopup.addCloseButton()
                        cantLoadGamePopup.open(force = true)
                    }
                }
            }
        }
        saveMapButton.isEnabled = mapNameEditor.text.isNotEmpty()
        add(saveMapButton).row()
        mapNameEditor.addListener {
            mapEditorScreen.mapName = mapNameEditor.text
            saveMapButton.isEnabled = mapNameEditor.text.isNotEmpty()
            true
        }

        val copyMapAsTextButton = "Copy to clipboard".toTextButton()
        copyMapAsTextButton.onClick {
            val json = Json().toJson(mapEditorScreen.tileMap)
            val base64Gzip = Gzip.zip(json)
            Gdx.app.clipboard.contents =  base64Gzip
        }
        add(copyMapAsTextButton).row()

        val loadMapButton = "Load map".toTextButton()
        loadMapButton.onClick {
            UncivGame.Current.setScreen(LoadMapScreen(mapEditorScreen.tileMap))
        }
        add(loadMapButton).row()

        val uploadMapButton = "Upload map".toTextButton()
        uploadMapButton.onClick {
            thread(name="MapUpload") {
                try {
                    val gzippedMap = Gzip.zip(Json().toJson(mapEditorScreen.tileMap))
                    DropBox.uploadFile("/Maps/" + mapEditorScreen.mapName, gzippedMap)

                    remove()
                    val uploadedSuccessfully = Popup(screen)
                    uploadedSuccessfully.addGoodSizedLabel("Map uploaded successfully!").row()
                    uploadedSuccessfully.addCloseButton()
                    uploadedSuccessfully.open()
                } catch (ex: Exception) {
                    remove()
                    val couldNotUpload = Popup(screen)
                    couldNotUpload.addGoodSizedLabel("Could not upload map!").row()
                    couldNotUpload.addCloseButton()
                    couldNotUpload.open()
                }
            }
        }
        add(uploadMapButton).row()


        val exitMapEditorButton = "Exit map editor".toTextButton()
        exitMapEditorButton.onClick { UncivGame.Current.setWorldScreen(); mapEditorScreen.dispose() }
        add(exitMapEditorButton ).row()

        val closeOptionsButton = Constants.close.toTextButton()
        closeOptionsButton.onClick { close() }
        add(closeOptionsButton).row()
    }
}
