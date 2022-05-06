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

    override fun isInternetConnected(): Boolean {
        return try {
            val multiplayerServer = UncivGame.Current.settings.multiplayerServer ?: "Dropbox"
            val u = URL(if (multiplayerServer != "Dropbox") multiplayerServer else "http://www.dropbox.com")
            val conn = u.openConnection()
            conn.connect()
            true
        } catch (ex: Throwable) {
            false
        }
    }

    override fun notifyTurnStarted() {
        turnNotifier.turnStarted()
    }

}
