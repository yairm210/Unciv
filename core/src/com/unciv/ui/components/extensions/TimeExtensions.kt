package com.unciv.ui.components.extensions

import java.time.Duration
import java.time.Instant


fun Duration.isLargerThan(other: Duration): Boolean {
    return compareTo(other) > 0
}
fun Instant.isLargerThan(other: Instant): Boolean {
    return compareTo(other) > 0
}
