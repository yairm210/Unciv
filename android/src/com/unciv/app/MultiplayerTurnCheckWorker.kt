package com.unciv.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.DefaultAndroidFiles
import com.unciv.logic.GameInfo
import com.unciv.logic.UncivFiles
import com.unciv.logic.multiplayer.Multiplayer.ServerData
import com.unciv.logic.multiplayer.MultiplayerGame
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerFiles
import com.unciv.models.metadata.GameSettingsMultiplayer
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit


class MultiplayerTurnCheckWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "UNCIV_MULTIPLAYER_TURN_CHECKER_WORKER"
        const val LOG_TAG = "Unciv turn checker"
        const val CLIPBOARD_EXTRA = "CLIPBOARD_STRING"
        const val NOTIFICATION_ID_SERVICE = 1
        const val NOTIFICATION_ID_INFO = 2

        // Notification Channels can't be modified after creation.
        // Therefore Unciv needs to create new ones and delete previously used ones.
        // Add old channel names here when replacing them with new ones below.
        private val HISTORIC_NOTIFICATION_CHANNELS = arrayOf("UNCIV_NOTIFICATION_CHANNEL_SERVICE")

        private const val NOTIFICATION_CHANNEL_ID_INFO = "UNCIV_NOTIFICATION_CHANNEL_INFO"
        private const val NOTIFICATION_CHANNEL_ID_SERVICE = "UNCIV_NOTIFICATION_CHANNEL_SERVICE_02"

        private const val FAIL_COUNT = "FAIL_COUNT"
        private const val USER_ID = "USER_ID"
        private const val CONFIGURED_DELAY = "CONFIGURED_DELAY"
        private const val PERSISTENT_NOTIFICATION_ENABLED = "PERSISTENT_NOTIFICATION_ENABLED"

        fun enqueue(appContext: Context, delay: Duration, inputData: Data) {

            val constraints = Constraints.Builder()
                    // If no internet is available, worker waits before becoming active.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val checkTurnWork = OneTimeWorkRequestBuilder<MultiplayerTurnCheckWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(delay.seconds, TimeUnit.SECONDS)
                    .addTag(WORK_TAG)
                    .setInputData(inputData)
                    .build()

            WorkManager.getInstance(appContext).enqueue(checkTurnWork)
        }

        /**
         * Notification Channel for 'It's your turn' and error notifications.
         *
         * This code is necessary for API level >= 26
         * API level < 26 does not support Notification Channels
         * For more infos: https://developer.android.com/training/notify-user/channels.html#CreateChannel
         */
        fun createNotificationChannelInfo(appContext: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val name = appContext.resources.getString(R.string.Notify_ChannelInfo_Short)
            val descriptionText = appContext.resources.getString(R.string.Notify_ChannelInfo_Long)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID_INFO, name, importance)
            mChannel.description = descriptionText
            mChannel.setShowBadge(true)
            mChannel.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC

            val notificationManager = appContext.getSystemService(AndroidApplication.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }

        /**
         * Notification Channel for persistent service notification.
         *
         * This code is necessary for API level >= 26
         * API level < 26 does not support Notification Channels
         * For more infos: https://developer.android.com/training/notify-user/channels.html#CreateChannel
         */
        fun createNotificationChannelService(appContext: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val name = appContext.resources.getString(R.string.Notify_ChannelService_Short)
            val descriptionText = appContext.resources.getString(R.string.Notify_ChannelService_Long)
            val importance = NotificationManager.IMPORTANCE_MIN
            val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID_SERVICE, name, importance)
            mChannel.setShowBadge(false)
            mChannel.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            mChannel.description = descriptionText

            val notificationManager = appContext.getSystemService(AndroidApplication.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }

        /**
         * The persistent notification is purely for informational reasons.
         * It is not technically necessary for the Worker, since it is not a Service.
         */
        fun showPersistentNotification(appContext: Context, lastTimeChecked: String, checkPeriod: Duration) {
            val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) FLAG_IMMUTABLE else 0) or
                FLAG_UPDATE_CURRENT
            val pendingIntent: PendingIntent =
                    Intent(appContext, AndroidLauncher::class.java).let { notificationIntent ->
                        PendingIntent.getActivity(appContext, 0, notificationIntent, flags)
                    }

            val notification: NotificationCompat.Builder = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID_SERVICE)
                    .setPriority(NotificationManagerCompat.IMPORTANCE_MIN) // it's only a status
                    .setContentTitle(appContext.resources.getString(R.string.Notify_Persist_Short) + " " + lastTimeChecked)
                    .setStyle(NotificationCompat.BigTextStyle()
                            .bigText(appContext.resources.getString(R.string.Notify_Persist_Long_P1) + " " +
                                    appContext.resources.getString(R.string.Notify_Persist_Long_P2) + " " + checkPeriod.seconds / 60f + " "
                                    + appContext.resources.getString(R.string.Notify_Persist_Long_P3)
                                    + " " + appContext.resources.getString(R.string.Notify_Persist_Long_P4)))
                    .setSmallIcon(R.drawable.uncivnotification)
                    .setContentIntent(pendingIntent)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setShowWhen(false)

            with(NotificationManagerCompat.from(appContext)) {
                notify(NOTIFICATION_ID_INFO, notification.build())
            }
        }

        fun notifyUserAboutTurn(applicationContext: Context, gameName: String, gameId: String) {
            Log.i(LOG_TAG, "notifyUserAboutTurn ${gameName}")
            val intent = Intent(applicationContext, AndroidLauncher::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("https://unciv.app/multiplayer?id=${gameId}")
            }
            val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) FLAG_IMMUTABLE else 0) or
                    FLAG_UPDATE_CURRENT
            val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, flags)

            val contentTitle = applicationContext.resources.getString(R.string.Notify_YourTurn_Short)
            val notification: NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID_INFO)
                    .setPriority(NotificationManagerCompat.IMPORTANCE_HIGH) // people are waiting!
                    .setContentTitle(contentTitle)
                    .setContentText(applicationContext.resources.getString(R.string.Notify_YourTurn_Long).replace("[gameName]", gameName))
                    .setTicker(contentTitle)
                    // without at least vibrate, some Android versions don't show a heads-up notification
                    .setDefaults(DEFAULT_VIBRATE)
                    .setLights(Color.YELLOW, 300, 100)
                    .setSmallIcon(R.drawable.uncivnotification)
                    .setContentIntent(pendingIntent)
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .setOngoing(false)

            with(NotificationManagerCompat.from(applicationContext)) {
                notify(NOTIFICATION_ID_INFO, notification.build())
            }
        }

        fun startTurnChecker(applicationContext: Context, files: UncivFiles, currentGameInfo: GameInfo, settings: GameSettingsMultiplayer) {
            Log.i(LOG_TAG, "startTurnChecker")
            val multiplayerGames = runBlocking {
                files.loadMultiplayerGames()
            }

            Log.d(LOG_TAG, "start gameNames: ${multiplayerGames.map { it.name }}")

            if (currentGameInfo.currentPlayerId == settings.userId) {
                // May be useful to remind a player that he forgot to complete his turn.
                val currentGame = multiplayerGames.find { it.gameId == currentGameInfo.gameId }
                // If reading the game status file threw an exception, gameIndex will be -1
                if (currentGame != null) {
                    notifyUserAboutTurn(applicationContext, currentGame.name, currentGame.gameId)
                }
            } else {
                val inputData = workDataOf(
                    FAIL_COUNT to 0,
                    USER_ID to settings.userId,
                    CONFIGURED_DELAY to settings.turnCheckerDelay.seconds,
                    PERSISTENT_NOTIFICATION_ENABLED to settings.turnCheckerPersistentNotificationEnabled
                )

                if (settings.turnCheckerPersistentNotificationEnabled) {
                    showPersistentNotification(applicationContext, "—", settings.turnCheckerDelay)
                }
                Log.d(LOG_TAG, "startTurnChecker enqueue")
                // Initial check always happens after a minute, ignoring delay config. Better user experience this way.
                enqueue(applicationContext, Duration.ofMinutes(1), inputData)
            }
        }

        /**
         * Necessary for Multiplayer Turner Checker, starting with Android Oreo
         */
        fun createNotificationChannels(appContext: Context) {
            createNotificationChannelInfo(appContext)
            createNotificationChannelService(appContext)
            destroyOldChannels(appContext)
        }

        /**
         *  Notification Channels can't be modified after creation.
         *  Therefore Unciv needs to create new ones and delete legacy ones.
         */
        private fun destroyOldChannels(appContext: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val notificationManager = appContext.getSystemService(AndroidApplication.NOTIFICATION_SERVICE) as NotificationManager
            HISTORIC_NOTIFICATION_CHANNELS.forEach {
                if (null != notificationManager.getNotificationChannel(it)) {
                    notificationManager.deleteNotificationChannel(it)
                }
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
        files = UncivFiles(gdxFiles, null, true)
    }

    override fun doWork(): Result = runBlocking {
        Log.i(LOG_TAG, "doWork")
        val showPersistNotific = inputData.getBoolean(PERSISTENT_NOTIFICATION_ENABLED, true)
        val configuredDelay = getConfiguredDelay(inputData)

        try {
            val multiplayerGames = runBlocking {
                files.loadMultiplayerGames()
            }
            Log.d(LOG_TAG, "doWork gameNames: ${multiplayerGames.map { it.name }}")


            var foundGame: MultiplayerGame? = null
            for (game in multiplayerGames){

                if (notFoundRemotely[game.gameId] == true) {
                    // Since the save was not found on the remote server, we do not need to check again, it'll only fail again.
                    continue
                }

                try {
                    Log.d(LOG_TAG, "doWork download ${game.gameId}")
                    val newStatus = MultiplayerFiles().tryDownloadGameStatus(game.serverData, game.gameId)
                    game.doManualUpdate(newStatus, false)
                    Log.d(LOG_TAG, "doWork download ${game.gameId} done")

                    if (newStatus.currentPlayerId == inputData.getString(USER_ID)!! && foundGame == null) {
                        foundGame = game
                    }
                } catch (ex: FileStorageRateLimitReached) {
                    Log.i(LOG_TAG, "doWork FileStorageRateLimitReached ${ex.message}")
                    // We just break here as the configured delay is probably enough to wait for the rate limit anyway
                    break
                } catch (ex: FileNotFoundException){
                    Log.i(LOG_TAG, "doWork FileNotFoundException ${ex.message}")
                    // FileNotFoundException is thrown by OnlineMultiplayer().tryDownloadGameStatus(gameId)
                    // and indicates that there is no game status file present for this game
                    // in the dropbox so we should not check for this game in the future anymore
                    notFoundRemotely[game.gameId] = true
                }
            }

            Log.i(LOG_TAG, "doWork save games")
            files.saveMultiplayerGames(multiplayerGames)
            Log.i(LOG_TAG, "doWork save games done")

            if (foundGame != null){
                notifyUserAboutTurn(applicationContext, foundGame.name, foundGame.gameId)
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
                if (showPersistNotific) { showPersistentNotification(applicationContext, applicationContext.resources.getString(R.string.Notify_Error_Retrying), configuredDelay) }
                // If check fails, retry in one minute.
                // Makes sense, since checks only happen if Internet is available in principle.
                // Therefore a failure means either a problem with the GameInfo or with Dropbox.
                val inputDataFailIncrease = Data.Builder().putAll(inputData).putInt(FAIL_COUNT, failCount + 1).build()
                enqueue(applicationContext, Duration.ofMinutes(1), inputDataFailIncrease)
            }
        } catch (outOfMemory: OutOfMemoryError){ // no point in trying multiple times if this was an oom error
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
                Intent(applicationContext, CopyToClipboardReceiver::class.java).putExtra(CLIPBOARD_EXTRA, stackTraceString)
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
