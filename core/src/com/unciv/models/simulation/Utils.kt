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
    var d = d
    val days = d.toDays()
    d = d.minusDays(days)
    val hours = d.toHours()
    d = d.minusHours(hours)
    val minutes = d.toMinutes()
    d = d.minusMinutes(minutes)
    val seconds = d.seconds
    d = d.minusSeconds(seconds)
    val millis = d.toMillis()
    return (if (days == 0L) "" else "$days"+"d ") +
            (if (hours == 0L) "" else "$hours"+"h ") +
            (if (minutes == 0L) "" else "$minutes"+"m ") +
            (if (seconds == 0L) "$millis"+"ms" else "$seconds"+"."+"$millis".take(2)+"s")
}