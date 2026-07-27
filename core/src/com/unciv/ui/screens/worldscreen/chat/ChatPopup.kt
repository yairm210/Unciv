package com.unciv.ui.screens.worldscreen.chat

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.chat.Chat
import com.unciv.logic.multiplayer.chat.ChatStore
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.coerceLightnessAtLeast
import com.unciv.ui.components.extensions.setItems
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.worldscreen.WorldScreen


private val civChatColorsMap = mapOf<String, Color>(
    "System" to Color.WHITE,
    "Server" to Color.DARK_GRAY,
)

private class ChatRecipient(
    val displayName: String,
    val playerId: String?,
    val civName: String?,
) {
    override fun toString() = displayName.tr()
}

class ChatPopup(
    val chat: Chat,
    private val worldScreen: WorldScreen,
) : Popup(screen = worldScreen, scrollable = Scrollability.None) {
    companion object {
        // the percentage of the minimum lightness allowed for a civName
        const val CIVNAME_COLOR_MIN_LIGHTNESS = 0.55f
        /** Server chat protocol version that supports private messages. */
        const val PRIVATE_MESSAGE_CHAT_VERSION = 2
    }

    private val chatTable = Table(skin)
    private val scrollPane = ScrollPane(chatTable, skin)
    private val messageField = UncivTextField(hint = "Type something...")
    private var recipientSelect: SelectBox<ChatRecipient>? = null

    init {
        ChatStore.chatPopup = this
        chatTable.defaults().growX().pad(5f).center()

        /**
         * Layout:
         * |  ChatHeader | CloseButton |
         * |  ChatTable (colSpan = 2)  |
         * | RecipientSelect (optional, colSpan = 2) |
         * | MessageField | SendButton |
         */

        // Header: |  ChatHeader | CloseButton  |
        val chatHeader = Table(skin)
        val chatLabel = "Chat".toLabel(fontSize = 30, alignment = Align.center)
        val chatIcon = ImageGetter.getImage("OtherIcons/Chat")

        chatHeader.add(chatIcon).size(chatLabel.height * 1.6f)
            .padRight(chatLabel.height / 3).padBottom(chatLabel.height / 4)
        chatHeader.add(chatLabel).expandX()

        add(chatHeader).left().pad(5f).expandX()
        add(
            ImageButton(ImageGetter.getImage("OtherIcons/Close").drawable)
                .onClick {
                    ChatStore.chatPopup = null
                    close()
                }
        ).size(chatLabel.height * 1.3f).right().row()

        // Chat: |  ChatTable (colSpan = 2)  |
        scrollPane.setFadeScrollBars(false)
        scrollPane.setScrollingDisabled(true, false)
        add(scrollPane).colspan(2)
            .size(0.5f * worldScreen.stage.width, 0.5f * worldScreen.stage.height)
            .expand().fill().row()

        if (supportsPrivateMessages()) {
            val recipients = buildRecipientOptions()
            if (recipients.size > 1) {
                val select = SelectBox<ChatRecipient>(skin)
                select.setItems(recipients)
                select.selected = recipients.first()
                recipientSelect = select
                add("To:".toLabel()).left().padLeft(5f)
                add(select).expandX().fillX().padBottom(5f).row()
            }
        }

        // Input: | MessageField | SendButton |
        add(messageField).expandX().fillX()
        val sendButton = Button(skin)
        sendButton.add(ImageGetter.getImage("OtherIcons/Send"))
        add(sendButton).size(messageField.height * 1.2f, messageField.height).padLeft(1f).row()

        // populate previous chats
        populateChat()

        // Send button logic (for demo, just adds to UI)
        sendButton.onClick { sendMessage() }

        messageField.addListener(object : InputListener() {
            override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    sendMessage()
                }
                return true
            }
        })
    }

    private fun supportsPrivateMessages(): Boolean =
        UncivGame.Current.onlineMultiplayer.multiplayerServer.getFeatureSet().chatVersion >=
            PRIVATE_MESSAGE_CHAT_VERSION

    private fun ownCivNameAndId(): Pair<String, String?> {
        val userId = UncivGame.Current.settings.multiplayer.getUserId()
        val currentPlayerCiv = worldScreen.gameInfo.currentPlayerCiv
        if (currentPlayerCiv.playerId == userId) {
            return currentPlayerCiv.civID to userId
        }
        val ownCiv = worldScreen.gameInfo.civilizations.firstOrNull { civ -> civ.playerId == userId }
        return (ownCiv?.civID ?: "Unknown") to ownCiv?.playerId
    }

    private fun buildRecipientOptions(): List<ChatRecipient> {
        val (ownCivName, ownPlayerId) = ownCivNameAndId()
        if (ownCivName == "Unknown" || ownPlayerId.isNullOrBlank()) {
            return listOf(ChatRecipient("Everyone", null, null))
        }

        val options = mutableListOf(ChatRecipient("Everyone", null, null))
        for (civ in worldScreen.gameInfo.civilizations) {
            if (!civ.isMajorCiv()) continue
            if (!civ.isHuman()) continue
            if (civ.playerId.isBlank()) continue
            if (civ.playerId == ownPlayerId) continue
            options.add(ChatRecipient(civ.civID, civ.playerId, civ.civID))
        }
        return options
    }

    fun sendMessage() {
        val message = messageField.text.trim()
        if (message.isEmpty()) return

        val (civName, _) = ownCivNameAndId()
        val recipient = recipientSelect?.selected
        chat.requestMessageSend(
            civName = civName,
            message = message,
            toPlayerId = recipient?.playerId,
            toCivName = recipient?.civName,
        )
        messageField.setText("")
    }

    fun addMessage(
        senderCivName: String,
        message: String,
        suffix: String? = null,
        toCivName: String? = null,
        scroll: Boolean = true
    ) {
        val namePart = buildString {
            append(senderCivName.tr())
            if (suffix != null) append(" [${suffix.tr()}]")
            if (toCivName != null) append(" → ${toCivName.tr()}")
            append(": ")
            append(message.tr())
        }

        val line = Label(namePart, skin).apply {
            wrap = true

            val civNameColor =
                civChatColorsMap[senderCivName]
                    ?: worldScreen.gameInfo.getCivilizationOrNull(senderCivName)?.nation?.getOuterColor()
                    ?: Color.BLACK

            color = civNameColor.coerceLightnessAtLeast(CIVNAME_COLOR_MIN_LIGHTNESS)
        }

        chatTable.add(line).row()
        if (scroll) scrollToBottom()
    }

    private fun populateChat() {
        chatTable.clearChildren()
        chat.forEachMessage { entry ->
            addMessage(entry.civName, entry.message, toCivName = entry.toCivName)
        }
        ChatStore.pollGlobalMessages { civName, message ->
            addMessage(civName, message, suffix = "one time")
        }
        scrollToBottom()
    }

    private fun scrollToBottom() {
        chatTable.invalidate()
        scrollPane.layout()
        scrollPane.scrollY = 0f
        scrollPane.scrollPercentY = 1f
    }
}
