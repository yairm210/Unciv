package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import com.unciv.ui.components.ArrowButton
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency

/**
 * A [Table] which combines [ChatMessageList] with a text input and send button to write a new message
 *
 * Optionally, it can replace the send message text box with a popup that asks for a message.
 */
class ChatTable(
    private val chatMessageList: ChatMessageList,
    useInputPopup: Boolean = false,
    actorHeight: Float? = null,
    maxMessageLength: Int? = null
): Table(), Disposable {
    internal val messageField = UncivTextField.create("New message")

    init {
        val chatScroll = AutoScrollPane(chatMessageList, BaseScreen.skin)

        // Callback listener to scroll to the bottom of the chat message list if the
        // list has been provided with the initial messages or if a new message just arrived
        var initialScrollToBottom = false
        chatMessageList.addListener {
            Concurrency.runOnGLThread {
                if (chatScroll.maxY > 0f) {
                    if (!initialScrollToBottom) {
                        initialScrollToBottom = true
                        chatScroll.scrollY = chatScroll.maxY
                    } else if (it) {
                        chatScroll.scrollY = chatScroll.maxY
                    }
                }
            }
        }

        val chatCell = add(chatScroll)
        if (actorHeight != null) {
            chatCell.actorHeight = actorHeight
        }
        val width = 2
        chatCell.colspan(width).fillX().expandY().padBottom(10f)
        row()

        if (useInputPopup) {
            val newButton = "New message".toTextButton()
            newButton.onActivation {
                val popup = Popup(stage)
                popup.addGoodSizedLabel("Enter your new chat message below:").colspan(2).row()
                val textField = UncivTextField.create("New message")
                if (maxMessageLength != null) {
                    textField.maxLength = maxMessageLength
                }
                popup.add(textField).growX().padBottom(5f).colspan(2).minWidth(stage.width * 0.25f).row()
                popup.addCloseButton()
                popup.addOKButton {
                    chatMessageList.sendMessage(textField.text)
                }
                popup.equalizeLastTwoButtonWidths()
                popup.open(force = true)
            }
            newButton.keyShortcuts.add(KeyCharAndCode.RETURN)
            add(newButton).growX().padRight(10f).padLeft(10f).row()

        } else {
            if (maxMessageLength != null) {
                messageField.maxLength = maxMessageLength
            }
            val sendButton = ArrowButton()
            sendButton.onActivation {
                sendMessage()
            }
            sendButton.keyShortcuts.add(KeyCharAndCode.RETURN)

            add(messageField).padLeft(5f).growX()
            add(sendButton).padLeft(10f).padRight(5f)
            row()
        }
    }

    /**
     * Simulate the button click on the send button (useful for scripts or hotkeys)
     */
    fun sendMessage() {
        chatMessageList.sendMessage(messageField.text)
        messageField.text = ""
    }

    override fun dispose() {
        chatMessageList.dispose()
    }
}
