package com.unciv.logic.multiplayer.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
