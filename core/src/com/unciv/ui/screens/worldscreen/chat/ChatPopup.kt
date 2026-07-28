package com.unciv.ui.screens.worldscreen.chat

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.multiplayer.chat.Chat
import com.unciv.logic.multiplayer.chat.ChatStore
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.getContrastRatio
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen


private val civChatColorsMap = mapOf<String, Color>(
    "System" to Color.DARK_GRAY,
    "Server" to Color.GRAY,
)

/** Soft parchment bubble — opaque white was too harsh on dark UI. */
private val chatBubbleBackground = Color(0.90f, 0.88f, 0.82f, 0.88f)

/** Opaque stand-in for contrast checks (alpha ignored by [getContrastRatio]). */
private val chatBubbleContrastBackground = Color(0.90f, 0.88f, 0.82f, 1f)

/** Darken [color] until contrast vs bubble background is usable (pale nation outerColors). */
private fun readableOnBubble(color: Color, minContrast: Double = 3.0): Color {
    if (getContrastRatio(chatBubbleContrastBackground, color) >= minContrast) return color
    val result = color.cpy()
    for (step in 1..12) {
        result.mul(0.85f)
        result.a = 1f
        if (getContrastRatio(chatBubbleContrastBackground, result) >= minContrast) return result
    }
    return Color.DARK_GRAY
}

/** Builds one chat bubble: soft cloud, nation-colored name, black body, untinted icons. */
fun createChatMessageBubble(
    gameInfo: GameInfo,
    senderCivName: String,
    message: String,
    suffix: String? = null,
): Table {
    val bubble = Table().apply {
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/ChatMessage",
            BaseScreen.skinStrings.roundedEdgeRectangleShape,
            chatBubbleBackground
        )
        pad(8f, 10f, 8f, 10f)
    }

    if (senderCivName in civChatColorsMap) {
        val line = Label(
            "${senderCivName.tr()}${if (suffix != null) " [${suffix.tr()}]" else ""}: ${message.tr()}",
            BaseScreen.skin
        ).apply {
            wrap = true
            setAlignment(Align.left)
            color = civChatColorsMap.getValue(senderCivName)
        }
        bubble.add(line).growX().left()
        return bubble
    }

    val civNameColor =
        gameInfo.getCivilizationOrNull(senderCivName)?.nation?.getOuterColor()
            ?: Color.BLACK
    val nameColor = readableOnBubble(civNameColor)
    val suffixPart = if (suffix != null) " ({$suffix})" else ""

    // Separate labels: nation-colored name + black body; WHITE symbols = untinted glyphs
    val nameLabel = ColorMarkupLabel(
        "{$senderCivName}$suffixPart:",
        textColor = nameColor,
        symbolColor = Color.WHITE
    ).apply { setAlignment(Align.left) }
    val messageLabel = ColorMarkupLabel(
        " {$message}",
        textColor = Color.BLACK,
        symbolColor = Color.WHITE
    ).apply {
        wrap = true
        setAlignment(Align.left)
    }
    bubble.add(nameLabel).left().top()
    bubble.add(messageLabel).growX().left().top()
    return bubble
}

class ChatPopup(
    val chat: Chat,
    private val worldScreen: WorldScreen,
) : Popup(screen = worldScreen, scrollable = Scrollability.None) {
    private val chatTable = Table(skin)
    private val scrollPane = ScrollPane(chatTable, skin)
    private val messageField = UncivTextField(hint = "Type something...")

    init {
        ChatStore.chatPopup = this
        chatTable.defaults().growX().pad(4f).left()

        /**
         * Layout:
         * |  ChatHeader | CloseButton |
         * |  ChatTable (colSpan = 2)  |
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

    fun sendMessage() {
        val message = messageField.text.trim()

        val userId = UncivGame.Current.settings.multiplayer.getUserId()
        val currentPlayerCiv = worldScreen.gameInfo.currentPlayerCiv
        val civName = if (currentPlayerCiv.playerId == userId) {
            currentPlayerCiv.civID
        } else {
            // what do I do if someone is a spectator?
            worldScreen.gameInfo.civilizations.firstOrNull { civ -> civ.playerId == userId }?.civID
                ?: "Unknown"
        }

        if (message.isNotEmpty()) {
            chat.requestMessageSend(civName, message)
            messageField.setText("")
        }
    }

    fun addMessage(
        senderCivName: String,
        message: String,
        suffix: String? = null,
        scroll: Boolean = true
    ) {
        chatTable.add(
            createChatMessageBubble(worldScreen.gameInfo, senderCivName, message, suffix)
        ).growX().left().row()
        if (scroll) scrollToBottom()
    }

    private fun populateChat() {
        chatTable.clearChildren()
        chat.forEachMessage { civName, message ->
            addMessage(civName, message)
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
