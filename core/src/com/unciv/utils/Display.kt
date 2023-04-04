package com.unciv.utils

import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr

enum class ScreenOrientation(val description: String)  {
    Landscape("Landscape (fixed)"),
    Portrait("Portrait (fixed)"),
    Auto("Auto (sensor adjusted)");

    override fun toString(): String {
        return description.tr()
    }
}

interface ScreenMode {
    fun getId(): Int
    fun hasUserSelectableSize(): Boolean = false
}

interface PlatformDisplay {

    fun setScreenMode(id: Int, settings: GameSettings) {}
    fun getScreenModes(): Map<Int, ScreenMode> { return hashMapOf() }

    fun hasCutout(): Boolean { return false }
    fun setCutout(enabled: Boolean) {}

    fun hasOrientation(): Boolean { return false }
    fun setOrientation(orientation: ScreenOrientation) {}

    fun hasUserSelectableSize(id: Int): Boolean = false
}

object Display {

    lateinit var platform: PlatformDisplay

    fun hasOrientation(): Boolean { return platform.hasOrientation() }
    fun setOrientation(orientation: ScreenOrientation) { platform.setOrientation(orientation) }

    fun hasCutout(): Boolean { return platform.hasCutout() }
    fun setCutout(enabled: Boolean) { platform.setCutout(enabled) }

    fun getScreenModes(): Map<Int, ScreenMode> { return platform.getScreenModes() }
    fun setScreenMode(id: Int, settings: GameSettings) { platform.setScreenMode(id, settings) }

    fun hasUserSelectableSize(id: Int) = platform.hasUserSelectableSize(id)
}
