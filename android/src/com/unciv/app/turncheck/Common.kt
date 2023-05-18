package com.unciv.app.turncheck

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.badlogic.gdx.backends.android.AndroidApplication
import com.unciv.app.AndroidLauncher
import com.unciv.app.R
import java.time.Duration
import java.util.UUID

/**
 * Collection of common utilities for [WorkerV1] and [WorkerV2]
 */
object Common {
    const val WORK_TAG = "UNCIV_MULTIPLAYER_TURN_CHECKER_WORKER"
    const val LOG_TAG = "Unciv turn checker"
    const val CLIPBOARD_EXTRA = "CLIPBOARD_STRING"
    const val NOTIFICATION_ID_SERVICE = 1
    const val NOTIFICATION_ID_INFO = 2

    // Notification Channels can't be modified after creation.
    // Therefore Unciv needs to create new ones and delete previously used ones.
    // Add old channel names here when replacing them with new ones below.
    private val HISTORIC_NOTIFICATION_CHANNELS = arrayOf("UNCIV_NOTIFICATION_CHANNEL_SERVICE")

    internal const val NOTIFICATION_CHANNEL_ID_INFO = "UNCIV_NOTIFICATION_CHANNEL_INFO"
    private const val NOTIFICATION_CHANNEL_ID_SERVICE = "UNCIV_NOTIFICATION_CHANNEL_SERVICE_02"

    /**
     * Notification Channel for 'It's your turn' and error notifications.
     *
     * This code is necessary for API level >= 26
     * API level < 26 does not support Notification Channels
     * For more infos: https://developer.android.com/training/notify-user/channels.html#CreateChannel
     */
    private fun createNotificationChannelInfo(appContext: Context) {
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
    private fun createNotificationChannelService(appContext: Context) {
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
        val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or
                PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent: PendingIntent =
                Intent(appContext, AndroidLauncher::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(appContext, 0, notificationIntent, flags)
                }

        val notification: NotificationCompat.Builder = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID_SERVICE)
            .setPriority(NotificationManagerCompat.IMPORTANCE_MIN) // it's only a status
            .setContentTitle(appContext.resources.getString(R.string.Notify_Persist_Short) + " " + lastTimeChecked)
            .setStyle(
                NotificationCompat.BigTextStyle()
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

    /**
     * Create a new notification to inform a user that its his turn in a specfic game
     *
     * The [game] is a pair of game name and game ID (which is a [UUID]).
     */
    fun notifyUserAboutTurn(applicationContext: Context, game: Pair<String, String>) {
        Log.i(LOG_TAG, "notifyUserAboutTurn ${game.first} (${game.second})")
        val intent = Intent(applicationContext, AndroidLauncher::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("https://unciv.app/multiplayer?id=${game.second}")
        }
        val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or
                PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, flags)

        val contentTitle = applicationContext.resources.getString(R.string.Notify_YourTurn_Short)
        val notification: NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID_INFO)
            .setPriority(NotificationManagerCompat.IMPORTANCE_HIGH) // people are waiting!
            .setContentTitle(contentTitle)
            .setContentText(applicationContext.resources.getString(R.string.Notify_YourTurn_Long).replace("[gameName]", game.first))
            .setTicker(contentTitle)
            // without at least vibrate, some Android versions don't show a heads-up notification
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setLights(Color.YELLOW, 300, 100)
            .setSmallIcon(R.drawable.uncivnotification)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setOngoing(false)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(NOTIFICATION_ID_INFO, notification.build())
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
}
