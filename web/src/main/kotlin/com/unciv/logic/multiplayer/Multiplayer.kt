package com.unciv.logic.multiplayer

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class Multiplayer {
    val multiplayerServer = MultiplayerServer()
    val multiplayerFiles = MultiplayerFiles()

    val games: Set<MultiplayerGamePreview> get() = emptySet()
    val multiplayerGameUpdater: Job = NonCancellable

    suspend fun requestUpdate(forceUpdate: Boolean = false, doNotUpdate: List<MultiplayerGamePreview> = listOf()) {
        // No-op: online multiplayer is disabled on web phase-1.
    }

    suspend fun createGame(newGame: GameInfo) {
        throw MultiplayerFileNotFoundException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
    }

    suspend fun addGame(gameId: String, gameName: String? = null) {
        throw MultiplayerFileNotFoundException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
    }

    suspend fun resignPlayer(game: MultiplayerGamePreview, playerCivName: String): String {
        return "Online multiplayer is disabled on web phase-1"
    }

    suspend fun skipCurrentPlayerTurn(game: MultiplayerGamePreview, player: String): String? {
        return "Online multiplayer is disabled on web phase-1"
    }

    suspend fun downloadGame(game: MultiplayerGamePreview) {
        throw MultiplayerFileNotFoundException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
    }

    suspend fun downloadGame(gameId: String) {
        throw MultiplayerFileNotFoundException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
    }

    suspend fun downloadGame(gameInfo: GameInfo) {
        UncivGame.Current.loadGame(gameInfo)
    }

    suspend fun updateGame(gameInfo: GameInfo) {
        // No-op: online multiplayer is disabled on web phase-1.
    }

    fun hasLatestGameState(gameInfo: GameInfo, preview: GameInfoPreview): Boolean {
        return gameInfo.currentPlayer == preview.currentPlayer && gameInfo.turns == preview.turns
    }

    companion object {
        fun usesCustomServer() = UncivGame.Current.settings.multiplayer.getServer() != Constants.dropboxMultiplayerServer
        fun usesDropbox() = !usesCustomServer()
    }
}

suspend fun <T> throttle(
    lastSuccessfulExecution: AtomicReference<Instant?>,
    throttleInterval: Duration,
    onNoExecution: () -> T,
    onFailed: (Throwable) -> T,
    action: suspend () -> T,
): T {
    val lastExecution = lastSuccessfulExecution.get()
    val now = Instant.now()
    val shouldRunAction = lastExecution == null || Duration.between(lastExecution, now) > throttleInterval
    return if (shouldRunAction) {
        attemptAction(lastSuccessfulExecution, onNoExecution, onFailed, action)
    } else {
        onNoExecution()
    }
}

suspend fun <T> attemptAction(
    lastSuccessfulExecution: AtomicReference<Instant?>,
    onNoExecution: () -> T,
    onFailed: (Throwable) -> T = { throw it },
    action: suspend () -> T,
): T {
    val lastExecution = lastSuccessfulExecution.get()
    val now = Instant.now()
    return if (lastSuccessfulExecution.compareAndSet(lastExecution, now)) {
        try {
            action()
        } catch (e: Throwable) {
            lastSuccessfulExecution.compareAndSet(now, lastExecution)
            onFailed(e)
        }
    } else {
        onNoExecution()
    }
}

fun GameInfoPreview.isUsersTurn() =
    getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.getUserId()

fun GameInfo.isUsersTurn() =
    currentPlayer.isNotEmpty() && getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.getUserId()
