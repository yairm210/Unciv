package com.unciv

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.unciv.logic.civilization.Civilization
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.UndoHandler.Companion.clearUndoCheckpoints
import com.unciv.ui.screens.worldscreen.worldmap.WorldMapHolder
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.unit.UnitTable
import yairm210.purity.annotations.Readonly

object GUI {

    fun setUpdateWorldOnNextRender() {
        UncivGame.Current.worldScreen?.shouldUpdate = true
    }

    fun pushScreen(screen: BaseScreen) {
        UncivGame.Current.pushScreen(screen)
    }

    fun resetToWorldScreen() {
        UncivGame.Current.resetToWorldScreen()
    }

    @Readonly
    fun getSettings(): GameSettings {
        return UncivGame.Current.settings
    }

    fun isWorldLoaded(): Boolean {
        return UncivGame.Current.worldScreen != null
    }

    fun isMyTurn(): Boolean {
        if (!UncivGame.isCurrentInitialized() || !isWorldLoaded()) return false
        return UncivGame.Current.worldScreen!!.isPlayersTurn
    }

    fun isAllowedChangeState(): Boolean {
        return UncivGame.Current.worldScreen!!.canChangeState
    }

    fun getWorldScreen(): WorldScreen {
        return UncivGame.Current.worldScreen!!
    }

    fun getWorldScreenIfActive(): WorldScreen? {
        return UncivGame.Current.getWorldScreenIfActive()
    }

    fun getMap(): WorldMapHolder {
        return UncivGame.Current.worldScreen!!.mapHolder
    }

    fun getUnitTable(): UnitTable {
        return UncivGame.Current.worldScreen!!.bottomUnitTable
    }

    fun getViewingPlayer(): Civilization {
        return UncivGame.Current.worldScreen!!.viewingCiv
    }

    fun getSelectedPlayer(): Civilization {
        return UncivGame.Current.worldScreen!!.selectedCiv
    }

    /** Disable Undo (as in: forget the way back, but allow future undo checkpoints) */
    fun clearUndoCheckpoints() {
        UncivGame.Current.worldScreen?.clearUndoCheckpoints()
    }

    /** Fallback in case you have no easy access to a BaseScreen that knows which Ruleset Civilopedia should display.
     *  If at all possible, use [BaseScreen.openCivilopedia] instead. */
    fun openCivilopedia(link: String = "") {
        UncivGame.Current.screen?.openCivilopedia(link)
    }

    private var keyboardAvailableCache: Boolean? = null
    /** Tests availability of a physical keyboard - cached (connecting a keyboard while the game is running won't be recognized until relaunch) */
    val keyboardAvailable: Boolean
        get() {
            // defer decision if Gdx.input not yet initialized
            if (keyboardAvailableCache == null && Gdx.input != null)
                keyboardAvailableCache = Gdx.input.isPeripheralAvailable(Input.Peripheral.HardwareKeyboard)
            return keyboardAvailableCache ?: false
        }

}
