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
            setSize(worldScreen.techPolicyAndDiplomacy.width, 50f)
            onClick {
                ChatPopup(worldScreen).open()
            }
        } else isVisible = false
    }
}
