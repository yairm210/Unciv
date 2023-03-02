package com.unciv.app

import android.app.Activity
import android.os.Build
import android.view.Display
import android.view.Display.Mode
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.utils.Log
import com.unciv.utils.PlatformDisplay
import com.unciv.utils.ScreenMode


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

    override fun getDefaultMode(): ScreenMode {
        return displayModes[0]!!
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

}
