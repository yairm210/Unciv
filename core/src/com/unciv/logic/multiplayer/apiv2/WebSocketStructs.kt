package com.unciv.logic.multiplayer.apiv2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

/**
 * Enum of all events that can happen in a friendship
 */
enum class FriendshipEvent(val type: String) {
    Accepted("accepted"),
    Rejected("rejected"),
    Deleted("deleted");
}

/**
 * The notification for the clients that a new game has started
 */
@Serializable
data class GameStarted(
    @SerialName("gameUuid")
    @Serializable(with = UUIDSerializer::class)
    val gameUUID: UUID,
    @SerialName("gameChatUuid")
    @Serializable(with = UUIDSerializer::class)
    val gameChatUUID: UUID,
    @SerialName("lobbyUuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID,
    @SerialName("lobbyChatUuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyChatUUID: UUID,
)

/**
 * An update of the game data
 *
 * This variant is sent from the server to all accounts that are in the game.
 */
@Serializable
data class UpdateGameData(
    @SerialName("gameId")
    @Serializable(with = UUIDSerializer::class)
    val gameUUID: UUID,
    val gameData: String,  // base64-encoded, gzipped game state
    /** A counter that is incremented every time a new game states has been uploaded for the same [gameUUID] via HTTP API. */
    @SerialName("gameDataId")
    val gameDataID: Long
)

/**
 * Notification for clients if a client in their game disconnected
 */
@Serializable
data class ClientDisconnected(
    @SerialName("gameUuid")
    @Serializable(with = UUIDSerializer::class)
    val gameUUID: UUID,
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID  // client identifier
)

/**
 * Notification for clients if a client in their game reconnected
 */
@Serializable
data class ClientReconnected(
    @SerialName("gameUuid")
    @Serializable(with = UUIDSerializer::class)
    val gameUUID: UUID,
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID  // client identifier
)

/**
 * A new chat message is sent to the client
 */
@Serializable
data class IncomingChatMessage(
    @SerialName("chatUuid")
    @Serializable(with = UUIDSerializer::class)
    val chatUUID: UUID,
    val message: ChatMessage
)

/**
 * An invite to a lobby is sent to the client
 */
@Serializable
data class IncomingInvite(
    @SerialName("inviteUuid")
    @Serializable(with = UUIDSerializer::class)
    val inviteUUID: UUID,
    val from: AccountResponse,
    @SerialName("lobbyUuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID
)

/**
 * A friend request is sent to a client
 */
@Serializable
data class IncomingFriendRequest(
    val from: AccountResponse
)

/**
 * A friendship was modified
 */
@Serializable
data class FriendshipChanged(
    val friend: AccountResponse,
    val event: FriendshipEvent
)

/**
 * A new player joined the lobby
 */
@Serializable
data class LobbyJoin(
    @SerialName("lobbyUuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID,
    val player: AccountResponse
)

/**
 * A lobby closed in which the client was part of
 */
@Serializable
data class LobbyClosed(
    @SerialName("lobbyUuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID
)

/**
 * A player has left the lobby
 */
@Serializable
data class LobbyLeave(
    @SerialName("lobbyUuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID,
    val player: AccountResponse
)

/**
 * A player was kicked out of the lobby.
 *
 * Make sure to check the player if you were kicked ^^
 */
@Serializable
data class LobbyKick(
    @SerialName("lobbyUuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID,
    val player: AccountResponse
)

/**
 * The user account was updated
 *
 * This might be especially useful for reflecting changes in the username, etc. in the frontend
 */
@Serializable
data class AccountUpdated(
    val account: AccountResponse
)

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
 * Message to indicate that a game started
 */
@Serializable
data class GameStartedMessage (
    override val type: WebSocketMessageType,
    val content: GameStarted
) : WebSocketMessage

/**
 * Message to publish the new game state from the server to all clients
 */
@Serializable
data class UpdateGameDataMessage (
    override val type: WebSocketMessageType,
    val content: UpdateGameData
) : WebSocketMessage

/**
 * Message to indicate that a client disconnected
 */
@Serializable
data class ClientDisconnectedMessage (
    override val type: WebSocketMessageType,
    val content: ClientDisconnected
) : WebSocketMessage

/**
 * Message to indicate that a client, who previously disconnected, reconnected
 */
@Serializable
data class ClientReconnectedMessage (
    override val type: WebSocketMessageType,
    val content: ClientReconnected
) : WebSocketMessage

/**
 * Message to indicate that a user received a new text message via the chat feature
 */
@Serializable
data class IncomingChatMessageMessage (
    override val type: WebSocketMessageType,
    val content: IncomingChatMessage
) : WebSocketMessage

/**
 * Message to indicate that a client gets invited to a lobby
 */
@Serializable
data class IncomingInviteMessage (
    override val type: WebSocketMessageType,
    val content: IncomingInvite
) : WebSocketMessage

/**
 * Message to indicate that a client received a friend request
 */
@Serializable
data class IncomingFriendRequestMessage (
    override val type: WebSocketMessageType,
    val content: IncomingFriendRequest
) : WebSocketMessage

/**
 * Message to indicate that a friendship has changed
 */
@Serializable
data class FriendshipChangedMessage (
    override val type: WebSocketMessageType,
    val content: FriendshipChanged
) : WebSocketMessage

/**
 * Message to indicate that a client joined the lobby
 */
@Serializable
data class LobbyJoinMessage (
    override val type: WebSocketMessageType,
    val content: LobbyJoin
) : WebSocketMessage

/**
 * Message to indicate that the current lobby got closed
 */
@Serializable
data class LobbyClosedMessage (
    override val type: WebSocketMessageType,
    val content: LobbyClosed
) : WebSocketMessage

/**
 * Message to indicate that a client left the lobby
 */
@Serializable
data class LobbyLeaveMessage (
    override val type: WebSocketMessageType,
    val content: LobbyLeave
) : WebSocketMessage

/**
 * Message to indicate that a client got kicked out of the lobby
 */
@Serializable
data class LobbyKickMessage (
    override val type: WebSocketMessageType,
    val content: LobbyKick
) : WebSocketMessage

/**
 * Message to indicate that the current user account's data have been changed
 */
@Serializable
data class AccountUpdatedMessage (
    override val type: WebSocketMessageType,
    val content: AccountUpdated
) : WebSocketMessage

/**
 * Type enum of all known WebSocket messages
 */
@Serializable(with = WebSocketMessageTypeSerializer::class)
enum class WebSocketMessageType(val type: String) {
    InvalidMessage("invalidMessage"),
    UpdateGameData("updateGameData"),
    ClientDisconnected("clientDisconnected"),
    ClientReconnected("clientReconnected"),
    IncomingChatMessage("incomingChatMessage"),
    IncomingInvite("incomingInvite"),
    GameStarted("gameStarted");

    companion object {
        private val VALUES = values()
        fun getByValue(type: String) = VALUES.first { it.type == type }
    }
}
