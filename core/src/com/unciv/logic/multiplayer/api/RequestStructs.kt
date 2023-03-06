/**
 * Collection of API request structs in a single file for simplicity
 */

package com.unciv.logic.multiplayer.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

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
    val username: String
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
 * The set password request data
 *
 * The parameter new_password must not be empty
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
