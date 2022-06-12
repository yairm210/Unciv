package com.unciv.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import androidx.annotation.RequiresApi
import com.unciv.ui.utils.GeneralPlatformSpecificHelpers

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

    override fun hasDisplayCutout(): Boolean {
        val displayCutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.windowManager.defaultDisplay.cutout
        } else {
            TODO("VERSION.SDK_INT < Q")
        }
        return displayCutout != null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun toggleDisplayCutout(androidCutout: Boolean) {
        val layoutParams = activity.window.attributes
        if (androidCutout) {
            layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }
    }

    /**
     * On Android, local is some android-internal data directory which may or may not be accessible by the user.
     * External is probably on an SD-card or similar which is always accessible by the user.
     */
    override fun shouldPreferExternalStorage(): Boolean = true
}
