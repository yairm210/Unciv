package com.unciv.utils

import java.util.Locale

interface PlatformSpecific {

    /** Whether online multiplayer transport features are available on this platform. */
    val supportsOnlineMultiplayer: Boolean get() = true

    /** Notifies player that his multiplayer turn started */
    fun notifyTurnStarted() {}

    /** Install system audio hooks */
    fun installAudioHooks() {}

    /** If not null, this is the path to the directory in which to store the local files - mods, saves, maps, etc */
    var customDataDirectory: String?

    /** If the OS localizes all error messages, this should provide a lookup */
    fun getSystemErrorMessage(errorCode: Int): String? = null

    /** Completed garbage collections, or null when the platform does not expose this metric. */
    fun getGcCount(): Int?

    /** Get system locale, on Android 13+ app-specific locale */
    fun getDefaultLocale(): Locale = Locale.getDefault()
}
