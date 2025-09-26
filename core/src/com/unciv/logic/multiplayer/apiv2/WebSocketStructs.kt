package com.unciv.logic.multiplayer.apiv2

import com.unciv.logic.event.Event
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Enum of all events that can happen in a friendship
 */
@Serializable(with = FriendshipEventSerializer::class)
enum class FriendshipEvent(val type: String) {
    Accepted("accepted"),
    Rejected("rejected"),
    Deleted("deleted");

    companion object {
        private val VALUES = FriendshipEvent.entries
        fun getByValue(type: String) = VALUES.first { it.type == type }
    }
}

/**
 * The notification for the clients that a new game has started
 */
@Serializable
data class GameStarted(
    @SerialName("game_uuid")
    @Serializable(with = UUIDSerializer::class)
    val gameUUID: UUID,
    @SerialName("game_chat_uuid")
    @Serializable(with = UUIDSerializer::class)
    val gameChatUUID: UUID,
    @SerialName("lobby_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID,
    @SerialName("lobby_chat_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyChatUUID: UUID,
) : Event

/**
 * An update of the game data
 *
 * This variant is sent from the server to all accounts that are in the game.
 */
@Serializable
data class UpdateGameData(
    @SerialName("game_uuid")
    @Serializable(with = UUIDSerializer::class)
    val gameUUID: UUID,
    @SerialName("game_data")
    val gameData: String,  // base64-encoded, gzipped game state
    /** A counter that is incremented every time a new game states has been uploaded for the same [gameUUID] via HTTP API. */
    @SerialName("game_data_id")
    val gameDataID: Long
) : Event

/**
 * Notification for clients if a client in their game disconnected
 */
@Serializable
data class ClientDisconnected(
    @SerialName("game_uuid")
    @Serializable(with = UUIDSerializer::class)
    val gameUUID: UUID,
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID  // client identifier
) : Event

/**
 * Notification for clients if a client in their game reconnected
 */
@Serializable
data class ClientReconnected(
    @SerialName("game_uuid")
    @Serializable(with = UUIDSerializer::class)
    val gameUUID: UUID,
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID  // client identifier
) : Event

/**
 * A new chat message is sent to the client
 */
@Serializable
data class IncomingChatMessage(
    @SerialName("chat_uuid")
    @Serializable(with = UUIDSerializer::class)
    val chatUUID: UUID,
    val message: ChatMessage
) : Event

/**
 * An invite to a lobby is sent to the client
 */
@Serializable
data class IncomingInvite(
    @SerialName("invite_uuid")
    @Serializable(with = UUIDSerializer::class)
    val inviteUUID: UUID,
    val from: AccountResponse,
    @SerialName("lobby_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID
) : Event

/**
 * A friend request is sent to a client
 */
@Serializable
data class IncomingFriendRequest(
    val from: AccountResponse
) : Event

/**
 * A friendship was modified
 */
@Serializable
data class FriendshipChanged(
    val friend: AccountResponse,
    val event: FriendshipEvent
) : Event

/**
 * A new player joined the lobby
 */
@Serializable
data class LobbyJoin(
    @SerialName("lobby_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID,
    val player: AccountResponse
) : Event

/**
 * A lobby closed in which the client was part of
 */
@Serializable
data class LobbyClosed(
    @SerialName("lobby_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID
) : Event

/**
 * A player has left the lobby
 */
@Serializable
data class LobbyLeave(
    @SerialName("lobby_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID,
    val player: AccountResponse
) : Event

/**
 * A player was kicked out of the lobby.
 *
 * Make sure to check the player if you were kicked ^^
 */
@Serializable
data class LobbyKick(
    @SerialName("lobby_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID,
    val player: AccountResponse
) : Event

/**
 * The user account was updated
 *
 * This might be especially useful for reflecting changes in the username, etc. in the frontend
 */
@Serializable
data class AccountUpdated(
    val account: AccountResponse
) : Event

/**
 * The base WebSocket message, encapsulating only the type of the message
 */
interface WebSocketMessage {
    val type: WebSocketMessageType
}

/**
 * The useful base WebSocket message, encapsulating only the type of the message and the content
 */
interface WebSocketMessageWithContent: WebSocketMessage {
    override val type: WebSocketMessageType
    val content: Event
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
    override val content: GameStarted
) : WebSocketMessageWithContent

/**
 * Message to publish the new game state from the server to all clients
 */
@Serializable
data class UpdateGameDataMessage (
    override val type: WebSocketMessageType,
    override val content: UpdateGameData
) : WebSocketMessageWithContent

/**
 * Message to indicate that a client disconnected
 */
@Serializable
data class ClientDisconnectedMessage (
    override val type: WebSocketMessageType,
    override val content: ClientDisconnected
) : WebSocketMessageWithContent

/**
 * Message to indicate that a client, who previously disconnected, reconnected
 */
@Serializable
data class ClientReconnectedMessage (
    override val type: WebSocketMessageType,
    override val content: ClientReconnected
) : WebSocketMessageWithContent

/**
 * Message to indicate that a user received a new text message via the chat feature
 */
@Serializable
data class IncomingChatMessageMessage (
    override val type: WebSocketMessageType,
    override val content: IncomingChatMessage
) : WebSocketMessageWithContent

/**
 * Message to indicate that a client gets invited to a lobby
 */
@Serializable
data class IncomingInviteMessage (
    override val type: WebSocketMessageType,
    override val content: IncomingInvite
) : WebSocketMessageWithContent

/**
 * Message to indicate that a client received a friend request
 */
@Serializable
data class IncomingFriendRequestMessage (
    override val type: WebSocketMessageType,
    override val content: IncomingFriendRequest
) : WebSocketMessageWithContent

/**
 * Message to indicate that a friendship has changed
 */
@Serializable
data class FriendshipChangedMessage (
    override val type: WebSocketMessageType,
    override val content: FriendshipChanged
) : WebSocketMessageWithContent

/**
 * Message to indicate that a client joined the lobby
 */
@Serializable
data class LobbyJoinMessage (
    override val type: WebSocketMessageType,
    override val content: LobbyJoin
) : WebSocketMessageWithContent

/**
 * Message to indicate that the current lobby got closed
 */
@Serializable
data class LobbyClosedMessage (
    override val type: WebSocketMessageType,
    override val content: LobbyClosed
) : WebSocketMessageWithContent

/**
 * Message to indicate that a client left the lobby
 */
@Serializable
data class LobbyLeaveMessage (
    override val type: WebSocketMessageType,
    override val content: LobbyLeave
) : WebSocketMessageWithContent

/**
 * Message to indicate that a client got kicked out of the lobby
 */
@Serializable
data class LobbyKickMessage (
    override val type: WebSocketMessageType,
    override val content: LobbyKick
) : WebSocketMessageWithContent

/**
 * Message to indicate that the current user account's data have been changed
 */
@Serializable
data class AccountUpdatedMessage (
    override val type: WebSocketMessageType,
    override val content: AccountUpdated
) : WebSocketMessageWithContent

/**
 * Type enum of all known WebSocket messages
 */
@Serializable(with = WebSocketMessageTypeSerializer::class)
enum class WebSocketMessageType(val type: String) {
    InvalidMessage("invalidMessage"),
    GameStarted("gameStarted"),
    UpdateGameData("updateGameData"),
    ClientDisconnected("clientDisconnected"),
    ClientReconnected("clientReconnected"),
    IncomingChatMessage("incomingChatMessage"),
    IncomingInvite("incomingInvite"),
    IncomingFriendRequest("incomingFriendRequest"),
    FriendshipChanged("friendshipChanged"),
    LobbyJoin("lobbyJoin"),
    LobbyClosed("lobbyClosed"),
    LobbyLeave("lobbyLeave"),
    LobbyKick("lobbyKick"),
    AccountUpdated("accountUpdated");

    companion object {
        fun getByValue(type: String) = entries.first { it.type == type }
    }
}
