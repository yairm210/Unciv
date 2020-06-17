package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.logic.map.Scenario
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.Player
import com.unciv.ui.newgamescreen.GameOptionsTable
import com.unciv.ui.newgamescreen.GameSetupInfo
import com.unciv.ui.newgamescreen.PlayerPickerTable
import com.unciv.ui.newgamescreen.PreviousScreenInterface
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import kotlin.concurrent.thread

class GameParametersScreen(var mapEditorScreen: MapEditorScreen): PreviousScreenInterface, PickerScreen() {

    override var gameSetupInfo: GameSetupInfo = mapEditorScreen.gameSetupInfo
    var playerPickerTable: PlayerPickerTable
    var gameOptionsTable: GameOptionsTable

    init {
        playerPickerTable = PlayerPickerTable(this, this.gameSetupInfo.gameParameters)
        gameOptionsTable = GameOptionsTable(mapEditorScreen.gameSetupInfo) { desiredCiv: String -> playerPickerTable.update(desiredCiv) }
        setDefaultCloseAction(mapEditorScreen)
        scrollPane.setScrollingDisabled(true, true)

        topTable.add(playerPickerTable)
        topTable.addSeparatorVertical()
        topTable.add(gameOptionsTable).row()
        rightSideButton.setText("OK")
        rightSideButton.onClick {
            mapEditorScreen.gameSetupInfo = gameSetupInfo
            mapEditorScreen.scenario = Scenario(mapEditorScreen.tileMap, mapEditorScreen.gameSetupInfo.gameParameters)
            mapEditorScreen.tileEditorOptions.update()
            UncivGame.Current.setScreen(mapEditorScreen)
            dispose()
        }
    }
}

