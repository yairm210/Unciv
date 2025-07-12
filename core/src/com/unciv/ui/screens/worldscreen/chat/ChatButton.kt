package com.unciv.ui.screens.worldscreen.chat

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class ChatButton(worldScreen: WorldScreen) : TextButton("Chat", BaseScreen.skin) {
    init {
        if (
            worldScreen.gameInfo.gameParameters.isOnlineMultiplayer &&
            UncivGame.Current.onlineMultiplayer.multiplayerServer.featureSet.chatVersion > 0
        ) {
            ChatWebSocketManager.requestMessageSend(
                Message.Join(listOf(worldScreen.gameInfo.gameId)),
            )

            update(worldScreen)
            onClick {
                ChatPopup(worldScreen).open()
            }
        } else isVisible = false
    }

    fun update(worldScreen: WorldScreen) {
        setPosition(
            worldScreen.techPolicyAndDiplomacy.x.coerceAtLeast(1f),
            worldScreen.techPolicyAndDiplomacy.y - height - 1f
        )
    }
}
