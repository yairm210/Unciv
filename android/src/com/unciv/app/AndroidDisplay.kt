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
    val modeId: Int,
    val refreshRate: Float,
    val width: Int,
    val height: Int) : ScreenMode {

    var name: String = "Default"

    @RequiresApi(Build.VERSION_CODES.M)
    constructor(mode: Mode) : this(
        mode.modeId,
        mode.refreshRate,
        mode.physicalWidth,
        mode.physicalHeight
    ) {
        name = "${width}x${height} (${refreshRate.toInt()}HZ)"
    }

    constructor() : this(0,0f,0,0)

    override fun getId(): Int {
        return modeId
    }

    override fun toString(): String {
        return name.tr()
    }

}

class AndroidDisplay(private val activity: Activity) : PlatformDisplay {

    private var display: Display? = null
    private var displayModes: ArrayList<ScreenMode> = arrayListOf()
    private var defaultMode = AndroidScreenMode()

    init {

        display = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> activity.display
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> activity.windowManager.defaultDisplay
            else -> null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            fetchScreenModes()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun fetchScreenModes() {
        displayModes.add(defaultMode)
        val display = display ?: return
        for (mode in display.supportedModes)
            displayModes.add(AndroidScreenMode(mode))
    }

    override fun getScreenModes(): ArrayList<ScreenMode> {
        return displayModes
    }

    override fun getDefaultMode(): ScreenMode {
        return defaultMode
    }

    override fun setScreenMode(mode: ScreenMode, settings: GameSettings) {

        if (mode !is AndroidScreenMode)
            return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.runOnUiThread {
                val params = activity.window.attributes
                params.preferredDisplayModeId = mode.modeId
                activity.window.attributes = params
            }
        }

    }

}
