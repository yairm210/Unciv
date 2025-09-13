package com.unciv.utils

import yairm210.purity.annotations.Pure
import java.util.UUID

/**
 * Checks if a [String] is a valid UUID
 */
@Pure
fun String.isUUID(): Boolean = try {
    UUID.fromString(this)
    true
} catch (_: Throwable) {
    false
}
