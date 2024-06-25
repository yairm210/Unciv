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
    fun getScreenModes(): Map<Int, ScreenMode> = hashMapOf()

    fun hasCutout(): Boolean = false
    fun setCutout(enabled: Boolean) {}

    fun hasOrientation(): Boolean = false
    fun setOrientation(orientation: ScreenOrientation) {}

    fun hasUserSelectableSize(id: Int): Boolean = false

    fun hasSystemUiVisibility(): Boolean = false
    fun setSystemUiVisibility(hide: Boolean) {}
}

object Display {
    lateinit var platform: PlatformDisplay

    fun hasOrientation() = platform.hasOrientation()
    fun setOrientation(orientation: ScreenOrientation) { platform.setOrientation(orientation) }

    fun hasCutout() = platform.hasCutout()
    fun setCutout(enabled: Boolean) { platform.setCutout(enabled) }

    fun getScreenModes() = platform.getScreenModes()
    fun setScreenMode(id: Int, settings: GameSettings) { platform.setScreenMode(id, settings) }

    fun hasUserSelectableSize(id: Int) = platform.hasUserSelectableSize(id)

    fun hasSystemUiVisibility() = platform.hasSystemUiVisibility()
    fun setSystemUiVisibility(hide: Boolean) = platform.setSystemUiVisibility(hide)
}
