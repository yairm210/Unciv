package com.unciv.ui.consolescreen

import com.badlogic.gdx.Input
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.scripting.ScriptingState
import com.unciv.scripting.api.ScriptingScope
import com.unciv.ui.consolescreen.ConsoleScreen
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.ui.mapeditor.MapEditorScreen

// TODO: Disable in Multiplayer WorldScreen.
// https://github.com/yairm210/Unciv/pull/5125/files

//Interface that extends BaseScreen with methods for exposing the global ConsoleScreen.
interface IConsoleScreenAccessible {

    val BaseScreen.consoleScreen: ConsoleScreen // TODO: Oh, apparently don't need to explicitly refer to this? Change in other extension functions too, I guess.
        get() = this.game.consoleScreen


    //Set the console screen tilde hotkey.
    fun BaseScreen.setOpenConsoleScreenHotkey() {
        this.keyPressDispatcher[Input.Keys.GRAVE] = { this.game.setConsoleScreen() }
    }

    //Set the console screen to return to the right screen when closed.

    //Defaults to setting the game's screen to this instance. Can also use a lambda, for E.G. WorldScreen and UncivGame.setWorldScreen().
    fun BaseScreen.setConsoleScreenCloseAction(closeAction: (() -> Unit)? = null) {
        // TODO: This can probably be combined with setOpenConsoleScreenHotkey.
        this.consoleScreen.closeAction = closeAction ?: { this.game.setScreen(this) }
    }

    //Extension method to update scripting API scope variables that are expected to change over the lifetime of a ScriptingState.

    //Unprovided arguments default to null. This way, screens inheriting this interface don't need to explicitly clear everything they don't have. They only need to provide what they do have.

    //@param gameInfo Active GameInfo.
    //@param civInfo Active CivilizationInfo.
    //@param worldScreen Active WorldScreen.
    fun BaseScreen.updateScriptingState( // TODO: Rename to setScriptingState
        gameInfo: GameInfo? = null,
        civInfo: CivilizationInfo? = null,
        worldScreen: WorldScreen? = null,
        mapEditorScreen: MapEditorScreen? = null
    ) {
        ScriptingScope.also {
            it.gameInfo = gameInfo
            it.civInfo = civInfo
            it.worldScreen = worldScreen
            it.mapEditorScreen = mapEditorScreen
        } // .apply errors on compile with "val cannot be reassigned".
    }

//    fun BaseScreen.updateScriptingState(){} // TODO: Same, but don't clear. Or not?
}
