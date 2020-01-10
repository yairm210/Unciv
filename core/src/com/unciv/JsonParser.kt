package com.unciv

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.models.ruleset.Nation
import com.unciv.ui.utils.colorFromRGB

class JsonParser {

    private val json = Json().apply {
        ignoreUnknownFields = true
        setSerializer(Nation::class.java, Nation.serializer())
    }

    fun <T> getFromJson(tClass: Class<T>, filePath: String): T = getFromJson(tClass, Gdx.files.internal(filePath))

    fun <T> getFromJson(tClass: Class<T>, file: FileHandle): T {
        val jsonText = file.readString(Charsets.UTF_8.name())
        return json.fromJson(tClass, jsonText)
    }
}

fun JsonValue.stringListOrDefault(name: String, default: List<String> = emptyList()): List<String> {
    val element = get(name)
    return if (element != null) {
        element.asStringArray().toList()
    } else {
        default
    }
}

fun JsonValue.colorOrDefault(name: String, default: Color = Color.BLACK): Color {
    val element = get(name)
    return if (element != null) {
        val items = element.asIntArray()
        colorFromRGB(items[0], items[1], items[2])
    } else {
        default
    }
}