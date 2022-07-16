package com.unciv.logic.multiplayer

import com.unciv.logic.GameInfo
import com.unciv.logic.HasGameId
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.GameUpdateResult.Type.CHANGED
import com.unciv.logic.multiplayer.GameUpdateResult.Type.FAILURE
import com.unciv.logic.multiplayer.GameUpdateResult.Type.UNCHANGED
import com.unciv.logic.multiplayer.Multiplayer.GameStatus
import com.unciv.logic.multiplayer.Multiplayer.ServerData
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerFiles
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import com.unciv.utils.debug
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference


/** @see getUpdateThrottleInterval */
private const val DROPBOX_THROTTLE_PERIOD = 8L
/** @see getUpdateThrottleInterval */
private const val CUSTOM_SERVER_THROTTLE_PERIOD = 1L

/** Create a new multiplayer game. If [name] is null, will use the [gameId] as the name. */
class MultiplayerGame(
    override val gameId: String,
    serverData: ServerData,
    name: String? = null,
    status: GameStatus? = null
) : HasGameId {

    var serverData: ServerData = serverData
        set(value) {
            field = value
            Concurrency.run("MultiplayerGame Update") {
                requestUpdate(forceUpdate = true)
            }
        }

    var name: String = name ?: gameId
        set(newName) {
            val oldName = field
            field = newName
            Concurrency.runOnGLThread {
                EventBus.send(MultiplayerGameNameChanged(this@MultiplayerGame, oldName))
            }
        }

    var status = status
        private set

    @Transient
    val lastUpdate = AtomicReference<Instant?>()

    @Transient
    var error: Exception? = null
        private set

    @Suppress("unused") // used by json serialization
    private constructor() : this("", ServerData(null))

    init {
        if (status != null) {
            lastUpdate.set(Instant.now())
        }
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
        debug("Starting multiplayer game update for %s with id %s", name, gameId)
        launchOnGLThread {
            EventBus.send(MultiplayerGameUpdateStarted(this@MultiplayerGame))
        }
        val throttleInterval = if (forceUpdate) Duration.ZERO else getUpdateThrottleInterval()
        val updateResult = if (forceUpdate || needsUpdate()) {
            attemptAction(lastUpdate, onUnchanged, onError, ::update)
        } else {
            throttle(lastUpdate, throttleInterval, onUnchanged, onError, ::update)
        }
        val updateEvent = when {
            updateResult.type == CHANGED && updateResult.status != null -> {
                debug("Game update for %s with id %s had remote change", name, gameId)
                MultiplayerGameUpdated(this@MultiplayerGame, updateResult.status)
            }
            updateResult.type == FAILURE && updateResult.error != null -> {
                debug("Game update for %s with id %s failed: %s", name, gameId, updateResult.error)
                MultiplayerGameUpdateFailed(this@MultiplayerGame, updateResult.error)
            }
            updateResult.type == UNCHANGED && updateResult.status != null -> {
                debug("Game update for %s with id %s had no changes", name, gameId)
                error = null
                MultiplayerGameUpdateUnchanged(this@MultiplayerGame, updateResult.status)
            }
            else -> throw IllegalStateException()
        }
        launchOnGLThread {
            EventBus.send(updateEvent)
        }
    }

    private suspend fun update(): GameUpdateResult {
        val curStatus = status
        val newStatus = MultiplayerFiles().tryDownloadGameStatus(serverData, gameId)
        if (curStatus?.hasLatestGameState(newStatus) == true) return GameUpdateResult(UNCHANGED, curStatus)
        status = newStatus
        return GameUpdateResult(CHANGED, newStatus)
    }

    fun doManualUpdate(newStatus: GameStatus, sendEvent: Boolean = true) {
        debug("Doing manual update of game %s", gameId)
        lastUpdate.set(Instant.now())
        error = null
        status = newStatus
        if (sendEvent) {
            Concurrency.runOnGLThread {
                EventBus.send(MultiplayerGameUpdated(this@MultiplayerGame, newStatus))
            }
        }
    }
    fun doManualUpdate(gameInfo: GameInfo) {
        doManualUpdate(GameStatus(gameInfo))
    }

    /**
     * How often this game can be checked for remote updates. More attempted checks within this time period will do nothing.
     */
    private fun getUpdateThrottleInterval(): Duration {
        val throttleIntervalSeconds = if (serverData.type == Multiplayer.ServerType.CUSTOM) {
            CUSTOM_SERVER_THROTTLE_PERIOD
        } else {
            DROPBOX_THROTTLE_PERIOD
        }
        return Duration.ofSeconds(throttleIntervalSeconds)
    }

    override fun equals(other: Any?): Boolean = other is MultiplayerGame && gameId == other.gameId
    override fun hashCode(): Int = gameId.hashCode()
}

private class GameUpdateResult private constructor(
    val type: Type,
    val status: GameStatus?,
    val error: Exception?
) {
    constructor(type: Type, status: GameStatus) : this(type, status, null)
    constructor(error: Exception) : this(FAILURE, null, error)

    enum class Type { CHANGED, UNCHANGED, FAILURE }
}
