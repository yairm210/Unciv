package com.unciv.json

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.SerializationException
import java.time.Duration


/**
 * [Json] is not thread-safe. Use a new one for each parse.
 */
fun json() = Json().apply {
    setIgnoreDeprecated(true)
    ignoreUnknownFields = true

    // Default output type is JsonWriter.OutputType.minimal, which generates invalid Json - e.g. most quotes removed.
    // To get better Json, use:
    // setOutputType(JsonWriter.OutputType.json)
    // Note an instance set to json can read minimal and vice versa

    setSerializer(HashMapVector2.getSerializerClass(), HashMapVector2.createSerializer())
    setSerializer(Duration::class.java, DurationSerializer())
}

/**
 * @throws SerializationException
 */
fun <T> Json.fromJsonFile(tClass: Class<T>, filePath: String): T = fromJsonFile(tClass, Gdx.files.internal(filePath))

/**
 * @throws SerializationException
 */
fun <T> Json.fromJsonFile(tClass: Class<T>, file: FileHandle): T {
    try {
        val jsonText = file.readString(Charsets.UTF_8.name())
        return fromJson(tClass, jsonText)
    } catch (exception:Exception){
        throw Exception("Could not parse json of file ${file.name()}", exception)
    }
}
