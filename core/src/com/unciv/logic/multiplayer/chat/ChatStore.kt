package com.unciv.logic.multiplayer.chat

import com.badlogic.gdx.Gdx
import com.unciv.logic.event.Event
import com.unciv.logic.event.EventBus
import com.unciv.ui.screens.worldscreen.chat.ChatPopup
import java.util.LinkedList

data class ChatMessageReceived(
    val gameId: String,
    val civName: String,
    val message: String,
) : Event

data class Chat(
    val gameId: String,
) {
    // <civName, message> pairs
    private val messages: MutableList<Pair<String, String>> = mutableListOf(INITIAL_MESSAGE)

    val length: Int get() = messages.size

    /**
     * Only requests a message to be sent.
     * Does not guarantee delivery.
     * Failures are mosly ignored.
     *
     * The server will relay it back if a delivery was acknowledged and that is when we should display it.
     */
    fun requestMessageSend(civName: String, message: String) {
        ChatWebSocket.requestMessageSend(Message.Chat(gameId, civName, message))
    }

    /**
     * Although public, this should only be called when a ChatMessageReceivedEvent is received once.
     */
    fun addMessage(civName: String, message: String) {
        messages.add(Pair(civName, message))
    }

    fun forEachMessage(action: (String, String) -> Unit) {
        for ((civName, message) in messages) {
            action(civName, message)
        }
    }

    companion object {
        val INITIAL_MESSAGE = "System" to "Welcome to Chat!"

        fun relayGlobalMessage(message: String, sender: String = "System") {
            Gdx.app.postRunnable {
                EventBus.send(
                    ChatMessageReceived(
                        String(), sender, message
                    )
                )
            }
        }
    }
}

object ChatStore {
    var chatPopupAvailable = false
    private val gameIdToChat = mutableMapOf<String, Chat>()

    /** When no [ChatPopup] is open to receive these oddities, we keep them here.
     * Certainly better than not knowing why the socket closed.
     */
    private val globalMessages = LinkedList<Pair<String, String>>()

    fun getChatByGameId(gameId: String) = gameIdToChat.getOrPut(gameId) { Chat(gameId) }

    fun getGameIds() = gameIdToChat.keys.toSet()

    fun pollGlobalMessages(action: (String, String) -> Unit) {
        Gdx.app.postRunnable {
            while (globalMessages.isNotEmpty()) {
                val item = globalMessages.poll()
                action(item.first, item.second)
            }
        }
    }

    fun addGlobalMessage(civName: String, message: String) {
        Gdx.app.postRunnable {
            if (chatPopupAvailable) return@postRunnable
            globalMessages.add(Pair(civName, message))
        }
    }
}
