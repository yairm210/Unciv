package com.unciv.ui.screens.worldscreen.chat

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.options.MultiplayerServerUrlChangeEvent
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class ChatButton(worldScreen: WorldScreen) : TextButton("Chat", BaseScreen.skin) {
    private val eventReceiver = EventBus.EventReceiver()

    init {
        onClick {
            ChatPopup(worldScreen).open()
        }

        eventReceiver.receive(MultiplayerServerUrlChangeEvent::class) {
            refreshVisibility(worldScreen)
        }

        refreshVisibility(worldScreen)
    }

    private fun refreshVisibility(worldScreen: WorldScreen) {
        isVisible = if (
            worldScreen.gameInfo.gameParameters.isOnlineMultiplayer &&
            UncivGame.Current.onlineMultiplayer.multiplayerServer.featureSet.chatVersion > 0
        ) {
            ChatWebSocketManager.requestMessageSend(
                Message.Join(listOf(worldScreen.gameInfo.gameId)),
            )

            updatePosition(worldScreen)
            true
        } else false
    }

    fun updatePosition(worldScreen: WorldScreen) {
        setPosition(
            worldScreen.techPolicyAndDiplomacy.x.coerceAtLeast(1f),
            worldScreen.techPolicyAndDiplomacy.y - height - 1f
        )
    }
}
