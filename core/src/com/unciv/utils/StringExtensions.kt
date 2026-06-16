package com.unciv.utils

import yairm210.purity.annotations.Pure
import java.util.UUID

/**
 * Tries to convert a [String] to a valid [UUID]
 * and returns `null` upon failure
 */
@Pure
fun String.toUUIDOrNull(): UUID? = try {
    UUID.fromString(this)
} catch (_: Throwable) {
    null
}

/**
 * Checks if a [String] is a valid [UUID]
 */
@Pure
fun String.isUUID(): Boolean = toUUIDOrNull() != null

/** Determines if we're running from a jar, given an instance of any Unciv-specific class.
 *  (Not a String extension, but as long as we don't have a 'generic' extension file...) */
fun isRunFromJar(obj: Any): Boolean = obj::class.java.`package`.specificationVersion != null
