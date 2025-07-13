package com.unciv.ui.screens.worldscreen.chat

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.chat.ChatMessageReceived
import com.unciv.logic.multiplayer.chat.ChatStore
import com.unciv.ui.components.extensions.coerceLightnessAtLeast
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.worldscreen.WorldScreen


private val civChatColorsMap = mapOf<String, Color>(
    "System" to Color.WHITE,
    "Server" to Color.DARK_GRAY,
)

class ChatPopup(
    val worldScreen: WorldScreen,
) : Popup(screen = worldScreen, scrollable = Scrollability.None) {
    companion object {
        // the percentage of the minimum lightness allowed for a civName
        const val CIVNAME_COLOR_LIGHTNESS_THRESHOLD = 0.45f
    }

    private val chat = ChatStore.getChatByGameId(worldScreen.gameInfo.gameId)
    private val eventReceiver = EventBus.EventReceiver()

    private val chatTable = Table(skin)
    private val scrollPane = ScrollPane(chatTable, skin)
    private val messageField = TextField("", skin)

    init {
        ChatStore.chatPopupAvailable = true
        chatTable.defaults().growX().pad(5f).left()

        /**
         * Layout:
         * |  ChatLabel | CloseButton  |
         * |  ChatTable (colSpan = 2)  |
         * | MessageField | SendButton |
         */

        // Header: |  ChatLabel | CloseButton  |
        add("Chat".toLabel(fontSize = 30)).left().pad(5f).expandX()
        add(
            ImageButton(ImageGetter.getImage("OtherIcons/Close").drawable)
                .onClick {
                    ChatStore.chatPopupAvailable = false
                    close()
                }
        ).size(30f).right().row()

        // Chat: |  ChatTable (colSpan = 2)  |
        scrollPane.setFadeScrollBars(false)
        scrollPane.setScrollingDisabled(true, false)
        add(scrollPane).colspan(2)
            .size(0.5f * worldScreen.stage.width, 0.5f * worldScreen.stage.height)
            .expand().fill().row()

        // Input: | MessageField | SendButton |
        add(messageField).expandX().fillX()
        val sendButton = "Send".toTextButton()
        add(sendButton).padLeft(1f).row()

        // populate previous chats
        populateChat()

        // Send button logic (for demo, just adds to UI)
        sendButton.onClick {
            val message = messageField.text.trim()

            val userId = UncivGame.Current.settings.multiplayer.userId
            val currentPlayerCiv = worldScreen.gameInfo.currentPlayerCiv
            val civName = if (currentPlayerCiv.playerId == userId) {
                currentPlayerCiv.civName
            } else {
                // what do I do if someone is a spectator?
                worldScreen.gameInfo.civilizations.firstOrNull { civ -> civ.playerId == userId }?.civName ?: "Unknown"
            }

            if (message.isNotEmpty()) {
                chat.requestMessageSend(civName, message)
                messageField.setText("")
            }
        }

        // empty Ids are used to display server & system messages unspecific to any Chat
        eventReceiver.receive(
            ChatMessageReceived::class, { it.gameId.isEmpty() || it.gameId == chat.gameId }
        ) { addMessage(it.civName, it.message, true) }
    }

    fun populateChat() {
        chatTable.clearChildren()
        chat.forEachMessage { civName, message ->
            addMessage(civName, message)
        }
        ChatStore.pollGlobalMessages { civName, message ->
            addMessage(civName, message)
        }
        scrollToBottom()
    }

    private fun addMessage(senderCivName: String, message: String, scroll: Boolean = true) {
        val line = "$senderCivName: $message".toLabel(alignment = Align.left).apply {
            wrap = true

            val civNameColor = civChatColorsMap[senderCivName] ?: worldScreen.gameInfo.getCivilizationOrNull(
                senderCivName
            )?.nation?.getOuterColor() ?: Color.BLACK

            color = civNameColor.coerceLightnessAtLeast(CIVNAME_COLOR_LIGHTNESS_THRESHOLD)
        }

        chatTable.add(line).row()
        if (scroll) scrollToBottom()
    }

    private fun scrollToBottom() {
        chatTable.invalidate()
        scrollPane.layout()
        scrollPane.scrollY = 0f
        scrollPane.scrollPercentY = 1f
    }
}
