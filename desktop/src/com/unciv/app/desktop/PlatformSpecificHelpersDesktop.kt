package com.unciv.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.unciv.ui.utils.GeneralPlatformSpecificHelpers

class PlatformSpecificHelpersDesktop(config: Lwjgl3ApplicationConfiguration) : GeneralPlatformSpecificHelpers {
    val turnNotifier = MultiplayerTurnNotifierDesktop()
    init {
        config.setWindowListener(turnNotifier)
    }

    override fun notifyTurnStarted() {
        turnNotifier.turnStarted()
    }

    /** On desktop, external is likely some document folder, while local is the game directory. We'd like to keep everything in the game directory */
    override fun shouldPreferExternalStorage(): Boolean = false
}
