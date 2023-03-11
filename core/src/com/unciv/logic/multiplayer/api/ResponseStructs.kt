/**
 * Collection of API response structs in a single file for simplicity
 */

package com.unciv.logic.multiplayer.api

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
    override val message: String,
    @SerialName("status_code")
    @Serializable(with = ApiStatusCodeSerializer::class)
    val statusCode: ApiStatusCode
) : Throwable()

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
    InvalidId(1013),
    MissingPrivileges(1014),
    InvalidMaxPlayersCount(1017),
    AlreadyInALobby(1018),

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
 * The parameter [id] should be used to uniquely identify a message.
 */
@Serializable
data class ChatMessage(
    val id: Long,
    val sender: AccountResponse,
    val message: String,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant
)

/**
 * The response of a create lobby request, which contains the [lobbyID] of the created lobby
 */
@Serializable
data class CreateLobbyResponse(
    @SerialName("lobby_id")
    val lobbyID: Long
)

/**
 * A single friend
 */
@Serializable
data class FriendResponse(
    val id: Long,
    val from: AccountResponse,
    val to: OnlineAccountResponse
)

/**
 * A single friend request
 */
@Serializable
data class FriendRequestResponse(
    val id: Long,
    val from: AccountResponse,
    val to: AccountResponse
)

/**
 * All chat rooms your user has access to
 */
@Serializable
data class GetAllChatsResponse(
    @SerialName("friend_chat_rooms")
    val friendChatRooms: List<Long>,
    @SerialName("lobby_chat_rooms")
    val lobbyChatRooms: List<Long>
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
 * The lobbies that are open
 */
@Serializable
data class GetLobbiesResponse(
    val lobbies: List<LobbyResponse>
)

/**
 * A single lobby
 */
@Serializable
data class LobbyResponse(
    val id: Long,
    val name: String,
    @SerialName("max_players")
    val maxPlayers: Int,
    @SerialName("current_players")
    val currentPlayers: Int,
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
 * It provides the extra field ``online`` indicating whether the account has any connected client.
 */
@Serializable
data class OnlineAccountResponse(
    val online: Boolean,
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID,
    val username: String,
    @SerialName("display_name")
    val displayName: String
)

/**
 * The version data for clients
 */
@Serializable
data class VersionResponse(
    val version: Int
)
