package com.unciv.logic.multiplayer.apiv2

import com.unciv.logic.UncivShowableException

/**
 * Subclass of [UncivShowableException] indicating network errors (timeout, connection refused and so on)
 */
class UncivNetworkException : UncivShowableException {
    constructor(cause: Throwable) : super("An unexpected network error occurred.", cause)
    constructor(text: String, cause: Throwable?) : super(text, cause)
}
