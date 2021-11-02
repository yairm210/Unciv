package com.unciv.scripting

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.UncivGame
import com.unciv.ui.worldscreen.WorldScreen

class ScriptingScope(
        var civInfo: CivilizationInfo?,
        var gameInfo: GameInfo?,
        var uncivGame: UncivGame?,
        var worldScreen: WorldScreen?
    ) {
    val isInGame: Boolean
        get() = (civInfo != null && gameInfo != null && uncivGame != null)
    // Holds references to all internal game data that the console has access to.
    // Mostly `.civInfo`/.`gameInfo`, but could be cool to E.G. allow loading and making saves through CLI/API too.
    // Also where to put any `PlayerAPI`, `CheatAPI`, `ModAPI`, etc.
    // For `LuaScriptingBackend`, `UpyScriptingBackend`, `QjsScriptingBackend`, etc, the hierarchy of data under this class definition should probably directly mirror the wrappers in the namespace exposed to the scripting language.
    // `WorldScreen` would give access to `UnitTable.selectedUnit`, `MapHolder.selectedTile`, etc.
}
