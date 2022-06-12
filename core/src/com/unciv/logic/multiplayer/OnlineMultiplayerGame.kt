package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerGameSaver
import com.unciv.ui.utils.extensions.isLargerThan
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import com.unciv.utils.concurrency.withGLContext
import com.unciv.utils.debug
import kotlinx.coroutines.coroutineScope
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference


/** @see getUpdateThrottleInterval */
private const val DROPBOX_THROTTLE_PERIOD = 8L
/** @see getUpdateThrottleInterval */
private const val CUSTOM_SERVER_THROTTLE_PERIOD = 1L

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
        val previewFromFile = UncivGame.Current.gameSaver.loadGamePreviewFromFile(fileHandle)
        preview = previewFromFile
        return previewFromFile
    }

    private fun needsUpdate(): Boolean = preview == null || error != null

    /**
     * Fires: [MultiplayerGameUpdateStarted], [MultiplayerGameUpdated], [MultiplayerGameUpdateUnchanged], [MultiplayerGameUpdateFailed]
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun requestUpdate(forceUpdate: Boolean = false) = coroutineScope {
        val onUnchanged = { GameUpdateResult.UNCHANGED }
        val onError = { e: Exception ->
            error = e
            GameUpdateResult.FAILURE
        }
        launchOnGLThread {
            EventBus.send(MultiplayerGameUpdateStarted(name))
        }
        val throttleInterval = if (forceUpdate) Duration.ZERO else getUpdateThrottleInterval()
        val updateResult = if (forceUpdate || needsUpdate()) {
            attemptAction(lastOnlineUpdate, onUnchanged, onError, ::update)
        } else {
            throttle(lastOnlineUpdate, throttleInterval, onUnchanged, onError, ::update)
        }
        when (updateResult) {
            GameUpdateResult.UNCHANGED, GameUpdateResult.CHANGED -> error = null
            else -> {}
        }
        val updateEvent = when (updateResult) {
            GameUpdateResult.CHANGED -> MultiplayerGameUpdated(name, preview!!)
            GameUpdateResult.FAILURE -> MultiplayerGameUpdateFailed(name, error!!)
            GameUpdateResult.UNCHANGED -> MultiplayerGameUpdateUnchanged(name, preview!!)
        }
        launchOnGLThread {
            EventBus.send(updateEvent)
        }
    }

    private suspend fun update(): GameUpdateResult {
        val curPreview = if (preview != null) preview!! else loadPreviewFromFile()
        val newPreview = OnlineMultiplayerGameSaver().tryDownloadGamePreview(curPreview.gameId)
        if (newPreview.turns == curPreview.turns && newPreview.currentPlayer == curPreview.currentPlayer) return GameUpdateResult.UNCHANGED
        UncivGame.Current.gameSaver.saveGame(newPreview, fileHandle)
        preview = newPreview
        return GameUpdateResult.CHANGED
    }

    suspend fun doManualUpdate(gameInfo: GameInfoPreview) {
        debug("Doing manual update of game %s", gameInfo.gameId)
        lastOnlineUpdate.set(Instant.now())
        error = null
        preview = gameInfo
        Concurrency.runOnGLThread {
            EventBus.send(MultiplayerGameUpdated(name, gameInfo))
        }
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
    return Duration.ofSeconds(if (OnlineMultiplayer.usesCustomServer()) CUSTOM_SERVER_THROTTLE_PERIOD else DROPBOX_THROTTLE_PERIOD)
}
