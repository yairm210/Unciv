package com.unciv.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Display
import android.view.Display.Mode
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.utils.PlatformDisplay
import com.unciv.utils.ScreenMode
import com.unciv.utils.ScreenOrientation


class AndroidScreenMode(
    private val modeId: Int) : ScreenMode {
    private var name: String = "Default"

    @RequiresApi(Build.VERSION_CODES.M)
    constructor(mode: Mode) : this(mode.modeId) {
        name = "${mode.physicalWidth}x${mode.physicalHeight} (${mode.refreshRate.toInt()}HZ)"
    }

    override fun getId(): Int {
        return modeId
    }

    override fun toString(): String {
        return name.tr()
    }

}

class AndroidDisplay(private val activity: Activity) : PlatformDisplay {

    private var display: Display? = null
    private var displayModes: HashMap<Int, ScreenMode> = hashMapOf()

    init {

        // Fetch current display
        display = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> activity.display
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> activity.windowManager.defaultDisplay
            else -> null
        }

        // Add default mode
        displayModes[0] = AndroidScreenMode(0)

        // Add other supported modes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            fetchScreenModes()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun fetchScreenModes() {
        val display = display ?: return
        for (mode in display.supportedModes)
            displayModes[mode.modeId] = AndroidScreenMode(mode)
    }

    override fun getScreenModes(): Map<Int, ScreenMode> {
        return displayModes
    }

    override fun setScreenMode(id: Int, settings: GameSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.runOnUiThread {
                val params = activity.window.attributes
                params.preferredDisplayModeId = id
                activity.window.attributes = params
            }
        }

    }

    override fun hasCutout(): Boolean {
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

    override fun setCutout(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = activity.window.attributes
            params.layoutInDisplayCutoutMode = when {
                enabled -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
            activity.window.attributes = params
        }
    }


    /*
    Sources for Info about current orientation in case need:
            val windowManager = (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            val displayRotation = windowManager.defaultDisplay.rotation
            val currentOrientation = activity.resources.configuration.orientation
            const val ORIENTATION_UNDEFINED = 0
            const val ORIENTATION_PORTRAIT = 1
            const val ORIENTATION_LANDSCAPE = 2
    */

    override fun hasOrientation(): Boolean {
        return true
    }

    override fun setOrientation(orientation: ScreenOrientation) {

        val mode = when (orientation) {
            ScreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            ScreenOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            ScreenOrientation.Dynamic -> ActivityInfo.SCREEN_ORIENTATION_USER
        }

        // Ensure ActivityTaskManager.getService().setRequestedOrientation isn't called unless necessary!
        if (activity.requestedOrientation != mode)
            activity.requestedOrientation = mode
    }

}
