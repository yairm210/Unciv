package com.unciv.utils

import com.unciv.models.metadata.GameSettings

enum class ScreenOrientation {
    Landscape,
    Portrait,
    Dynamic
}

interface ScreenMode {
    fun getId(): Int
}

interface PlatformDisplay {

    fun setScreenMode(id: Int, settings: GameSettings) {}
    fun getScreenModes(): Map<Int, ScreenMode> { return hashMapOf() }

    fun hasCutout(): Boolean { return false }
    fun setCutout(enabled: Boolean) {}

    fun hasOrientation(): Boolean { return false }
    fun setOrientation(orientation: ScreenOrientation) {}
}

object Display {

    lateinit var platform: PlatformDisplay

    fun hasOrientation(): Boolean { return platform.hasOrientation() }
    fun setOrientation(orientation: ScreenOrientation) { platform.setOrientation(orientation) }

    fun hasCutout(): Boolean { return platform.hasCutout() }
    fun setCutout(enabled: Boolean) { platform.setCutout(enabled) }

    fun getScreenModes(): Map<Int, ScreenMode> { return platform.getScreenModes() }
    fun setScreenMode(id: Int, settings: GameSettings) { platform.setScreenMode(id, settings) }

}
