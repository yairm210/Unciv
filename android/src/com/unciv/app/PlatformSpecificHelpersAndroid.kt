package com.unciv.app

import android.app.Activity
import android.content.pm.ActivityInfo
import com.unciv.ui.utils.GeneralPlatformSpecificHelpers
import com.unciv.UncivGame
import java.net.URL

/** See also interface [GeneralPlatformSpecificHelpers].
 *
 *  The Android implementation (currently the only one) effectively ends up doing
 *  [Activity.setRequestedOrientation]
 */
class PlatformSpecificHelpersAndroid(private val activity: Activity) : GeneralPlatformSpecificHelpers {

/*
Sources for Info about current orientation in case need:
        val windowManager = (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        val displayRotation = windowManager.defaultDisplay.rotation
        val currentOrientation = activity.resources.configuration.orientation
        const val ORIENTATION_UNDEFINED = 0
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
*/

    override fun allowPortrait(allow: Boolean) {
        val orientation = when {
            allow -> ActivityInfo.SCREEN_ORIENTATION_USER
            else -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        // Comparison ensures ActivityTaskManager.getService().setRequestedOrientation isn't called unless necessary
        if (activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
    }

    override fun isInternetConnected(): Boolean {
        return try {
            val multiplayerServer = UncivGame.Current.settings.multiplayerServer ?: "Dropbox"
            val u = URL(if (multiplayerServer != "Dropbox") multiplayerServer else "https://content.dropboxapi.com")
            val conn = u.openConnection()
            conn.connect()
            true
        } catch (ex: Throwable) {
            false
        }
    }
}
