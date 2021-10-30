package com.unciv.console

import com.unciv.logic.civilization.CivilizationInfo

class ConsoleScope(val civInfo: CivilizationInfo) {
    // Holds references to all internal game data that the console has access to.
    // Currently just `.civInfo`, but could be cool to E.G. allow loading and making saves through CLI/API too.
}
