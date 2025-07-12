package com.unciv.ui.screens.worldscreen.chat

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Align
import com.unciv.logic.event.EventBus
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.worldscreen.WorldScreen


private val civChatColorsMap = mapOf<String, Color>(
    "System" to Color.WHITE,
    "Server" to Color.DARK_GRAY,
)

class ChatPopup(
    val worldScreen: WorldScreen,
) : Popup(screen = worldScreen, scrollable = Scrollability.None) {
    private val chat = ChatStore.getChatByGameId(worldScreen.gameInfo.gameId)
    private val eventReceiver = EventBus.EventReceiver()

    private val chatTable = Table(skin)
    private val scrollPane = ScrollPane(chatTable, skin)
    private val messageField = TextField("", skin)

    init {
        add("Chat".toLabel()).expandX()
        addCloseButton().row()

        // Chat area (scrollable)
        scrollPane.setFadeScrollBars(false)
        scrollPane.setScrollingDisabled(true, false)
        add(scrollPane).colspan(2).size(400f, 200f).expand().fill().row()

        // Input area: text field and send button
        add(messageField).expandX().fillX()
        val sendButton = "Send".toTextButton()
        add(sendButton).padLeft(1f).row()

        // populate previous chats
        populateChat()

        // Send button logic (for demo, just adds to UI)
        sendButton.onClick {
            val message = messageField.text.trim()
            if (message.isNotEmpty()) {
                val civName = worldScreen.gameInfo.currentPlayer
                chat.requestMessageSend(civName, message)
                messageField.setText("")
            }
        }

        eventReceiver.receive(
            ChatMessageReceivedEvent::class, { it.gameId == chat.gameId }) {
            addMessage(it.civName, it.message, true)
        }
    }

    fun populateChat() {
        chatTable.clearChildren()
        chat.forEachMessage { civName, message ->
            addMessage(civName, message)
        }
        scrollToBottom()
    }

    private fun addMessage(senderCivName: String, message: String, scroll: Boolean = true) {
        val civColor = civChatColorsMap[senderCivName]
            ?: worldScreen.gameInfo.getCivilizationOrNull(senderCivName)?.nation?.getOuterColor() ?: Color.BLACK

        val line = "$senderCivName: $message".toLabel(alignment = Align.left).apply {
            color = civColor
            wrap = true
        }

        chatTable.add(line).growX().pad(5f).left().row()
        if (scroll) scrollToBottom()
    }

    private fun scrollToBottom() {
        chatTable.invalidate()
        scrollPane.layout()
        scrollPane.scrollY = 0f
        scrollPane.scrollPercentY = 1f
    }
}
