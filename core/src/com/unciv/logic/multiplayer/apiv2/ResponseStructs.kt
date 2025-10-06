/**
 * Collection of API response structs in a single file for simplicity
 */

package com.unciv.logic.multiplayer.apiv2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * The account data
 */
@Serializable
data class AccountResponse(
    val username: String,
    @SerialName("display_name")
    val displayName: String,
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID
)

/**
 * The Response that is returned in case of an error
 *
 * For client errors the HTTP status code will be 400, for server errors the 500 will be used.
 */
@Serializable
data class ApiErrorResponse(
    val message: String,
    @SerialName("status_code")
    @Serializable(with = ApiStatusCodeSerializer::class)
    val statusCode: ApiStatusCode
) {

    /**
     * Convert the [ApiErrorResponse] to a [ApiException] for throwing and showing to users
     */
    fun to() = ApiException(this)
}

/**
 * API status code enum for mapping integer codes to names
 *
 * The status code represents a unique identifier for an error.
 * Error codes in the range of 1000..2000 represent client errors that could be handled
 * by the client. Error codes in the range of 2000..3000 represent server errors.
 * The [message] is a user-showable default string for every possible status code.
 */
@Serializable(with = ApiStatusCodeSerializer::class)
enum class ApiStatusCode(val value: Int, val message: String) {
    Unauthenticated(1000, "You are not logged in. Please login first."),
    NotFound(1001, "The operation couldn't be completed, since the resource was not found."),
    InvalidContentType(1002, "The media content type was invalid. Please report this as a bug."),
    InvalidJson(1003, "The server didn't understand the sent data. Please report this as a bug."),
    PayloadOverflow(1004, "The amount of data sent to the server was too large. Please report this as a bug."),

    LoginFailed(1005, "The login failed. Is the username and password correct?"),
    UsernameAlreadyOccupied(1006, "The selected username is already taken. Please choose another name."),
    InvalidPassword(1007, "This password is not valid. Please choose another password."),
    EmptyJson(1008, "The server encountered an empty JSON problem. Please report this as a bug."),
    InvalidUsername(1009, "The username is not valid. Please choose another one."),
    InvalidDisplayName(1010, "The display name is not valid. Please choose another one."),
    FriendshipAlreadyRequested(1011, "You have already requested friendship with this player. Please wait until the request is accepted."),
    AlreadyFriends(1012, "You are already friends, you can't request it again."),
    MissingPrivileges(1013, "You don't have the required privileges to perform this operation."),
    InvalidMaxPlayersCount(1014, "The maximum number of players for this lobby is out of the supported range for this server. Please adjust the number. Two players should always work."),
    AlreadyInALobby(1015, "You are already in another lobby. You need to close or leave the other lobby before."),
    InvalidUuid(1016, "The operation could not be completed, since an invalid UUID was given. Please retry later or restart the game. If the problem persists, please report this as a bug."),
    InvalidLobbyUuid(1017, "The lobby was not found. Maybe it has already been closed?"),
    InvalidFriendUuid(1018, "You must be friends with the other player before this action can be completed. Try again later."),
    GameNotFound(1019, "The game was not found on the server. Try again later. If the problem persists, the game was probably already removed from the server, sorry."),
    InvalidMessage(1020, "This message could not be sent, since it was invalid. Remove any invalid characters and try again."),
    WsNotConnected(1021, "The WebSocket is not available. Please restart the game and try again. If the problem persists, please report this as a bug."),
    LobbyFull(1022, "The lobby is currently full. You can't join right now."),
    InvalidPlayerUUID(1023, "The ID of the player was invalid. Does the player exist? Please try again. If the problem persists, please report this as a bug."),

    InternalServerError(2000, "Internal server error. Please report this as a bug."),
    DatabaseError(2001, "Internal server database error. Please report this as a bug."),
    SessionError(2002, "Internal session error. Please report this as a bug.");

    companion object {
        fun getByValue(value: Int) = entries.first { it.value == value }
    }
}

/**
 * A member of a chatroom
 */
@Serializable
data class ChatMember(
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    val username: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("joined_at")
    @Serializable(with = InstantSerializer::class)
    val joinedAt: Instant
)

/**
 * The message of a chatroom
 *
 * The parameter [uuid] should be used to uniquely identify a message.
 */
@Serializable
data class ChatMessage(
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    val sender: AccountResponse,
    val message: String,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant
)

/**
 * The small representation of a chatroom
 */
