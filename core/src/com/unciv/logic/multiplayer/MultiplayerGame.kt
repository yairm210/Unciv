package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.GameUpdateResult.Type.CHANGED
import com.unciv.logic.multiplayer.GameUpdateResult.Type.FAILURE
import com.unciv.logic.multiplayer.GameUpdateResult.Type.UNCHANGED
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerFiles
import com.unciv.ui.utils.extensions.isLargerThan
import com.unciv.utils.concurrency.launchOnGLThread
import com.unciv.utils.concurrency.withGLContext
import com.unciv.utils.debug
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference


/** @see getUpdateThrottleInterval */
private const val DROPBOX_THROTTLE_PERIOD = 8L
/** @see getUpdateThrottleInterval */
private const val CUSTOM_SERVER_THROTTLE_PERIOD = 1L

class MultiplayerGame(
    val fileHandle: FileHandle,
    var status: Multiplayer.GameStatus? = null,
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
        if (status == null) {
            try {
                loadStatusFromFile()
            } catch (e: Exception) {
                error = e
            }
        }
    }

    private fun loadStatusFromFile(): Multiplayer.GameStatus {
        val statusFromFile = UncivGame.Current.files.loadMultiplayerGameStatusFromFile(fileHandle)
        status = statusFromFile
        return statusFromFile
    }

    private fun needsUpdate(): Boolean = status == null || error != null

    /**
     * Fires: [MultiplayerGameUpdateStarted], [MultiplayerGameUpdated], [MultiplayerGameUpdateUnchanged], [MultiplayerGameUpdateFailed]
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun requestUpdate(forceUpdate: Boolean = false) = coroutineScope {
        val onUnchanged = { GameUpdateResult(UNCHANGED, status!!) }
        val onError = { e: Exception ->
            error = e
            GameUpdateResult(e)
        }
        debug("Starting multiplayer game update for %s with id %s", name, status?.gameId)
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
                debug("Game update for %s with id %s had remote change", name, status?.gameId)
                MultiplayerGameUpdated(name, updateResult.status)
            }
            updateResult.type == FAILURE && updateResult.error != null -> {
                debug("Game update for %s with id %s failed: %s", name, status?.gameId, updateResult.error)
                MultiplayerGameUpdateFailed(name, updateResult.error)
            }
            updateResult.type == UNCHANGED && updateResult.status != null -> {
                debug("Game update for %s with id %s had no changes", name, status?.gameId)
                error = null
                MultiplayerGameUpdateUnchanged(name, updateResult.status)
            }
            else -> throw IllegalStateException()
        }
        launchOnGLThread {
            EventBus.send(updateEvent)
        }
    }

    private suspend fun update(): GameUpdateResult {
        val curStatus = if (status != null) status!! else loadStatusFromFile()
        val newStatus = MultiplayerFiles().tryDownloadGameStatus(curStatus.gameId)
        if (curStatus.hasLatestGameState(newStatus)) return GameUpdateResult(UNCHANGED, newStatus)
        UncivGame.Current.files.saveMultiplayerGameStatus(newStatus, fileHandle)
        status = newStatus
        return GameUpdateResult(CHANGED, newStatus)
    }

    suspend fun doManualUpdate(newStatus: Multiplayer.GameStatus) {
        debug("Doing manual update of game %s", newStatus.gameId)
        lastOnlineUpdate.set(Instant.now())
        error = null
        status = newStatus
        withGLContext {
            EventBus.send(MultiplayerGameUpdated(name, newStatus))
        }
    }
    suspend fun doManualUpdate(gameInfo: GameInfo) {
        doManualUpdate(Multiplayer.GameStatus(gameInfo))
    }

    override fun equals(other: Any?): Boolean = other is MultiplayerGame && fileHandle == other.fileHandle
    override fun hashCode(): Int = fileHandle.hashCode()
}

private class GameUpdateResult private constructor(
    val type: Type,
    val status: Multiplayer.GameStatus?,
    val error: Exception?
) {
    constructor(type: Type, status: Multiplayer.GameStatus) : this(type, status, null)
    constructor(error: Exception) : this(FAILURE, null, error)

    enum class Type { CHANGED, UNCHANGED, FAILURE }
}

/**
 * How often games can be checked for remote updates. More attempted checks within this time period will do nothing.
 */
private fun getUpdateThrottleInterval(): Duration {
    return Duration.ofSeconds(if (Multiplayer.usesCustomServer()) CUSTOM_SERVER_THROTTLE_PERIOD else DROPBOX_THROTTLE_PERIOD)
}
