/**
 * Collection of API response structs in a single file for simplicity
 */

package com.unciv.logic.multiplayer.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The account data
 */
@Serializable
data class AccountResponse(
    val username: String,
    @SerialName("display_name")
    val displayName: String
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
    // TODO: @JsonValue or something similar, at least in Jackson
    val statusCode: ApiStatusCode
) : Throwable()

/**
 * Experimental serializer for the ApiStatusCode enum to make encoding/decoding as integer work
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = ApiStatusCode::class)
class ApiStatusCodeSerializer : KSerializer<ApiStatusCode> {
    override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("ApiStatusCode", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: ApiStatusCode) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): ApiStatusCode {
        val key = decoder.decodeInt()
        return ApiStatusCode.getByValue(key)
    }
}

/**
 * The status code represents a unique identifier for an error.  Error codes in the range of 1000..2000 represent client errors that could be handled by the client. Error codes in the range of 2000..3000 represent server errors.
 * Values: C1000,C1001,C1002,C1003,C1004,C1005,C1006,C1007,C1008,C1009,C1010,C1011,C1012,C1013,C1014,C2000,C2001,C2002
 */
@Serializable(with = ApiStatusCodeSerializer::class)
enum class ApiStatusCode(val value: Int) {
    C1000(1000),
    C1001(1001),
    C1002(1002),
    C1003(1003),
    C1004(1004),
    C1005(1005),
    C1006(1006),
    C1007(1007),
    C1008(1008),
    C1009(1009),
    C1010(1010),
    C1011(1011),
    C1012(1012),
    C1013(1013),
    C1014(1014),
    C2000(2000),
    C2001(2001),
    C2002(2002);

    companion object {
        private val VALUES = values()
        fun getByValue(value: Int) = VALUES.first { it.value == value }
    }
}

/**
 * A single friend or friend request
 */
@Serializable
data class FriendResponse(
    val id: Long,
    @SerialName("is_request")
    val isRequest: Boolean,
    val from: String,
    val to: String
)

/**
 * A list of your friends and friend requests
 */
@Serializable
data class GetFriendResponse(
    val friends: List<FriendResponse>
)

/**
 * The version data for clients
 */
@Serializable
data class VersionResponse(
    val version: Int
)