@Serializable
data class ChatSmall(
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    @SerialName("last_message_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lastMessageUUID: UUID? = null
)

/**
 * The response of a create lobby request, which contains the [lobbyUUID] and [lobbyChatRoomUUID]
 */
@Serializable
data class CreateLobbyResponse(
    @SerialName("lobby_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID,
    @SerialName("lobby_chat_room_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyChatRoomUUID: UUID
)

/**
 * A single friend (the relationship is identified by the [uuid])
 */
@Serializable
data class FriendResponse(
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    @SerialName("chat_uuid")
    @Serializable(with = UUIDSerializer::class)
    val chatUUID: UUID,
    val friend: OnlineAccountResponse
)

/**
 * A single friend request
 *
 * Use [from] and [to] comparing with "myself" to determine if it's incoming or outgoing.
 */
@Serializable
data class FriendRequestResponse(
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    val from: AccountResponse,
    val to: AccountResponse
)

/**
 * A shortened game state identified by its ID and state identifier
 *
 * If the state ([gameDataID]) of a known game differs from the last known
 * identifier, the server has a newer state of the game. The [lastActivity]
 * field is a convenience attribute and shouldn't be used for update checks.
 */
@Serializable
data class GameOverviewResponse(
    @SerialName("chat_room_uuid")
    @Serializable(with = UUIDSerializer::class)
    val chatRoomUUID: UUID,
    @SerialName("game_data_id")
    val gameDataID: Long,
    @SerialName("game_uuid")
    @Serializable(with = UUIDSerializer::class)
    val gameUUID: UUID,
    @SerialName("last_activity")
    @Serializable(with = InstantSerializer::class)
    val lastActivity: Instant,
    @SerialName("last_player")
    val lastPlayer: AccountResponse,
    @SerialName("max_players")
    val maxPlayers: Int,
    val name: String
)

/**
 * A single game state identified by its ID and state identifier; see [gameData]
 *
 * If the state ([gameDataID]) of a known game differs from the last known
 * identifier, the server has a newer state of the game. The [lastActivity]
 * field is a convenience attribute and shouldn't be used for update checks.
 */
@Serializable
data class GameStateResponse(
    @SerialName("chat_room_uuid")
    @Serializable(with = UUIDSerializer::class)
    val chatRoomUUID: UUID,
    @SerialName("game_data")
    val gameData: String,
    @SerialName("game_data_id")
    val gameDataID: Long,
    @SerialName("last_activity")
    @Serializable(with = InstantSerializer::class)
    val lastActivity: Instant,
    @SerialName("last_player")
    val lastPlayer: AccountResponse,
    @SerialName("max_players")
    val maxPlayers: Int,
    val name: String
)

/**
 * The response a user receives after uploading a new game state successfully
 */
@Serializable
data class GameUploadResponse(
    @SerialName("game_data_id")
    val gameDataID: Long
)

/**
 * All chat rooms your user has access to
 */
@Serializable
data class GetAllChatsResponse(
    @SerialName("friend_chat_rooms")
    val friendChatRooms: List<ChatSmall>,
    @SerialName("game_chat_rooms")
    val gameChatRooms: List<ChatSmall>,
    @SerialName("lobby_chat_rooms")
    val lobbyChatRooms: List<ChatSmall>
)

/**
 * The response to a get chat
 *
 * [messages] should be sorted by the datetime of message.created_at.
 */
@Serializable
data class GetChatResponse(
    val members: List<ChatMember>,
    val messages: List<ChatMessage>
)

/**
 * A list of your friends and friend requests
 *
 * [friends] is a list of already established friendships
 * [friendRequests] is a list of friend requests (incoming and outgoing)
 */
@Serializable
data class GetFriendResponse(
    val friends: List<FriendResponse>,
    @SerialName("friend_requests")
    val friendRequests: List<FriendRequestResponse>
)

/**
 * An overview of games a player participates in
 */
@Serializable
data class GetGameOverviewResponse(
    val games: List<GameOverviewResponse>
)

/**
 * A single invite
 */
@Serializable
data class GetInvite(
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    val from: AccountResponse,
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    @SerialName("lobby_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID
)

/**
 * The invites that an account has received
 */
@Serializable
data class GetInvitesResponse(
    val invites: List<GetInvite>
)

/**
 * The lobbies that are open
 */
@Serializable
data class GetLobbiesResponse(
    val lobbies: List<LobbyResponse>
)

/**
 * A single lobby (in contrast to [LobbyResponse], this is fetched by its own)
 */
@Serializable
data class GetLobbyResponse(
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    val name: String,
    @SerialName("max_players")
    val maxPlayers: Int,
    @SerialName("current_players")
    val currentPlayers: List<AccountResponse>,
    @SerialName("chat_room_uuid")
    @Serializable(with = UUIDSerializer::class)
    val chatRoomUUID: UUID,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @SerialName("password")
    val hasPassword: Boolean,
    val owner: AccountResponse
)

/**
 * A single lobby
 */
@Serializable
data class LobbyResponse(
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    val name: String,
    @SerialName("max_players")
    val maxPlayers: Int,
    @SerialName("current_players")
    val currentPlayers: Int,
    @SerialName("chat_room_uuid")
    @Serializable(with = UUIDSerializer::class)
    val chatRoomUUID: UUID,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @SerialName("password")
    val hasPassword: Boolean,
    val owner: AccountResponse
)

/**
 * The account data
 *
 * It provides the extra field [online] indicating whether the account has any connected client.
 */
@Serializable
data class OnlineAccountResponse(
    val online: Boolean,
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    val username: String,
    @SerialName("display_name")
    val displayName: String
) {
    fun to() = AccountResponse(uuid = uuid, username = username, displayName = displayName)
}

/**
 * The response when starting a game
 */
@Serializable
data class StartGameResponse(
    @SerialName("game_chat_uuid")
    @Serializable(with = UUIDSerializer::class)
    val gameChatUUID: UUID,
    @SerialName("game_uuid")
    @Serializable(with = UUIDSerializer::class)
    val gameUUID: UUID
)

/**
 * The version data for clients
 */
@Serializable
data class VersionResponse(
    val version: Int
)
