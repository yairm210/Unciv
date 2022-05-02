package com.unciv.app.desktop

import com.unciv.ui.utils.GeneralPlatformSpecificHelpers
import java.net.InetAddress

class PlatformSpecificHelpersDesktop : GeneralPlatformSpecificHelpers {
    override fun allowPortrait(allow: Boolean) {
        // No need to do anything
    }

    override fun isInternetConnected(): Boolean {
        return try{
            InetAddress.getByName("8.8.8.8").isReachable(500)  // Parameter timeout in milliseconds
        }catch (ex: Exception){
            false
        }
    }
}
