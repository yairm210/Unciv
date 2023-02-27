package com.unciv.utils

interface PlatformSpecific {

    /** Notifies player that his multiplayer turn started */
    fun notifyTurnStarted() {}

    /** Install system audio hooks */
    fun installAudioHooks() {}

    /** (Android) allow screen orientation switch */
    fun allowPortrait(allow: Boolean) {}

    /** (Android) returns whether display has cutout */
    fun hasDisplayCutout(): Boolean { return false }

}
