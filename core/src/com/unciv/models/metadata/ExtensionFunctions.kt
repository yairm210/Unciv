package com.unciv.models.metadata

/** Check the url, if no port, add [defaultPort], then return the url. */
fun String.checkMultiplayerServerWithPort(defaultPort: Int = 8080): String {
    return if (contains(":")) this
    else "$this:$defaultPort"
}