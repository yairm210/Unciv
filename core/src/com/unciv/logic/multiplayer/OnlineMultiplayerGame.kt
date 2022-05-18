package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.GameSaver
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerGameSaver
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.utils.isLargerThan
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference


/** @see getUpdateThrottleInterval */
private const val DROPBOX_THROTTLE_INTERVAL = 8L
/** @see getUpdateThrottleInterval */
private const val CUSTOM_SERVER_THROTTLE_INTERVAL = 1L

class OnlineMultiplayerGame(
    val fileHandle: FileHandle,
    var preview: GameInfoPreview? = null,
    lastOnlineUpdate: Instant? = null
) {
    private val lastOnlineUpdate: AtomicReference<Instant?> = AtomicReference(lastOnlineUpdate)
    val lastUpdate: Instant
        get() {
            val lastFileUpdateTime = Instant.ofEpochMilli(fileHandle.lastModified())
            val lastOnlineUpdateTime = lastOnlineUpdate.get()
            return if (lastOnlineUpdateTime == null || lastFileUpdateTime.isLargerThan(lastOnlineUpdateTime)) {
                lastFileUpdateTime
            } else {
                lastOnlineUpdateTime
            }
        }
    val name get() = fileHandle.name()
    var error: Exception? = null

    init {
        if (preview == null) {
            try {
                loadPreviewFromFile()
            } catch (e: Exception) {
                error = e
            }
        }
    }

    private fun loadPreviewFromFile(): GameInfoPreview {
        val previewFromFile = GameSaver.loadGamePreviewFromFile(fileHandle)
        preview = previewFromFile
        return previewFromFile
    }

    private fun shouldUpdate(lastUpdateTime: Instant?): Boolean =
        preview == null || error != null || lastUpdateTime == null || Duration.between(lastUpdateTime, Instant.now()).isLargerThan(getUpdateThrottleInterval())

    /**
     * Fires: [MultiplayerGameUpdateStarted], [MultiplayerGameUpdated], [MultiplayerGameUpdateUnchanged], [MultiplayerGameUpdateErrored]
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun requestUpdate(forceUpdate: Boolean = false) {
        fun alwaysUpdate(instant: Instant?): Boolean = true
        val shouldUpdateFun = if (forceUpdate) ::alwaysUpdate else ::shouldUpdate
        val onUnchanged = { GameUpdateResult.UNCHANGED }
        val onError = { e: Exception ->
            error = e
            GameUpdateResult.FAILURE
        }
        postCrashHandlingRunnable { EventBus.send(MultiplayerGameUpdateStarted(name)) }
        val updateResult = safeUpdateIf(lastOnlineUpdate, shouldUpdateFun, ::update, onUnchanged, onError)
        when (updateResult) {
            GameUpdateResult.UNCHANGED, GameUpdateResult.CHANGED -> error = null
            else -> {}
        }
        val updateEvent = when (updateResult) {
            GameUpdateResult.CHANGED -> MultiplayerGameUpdated(name, preview!!)
            GameUpdateResult.FAILURE -> MultiplayerGameUpdateErrored(name, error!!)
            GameUpdateResult.UNCHANGED -> MultiplayerGameUpdateUnchanged(name)
        }
        postCrashHandlingRunnable { EventBus.send(updateEvent) }
    }

    private suspend fun update(): GameUpdateResult {
        val curPreview = if (preview != null) preview!! else loadPreviewFromFile()
        val newPreview = OnlineMultiplayerGameSaver().tryDownloadGamePreview(curPreview.gameId)
        if (newPreview.turns == curPreview.turns && newPreview.currentPlayer == curPreview.currentPlayer) return GameUpdateResult.UNCHANGED
        GameSaver.saveGame(newPreview, fileHandle)
        preview = newPreview
        return GameUpdateResult.CHANGED
    }

    fun doManualUpdate(gameInfo: GameInfoPreview) {
        lastOnlineUpdate.set(Instant.now())
        error = null
        preview = gameInfo
    }

    override fun equals(other: Any?): Boolean = other is OnlineMultiplayerGame && fileHandle == other.fileHandle
    override fun hashCode(): Int = fileHandle.hashCode()
}

private enum class GameUpdateResult {
    CHANGED, UNCHANGED, FAILURE
}

/**
 * How often games can be checked for remote updates. More attempted checks within this time period will do nothing.
 */
private fun getUpdateThrottleInterval(): Duration {
    val isDropbox = UncivGame.Current.settings.multiplayerServer == Constants.dropboxMultiplayerServer
    return Duration.ofSeconds(if (isDropbox) DROPBOX_THROTTLE_INTERVAL else CUSTOM_SERVER_THROTTLE_INTERVAL)
}
