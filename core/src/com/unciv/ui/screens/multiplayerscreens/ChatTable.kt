package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import com.unciv.ui.components.ArrowButton
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.RefreshButton
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * A [Table] which combines [ChatMessageList] with a text input and send button to write a new message
 *
 * Optionally, it can display a [RefreshButton] to the right of the send button
 * or replace the send message text box with a popup that asks for a message.
 */
class ChatTable(
    private val chatMessageList: ChatMessageList,
    showRefreshButton: Boolean,
    useInputPopup: Boolean = false,
    actorHeight: Float? = null,
    maxMessageLength: Int? = null
): Table(), Disposable {
    init {
        val chatScroll = AutoScrollPane(chatMessageList, BaseScreen.skin)
        chatScroll.setScrollingDisabled(true, false)

        val chatCell = add(chatScroll)
        if (actorHeight != null) {
            chatCell.actorHeight = actorHeight
        }
        val width = if (showRefreshButton) 3 else 2
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
            val messageField = UncivTextField.create("New message")
            if (maxMessageLength != null) {
                messageField.maxLength = maxMessageLength
            }
            val sendButton = ArrowButton()
            sendButton.onActivation {
                chatMessageList.sendMessage(messageField.text)
                messageField.text = ""
            }
            sendButton.keyShortcuts.add(KeyCharAndCode.RETURN)

            add(messageField).padLeft(5f).growX()
            if (showRefreshButton) {
                add(sendButton).padLeft(10f).padRight(10f)
                val refreshButton = RefreshButton()
                refreshButton.onActivation {
                    chatMessageList.triggerRefresh(stage, false)
                }
                add(refreshButton).padRight(5f)
            } else {
                add(sendButton).padLeft(10f).padRight(5f)
            }
            row()
        }
    }

    override fun dispose() {
        chatMessageList.dispose()
    }
}
