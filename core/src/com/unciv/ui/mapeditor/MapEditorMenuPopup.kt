package com.unciv.ui.mapeditor

import com.unciv.Constants
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.Player
import com.unciv.ui.utils.Popup
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toTextButton

class MapEditorMenuPopup(var mapEditorScreen: MapEditorScreen): Popup(mapEditorScreen){

    init {
        addNewMapButton()
        addSaveMapButton()
        addLoadMapButton()
        addExitMapEditorButton()
        addCloseOptionsButton()
    }

    private fun Popup.addNewMapButton() {
        val newMapButton = "New map".toTextButton()
        newMapButton.onClick {
            UncivGame.Current.setScreen(NewMapScreen())
        }
        add(newMapButton).row()
    }

    private fun Popup.addSaveMapButton() {
        val saveMapButton = "Save map".toTextButton()
        saveMapButton.onClick {
            mapEditorScreen.game.setScreen(SaveAndLoadMapScreen(mapEditorScreen.tileMap, true))
        }
        add(saveMapButton).row()
    }

    private fun Popup.addLoadMapButton() {
        val loadMapButton = "Load map".toTextButton()
        loadMapButton.onClick {
            UncivGame.Current.setScreen(SaveAndLoadMapScreen(mapEditorScreen.tileMap))
        }
        add(loadMapButton).row()
    }


    private fun Popup.addExitMapEditorButton() {
        val exitMapEditorButton = "Exit map editor".toTextButton()
        add(exitMapEditorButton).row()
        exitMapEditorButton.onClick { mapEditorScreen.game.setScreen(MainMenuScreen()); mapEditorScreen.dispose() }
    }

    private fun Popup.addCloseOptionsButton() {
        val closeOptionsButton = Constants.close.toTextButton()
        closeOptionsButton.onClick { close() }
        add(closeOptionsButton).row()
    }

    private fun getPlayersFromMap(tileMap: TileMap): ArrayList<Player> {
        val tilesWithStartingLocations = tileMap.values
                .filter { it.improvement != null && it.improvement!!.startsWith("StartingLocation ") }
        var players = ArrayList<Player>()
        for (tile in tilesWithStartingLocations) {
            players.add(Player().apply{
                chosenCiv = tile.improvement!!.removePrefix("StartingLocation ")
            })
        }
        return players
    }

}
