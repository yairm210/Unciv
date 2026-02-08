package com.unciv.utils

import games.rednblack.miniaudio.MiniAudio

interface PlatformSpecific {

    /** Notifies player that his multiplayer turn started */
    fun notifyTurnStarted() {}

    /** Platform-specific MiniAudio setup */
    fun initAudio(miniAudio: MiniAudio) {}

    /** If not null, this is the path to the directory in which to store the local files - mods, saves, maps, etc */
    var customDataDirectory: String?

    /** If the OS localizes all error messages, this should provide a lookup */
    fun getSystemErrorMessage(errorCode: Int): String? = null
}
