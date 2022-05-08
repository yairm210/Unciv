package com.unciv.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.unciv.ui.utils.GeneralPlatformSpecificHelpers
import java.net.InetAddress

class PlatformSpecificHelpersDesktop(config: Lwjgl3ApplicationConfiguration) : GeneralPlatformSpecificHelpers {
    val turnNotifier = MultiplayerTurnNotifierDesktop()
    init {
        config.setWindowListener(turnNotifier);
    }

    override fun isInternetConnected(): Boolean {
        return try {
            InetAddress.getByName("8.8.8.8").isReachable(500)  // Parameter timeout in milliseconds
        } catch (ex: Throwable) {
            false
        }
    }

    override fun notifyTurnStarted() {
        turnNotifier.turnStarted()
    }

}
