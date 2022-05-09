package com.unciv.json

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json


/**
 * [Json] is not thread-safe. Use a new one for each parse.
 */
fun json() = Json().apply {
    setIgnoreDeprecated(true)
    ignoreUnknownFields = true

    setSerializer(Vector2::class.java, Vector2Serializer())
    setSerializer(HashMapVector2.getSerializerClass(), HashMapVector2.createSerializer())
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
