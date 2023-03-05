package com.unciv.utils

import com.unciv.models.metadata.GameSettings

interface ScreenMode {
    fun getId(): Int
}

interface PlatformDisplay {

    fun setScreenMode(id: Int, settings: GameSettings) {}
    fun getScreenModes(): Map<Int, ScreenMode> { return hashMapOf() }
    fun getDefaultMode(): ScreenMode

}

object Display {

    lateinit var platform: PlatformDisplay

    fun getDefaultMode(): ScreenMode {
        return platform.getDefaultMode()
    }

    fun getScreenModes(): Map<Int, ScreenMode> {
        return platform.getScreenModes()
    }

    fun setScreenMode(id: Int, settings: GameSettings) {
        platform.setScreenMode(id, settings)
    }

}
