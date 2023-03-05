package com.unciv.utils

interface PlatformSpecific {

    /** Notifies player that his multiplayer turn started */
    fun notifyTurnStarted() {}

    /** Install system audio hooks */
    fun installAudioHooks() {}

}
