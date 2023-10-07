package com.unciv.app.turncheck

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.DEFAULT_VIBRATE
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.DefaultAndroidFiles
import com.unciv.UncivGame
import com.unciv.app.AndroidLauncher
import com.unciv.app.CopyToClipboardReceiver
import com.unciv.app.R
import com.unciv.app.turncheck.Common.CLIPBOARD_EXTRA
import com.unciv.app.turncheck.Common.LOG_TAG
import com.unciv.app.turncheck.Common.NOTIFICATION_CHANNEL_ID_INFO
import com.unciv.app.turncheck.Common.NOTIFICATION_ID_INFO
import com.unciv.app.turncheck.Common.NOTIFICATION_ID_SERVICE
import com.unciv.app.turncheck.Common.WORK_TAG
import com.unciv.app.turncheck.Common.notifyUserAboutTurn
import com.unciv.app.turncheck.Common.showPersistentNotification
import com.unciv.logic.GameInfo
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.models.metadata.GameSettingsMultiplayer
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.time.Duration
import java.util.GregorianCalendar
import java.util.concurrent.TimeUnit

/**
 * Active poll-based multiplayer turn checker for APIv0 and APIv1
 */
class WorkerV1(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    companion object {
        private const val FAIL_COUNT = "FAIL_COUNT"
        private const val GAME_ID = "GAME_ID"
        private const val GAME_NAME = "GAME_NAME"
        private const val USER_ID = "USER_ID"
        private const val CONFIGURED_DELAY = "CONFIGURED_DELAY"
        private const val PERSISTENT_NOTIFICATION_ENABLED = "PERSISTENT_NOTIFICATION_ENABLED"
        private const val FILE_STORAGE = "FILE_STORAGE"
        private const val AUTH_HEADER = "AUTH_HEADER"

        private val constraints = Constraints.Builder()
            // If no internet is available, worker waits before becoming active.
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(appContext: Context, delay: Duration, inputData: Data) {
            val checkTurnWork = OneTimeWorkRequestBuilder<WorkerV1>()
                    .setConstraints(constraints)
                    .setInitialDelay(delay.seconds, TimeUnit.SECONDS)
                    .addTag(WORK_TAG)
                    .setInputData(inputData)
                    .build()

            WorkManager.getInstance(appContext).enqueue(checkTurnWork)
        }

        fun startTurnChecker(applicationContext: Context, files: UncivFiles, currentGameInfo: GameInfo, settings: GameSettingsMultiplayer) {
            Log.i(LOG_TAG, "startTurnChecker")

            // Games that haven't been updated in a week are considered stale
            val oneWeekWorthOfMilliseconds = 1000*60*60*24*7
            val gameFiles = files.getMultiplayerSaves()
                .filter { it.lastModified() > System.currentTimeMillis() - oneWeekWorthOfMilliseconds }
            val gameIds = Array(gameFiles.count()) {""}
            val gameNames = Array(gameFiles.count()) {""}

            var count = 0
            for (gameFile in gameFiles) {
                try {
                    val gamePreview = files.loadGamePreviewFromFile(gameFile)
                    gameIds[count] = gamePreview.gameId
                    gameNames[count] = gameFile.name()
                    count++
                } catch (_: Throwable) {
                    //only loadGamePreviewFromFile can throw an exception
                    //nothing will be added to the arrays if it fails
                    //just skip one file
                }
            }
            if (count==0) return // no games to update

            Log.d(LOG_TAG, "start gameNames: ${gameNames.contentToString()}")

            if (currentGameInfo.getCurrentPlayerCivilization().playerId == settings.userId) {
                // May be useful to remind a player that he forgot to complete his turn.
                val gameIndex = gameIds.indexOf(currentGameInfo.gameId)
                // If reading the preview file threw an exception, gameIndex will be -1
                if (gameIndex != -1) {
                    notifyUserAboutTurn(applicationContext, Pair(gameNames[gameIndex], gameIds[gameIndex]))
                }
            } else {
                val inputData = workDataOf(Pair(FAIL_COUNT, 0), Pair(GAME_ID, gameIds), Pair(
                    GAME_NAME, gameNames),
                        Pair(USER_ID, settings.userId), Pair(CONFIGURED_DELAY, settings.turnCheckerDelay.seconds),
                        Pair(PERSISTENT_NOTIFICATION_ENABLED, settings.turnCheckerPersistentNotificationEnabled),
                        Pair(FILE_STORAGE, settings.server),
                        Pair(AUTH_HEADER, settings.getAuthHeader()))

                if (settings.turnCheckerPersistentNotificationEnabled) {
                    showPersistentNotification(applicationContext, "—", settings.turnCheckerDelay)
                }
                Log.d(LOG_TAG, "startTurnChecker enqueue")
                // Initial check always happens after a minute, ignoring delay config. Better user experience this way.
                enqueue(applicationContext, Duration.ofMinutes(1), inputData)
            }
        }

        private fun getConfiguredDelay(inputData: Data): Duration {
            val delay = inputData.getLong(CONFIGURED_DELAY, Duration.ofMinutes(5).seconds)
            return Duration.ofSeconds(delay)
        }
    }

    /**
     * Contains gameIds that, if true is set for them, we will not check for updates again. The data here only lives until the user enters Unciv again,
     * so if the user changes their remote server, this will be reset and we will check for turns again.
     */
    private val notFoundRemotely = mutableMapOf<String, Boolean>()

    private val files: UncivFiles
    init {
        // We can't use Gdx.files since that is only initialized within a com.badlogic.gdx.backends.android.AndroidApplication.
        // Worker instances may be stopped & recreated by the Android WorkManager, so no AndroidApplication and thus no Gdx.files available
        val gdxFiles = DefaultAndroidFiles(applicationContext.assets, ContextWrapper(applicationContext), true)
        // GDX's AndroidFileHandle uses Gdx.files internally, so we need to set that to our new instance
        Gdx.files = gdxFiles
        files = UncivFiles(gdxFiles)
    }

    override fun doWork(): Result = runBlocking {
        Log.i(LOG_TAG, "doWork")
        val showPersistNotific = inputData.getBoolean(PERSISTENT_NOTIFICATION_ENABLED, true)
        val configuredDelay = getConfiguredDelay(inputData)
        val fileStorage = inputData.getString(FILE_STORAGE)
        val authHeader = inputData.getString(AUTH_HEADER)!!

        try {
            val gameIds = inputData.getStringArray(GAME_ID)!!
            val gameNames = inputData.getStringArray(GAME_NAME)!!
            Log.d(LOG_TAG, "doWork gameNames: ${gameNames.contentToString()}")
            // We only want to notify the user or update persisted notification once but still want
            // to download all games to update the files so we save the first one we find
            var foundGame: Pair<String, String>? = null

            for (idx in gameIds.indices){
                val gameId = gameIds[idx]
                //gameId could be an empty string if startTurnChecker fails to load all files
                if (gameId.isEmpty())
                    continue

                if (notFoundRemotely[gameId] == true) {
                    // Since the save was not found on the remote server, we do not need to check again, it'll only fail again.
                    continue
                }

                try {
                    Log.d(LOG_TAG, "doWork download $gameId")
                    val gamePreview = UncivGame.Current.onlineMultiplayer.multiplayerServer.tryDownloadGamePreview(gameId)
                    //val gamePreview = OnlineMultiplayerServer(fileStorage, mapOf("Authorization" to authHeader)).tryDownloadGamePreview(gameId)
                    Log.d(LOG_TAG, "doWork download $gameId done")
                    val currentTurnPlayer = gamePreview.getCivilization(gamePreview.currentPlayer)

                    //Save game so MultiplayerScreen gets updated
                    /*
                    I received multiple reports regarding broken save games.
                    All of them where missing a few thousand chars at the end of the save game.
                    I assume this happened because the TurnCheckerWorker gets canceled by the AndroidLauncher
                    while saves are getting saved right here.
                    Lets hope it works with gamePreview as they are a lot smaller and faster to save
                     */
                    Log.i(LOG_TAG, "doWork save gameName: ${gameNames[idx]}")
                    files.saveGame(gamePreview, gameNames[idx])
                    Log.i(LOG_TAG, "doWork save ${gameNames[idx]} done")

                    if (currentTurnPlayer.playerId == inputData.getString(USER_ID)!! && foundGame == null) {
                        foundGame = Pair(gameNames[idx], gameIds[idx])
                    }
                } catch (ex: FileStorageRateLimitReached) {
                    Log.i(LOG_TAG, "doWork FileStorageRateLimitReached ${ex.message}")
                    // We just break here as the configured delay is probably enough to wait for the rate limit anyway
                    break
                } catch (ex: FileNotFoundException){
                    Log.i(LOG_TAG, "doWork FileNotFoundException ${ex.message}")
                    // FileNotFoundException is thrown by OnlineMultiplayer().tryDownloadGamePreview(gameId)
                    // and indicates that there is no game preview present for this game
                    // in the dropbox so we should not check for this game in the future anymore
                    notFoundRemotely[gameId] = true
                }
            }

            if (foundGame != null){
                notifyUserAboutTurn(applicationContext, foundGame)
                with(NotificationManagerCompat.from(applicationContext)) {
                    cancel(NOTIFICATION_ID_SERVICE)
                }
            } else {
                if (showPersistNotific) { updatePersistentNotification(inputData) }
                // We have to reset the fail counter since no exception appeared
                val inputDataFailReset = Data.Builder().putAll(inputData).putInt(FAIL_COUNT, 0).build()
                Log.d(LOG_TAG, "doWork enqueue")
                enqueue(applicationContext, configuredDelay, inputDataFailReset)
            }

        } catch (ex: Exception) {
            Log.e(LOG_TAG, "doWork ${ex::class.simpleName}: ${ex.message}")
            val failCount = inputData.getInt(FAIL_COUNT, 0)
            if (failCount > 3) {
                showErrorNotification(getStackTraceString(ex))
                with(NotificationManagerCompat.from(applicationContext)) {
                    cancel(NOTIFICATION_ID_SERVICE)
                }
                return@runBlocking Result.failure()
            } else {
                if (showPersistNotific) { showPersistentNotification(applicationContext, applicationContext.resources.getString(
                    R.string.Notify_Error_Retrying
                ), configuredDelay) }
                // If check fails, retry in one minute.
                // Makes sense, since checks only happen if Internet is available in principle.
                // Therefore a failure means either a problem with the GameInfo or with Dropbox.
                val inputDataFailIncrease = Data.Builder().putAll(inputData).putInt(FAIL_COUNT, failCount + 1).build()
                enqueue(applicationContext, Duration.ofMinutes(1), inputDataFailIncrease)
            }
        } catch (outOfMemory: OutOfMemoryError){ // no point in trying multiple times if this was an oom error
            Log.e(LOG_TAG, "doWork ${outOfMemory::class.simpleName}: ${outOfMemory.message}")
            return@runBlocking Result.failure()
        }
        return@runBlocking Result.success()
    }

    private fun getStackTraceString(ex: Exception): String {
        val writer: Writer = StringWriter()
        ex.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun updatePersistentNotification(inputData: Data) {
        val cal = GregorianCalendar.getInstance()
        val hour = cal.get(GregorianCalendar.HOUR_OF_DAY).toString()
        var minute = cal.get(GregorianCalendar.MINUTE).toString()
        if (minute.length == 1) {
            minute = "0$minute"
        }
        val displayTime = "$hour:$minute"

        showPersistentNotification(applicationContext, displayTime, getConfiguredDelay(inputData))
    }

    private fun showErrorNotification(stackTraceString: String) {
        val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) FLAG_IMMUTABLE else 0) or
                FLAG_UPDATE_CURRENT
        val pendingLaunchGameIntent: PendingIntent =
                Intent(applicationContext, AndroidLauncher::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(applicationContext, 0, notificationIntent, flags)
                }

        val pendingCopyClipboardIntent: PendingIntent =
                Intent(applicationContext, CopyToClipboardReceiver::class.java).putExtra(
                    CLIPBOARD_EXTRA, stackTraceString)
                        .let { notificationIntent -> PendingIntent.getBroadcast(applicationContext,0, notificationIntent, flags)
                }

        val notification: NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID_INFO)
                .setPriority(NotificationManagerCompat.IMPORTANCE_DEFAULT) // No direct user action expected
                .setContentTitle(applicationContext.resources.getString(R.string.Notify_Error_Short))
                .setContentText(applicationContext.resources.getString(R.string.Notify_Error_Long))
                .setSmallIcon(R.drawable.uncivnotification)
                // without at least vibrate, some Android versions don't show a heads-up notification
                .setDefaults(DEFAULT_VIBRATE)
                .setLights(Color.YELLOW, 300, 100)
                .setContentIntent(pendingLaunchGameIntent)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setOngoing(false)
                .addAction(0, applicationContext.resources.getString(R.string.Notify_Error_CopyAction), pendingCopyClipboardIntent)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(NOTIFICATION_ID_INFO, notification.build())
        }
    }
}
