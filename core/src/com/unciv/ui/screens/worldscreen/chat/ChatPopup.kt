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
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen


private val civChatColorsMap = mapOf<String, Color>(
    "System" to Color.WHITE,
    "Server" to Color.DARK_GRAY,
)

class ChatPopup(
    val worldScreen: WorldScreen,
) : Popup(worldScreen) {
    private val chat = ChatStore.getChatByGameId(worldScreen.gameInfo.gameId)
    private val chatEvents = EventBus.EventReceiver()

    private val chatTable = Table(BaseScreen.skin)
    private val scrollPane = ScrollPane(chatTable, BaseScreen.skin)
    private val messageField = TextField("", BaseScreen.skin)

    init {
        // Header row: Chat label and close button
        val headerTable = Table(BaseScreen.skin)
        val chatLabel = "Chat".toLabel()
        chatLabel.setAlignment(Align.left)
        headerTable.add(chatLabel).expandX().left().pad(8f)
        headerTable.add(addCloseButton().actor).right().pad(8f)
        add(headerTable).fillX().row()

        // Chat area (scrollable)
        chatTable.top().pad(10f)
        scrollPane.setFadeScrollBars(false)
        scrollPane.setScrollingDisabled(true, false)
        add(scrollPane).width(400f).height(220f).row()

        // Input area: text field and send button
        val inputTable = Table(BaseScreen.skin)
        inputTable.add(messageField).width(320f).padRight(10f)
        val sendButton = "Send".toTextButton()
        inputTable.add(sendButton).width(60f)
        add(inputTable).padTop(8f).row()

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

        chatEvents.receive(
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
        val civColor: Color = when {
            civChatColorsMap.containsKey(senderCivName) ->
                civChatColorsMap.getOrDefault(senderCivName, Color.BLACK)

            worldScreen.gameInfo.getCivilizationOrNull(senderCivName) != null ->
                worldScreen.gameInfo.getCivilizationOrNull(senderCivName)!!.nation.getOuterColor()

            else -> Color.BLACK
        }

        val line = "$senderCivName: $message".toLabel().apply {
            color = civColor
            wrap = true
            setAlignment(Align.left)
        }

        chatTable.add(line).growX().pad(6f, 8f, 6f, 8f).align(Align.left).row()
        // Separator line
        val sep = Table().apply { background = null }
        sep.add("".toLabel()).height(1f).growX().padTop(2f).padBottom(2f)
        chatTable.add(sep).growX().row()
        if (scroll) scrollToBottom()
    }

    private fun scrollToBottom() {
        chatTable.invalidate()
        scrollPane.layout()
        scrollPane.scrollY = 0f
        scrollPane.scrollPercentY = 1f
    }
}
