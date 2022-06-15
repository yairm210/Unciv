package com.unciv.ui.mapeditor

import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.newgamescreen.GameOptionsTable
import com.unciv.ui.newgamescreen.IPreviousScreen
import com.unciv.ui.newgamescreen.PlayerPickerTable
import com.unciv.ui.pickerscreens.PickerScreen

/**
 * As of MapEditor V2, the editor no longer deals with GameParameters, **only** with MapParameters,
 * and has no need of this. There are no instantiations. The class, stripped, is left in as skeleton
 * so its references in PlayerPickerTable and NationPickerPopup can stay. They have been effectively dead even before.
 *
 * This [Screen] is used for editing game parameters when scenario is edited/created in map editor.
 * Implements [IPreviousScreen] for compatibility with [PlayerPickerTable], [GameOptionsTable]
 * Uses [PlayerPickerTable] and [GameOptionsTable] to change local [gameSetupInfo]. Upon confirmation
 * updates [mapEditorScreen] and switches to it.
 * @param [mapEditorScreen] previous screen from map editor.
 */
@Deprecated("As of 4.0.x")
class GameParametersScreen(var mapEditorScreen: MapEditorScreen): IPreviousScreen, PickerScreen(disableScroll = true) {
    override var gameSetupInfo = GameSetupInfo(mapParameters = mapEditorScreen.newMapParameters)
    override var ruleset = RulesetCache.getComplexRuleset(gameSetupInfo.gameParameters)
}
