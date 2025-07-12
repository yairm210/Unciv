package com.unciv.ui.screens.worldscreen.chat

import com.unciv.logic.event.Event
import com.unciv.logic.event.EventBus

internal data class ChatMessageSendRequestEvent(
    val gameId: String,
    val civName: String,
    val message: String,
) : Event

internal data class ChatMessageReceivedEvent(
    val gameId: String,
    val civName: String,
    val message: String,
) : Event

private val INITIAL_MESSAGE = "System" to "Welcome to Chat!"

internal data class Chat(
    val gameId: String,
) {
    // <civName, message> pairs
    private val messages: MutableList<Pair<String, String>> = mutableListOf(INITIAL_MESSAGE)

    fun length(): Int = messages.size

    /**
     * Only requests a message to be sent, does not guarantee delivery.
     *
     * The server will relay it back if a delivery was acknowledged and that is when we should display it.
     * **/
    fun requestMessageSend(civName: String, message: String) {
        EventBus.send(ChatMessageSendRequestEvent(gameId, civName, message))
    }

    /**
     * Although public, this should only be called when a ChatMessageReceivedEvent is received once.
     */
    internal fun addMessage(civName: String, message: String) {
        messages.add(Pair(civName, message))
    }

    fun forEachMessage(action: (String, String) -> Unit) {
        for ((civName, message) in messages) {
            action(civName, message)
        }
    }
}

internal object ChatStore {
    private var eventReceiver = EventBus.EventReceiver()

    private val gameIdToChat = mutableMapOf<String, Chat>()

    fun getChatByGameId(gameId: String) = gameIdToChat.getOrPut(gameId) { Chat(gameId) }

    init {
        eventReceiver.receive(ChatMessageReceivedEvent::class) {
            getChatByGameId(it.gameId).addMessage(it.civName, it.message)
        }
    }
}
