package com.unciv.logic.multiplayer.utils

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Adapter for moshi to handle datetime data as local dates from and to JSON
 */
class LocalDateAdapter {
    @ToJson
    fun toJson(value: LocalDate): String {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(value)
    }

    @FromJson
    fun fromJson(value: String): LocalDate {
        return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
