package com.unciv.json

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.Json.Serializer

internal val jsonSerializers = ArrayList<Pair<Class<*>, Serializer<*>>>()

/**
 * [Json] is not thread-safe.
 */
fun json() = Json().apply {
    setIgnoreDeprecated(true)
    ignoreUnknownFields = true
    for ((`class`, serializer) in jsonSerializers) {
        setSerializer(`class` as Class<Any>, serializer as Serializer<Any>)
    }
}