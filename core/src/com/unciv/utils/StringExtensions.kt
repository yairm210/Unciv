package com.unciv.utils

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


/**
 * Checks if a [String] is a valid UUID
 */
@OptIn(ExperimentalUuidApi::class)
fun String.isUUID(): Boolean {
    return try {
        Uuid.parse(this)
        true
    } catch (_: Throwable) {
        false
    }
}
