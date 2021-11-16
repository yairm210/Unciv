package com.unciv.scripting

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.scripting.utils.ScriptingApiEnums
import com.unciv.scripting.utils.InstanceFactories
import com.unciv.scripting.utils.InstanceRegistry
import com.unciv.ui.worldscreen.WorldScreen


/**
 * Holds references to all internal game data that the console has access to.
 *
 * Also where to put any future `PlayerAPI`, `CheatAPI`, `ModAPI`, etc.
 *
 * For `LuaScriptingBackend`, `UpyScriptingBackend`, `QjsScriptingBackend`, etc, the hierarchy of data under this class definition should probably directly mirror the wrappers in the namespace exposed to the scripting language.
 *
 * `WorldScreen` gives access to `UnitTable.selectedUnit`, `MapHolder.selectedTile`, etc. Useful for contextual operations.
 */
class ScriptingScope(
        var civInfo: CivilizationInfo?,
        var gameInfo: GameInfo?,
        var uncivGame: UncivGame?,
        var worldScreen: WorldScreen?
        //mapEditorScreen
        //val _availableNames = listOf("civInfo", "gameInfo", "uncivGame", "worldScreen", "apiHelpers") // Nope. Annotate instead.
    ) {

    val apiHelpers = ApiHelpers(this)

    class ApiHelpers(val scriptingScope: ScriptingScope) {
        val isInGame: Boolean
            get() = (scriptingScope.civInfo != null && scriptingScope.gameInfo != null && scriptingScope.uncivGame != null)
        val Factories = InstanceFactories
        val Enums = ScriptingApiEnums
        val registeredInstances = InstanceRegistry()
        fun unchanged(obj: Any?) = obj //Debug/dev identity function for both Kotlin and scripts. Check if value survives serialization, force something to be added to ScriptingProtocol.instanceSaver, etc.
        fun printLn(msg: Any?) = println(msg)
        //fun readLine
        //Return a line from the main game process's STDIN.
        fun toString(obj: Any?) = obj.toString()
        //fun typeOf(obj: Any?) = obj::class.simpleName
        //fun typeOfQualified(obj: Any?) = obj::class.qualifiedName
    }

}
//worldScreen.bottomUnitTable.selectedCity.cityConstructions.purchaseConstruction("Missionary", -1, False, apiHelpers.Enums.Stat.statsUsableToBuy[4])
