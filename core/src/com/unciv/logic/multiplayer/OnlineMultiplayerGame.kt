package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.GameUpdateResult.Type.CHANGED
import com.unciv.logic.multiplayer.GameUpdateResult.Type.FAILURE
import com.unciv.logic.multiplayer.GameUpdateResult.Type.UNCHANGED
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerServer
import com.unciv.ui.components.extensions.isLargerThan
import com.unciv.utils.debug
import com.unciv.utils.launchOnGLThread
import com.unciv.utils.withGLContext
import kotlinx.coroutines.coroutineScope
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
    fun getLastUpdate(): Instant {
        val lastFileUpdateTime = Instant.ofEpochMilli(fileHandle.lastModified())
        val lastOnlineUpdateTime = lastOnlineUpdate.get()
        return if (lastOnlineUpdateTime == null || lastFileUpdateTime.isLargerThan(lastOnlineUpdateTime)) {
            lastFileUpdateTime
        } else {
            lastOnlineUpdateTime
        }
    }
    val name = fileHandle.name()
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
        val previewFromFile = UncivGame.Current.files.loadGamePreviewFromFile(fileHandle)
        preview = previewFromFile
        return previewFromFile
    }

    private fun needsUpdate(): Boolean = preview == null || error != null

    /**
     * Fires: [MultiplayerGameUpdateStarted], [MultiplayerGameUpdated], [MultiplayerGameUpdateUnchanged], [MultiplayerGameUpdateFailed]
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws  MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun requestUpdate(forceUpdate: Boolean = false) = coroutineScope {
        val onUnchanged = { GameUpdateResult(UNCHANGED, preview!!) }
        val onError = { e: Exception ->
            error = e
            GameUpdateResult(e)
        }
        debug("Starting multiplayer game update for %s with id %s", name, preview?.gameId)
        launchOnGLThread {
            EventBus.send(MultiplayerGameUpdateStarted(name))
        }
        val throttleInterval = if (forceUpdate) Duration.ZERO else getUpdateThrottleInterval()
        val updateResult = if (forceUpdate || needsUpdate()) {
            attemptAction(lastOnlineUpdate, onUnchanged, onError, ::update)
        } else {
            throttle(lastOnlineUpdate, throttleInterval, onUnchanged, onError, ::update)
        }
        val updateEvent = when {
            updateResult.type == CHANGED && updateResult.status != null -> {
                debug("Game update for %s with id %s had remote change", name, updateResult.status.gameId)
                MultiplayerGameUpdated(name, updateResult.status)
            }
            updateResult.type == FAILURE && updateResult.error != null -> {
                debug("Game update for %s with id %s failed: %s", name, preview?.gameId, updateResult.error)
                MultiplayerGameUpdateFailed(name, updateResult.error)
            }
            updateResult.type == UNCHANGED && updateResult.status != null -> {
                debug("Game update for %s with id %s had no changes", name, updateResult.status.gameId)
                error = null
                MultiplayerGameUpdateUnchanged(name, updateResult.status)
            }
            else -> error("Unknown update event")
        }
        launchOnGLThread {
            EventBus.send(updateEvent)
        }
    }

    private suspend fun update(): GameUpdateResult {
        val curPreview = if (preview != null) preview!! else loadPreviewFromFile()
        val serverIdentifier = curPreview.gameParameters.multiplayerServerUrl
        val newPreview = OnlineMultiplayerServer(serverIdentifier).tryDownloadGamePreview(curPreview.gameId)
        if (newPreview.turns == curPreview.turns && newPreview.currentPlayer == curPreview.currentPlayer) return GameUpdateResult(UNCHANGED, newPreview)
        UncivGame.Current.files.saveGame(newPreview, fileHandle)
        preview = newPreview
        return GameUpdateResult(CHANGED, newPreview)
    }

    suspend fun doManualUpdate(gameInfo: GameInfoPreview) {
        debug("Doing manual update of game %s", gameInfo.gameId)
        lastOnlineUpdate.set(Instant.now())
        error = null
        preview = gameInfo
        withGLContext {
            EventBus.send(MultiplayerGameUpdated(name, gameInfo))
        }
    }

    override fun equals(other: Any?): Boolean = other is OnlineMultiplayerGame && fileHandle == other.fileHandle
    override fun hashCode(): Int = fileHandle.hashCode()
}

private class GameUpdateResult private constructor(
    val type: Type,
    val status: GameInfoPreview?,
    val error: Exception?
) {
    constructor(type: Type, status: GameInfoPreview) : this(type, status, null)
    constructor(error: Exception) : this(FAILURE, null, error)

    enum class Type { CHANGED, UNCHANGED, FAILURE }
}

/**
 * How often games can be checked for remote updates. More attempted checks within this time period will do nothing.
 */
private fun getUpdateThrottleInterval(): Duration {
    return Duration.ofSeconds(if (OnlineMultiplayer.usesCustomServer()) CUSTOM_SERVER_THROTTLE_PERIOD else DROPBOX_THROTTLE_PERIOD)
}
