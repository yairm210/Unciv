package com.unciv.app.desktop

import com.unciv.ui.utils.GeneralPlatformSpecificHelpers
import java.net.InetAddress
import java.net.URL

class PlatformSpecificHelpersDesktop : GeneralPlatformSpecificHelpers {
    override fun allowPortrait(allow: Boolean) {
        // No need to do anything
    }

    override fun isInternetConnected(): Boolean {
        return try {
            val u = URL("http://www.dropbox.com")
            val conn = u.openConnection()
            conn.connect() //because isReachable fails while using proxy

            InetAddress.getByName("8.8.8.8").isReachable(500) // Parameter timeout in milliseconds
        } catch (ex: Throwable) {
            false
        }
    }
}
