package com.unciv.scripting.api

import com.unciv.ui.utils.UncivDateFormat

object ScriptingApiUnciv {
    // These are also all accessible by qualified name through ScriptingApiJvmHelpers.
    // But the functionality they provide is basic enough that it probably merits explicitly exposing.
    val GameSaver = com.unciv.logic.GameSaver
    val GameStarter = com.unciv.logic.GameStarter
    val HexMath = com.unciv.logic.HexMath
    val MapSaver = com.unciv.logic.MapSaver
    val UncivDateFormat = com.unciv.ui.utils.UncivDateFormat
}
