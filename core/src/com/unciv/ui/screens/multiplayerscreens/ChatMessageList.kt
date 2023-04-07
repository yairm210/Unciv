package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.logic.multiplayer.apiv2.AccountResponse
import com.unciv.logic.multiplayer.apiv2.ApiV2
import com.unciv.logic.multiplayer.apiv2.ChatMessage
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.concurrency.Concurrency
import java.time.Instant
import java.util.*

/**
 * Simple list for messages from a multiplayer chat
 *
 * You most likely want to center this actor in a parent and wrap it in a
 * [AutoScrollPane] and set [AutoScrollPane.setScrollingDisabled] for the X direction.
 * You **must** set [parent] to this actor's parent, otherwise width calculations won't
 * work, which is required to avoid scrolling in X direction due to overfull lines of text.
 *
 * @sample
 *      val chatMessages = ChatMessageList(UUID.randomUUID())
 *      val chatScroll = AutoScrollPane(chatMessages, skin)
 *      chatScroll.setScrollingDisabled(true, false)
 *
 * Another good way is to use the [ChatTable] directly.
 */
class ChatMessageList(private val chatRoomUUID: UUID, private val mp: OnlineMultiplayer): Table() {
    init {
        defaults().expandX().space(5f)
        recreate(listOf<ChatMessage>())
    }

    /**
     * Send a [message] to the chat room by dispatching a coroutine which handles it
     */
    fun sendMessage(message: String) {
        // TODO
    }

    /**
     * Trigger a background refresh of the chat messages
     *
     * This will update the messages of the chat room by querying the server
     * and then recreate the message list in a separate coroutine.
     * Use [suppress] to avoid showing an [InfoPopup] for any failures.
     */
    fun triggerRefresh(suppress: Boolean = false) {
        Concurrency.run {
            if (suppress) {
                val chatInfo = mp.api.chat.get(chatRoomUUID, true)
                if (chatInfo != null) {
                    Concurrency.runOnGLThread {
                        recreate(chatInfo.messages)
                    }
                }
            } else {
                InfoPopup.wrap(stage) {
                    val chatInfo = mp.api.chat.get(chatRoomUUID, false)
                    if (chatInfo != null) {
                        Concurrency.runOnGLThread {
                            recreate(chatInfo.messages)
                        }
                    }
                }
            }
        }
    }

    /**
     * Recreate the message list from strings for testing purposes using random fill data
     */
    internal fun recreate(messages: List<String>) {
        recreate(messages.map { ChatMessage(UUID.randomUUID(), AccountResponse("user", "User", UUID.randomUUID()), it, Instant.now()) })
    }

    /**
     * Recreate the table of messages using the given list of chat messages
     */
    fun recreate(messages: List<ChatMessage>) {
        clearChildren()
        if (messages.isEmpty()) {
            val label = "No messages here yet".toLabel()
            label.setAlignment(Align.center)
            add(label).fillX().fillY().center()
            return
        }

        for (message in messages) {
            row()
            val label = Label("${message.sender.displayName} [${message.sender.username}] (${message.createdAt}):\n${message.message}", BaseScreen.skin)
            label.setAlignment(Align.left)
            label.wrap = true
            val cell = add(label)
            cell.fillX()
        }
    }

}
