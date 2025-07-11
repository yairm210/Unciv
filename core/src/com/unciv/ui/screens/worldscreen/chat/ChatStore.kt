package com.unciv.ui.screens.worldscreen.chat

import com.unciv.logic.event.Event
import com.unciv.logic.event.EventBus

internal data class NewChatMessageEvent(
    val gameId: String,
    val civName: String,
    val message: String,
) : Event

internal data class Chat(
    val gameId: String,
    // <civName, message> pairs
    private val messages: MutableList<Pair<String, String>> = mutableListOf(),
) {
    fun addMessage(civName: String, message: String) {
        messages.add(civName to message)
        EventBus.send(NewChatMessageEvent(gameId, civName, message))
    }

    fun forEachMessage(action: (String, String) -> Unit) {
        for ((civName, message) in messages) {
            action(civName, message)
        }
    }
}

internal object ChatStore {
    private val gameIdToChat = mutableMapOf<String, Chat>()

    fun getChatByGameId(gameId: String) = gameIdToChat.getOrPut(gameId) { Chat(gameId) }
}
