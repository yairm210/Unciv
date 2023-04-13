package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.ArrowButton
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.RefreshButton
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * A [Table] which combines [ChatMessageList] with a text input and send button to write a new message
 *
 * Optionally, it can display a [RefreshButton] to the right of the send button.
 */
class ChatTable(private val chatMessageList: ChatMessageList, showRefreshButton: Boolean, maxLength: Int? = null): Table() {
    init {
        val chatScroll = AutoScrollPane(chatMessageList, BaseScreen.skin)
        chatScroll.setScrollingDisabled(true, false)
        val width = if (showRefreshButton) 3 else 2
        add(chatScroll).colspan(width).fillX().expandY().padBottom(10f)
        row()

        val nameField = UncivTextField.create("New message")
        if (maxLength != null) {
            nameField.maxLength = maxLength
        }
        val sendButton = ArrowButton()
        sendButton.onActivation {
            chatMessageList.sendMessage(nameField.text)
            nameField.text = ""
        }
        sendButton.keyShortcuts.add(KeyCharAndCode.RETURN)

        add(nameField).padLeft(5f).growX()
        if (showRefreshButton) {
            add(sendButton).padLeft(10f).padRight(10f)
            val refreshButton = RefreshButton()
            refreshButton.onActivation {
                chatMessageList.triggerRefresh(false)
            }
            add(refreshButton).padRight(5f)
        } else {
            add(sendButton).padLeft(10f).padRight(5f)
        }
        row()
    }

    fun dispose() {
        chatMessageList.dispose()
    }
}
