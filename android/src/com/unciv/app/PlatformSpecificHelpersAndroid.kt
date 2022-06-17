package com.unciv.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.ui.utils.GeneralPlatformSpecificHelpers
import kotlin.concurrent.thread

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

    override fun hasDisplayCutout() = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            activity.display?.cutout != null
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.cutout != null
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
            activity.window.decorView.rootWindowInsets.displayCutout != null
        else -> false
    }

    override fun toggleDisplayCutout(androidCutout: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
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

    override fun handleUncaughtThrowable(ex: Throwable): Boolean {
        thread { throw ex } // this will kill the app but report the exception to the Google Play Console if the user allows it
        return true
    }

    override fun addImprovements(textField: TextField): TextField {
        return TextfieldImprovements.add(textField)
    }
}
