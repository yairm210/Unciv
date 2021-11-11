package com.unciv.scripting

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.UncivGame
import com.unciv.scripting.utils.InstanceFactories
import com.unciv.ui.worldscreen.WorldScreen


class ScriptingScope(
        var civInfo: CivilizationInfo?,
        var gameInfo: GameInfo?,
        var uncivGame: UncivGame?,
        var worldScreen: WorldScreen?
    ) {
        
    val apiHelpers = ApiHelpers(this)

    class ApiHelpers(val scriptingScope: ScriptingScope) {
        val isInGame: Boolean
            get() = (scriptingScope.civInfo != null && scriptingScope.gameInfo != null && scriptingScope.uncivGame != null)
        val Factories = InstanceFactories
        val registeredInstances = mutableMapOf<String, Any?>()
        fun unchanged(obj: Any?) = obj //Debug/dev identity function for both Kotlin and scripts. Check if value survives serialization, force something to be added to ScriptingProtocol.instanceSaver, etc.
        fun printLn(msg: Any?) = println(msg)
        fun toString(obj: Any?) = obj.toString()
    }
        
    // Holds references to all internal game data that the console has access to.
    // Also where to put any `PlayerAPI`, `CheatAPI`, `ModAPI`, etc.
    // For `LuaScriptingBackend`, `UpyScriptingBackend`, `QjsScriptingBackend`, etc, the hierarchy of data under this class definition should probably directly mirror the wrappers in the namespace exposed to the scripting language.
    // `WorldScreen` gives access to `UnitTable.selectedUnit`, `MapHolder.selectedTile`, etc. Useful for contextual operations.
    
    // 
}
