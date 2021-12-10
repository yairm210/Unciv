package com.unciv.scripting.api

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.mapeditor.MapEditorScreen
import com.unciv.ui.worldscreen.WorldScreen


/**
 * Holds references to all internal game data that scripting backends have access to.
 *
 * Also where to put any future PlayerAPI, CheatAPI, ModAPI, etc.
 *
 * For LuaScriptingBackend, UpyScriptingBackend, QjsScriptingBackend, etc, the hierarchy of data under this class definition should probably directly mirror the wrappers in the namespace exposed to running scripts.
 *
 * WorldScreen gives access to UnitTable.selectedUnit, MapHolder.selectedTile, etc. Useful for contextual operations.
 *
 * The members of this class and its nested classes should be designed for use by running scripts, not for implementing the protocol or API of scripting backends.
 * E.G.: If you need access to a file to build the scripting environment, then add it to ScriptingEngineConstants.json instead of using apiHelpers.assetFileB64. If you need access to some new type of property, then geneneralize it as much as possible and add an IPC request type for it in ScriptingProtocol.kt or add support for it in Reflection.kt.
 * In Python terms, that means that magic methods all directly send and parse IPC packets, while running scripts transparently use those magic methods to access the functions here.
 * API calls are for running scripts, and may be less stable. Building the scripting environment itself should be done directly using the IPC protocol and other lower-level constructs.
 *
 * To reduce the chance of E.G. name collisions in .apiHelpers.registeredInstances, or one misbehaving mod breaking everything by unassigning .gameInfo, different ScriptingState()s should each have their own ScriptingScope().
 */
object ScriptingScope
        // This entire API should still be considered unstable. It may be drastically changed at any time.

        //If this is going to be exposed to downloaded mods, then every declaration here, as well as *every* declaration that is safe for scripts to have access to, should probably be whitelisted with annotations and checked or errored at the point of reflection.
    {
    var civInfo: CivilizationInfo? = null
    var gameInfo: GameInfo? = null
    var uncivGame: UncivGame? = null
    var worldScreen: WorldScreen? = null
    var mapEditorScreen: MapEditorScreen? = null

    val Unciv = ScriptingApiUnciv

    val apiHelpers = ScriptingApiHelpers

    val modApiHelpers = ScriptingModApiHelpers

    val apiExecutionContext = ScriptingApiExecutionContext

    // TODO: Some way to clear the instancesaver?

}

// Does having one state manage multiple backends that all share the same scope really make sense? Mod handler dispatch, callbacks, etc might all be easier if the multi-backend functionality of ScriptingState were implemented only for ConsoleScreen.
// ScriptingState also helps separate , keep the shared ScriptingScope between all of them (such that it only needs to be updated once on game context changes), and update
