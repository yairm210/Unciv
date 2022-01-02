package com.unciv.ui.mapeditor

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.newgamescreen.GameOptionsTable
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.ui.newgamescreen.PlayerPickerTable
import com.unciv.ui.newgamescreen.IPreviousScreen
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*

/**
 * This [Screen] is used for editing game parameters when scenario is edited/created in map editor.
 * Implements [IPreviousScreen] for compatibility with [PlayerPickerTable], [GameOptionsTable]
 * Uses [PlayerPickerTable] and [GameOptionsTable] to change local [gameSetupInfo]. Upon confirmation
 * updates [mapEditorScreen] and switches to it.
 * @param [mapEditorScreen] previous screen from map editor.
 */
class GameParametersScreen(var mapEditorScreen: MapEditorScreen): IPreviousScreen, PickerScreen(disableScroll = true) {
    override var gameSetupInfo = GameSetupInfo(mapEditorScreen.gameSetupInfo)
    override var ruleset = RulesetCache.getComplexRuleset(gameSetupInfo.gameParameters.mods, gameSetupInfo.gameParameters.baseRuleset)
    var playerPickerTable = PlayerPickerTable(this, gameSetupInfo.gameParameters)
    var gameOptionsTable = GameOptionsTable(this) { desiredCiv: String -> playerPickerTable.update(desiredCiv) }


    init {
        setDefaultCloseAction(mapEditorScreen)

        topTable.add(AutoScrollPane(gameOptionsTable).apply { setScrollingDisabled(true, false) })
                .maxHeight(topTable.parent.height).width(stage.width / 2).padTop(20f).top()
        topTable.addSeparatorVertical()
        topTable.add(playerPickerTable).maxHeight(topTable.parent.height).width(stage.width / 2).padTop(20f).top()
        rightSideButton.setText(Constants.OK.tr())
        rightSideButton.onClick {
            mapEditorScreen.gameSetupInfo = gameSetupInfo
            mapEditorScreen.ruleset.clear()
            mapEditorScreen.ruleset.add(ruleset)
            mapEditorScreen.mapEditorOptionsTable.update()
            // Remove resources that are not applicable to this ruleset
            for(tile in mapEditorScreen.tileMap.values) {
                if (tile.resource != null && !ruleset.tileResources.containsKey(tile.resource!!))
                    tile.resource = null
                if (tile.improvement != null && !ruleset.tileImprovements.containsKey(tile.improvement!!))
                    tile.improvement = null
            }

            mapEditorScreen.mapHolder.updateTileGroups()
            UncivGame.Current.setScreen(mapEditorScreen)
            dispose()
        }
    }
}
