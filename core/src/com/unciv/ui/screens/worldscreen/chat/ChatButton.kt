package com.unciv.ui.screens.worldscreen.chat

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.AlternatingStateManager
import com.unciv.logic.multiplayer.chat.ChatStore
import com.unciv.logic.multiplayer.chat.ChatWebSocket
import com.unciv.logic.multiplayer.chat.Message
import com.unciv.ui.components.SmallButtonStyle
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen

private val smallButtonStyle = SmallButtonStyle()

class ChatButton(val worldScreen: WorldScreen) : IconTextButton(
    "Chat", ImageGetter.getImage("OtherIcons/Chat"), 23
) {
    private val chat = ChatStore.getChatByGameId(worldScreen.gameInfo.gameId)

    private val badge = "".toTextButton(smallButtonStyle).apply {
        disable()
        label.setColor(Color.WHITE)
        label.setAlignment(Align.center)
        label.setFontScale(0.2f)
    }

    private val flash = AlternatingStateManager(
        name = "ChatButton color flash",
        originalState = {
            icon?.color = fontColor
            label.color = fontColor
        }, alternateState = {
            icon?.color = Color.ORANGE
            label.color = Color.ORANGE
        }
    )

    private fun updateBadge() {
        badge.height = height / 3
        badge.setPosition(
            width - badge.width / 1.5f,
            height - badge.height / 1.5f
        )

        badge.isVisible = chat.unreadCount > 0 || ChatStore.hasGlobalMessage

        if (badge.isVisible) {
            var text = chat.unreadCount.toString()
            if (ChatStore.hasGlobalMessage) {
                text += '+'
            }
            badge.setText(text)
        } else flash.stop()
    }

    fun triggerChatIndication() {
        updateBadge()
        flash.start()
    }

    init {
        width = 95f
        iconCell.pad(3f).center()
        addActor(badge)
        updateBadge()

        onClick {
            chat.unreadCount = 0
            ChatStore.hasGlobalMessage = false
            updateBadge()

            ChatPopup(chat, worldScreen).open()
        }

        refreshVisibility()
    }

    /**
     * Toggles [ChatButton] if needed and also starts or stops [ChatWebSocket] as required.
     */
    fun refreshVisibility() {
        isVisible = if (
            worldScreen.gameInfo.gameParameters.isOnlineMultiplayer &&
            UncivGame.Current.onlineMultiplayer.multiplayerServer.getFeatureSet().chatVersion > 0
        ) {
            ChatWebSocket.requestMessageSend(
                Message.Join(listOf(worldScreen.gameInfo.gameId)),
            )
            updatePosition()
            updateBadge()
            true
        } else {
            ChatWebSocket.stop()
            false
        }
    }

    fun updatePosition() = setPosition(
        worldScreen.techPolicyAndDiplomacy.x.coerceAtLeast(1f),
        worldScreen.techPolicyAndDiplomacy.y - height - 1f
    )
}
