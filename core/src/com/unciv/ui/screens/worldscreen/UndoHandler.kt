package com.unciv.ui.screens.worldscreen

import com.unciv.utils.Concurrency
import yairm210.purity.annotations.Readonly

/** Encapsulates the Undo functionality.
 *
 *  Implementation is based on actively saving GameInfo clones and restoring them when needed.
 *  For now, there's only one single Undo level (but the class signature could easily support more).
 */
class UndoHandler(private val worldScreen: WorldScreen) {
    private var preActionGameInfo = worldScreen.gameInfo

    @Readonly fun canUndo() = preActionGameInfo != worldScreen.gameInfo && worldScreen.canChangeState

    fun recordCheckpoint() {
        preActionGameInfo = worldScreen.gameInfo.clone()
    }

    fun restoreCheckpoint() {
        Concurrency.run {
            // Most of the time we won't load this, so we only set transients once we see it's relevant
            preActionGameInfo.setTransients()
            preActionGameInfo.isUpToDate = worldScreen.gameInfo.isUpToDate  // Multiplayer!
            worldScreen.game.loadGame(preActionGameInfo)
        }
    }

    fun clearCheckpoints() {
        preActionGameInfo = worldScreen.gameInfo
    }

    /** Simple readability proxies so the caller can pretend the interface exists directly on WorldScreen (imports ugly but calls neat) */
    companion object {
        @Readonly fun WorldScreen.canUndo() = undoHandler.canUndo()
        fun WorldScreen.recordUndoCheckpoint() = undoHandler.recordCheckpoint()
        fun WorldScreen.restoreUndoCheckpoint() = undoHandler.restoreCheckpoint()
        fun WorldScreen.clearUndoCheckpoints() = undoHandler.clearCheckpoints()
    }
}
