/**
 * Collection of API response structs in a single file for simplicity
 */

package com.unciv.logic.multiplayer.apiv2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

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
 */
@Serializable(with = ApiStatusCodeSerializer::class)
enum class ApiStatusCode(val value: Int) {
    Unauthenticated(1000),
    NotFound(1001),
    InvalidContentType(1002),
    InvalidJson(1003),
    PayloadOverflow(1004),

    LoginFailed(1005),
    UsernameAlreadyOccupied(1006),
    InvalidPassword(1007),
    EmptyJson(1008),
    InvalidUsername(1009),
    InvalidDisplayName(1010),
    FriendshipAlreadyRequested(1011),
    AlreadyFriends(1012),
    MissingPrivileges(1013),
    InvalidMaxPlayersCount(1014),
    AlreadyInALobby(1015),
    InvalidUuid(1016),
    InvalidLobbyUuid(1017),
    InvalidFriendUuid(1018),
    GameNotFound(1019),
    InvalidMessage(1020),
    WsNotConnected(1021),
    LobbyFull(1022),
    InvalidPlayerUUID(1023),

    InternalServerError(2000),
    DatabaseError(2001),
    SessionError(2002);

    companion object {
        private val VALUES = values()
        fun getByValue(value: Int) = VALUES.first { it.value == value }
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
 * Internal wrapper around [GetAllChatsResponse] that prevents serialization issues of lists of [UUID]s
 */
@Serializable
internal class GetAllChatsResponseImpl(
    @SerialName("friend_chat_rooms")
    val friendChatRooms: List<String>,
    @SerialName("game_chat_rooms")
    val gameChatRooms: List<String>,
    @SerialName("lobby_chat_rooms")
    val lobbyChatRooms: List<String>
) {
    internal fun to() = GetAllChatsResponse(
        friendChatRooms.map { UUID.fromString(it) },
        gameChatRooms.map { UUID.fromString(it) },
        lobbyChatRooms.map { UUID.fromString(it) }
    )
}

/**
 * All chat rooms your user has access to
 */
data class GetAllChatsResponse(
    val friendChatRooms: List<UUID>,
    val gameChatRooms: List<UUID>,
    val lobbyChatRooms: List<UUID>
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
