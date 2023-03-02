package com.unciv.utils

import com.unciv.models.metadata.GameSettings

interface ScreenMode {
    fun getId(): Int
}

interface PlatformDisplay {

    fun setScreenMode(mode: ScreenMode, settings: GameSettings) {}
    fun getScreenModes(): ArrayList<ScreenMode> { return arrayListOf() }
    fun getDefaultMode(): ScreenMode

}

object Display {

    lateinit var platform: PlatformDisplay

    fun getDefaultMode(): ScreenMode {
        return platform.getDefaultMode()
    }

    fun getScreenModes(): ArrayList<ScreenMode> {
        return platform.getScreenModes()
    }

    fun setScreenMode(id: Int, settings: GameSettings) {
        val mode = getScreenModes().firstOrNull { it.getId() == id } ?: return
        platform.setScreenMode(mode, settings)
    }

}
