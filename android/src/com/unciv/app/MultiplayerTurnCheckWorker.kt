package com.unciv.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.DEFAULT_VIBRATE
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.badlogic.gdx.backends.android.AndroidApplication
import com.unciv.logic.GameInfo
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.worldscreen.mainmenu.OnlineMultiplayer
import java.util.*
import java.util.concurrent.TimeUnit


class MultiplayerTurnCheckWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "UNCIV_MULTIPLAYER_TURN_CHECKER_WORKER"
        const val NOTIFICATION_ID_SERVICE = 1
        const val NOTIFICATION_ID_INFO = 2

        // Notification Channels can't be modified after creation.
        // Therefore Unciv needs to create new ones and delete previously used ones.
        // Add old channel names here when replacing them with new ones below.
        private val HISTORIC_NOTIFICATION_CHANNELS = arrayOf("UNCIV_NOTIFICATION_CHANNEL_SERVICE")

        private const val NOTIFICATION_CHANNEL_ID_INFO = "UNCIV_NOTIFICATION_CHANNEL_INFO"
        private const val NOTIFICATION_CHANNEL_ID_SERVICE = "UNCIV_NOTIFICATION_CHANNEL_SERVICE_02"

        private const val FAIL_COUNT = "FAIL_COUNT"
        private const val GAME_ID = "GAME_ID"
        private const val USER_ID = "USER_ID"
        private const val CONFIGURED_DELAY = "CONFIGURED_DELAY"
        private const val PERSISTENT_NOTIFICATION_ENABLED = "PERSISTENT_NOTIFICATION_ENABLED"

        fun enqueue(appContext: Context,
                    delayInMinutes: Int, inputData: Data) {

            val constraints = Constraints.Builder()
                    // If no internet is available, worker waits before becoming active.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val checkTurnWork = OneTimeWorkRequestBuilder<MultiplayerTurnCheckWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(delayInMinutes.toLong(), TimeUnit.MINUTES)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Unciv Multiplayer Turn Checker Alert"
                val descriptionText = "Informs you when it's your turn in multiplayer."
                val importance = NotificationManager.IMPORTANCE_HIGH
                val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID_INFO, name, importance)
                mChannel.description = descriptionText
                mChannel.setShowBadge(true)
                mChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC)

                val notificationManager = appContext.getSystemService(AndroidApplication.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(mChannel)
            }
        }

        /**
         * Notification Channel for persistent service notification.
         *
         * This code is necessary for API level >= 26
         * API level < 26 does not support Notification Channels
         * For more infos: https://developer.android.com/training/notify-user/channels.html#CreateChannel
         */
        fun createNotificationChannelService(appContext: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Unciv Multiplayer Turn Checker Persistent Status"
                val descriptionText = "Shown constantly to inform you about background checking."
                val importance = NotificationManager.IMPORTANCE_MIN
                val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID_SERVICE, name, importance)
                mChannel.setShowBadge(false)
                mChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                mChannel.description = descriptionText

                val notificationManager = appContext.getSystemService(AndroidApplication.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(mChannel)
            }
        }

        /**
         * The persistent notification is purely for informational reasons.
         * It is not technically necessary for the Worker, since it is not a Service.
         */
        fun showPersistentNotification(appContext: Context, lastTimeChecked: String, checkPeriod: String) {
            val pendingIntent: PendingIntent =
                    Intent(appContext, AndroidLauncher::class.java).let { notificationIntent ->
                        PendingIntent.getActivity(appContext, 0, notificationIntent, 0)
                    }

            val notification: NotificationCompat.Builder = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID_SERVICE)
                    .setPriority(NotificationManagerCompat.IMPORTANCE_MIN) // it's only a status
                    .setContentTitle(appContext.resources.getString(R.string.Notify_Persist_Short) + " " + lastTimeChecked)
                    .setStyle(NotificationCompat.BigTextStyle()
                            .bigText(appContext.resources.getString(R.string.Notify_Persist_Long_P1) + " " +
                                    appContext.resources.getString(R.string.Notify_Persist_Long_P2) + checkPeriod
                                    + appContext.resources.getString(R.string.Notify_Persist_Long_P3)
                                    + " " + appContext.resources.getString(R.string.Notify_Persist_Long_P4)))
                    .setSmallIcon(R.drawable.uncivicon2)
                    .setContentIntent(pendingIntent)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setShowWhen(false)

            with(NotificationManagerCompat.from(appContext)) {
                notify(NOTIFICATION_ID_INFO, notification.build())
            }
        }

        fun notifyUserAboutTurn(applicationContext: Context) {
            val pendingIntent: PendingIntent =
                    Intent(applicationContext, AndroidLauncher::class.java).let { notificationIntent ->
                        PendingIntent.getActivity(applicationContext, 0, notificationIntent, 0)
                    }

            val contentTitle = applicationContext.resources.getString(R.string.Notify_YourTurn_Short)
            val notification: NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID_INFO)
                    .setPriority(NotificationManagerCompat.IMPORTANCE_HIGH) // people are waiting!
                    .setContentTitle(contentTitle)
                    .setContentText(applicationContext.resources.getString(R.string.Notify_YourTurn_Long))
                    .setTicker(contentTitle)
                    // without at least vibrate, some Android versions don't show a heads-up notification
                    .setDefaults(DEFAULT_VIBRATE)
                    .setLights(Color.YELLOW, 300, 100)
                    .setSmallIcon(R.drawable.uncivicon2)
                    .setContentIntent(pendingIntent)
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .setOngoing(false)

            with(NotificationManagerCompat.from(applicationContext)) {
                notify(NOTIFICATION_ID_INFO, notification.build())
            }
        }

        fun startTurnChecker(applicationContext: Context, gameInfo: GameInfo, settings: GameSettings) {
            if (gameInfo.currentPlayerCiv.playerId == settings.userId) {
                // May be useful to remind a player that he forgot to complete his turn.
                notifyUserAboutTurn(applicationContext)
            } else {
                val inputData = workDataOf(Pair(FAIL_COUNT, 0), Pair(GAME_ID, gameInfo.gameId),
                        Pair(USER_ID, settings.userId), Pair(CONFIGURED_DELAY, settings.multiplayerTurnCheckerDelayInMinutes),
                        Pair(PERSISTENT_NOTIFICATION_ENABLED, settings.multiplayerTurnCheckerPersistentNotificationEnabled))

                if (settings.multiplayerTurnCheckerPersistentNotificationEnabled) {
                    showPersistentNotification(applicationContext,
                            "—", settings.multiplayerTurnCheckerDelayInMinutes.toString())
                }
                // Initial check always happens after a minute, ignoring delay config. Better user experience this way.
                enqueue(applicationContext, 1, inputData)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = appContext.getSystemService(AndroidApplication.NOTIFICATION_SERVICE) as NotificationManager
                HISTORIC_NOTIFICATION_CHANNELS.forEach {
                    if (null != notificationManager.getNotificationChannel(it)) {
                        notificationManager.deleteNotificationChannel(it)
                    }
                }
            }
        }
    }

    override fun doWork(): Result {
        try {
            val latestGame = OnlineMultiplayer().tryDownloadGame(inputData.getString(GAME_ID)!!)
            if (latestGame.currentPlayerCiv.playerId == inputData.getString(USER_ID)!!) {
                notifyUserAboutTurn(applicationContext)
                with(NotificationManagerCompat.from(applicationContext)) {
                    cancel(NOTIFICATION_ID_SERVICE)
                }
            } else {
                updatePersistentNotification(inputData)
                // We have to reset the fail counter since no exception appeared
                val inputDataFailReset = Data.Builder().putAll(inputData).putInt(FAIL_COUNT, 0).build()
                enqueue(applicationContext, inputData.getInt(CONFIGURED_DELAY, 5), inputDataFailReset)
            }
        } catch (ex: Exception) {
            if (inputData.getInt(FAIL_COUNT, 0) > 3) {
                showErrorNotification()
                with(NotificationManagerCompat.from(applicationContext)) {
                    cancel(NOTIFICATION_ID_SERVICE)
                }
                return Result.failure()
            } else {
                // If check fails, retry in one minute.
                // Makes sense, since checks only happen if Internet is available in principle.
                // Therefore a failure means either a problem with the GameInfo or with Dropbox.
                val newErrorCount = inputData.getInt(FAIL_COUNT, 0) + 1
                val inputDataFailReset = Data.Builder().putAll(inputData).putInt(FAIL_COUNT, newErrorCount).build()
                enqueue(applicationContext, 1, inputDataFailReset)
                // Persistent Notification is not updated, because user may think check succeed.
            }
        }
        return Result.success()
    }

    private fun updatePersistentNotification(inputData: Data) {
        if (inputData.getBoolean(PERSISTENT_NOTIFICATION_ENABLED, true)) {
            val cal = GregorianCalendar.getInstance()
            val hour = cal.get(GregorianCalendar.HOUR_OF_DAY).toString()
            var minute = cal.get(GregorianCalendar.MINUTE).toString()
            if (minute.length == 1) {
                minute = "0$minute"
            }
            val displayTime = "$hour:$minute"

            showPersistentNotification(applicationContext, displayTime,
                    inputData.getInt(CONFIGURED_DELAY, 5).toString())
        }
    }

    private fun showErrorNotification() {
        val pendingIntent: PendingIntent =
                Intent(applicationContext, AndroidLauncher::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(applicationContext, 0, notificationIntent, 0)
                }

        val notification: NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID_INFO)
                .setPriority(NotificationManagerCompat.IMPORTANCE_DEFAULT) // No direct user action expected
                // These two strings must not be translated,
                // because the translation engine can be the cause of the exception.
                .setContentTitle(applicationContext.resources.getString(R.string.Notify_Error_Short))
                .setContentText(applicationContext.resources.getString(R.string.Notify_Error_Long))
                .setSmallIcon(R.drawable.uncivicon2)
                // without at least vibrate, some Android versions don't show a heads-up notification
                .setDefaults(DEFAULT_VIBRATE)
                .setLights(Color.YELLOW, 300, 100)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setOngoing(false)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(NOTIFICATION_ID_INFO, notification.build())
        }
    }
}