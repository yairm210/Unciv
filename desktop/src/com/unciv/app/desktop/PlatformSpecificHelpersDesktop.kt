package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.unciv.models.metadata.WindowState
import com.unciv.ui.utils.GeneralPlatformSpecificHelpers

class PlatformSpecificHelpersDesktop(
    config: Lwjgl3ApplicationConfiguration
) : GeneralPlatformSpecificHelpers {

    private val windowListener = UncivWindowListener()

    init {
        config.setWindowListener(windowListener)
    }

    override fun notifyTurnStarted() {
        windowListener.flashWindow()
    }

    /** On desktop, external is likely some document folder, while local is the game directory. We'd like to keep everything in the game directory */
    override fun shouldPreferExternalStorage(): Boolean = false

    override fun getWindowState(): WindowState {
        val rect = windowListener.getWindowDimensions() ?: return super.getWindowState()
        val monitor = Gdx.graphics.monitor.name
        return WindowState(rect.width, rect.height, windowListener.isMaximized, rect.x, rect.y, monitor)
    }

}
