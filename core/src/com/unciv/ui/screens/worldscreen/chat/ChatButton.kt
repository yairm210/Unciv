package com.unciv.ui.screens.worldscreen.chat

import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.chat.ChatWebSocket
import com.unciv.logic.multiplayer.chat.Message
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen

class ChatButton(val worldScreen: WorldScreen) : IconTextButton(
    "Chat", ImageGetter.getImage("OtherIcons/Chat"), 23
) {
    private val eventReceiver = EventBus.EventReceiver()

    init {
        width = 95f
        iconCell.pad(3f).center()

        onClick {
            ChatPopup(worldScreen).open()
        }

        refreshVisibility()
    }

    fun refreshVisibility() {
        isVisible = if (
            worldScreen.gameInfo.gameParameters.isOnlineMultiplayer &&
            UncivGame.Current.onlineMultiplayer.multiplayerServer.featureSet.chatVersion > 0
        ) {
            ChatWebSocket.requestMessageSend(
                Message.Join(listOf(worldScreen.gameInfo.gameId)),
            )

            updatePosition()
            true
        } else false
    }

    fun updatePosition() = setPosition(
        worldScreen.techPolicyAndDiplomacy.x.coerceAtLeast(1f),
        worldScreen.techPolicyAndDiplomacy.y - height - 1f
    )
}
