package com.unciv.ui.mapeditor

import com.unciv.UncivGame
import com.unciv.logic.map.Scenario
import com.unciv.ui.newgamescreen.GameOptionsTable
import com.unciv.ui.newgamescreen.GameSetupInfo
import com.unciv.ui.newgamescreen.PlayerPickerTable
import com.unciv.ui.newgamescreen.IPreviousScreen
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
/**
 * This [Screen] is used for editing game parameters when scenario is edited/created in map editor.
 * Implements [PreviousScreenInterface] for compatibility with [PlayerPickerTable], [GameOptionsTable]
 * Uses [PlayerPickerTable] and [GameOptionsTable] to change local [gameSetupInfo]. Upon confirmation
 * updates [mapEditorScreen] and switches to it.
 * @param [mapEditorScreen] previous screen from map editor.
 */
class GameParametersScreen(var mapEditorScreen: MapEditorScreen): IPreviousScreen, PickerScreen() {

    override var gameSetupInfo: GameSetupInfo = mapEditorScreen.gameSetupInfo
    var playerPickerTable = PlayerPickerTable(this, this.gameSetupInfo.gameParameters)
    var gameOptionsTable = GameOptionsTable(mapEditorScreen.gameSetupInfo) { desiredCiv: String -> playerPickerTable.update(desiredCiv) }

    init {
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
            mapEditorScreen.mapHolder.updateTileGroups()
            UncivGame.Current.setScreen(mapEditorScreen)
            dispose()
        }
    }
}

