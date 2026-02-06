package com.unciv.logic.multiplayer.chat

import java.util.concurrent.CancellationException

sealed class Message {
    data class Chat(
        val civName: String,
        val message: String,
        val gameId: String,
    ) : Message()

    data class Join(
        val gameIds: List<String>,
    ) : Message()

    data class Leave(
        val gameIds: List<String>,
    ) : Message()
}

sealed class Response {
    data class Chat(
        val civName: String,
        val message: String,
        val gameId: String? = null,
    ) : Response()

    data class JoinSuccess(
        val gameIds: List<String>,
    ) : Response()

    data class Error(
        val message: String,
    ) : Response()
}

class ChatRestartException : CancellationException("Chat restart requested")
class ChatStopException : CancellationException("Chat stop requested")

object ChatWebSocket {
    fun requestMessageSend(message: Message) {
        // Online chat is disabled on web phase-1.
        if (message is Message.Chat) {
            ChatStore.relayGlobalMessage("Chat is unavailable on web in this phase.")
        }
    }

    fun stop() {
        ChatStore.clear()
    }

    fun restart(dueToError: Boolean = false, force: Boolean = false) {
        if (dueToError || force) {
            ChatStore.relayGlobalMessage("Chat is unavailable on web in this phase.")
        }
    }
}
