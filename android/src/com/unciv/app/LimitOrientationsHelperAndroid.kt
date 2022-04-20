package com.unciv.app

import android.app.Activity
import android.content.pm.ActivityInfo
import com.unciv.ui.utils.LimitOrientationsHelper

/** See also interface [LimitOrientationsHelper].
 *
 *  The Android implementation (currently the only one) effectively ends up doing
 *  [Activity.setRequestedOrientation]
 */
class LimitOrientationsHelperAndroid(private val activity: Activity) : LimitOrientationsHelper {
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
}
