package com.unciv.models.metadata

fun String.getUrlWithPort(defaultPort: Int = 8080): String {
    return if (contains(":")) this
    else "$this:$defaultPort"
}