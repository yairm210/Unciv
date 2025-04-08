package com.unciv.utils

interface PlatformSpecific {

    /** Notifies player that his multiplayer turn started */
    fun notifyTurnStarted() {}

    /** Install system audio hooks */
    fun installAudioHooks() {}

    /** If not null, this is the path to the directory in which to store the local files - mods, saves, maps, etc */
    var customDataDirectory: String?

    /** If the OS localizes all error messages, this should provide a lookup */
    fun getSystemErrorMessage(errorCode: Int): String? = null
}
