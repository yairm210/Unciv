package com.unciv.logic.multiplayer.utils

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Adapter for moshi to handle byte arrays from and to JSON
 */
class ByteArrayAdapter {
    @ToJson
    fun toJson(data: ByteArray): String = String(data)

    @FromJson
    fun fromJson(data: String): ByteArray = data.toByteArray()
}
