package com.unciv.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.unciv.ui.utils.GeneralPlatformSpecificHelpers
import com.unciv.UncivGame
import java.net.URL

class PlatformSpecificHelpersDesktop(config: Lwjgl3ApplicationConfiguration) : GeneralPlatformSpecificHelpers {
    val turnNotifier = MultiplayerTurnNotifierDesktop()
    init {
        config.setWindowListener(turnNotifier);
    }

    override fun notifyTurnStarted() {
        turnNotifier.turnStarted()
    }

}
