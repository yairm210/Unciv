package com.unciv.logic.multiplayer.utils

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Adapter for moshi to handle datetime data with offsets from and to JSON
 */
class OffsetDateTimeAdapter {
    @ToJson
    fun toJson(value: OffsetDateTime): String {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value)
    }

    @FromJson
    fun fromJson(value: String): OffsetDateTime {
        return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
