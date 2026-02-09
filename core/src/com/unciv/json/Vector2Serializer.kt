package com.unciv.json

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.Json.Serializer
import com.badlogic.gdx.utils.JsonValue

/**
 * TeaVM can miss reflective field writes on Kotlin/Java classes in some paths.
 * Keep Vector2 ser/deser explicit so map/save coordinates stay stable on web.
 */
class Vector2Serializer : Serializer<Vector2> {
    override fun write(json: Json, vector: Vector2, knownType: Class<*>?) {
        json.writeObjectStart()
        if (vector.x != 0f) json.writeValue("x", vector.x)
        if (vector.y != 0f) json.writeValue("y", vector.y)
        json.writeObjectEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): Vector2 {
        // Accept both compact numeric format and verbose legacy object format.
        val x = json.readValue("x", Float::class.java, 0f, jsonData)
        val y = json.readValue("y", Float::class.java, 0f, jsonData)
        return Vector2(x, y)
    }
}
