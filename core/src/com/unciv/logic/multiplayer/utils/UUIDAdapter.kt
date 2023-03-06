package com.unciv.logic.multiplayer.utils

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.util.UUID

/**
 * Adapter for moshi to handle UUIDs from and to JSON
 */
class UUIDAdapter {
    @ToJson
    fun toJson(uuid: UUID) = uuid.toString()

    @FromJson
    fun fromJson(s: String): UUID = UUID.fromString(s)
}
