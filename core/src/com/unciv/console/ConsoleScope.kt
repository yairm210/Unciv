package com.unciv.console

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.UncivGame

class ConsoleScope(val civInfo: CivilizationInfo, val gameInfo: GameInfo, val uncivGame: UncivGame) {
    // Holds references to all internal game data that the console has access to.
    // Mostly `.civInfo`/.`gameInfo`, but could be cool to E.G. allow loading and making saves through CLI/API too.
    // Also where to put any `PlayerAPI`, `CheatAPI`, `ModAPI`, etc.
    // For `LuaConsoleBackend`, `UpyConsoleBackend`, `QjsConsoleBackend`, etc, this should probably directly mirror the wrappers exposed to the scripting language.
}
