package com.unciv

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.unciv.logic.UncivShowableException

class JsonParser {

    private val json = Json().apply { ignoreUnknownFields = true }

    fun <T> getFromJson(tClass: Class<T>, filePath: String): T = getFromJson(tClass, Gdx.files.internal(filePath))

    fun <T> getFromJson(tClass: Class<T>, file: FileHandle): T {
        try{
            val jsonText = file.readString(Charsets.UTF_8.name())
            return json.fromJson(tClass, jsonText)
        } catch (exception:Exception){
            throw Exception("Could not parse json of file ${file.name()}", exception)
        }
    }
}