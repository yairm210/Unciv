package com.unciv.logic.multiplayer

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import yairm210.purity.annotations.Readonly

/**
 * Closes mid-turn undo/reload abuse when [UniqueType.DisableUndo] is enabled in online multiplayer.
 *
 * Uploads the current game state to the multiplayer server after player actions (debounced),
 * so rejoining downloads the post-action state instead of the turn-start snapshot.
 */
object MultiplayerTurnIntegrity {

    private const val UPLOAD_DEBOUNCE_MS = 1500L
    private var uploadJob: Job? = null

    @Readonly
    fun isEnabled(gameInfo: GameInfo): Boolean =
        gameInfo.gameParameters.isOnlineMultiplayer
            && gameInfo.ruleset.modOptions.hasUnique(UniqueType.DisableUndo)

    /** Always prefer the remote save over a local copy when integrity mode is on. */
    @Readonly
    fun mustDownloadFromServer(gameInfo: GameInfo): Boolean = isEnabled(gameInfo)

    /**
     * Schedule a debounced mid-turn upload of [worldScreen]'s current game.
     * No-op unless integrity mode is active and it is this client's turn to play.
     */
    fun scheduleUpload(worldScreen: WorldScreen) {
        val gameInfo = worldScreen.gameInfo
        if (!isEnabled(gameInfo)) return
        if (!worldScreen.isPlayersTurn || !worldScreen.canChangeState) return

        uploadJob?.cancel()
        uploadJob = Concurrency.run("MidTurnMpUpload") {
            delay(UPLOAD_DEBOUNCE_MS)
            try {
                // Clone so a concurrent UI change cannot mutate the payload mid-upload
                val toUpload = worldScreen.gameInfo.clone()
                toUpload.setTransients()
                toUpload.isUpToDate = true
                UncivGame.Current.onlineMultiplayer.updateGame(toUpload)
            } catch (ex: Exception) {
                // Never break gameplay if upload fails; next action / end turn can retry
                Log.error("Mid-turn multiplayer upload failed", ex)
            }
        }
    }
}
