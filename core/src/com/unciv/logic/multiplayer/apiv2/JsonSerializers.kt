package com.unciv.logic.multiplayer.apiv2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID

/**
 * Serializer for the ApiStatusCode enum to make encoding/decoding as integer work
 */
internal class ApiStatusCodeSerializer : KSerializer<ApiStatusCode> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ApiStatusCode", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: ApiStatusCode) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): ApiStatusCode {
        return ApiStatusCode.getByValue(decoder.decodeInt())
    }
}

/**
 * Serializer for instants (date times) from/to strings in ISO 8601 format
 */
internal class InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}

/**
 * Serializer for UUIDs from/to strings
 */
internal class UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

/**
 * Serializer for incoming and outgoing WebSocket messages that also differentiate by type
 */
internal class WebSocketMessageSerializer : JsonContentPolymorphicSerializer<WebSocketMessage>(WebSocketMessage::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        // Text frames in JSON format but without 'type' field are invalid
        "type" !in element.jsonObject -> InvalidMessage.serializer()
        else -> {
            // This mapping of the enum enforces to specify all serializer types at compile time
            when (WebSocketMessageType.getByValue(element.jsonObject["type"]!!.jsonPrimitive.content)) {
                WebSocketMessageType.InvalidMessage -> InvalidMessage.serializer()
                WebSocketMessageType.GameStarted -> GameStartedMessage.serializer()
                WebSocketMessageType.UpdateGameData -> UpdateGameDataMessage.serializer()
                WebSocketMessageType.ClientDisconnected -> ClientDisconnectedMessage.serializer()
                WebSocketMessageType.ClientReconnected -> ClientReconnectedMessage.serializer()
                WebSocketMessageType.IncomingChatMessage -> IncomingChatMessageMessage.serializer()
                WebSocketMessageType.IncomingInvite -> IncomingInviteMessage.serializer()
                WebSocketMessageType.IncomingFriendRequest -> IncomingFriendRequestMessage.serializer()
                WebSocketMessageType.FriendshipChanged -> FriendshipChangedMessage.serializer()
                WebSocketMessageType.LobbyJoin -> LobbyJoinMessage.serializer()
                WebSocketMessageType.LobbyClosed -> LobbyClosedMessage.serializer()
                WebSocketMessageType.LobbyLeave -> LobbyLeaveMessage.serializer()
                WebSocketMessageType.LobbyKick -> LobbyKickMessage.serializer()
                WebSocketMessageType.AccountUpdated -> AccountUpdatedMessage.serializer()
            }
        }
    }
}

/**
 * Serializer for the WebSocket message type enum to make encoding/decoding as string work
 */
internal class WebSocketMessageTypeSerializer : KSerializer<WebSocketMessageType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("WebSocketMessageType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: WebSocketMessageType) {
        encoder.encodeString(value.type)
    }

    override fun deserialize(decoder: Decoder): WebSocketMessageType {
        return WebSocketMessageType.getByValue(decoder.decodeString())
    }
}

/**
 * Serializer for the FriendshipEvent WebSocket message enum to make encoding/decoding as string work
 */
internal class FriendshipEventSerializer : KSerializer<FriendshipEvent> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FriendshipEventSerializer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FriendshipEvent) {
        encoder.encodeString(value.type)
    }

    override fun deserialize(decoder: Decoder): FriendshipEvent {
        return FriendshipEvent.getByValue(decoder.decodeString())
    }
}
