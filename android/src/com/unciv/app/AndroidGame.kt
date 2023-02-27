package com.unciv.app

import android.os.Build
import com.unciv.UncivGame

class AndroidGame(private val activity: AndroidLauncher) : UncivGame() {

    /*
    Sources for Info about current orientation in case need:
            val windowManager = (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            val displayRotation = windowManager.defaultDisplay.rotation
            val currentOrientation = activity.resources.configuration.orientation
            const val ORIENTATION_UNDEFINED = 0
            const val ORIENTATION_PORTRAIT = 1
            const val ORIENTATION_LANDSCAPE = 2
    */

    override fun hasDisplayCutout(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                activity.display?.cutout != null
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.cutout != null
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                activity.window.decorView.rootWindowInsets.displayCutout != null
            else -> false
        }
    }

    override fun allowPortrait(allow: Boolean) {
        activity.allowPortrait(allow)
    }

}
