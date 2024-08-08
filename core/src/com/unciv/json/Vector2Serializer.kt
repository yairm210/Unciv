package com.unciv.json

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.Json.Serializer
import com.badlogic.gdx.utils.JsonValue

class Vector2Serializer : Serializer<Vector2> {
    override fun write(json: Json, vector2: Vector2?, knownType: Class<*>?) {

        if (vector2 == null) json.writeValue(null)
        else {
            // NEW vector serialization - currently disabled
//            json.writeValue("${vector2.x.toInt()}/${vector2.y.toInt()}")

            // OLD vector serialization - deprecated 4.12.18
            json.writeObjectStart()
            json.writeFields(vector2)
            json.writeObjectEnd()
        }
    }

    override fun read(json: Json, jsonData: JsonValue, knownType: Class<*>?): Vector2? {
        if (jsonData.isNull) return null // Not entirely sure it's necessary
        if (jsonData.isString) {
            val split = jsonData.asString().split("/")
            return Vector2(split[0].toFloat(), split[1].toFloat())
        }

        // OLD vector serialization
        val vector = Vector2()
        json.readFields(vector, jsonData)
        return vector
    }
}
