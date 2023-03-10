package com.unciv.logic.multiplayer.api

import kotlinx.serialization.Serializable

/**
 * The base WebSocket message, encapsulating only the type of the message
 */
interface WebSocketMessage {
    val type: WebSocketMessageType
}

/**
 * Message when a previously sent WebSocket frame a received frame is invalid
 */
@Serializable
data class InvalidMessage(
    override val type: WebSocketMessageType,
) : WebSocketMessage

/**
 * Message to upload the game state after finishing the turn
 */
@Serializable
data class FinishedTurnMessage (
    override val type: WebSocketMessageType,
    val content: String  // TODO
) : WebSocketMessage

/**
 * Message to publish the new game state from the server to all clients
 */
@Serializable
data class UpdateGameDataMessage (
    override val type: WebSocketMessageType,
    val content: String  // TODO
) : WebSocketMessage

/**
 * Message to indicate that a client disconnected
 */
@Serializable
data class ClientDisconnectedMessage (
    override val type: WebSocketMessageType,
    val content: String  // TODO
) : WebSocketMessage

/**
 * Message to indicate that a client, who previously disconnected, reconnected
 */
@Serializable
data class ClientReconnectedMessage (
    override val type: WebSocketMessageType,
    val content: String  // TODO
) : WebSocketMessage

/**
 * Message to indicate that a user received a new text message via the chat feature
 */
@Serializable
data class IncomingChatMessageMessage (
    override val type: WebSocketMessageType,
    val content: String  // TODO
) : WebSocketMessage


/**
 * Type enum of all known WebSocket messages
 */
@Serializable(with = WebSocketMessageTypeSerializer::class)
enum class WebSocketMessageType(val type: String) {
    InvalidMessage("invalidMessage"),
    FinishedTurn("finishedTurn"),
    UpdateGameData("updateGameData"),
    ClientDisconnected("clientDisconnected"),
    ClientReconnected("clientReconnected"),
    IncomingChatMessage("incomingChatMessage");

    companion object {
        private val VALUES = values()
        fun getByValue(type: String) = VALUES.first { it.type == type }
    }
}
