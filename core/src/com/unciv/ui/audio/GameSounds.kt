package com.unciv.ui.audio

import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.MultiplayerGameUpdated
import com.unciv.logic.multiplayer.isUsersTurn

/**
 * Controls which sounds should be played when something happens while playing the game.
 */
object GameSounds {
    private val events = EventBus.EventReceiver()
    private val settings get() = UncivGame.Current.settings
    private val mpSettings get() = settings.multiplayer

    /**
     * Has to be called for sounds to be played.
     */
    fun init() {
        playMultiplayerTurnNotification()
    }

    private fun playMultiplayerTurnNotification() {
        events.receive(MultiplayerGameUpdated::class, { it.preview.isUsersTurn() }) {
            if (UncivGame.isDeepLinkedGameLoading()) return@receive // This means we already arrived here through a turn notification, no need to notify again
            val gameId = it.preview.gameId
            val sound = if (UncivGame.isCurrentGame(gameId)) {
                mpSettings.currentGameTurnNotificationSound
            } else {
                mpSettings.otherGameTurnNotificationSound
            }
            SoundPlayer.play(sound)
        }
    }
}
