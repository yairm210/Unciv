package com.unciv.logic.multiplayer.chat

import com.badlogic.gdx.Gdx
import com.unciv.logic.event.Event
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
    }
}

object ChatStore {
    /** The reference to the [ChatPopup] instance which trying to take a peek at our messages currently.
     * What audacity this popup has!!
     */
    var chatPopup: ChatPopup? = null

    private var gameIdToChat = mutableMapOf<String, Chat>()

    /** When no [ChatPopup] is open to receive these oddities, we keep them here.
     * Certainly better than not knowing why the socket closed.
     */
    private var globalMessages = LinkedList<Pair<String, String>>()

    fun getChatByGameId(gameId: String) = gameIdToChat.getOrPut(gameId) { Chat(gameId) }

    fun getGameIds() = gameIdToChat.keys.toSet()

    /**
     * Clears chat by triggering a garbage collection.
     */
    fun clear() {
        gameIdToChat = mutableMapOf()
        globalMessages = LinkedList()
    }
    
    fun relayChatMessage(chat: Response.Chat) {
        getChatByGameId(chat.gameId).addMessage(chat.civName, chat.message)
        if (chatPopup?.chat?.gameId == chat.gameId) {
            chatPopup?.addMessage(chat.civName, chat.message)
        }
    }

    fun pollGlobalMessages(action: (String, String) -> Unit) {
        Gdx.app.postRunnable {
            while (globalMessages.isNotEmpty()) {
                val item = globalMessages.poll()
                action(item.first, item.second)
            }
        }
    }

    fun relayGlobalMessage(message: String, civName: String = "System") {
        Gdx.app.postRunnable {
            chatPopup?.addMessage(civName, message, suffix = "one time")
                ?: globalMessages.add(Pair(civName, message))
        }
    }
}
