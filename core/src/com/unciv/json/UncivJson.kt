package com.unciv.json

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.Json.Serializer

internal val jsonSerializers = ArrayList<Pair<Class<*>, Serializer<*>>>()

/**
 * [Json] is not thread-safe.
 */
fun json() = Json().apply {
    setIgnoreDeprecated(true)
    ignoreUnknownFields = true
    for ((clazz, serializer) in jsonSerializers) {
        @Suppress("UNCHECKED_CAST") // we used * to accept all types, so kotlin can't know if the class & serializer parameters are actually the same
        setSerializer(clazz as Class<Any>, serializer as Serializer<Any>)
    }
}

fun <T> Json.fromJsonFile(tClass: Class<T>, filePath: String): T = fromJsonFile(tClass, Gdx.files.internal(filePath))

fun <T> Json.fromJsonFile(tClass: Class<T>, file: FileHandle): T {
    try {
        val jsonText = file.readString(Charsets.UTF_8.name())
        return fromJson(tClass, jsonText)
    } catch (exception:Exception){
        throw Exception("Could not parse json of file ${file.name()}", exception)
    }
}