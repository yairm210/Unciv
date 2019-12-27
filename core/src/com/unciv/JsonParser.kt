package com.unciv

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json

class JsonParser {

    private val json = Json().apply { ignoreUnknownFields = true }

    fun <T> getFromJson(tClass: Class<T>, filePath: String): T {
        val jsonText = Gdx.files.internal(filePath).readString(Charsets.UTF_8.name())
        return json.fromJson(tClass, jsonText)
    }
}