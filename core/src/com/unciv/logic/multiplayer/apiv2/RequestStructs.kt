/**
 * Collection of API request structs in a single file for simplicity
 */

package com.unciv.logic.multiplayer.apiv2

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.UUID

/**
 * The content to register a new account
 */
@Serializable
data class AccountRegistrationRequest(
    val username: String,
    @SerialName("display_name")
    val displayName: String,
    val password: String
)

/**
 * The request of a new friendship
 */
@Serializable
data class CreateFriendRequest(
    @Serializable(with = UUIDSerializer::class)
    val uuid: UUID
)

/**
 * The request to invite a friend into a lobby
 */
@Serializable
data class CreateInviteRequest(
    @SerialName("friend_uuid")
    @Serializable(with = UUIDSerializer::class)
    val friendUUID: UUID,
    @SerialName("lobby_uuid")
    @Serializable(with = UUIDSerializer::class)
    val lobbyUUID: UUID
)

/**
 * The parameters to create a lobby
 *
 * The parameter [maxPlayers] must be greater or equals 2.
 */
@Serializable
data class CreateLobbyRequest(
    val name: String,
    val password: String?,
    @SerialName("max_players")
    val maxPlayers: Int
)

/**
 * The request a user sends to the server to upload a new game state (non-WebSocket API)
 *
 * The game's UUID has to be set via the path argument of the endpoint.
 */
@Serializable
data class GameUploadRequest(
    @SerialName("game_data")
    val gameData: String
)

/**
 * The request to join a lobby
 */
@Serializable
data class JoinLobbyRequest(
    val password: String? = null
)

/**
 * The request data of a login request
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * The request to lookup an account by its username
 */
@Serializable
data class LookupAccountUsernameRequest(
    val username: String
)

/**
 * The request for sending a message to a chatroom
 */
@Serializable
data class SendMessageRequest(
    val message: String
)

/**
 * The set password request data
 *
 * The parameter [newPassword] must not be empty.
 */
@Serializable
data class SetPasswordRequest(
    @SerialName("old_password")
    val oldPassword: String,
    @SerialName("new_password")
    val newPassword: String
)

/**
 * Update account request data
 *
 * All parameter are optional, but at least one of them is required.
 */
@Serializable
data class UpdateAccountRequest(
    val username: String?,
    @SerialName("display_name")
    val displayName: String?
)
