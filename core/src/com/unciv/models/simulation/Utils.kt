package com.unciv.models.simulation

import java.time.Duration

class MutableInt(var value: Int = 0) {
    fun inc() { ++value }
    fun get(): Int { return value }
    fun set(newValue: Int) { value = newValue }

    override fun toString(): String {
        return value.toString()
    }
}

fun formatDuration(d: Duration): String {
    var newDuration = d
    val days = newDuration.toDays()
    newDuration = newDuration.minusDays(days)
    val hours = newDuration.toHours()
    newDuration = newDuration.minusHours(hours)
    val minutes = newDuration.toMinutes()
    newDuration = newDuration.minusMinutes(minutes)
    val seconds = newDuration.seconds
    newDuration = newDuration.minusSeconds(seconds)
    val millis = newDuration.toMillis()
    return (if (days == 0L) "" else "$days"+"d ") +
            (if (hours == 0L) "" else "$hours"+"h ") +
            (if (minutes == 0L) "" else "$minutes"+"m ") +
            (if (seconds == 0L) "$millis"+"ms" else "$seconds"+"."+"$millis".take(2)+"s")
}