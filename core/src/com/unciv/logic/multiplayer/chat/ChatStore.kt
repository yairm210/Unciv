package com.unciv.logic.multiplayer.chat

import com.badlogic.gdx.Gdx
import com.unciv.ui.screens.worldscreen.chat.ChatPopup
import com.unciv.utils.isUUID
import java.util.LinkedList
import java.util.Queue

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
        Gdx.app.postRunnable {
            ChatWebSocket.requestMessageSend(Message.Chat(civName, message, gameId))
        }
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
    private var globalMessages: Queue<Pair<String, String>> = LinkedList()

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
        Gdx.app.postRunnable {
            if (chat.gameId == null || chat.gameId.isBlank()) {
                relayGlobalMessage(chat.message, chat.civName)
            } else {
                // Discard messages with invalid UUID
                if (!chat.gameId.isUUID()) return@postRunnable

                getChatByGameId(chat.gameId).addMessage(chat.civName, chat.message)
                if (chatPopup?.chat?.gameId == chat.gameId) {
                    chatPopup?.addMessage(chat.civName, chat.message)
                }
            }
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
            chatPopup?.addMessage(civName, message, suffix = "one time") ?: globalMessages.add(Pair(civName, message))
        }
    }
}
