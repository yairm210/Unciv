package com.unciv.ui

/**
 * Used to implement platform-specific turn notifications for multiplayer while the game is running
 */
interface MultiplayerTurnNotifier {
    fun turnStarted()
}
