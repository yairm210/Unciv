package com.unciv.app

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Display
import android.view.Display.Mode
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.badlogic.gdx.backends.android.AndroidApplication
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.utils.PlatformDisplay
import com.unciv.utils.ScreenMode
import com.unciv.utils.ScreenOrientation


class AndroidDisplay(private val activity: AndroidApplication) : PlatformDisplay {

    private var display: Display? = null
    private var displayModes: HashMap<Int, ScreenMode> = hashMapOf()

    init {

        // Fetch current display
        @Suppress("DEPRECATION") // M..P should use the deprecated API
        display = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> activity.display
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> activity.windowManager.defaultDisplay
            else -> null
        }

        // Add default mode
        displayModes[AndroidScreenMode.defaultId] = AndroidScreenMode.default

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        activity.runOnUiThread {
            val params = activity.window.attributes
            params.preferredDisplayModeId = id
            activity.window.attributes = params
        }
    }

    override fun hasSystemUiVisibility() = true

    override fun setSystemUiVisibility(hide: Boolean) {
        activity.runOnUiThread {
            setSystemUiVisibilityFromUiThread(hide)
        }
    }
    internal fun setSystemUiVisibilityFromUiThread(hide: Boolean) {
        @Suppress("DEPRECATION") // Avoids @RequiresApi(Build.VERSION_CODES.R)
        activity.window.decorView.systemUiVisibility =
            if (hide)
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            else
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
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
        activity.runOnUiThread {
            setCutoutFromUiThread(enabled)
        }
    }
    internal fun setCutoutFromUiThread(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val params = activity.window.attributes
        params.layoutInDisplayCutoutMode = when {
            enabled -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }
        activity.window.attributes = params  // This is the only line to actually need to be running on the Ui Thread
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
            ScreenOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }

        // Ensure ActivityTaskManager.getService().setRequestedOrientation isn't called unless necessary!
        if (activity.requestedOrientation != mode)
            activity.requestedOrientation = mode
    }

    class AndroidScreenMode private constructor(
        private val modeId: Int,
        private val name: String
    ) : ScreenMode {
        @RequiresApi(Build.VERSION_CODES.M)
        constructor(mode: Mode) : this(mode.modeId, "${mode.physicalWidth}x${mode.physicalHeight} (${mode.refreshRate.toInt()}Hz)")

        override fun getId(): Int {
            return modeId
        }

        override fun toString(): String {
            return name.tr()
        }

        companion object {
            const val defaultId = 0
            val default: AndroidScreenMode
                get() = AndroidScreenMode(defaultId, "Default")
        }
    }
}
