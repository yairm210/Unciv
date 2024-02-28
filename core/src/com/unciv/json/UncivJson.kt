package com.unciv.json

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import com.badlogic.gdx.utils.SerializationException
import com.unciv.logic.civilization.CivRankingHistory
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.tile.TileHistory
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBindings
import java.time.Duration


/**
 * [Json] is not thread-safe. Use a new one for each parse.
 */
fun json() = Json(JsonWriter.OutputType.json).apply {
    // Gdx default output type is JsonWriter.OutputType.minimal, which generates invalid Json - e.g. most quotes removed.
    // The constructor parameter above changes that to valid Json
    // Note an instance set to json can read minimal and vice versa

    setIgnoreDeprecated(true)
    ignoreUnknownFields = true

    setSerializer(HashMapVector2.getSerializerClass(), HashMapVector2.createSerializer())
    setSerializer(Duration::class.java, DurationSerializer())
    setSerializer(KeyCharAndCode::class.java, KeyCharAndCode.Serializer())
    setSerializer(KeyboardBindings::class.java, KeyboardBindings.Serializer())
    setSerializer(TileHistory::class.java, TileHistory.Serializer())
    setSerializer(CivRankingHistory::class.java, CivRankingHistory.Serializer())
    setSerializer(Notification::class.java, Notification.Serializer())
}

/**
 *  Load a json file by [filePath] from Gdx.files.internal
 *  (meaning from jar/apk for packaged release code, and not appropriate for mod files)
 *  @throws SerializationException
 */
fun <T> Json.fromJsonFile(tClass: Class<T>, filePath: String): T = fromJsonFile(tClass, Gdx.files.internal(filePath))

/**
 *  Load a json [file] - by handle, so internal/external/local is caller's decision.
 *
 *  Reminder:
 *  * `internal` for Unciv-packaged assets, loaded from jar/apk, e.g. Built-in ruleset files.
 *  * `local` for mods and settings - Android will place that under /data/data/com.unciv.app/files.
 *  * `external` for saves - Android will place that under /sdcard/Android/data/com.unciv.app/files.
 *  @throws SerializationException
 */
fun <T> Json.fromJsonFile(tClass: Class<T>, file: FileHandle): T {
    try {
        val jsonText = file.readString(Charsets.UTF_8.name())
        return fromJson(tClass, jsonText)
    } catch (exception: Exception) {
        throw Exception("Could not parse json of file ${file.name()}", exception)
    }
}
