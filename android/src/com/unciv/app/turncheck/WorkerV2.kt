package com.unciv.app.turncheck

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.unciv.UncivGame
import com.unciv.app.turncheck.Common.LOG_TAG
import com.unciv.app.turncheck.Common.WORK_TAG
import com.unciv.logic.GameInfo
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.logic.multiplayer.apiv2.ApiV2
import com.unciv.logic.multiplayer.apiv2.IncomingChatMessage
import com.unciv.logic.multiplayer.apiv2.UpdateGameData
import com.unciv.logic.multiplayer.isUsersTurn
import com.unciv.models.metadata.GameSettingsMultiplayer
import com.unciv.utils.Concurrency
import com.unciv.utils.Dispatcher
import kotlinx.coroutines.Job
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Push-based multiplayer turn checker for APIv2
 */
class WorkerV2(appContext: Context, private val params: WorkerParameters) : CoroutineWorker(appContext, params) {
    @Deprecated("use withContext(...) inside doWork() instead.")
    override val coroutineContext = Dispatcher.DAEMON

    companion object {
        private const val USER_ID = "USER_ID"
        private const val CONFIGURED_DELAY = "CONFIGURED_DELAY"
        private const val MULTIPLAYER_SERVER = "MULTIPLAYER_SERVER"
        private const val PERSISTENT_NOTIFICATION_ENABLED = "PERSISTENT_NOTIFICATION_ENABLED"
        private const val UNIQUE_WORKER_V2_JOB_NAME = "UNIQUE_WORKER_V2_JOB_NAME"

        private var gameUUID: UUID? = null
        private var onlineMultiplayer: OnlineMultiplayer? = null

        /** Job for listening to parsed WebSocket events (created here) */
        private var eventJob: Job? = null
        /** Job for listening for raw incoming WebSocket packets (not created here, but in the [ApiV2]) */
        private var websocketJob: Job? = null

        fun start(applicationContext: Context, files: UncivFiles, currentGameInfo: GameInfo?, onlineMultiplayer: OnlineMultiplayer, settings: GameSettingsMultiplayer) {
            Log.d(LOG_TAG, "Starting V2 worker to listen for push notifications")
            if (currentGameInfo != null) {
                this.gameUUID = UUID.fromString(currentGameInfo.gameId)
            }
            this.onlineMultiplayer = onlineMultiplayer

            // May be useful to remind a player that he forgot to complete his turn
            if (currentGameInfo?.isUsersTurn() == true) {
                val name = currentGameInfo.gameId  // TODO: Lookup the name of the game
                Common.notifyUserAboutTurn(applicationContext, Pair(name, currentGameInfo.gameId))
            } else if (settings.turnCheckerPersistentNotificationEnabled) {
                Common.showPersistentNotification(
                    applicationContext,
                    "—",
                    settings.turnCheckerDelay
                )
            }

            val data = workDataOf(
                Pair(USER_ID, settings.userId),
                Pair(CONFIGURED_DELAY, settings.turnCheckerDelay.seconds),
                Pair(MULTIPLAYER_SERVER, settings.server),
                Pair(PERSISTENT_NOTIFICATION_ENABLED, settings.turnCheckerPersistentNotificationEnabled)
            )
            enqueue(applicationContext, data, 0)
        }

        private fun enqueue(applicationContext: Context, data: Data, delaySeconds: Long) {
            val worker = OneTimeWorkRequest.Builder(WorkerV2::class.java)
                .addTag(WORK_TAG)
                .setInputData(data)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            if (delaySeconds > 0) {
                // If no internet is available, worker waits before becoming active
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                worker.setConstraints(constraints)
            }
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(UNIQUE_WORKER_V2_JOB_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, worker.build())
            Log.d(LOG_TAG, "Enqueued APIv2-comptabile oneshot worker with delay of $delaySeconds seconds")
        }
    }

    private suspend fun checkTurns() {
        val channel = onlineMultiplayer?.api?.getWebSocketEventChannel()
        if (channel == null) {
            Log.w(LOG_TAG, "Failed to get an event channel for parsed WebSocket events!")
            return
        }
        try {
            while (true) {
                val event = channel.receive()
                Log.d(LOG_TAG, "Incoming channel event: $event")
                when (event) {
                    is IncomingChatMessage -> {
                        Log.i(LOG_TAG, "Incoming chat message! ${event.message}")
                    }
                    is UpdateGameData -> {
                        // TODO: The user here always receives a notification, even if somebody *else* completed their turn. Fix this!
                        Log.i(LOG_TAG, "Incoming game update! ${event.gameUUID} / ${event.gameDataID}")
                        // TODO: Resolve the name of the game by cached lookup instead of API query
                        val name = UncivGame.Current.onlineMultiplayer.api.game.head(event.gameUUID, suppress = true)?.name
                        Common.notifyUserAboutTurn(
                            applicationContext,
                            Pair(name ?: event.gameUUID.toString(), event.gameUUID.toString())
                        )
                        with(NotificationManagerCompat.from(applicationContext)) {
                            cancel(Common.NOTIFICATION_ID_SERVICE)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(LOG_TAG, "CheckTurns APIv2 failure: $t / ${t.localizedMessage}\n${t.stackTraceToString()}")
            channel.cancel()
            throw t
        }
    }

    override suspend fun doWork(): Result {
        try {
            Log.d(LOG_TAG, "Starting doWork for WorkerV2: $this")
            enqueue(applicationContext, params.inputData, params.inputData.getLong(CONFIGURED_DELAY, 600L))

            val ping = onlineMultiplayer?.api?.ensureConnectedWebSocket {
                Log.d(LOG_TAG, "WebSocket job $websocketJob, completed ${websocketJob?.isCompleted}, cancelled ${websocketJob?.isCancelled}, active ${websocketJob?.isActive}\nNew Job: $it")
                websocketJob = it
            }
            if (ping != null) {
                Log.d(LOG_TAG, "WebSocket ping took $ping ms")
            }

            if (eventJob == null || eventJob?.isActive == false || eventJob?.isCancelled == true) {
                val job = Concurrency.runOnNonDaemonThreadPool { checkTurns() }
                Log.d(LOG_TAG, "Added event job $job from $this (overwrite previous $eventJob)")
                eventJob = job
            } else {
                Log.d(LOG_TAG, "Event job $eventJob seems to be running, so everything is fine")
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error in $this: $e\nMessage: ${e.localizedMessage}\n${e.stackTraceToString()}\nWebSocket job: $websocketJob\nEvent job: $eventJob")
        }
        return Result.success()
    }
}
