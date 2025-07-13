package com.unciv.ui.screens.worldscreen.chat

import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.ServerFeatureSetChanged
import com.unciv.logic.multiplayer.chat.ChatWebSocketManager
import com.unciv.logic.multiplayer.chat.Message
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen

class ChatButton(worldScreen: WorldScreen) : IconTextButton(
    "Chat", ImageGetter.getImage("OtherIcons/Chat"), 23
) {
    private val eventReceiver = EventBus.EventReceiver()

    init {
        width = 95f
        iconCell.pad(3f).center()

        onClick {
            ChatPopup(worldScreen).open()
        }

        eventReceiver.receive(ServerFeatureSetChanged::class) {
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

    fun updatePosition(worldScreen: WorldScreen) = setPosition(
        worldScreen.techPolicyAndDiplomacy.x.coerceAtLeast(1f),
        worldScreen.techPolicyAndDiplomacy.y - height - 1f
    )
}
