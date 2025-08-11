package com.unciv.json

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.ui.components.extensions.toPrettyString
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

/**
 *  Dedicated HashMap with [Vector2] keys for [Civilization.lastSeenImprovement].
 *
 *  Deals with the problem that serialization uses map keys converted to strings as json object field names,
 *   and generic deserialization can't convert them back,
 *   by implementing Json.Serializable and parsing the key string explicitly.
 *
 *  Backward compatibility is implemented in [readOldFormat], but there can be no forward compatibility.
 *  To remove compatibility, remove the `open` modifier, remove `class HashMapVector2`, remove `readOldFormat`, and the first two lines of `read`. Moving to another package is now allowed.
 *  To understand the old solution with its nonstandard format that readOldFormat parses, use git history
 *  to dig out the com.unciv.json.HashMapVector2 and com.unciv.json.NonStringKeyMapSerializer files.
 */
open class LastSeenImprovement(
    private val map: HashMap<Vector2, String> = hashMapOf()
) : MutableMap<Vector2, String> by map, Json.Serializable {
    override fun write(json: Json) {
        for ((key, value) in entries) {
            val name = key.toPrettyString()
            json.writeValue(name, value, String::class.java)
        }
    }

    override fun read(json: Json, jsonData: JsonValue) {
        if (jsonData.get("class")?.asString() == "com.unciv.json.HashMapVector2")
            return readOldFormat(json, jsonData)
        for (entry in jsonData) {
            val key = entry.name.toVector2()
            val value = if (entry.isValue) entry.asString() else entry.getString("value")
            put(key, value)
        }
    }

    @Pure
    private fun String.toVector2(): Vector2 {
        val (x, y) = removeSurrounding("(", ")").split(',')
        return Vector2(x.toFloat(), y.toFloat())
    }

    private fun readOldFormat(json: Json, jsonData: JsonValue) {
        for (entry in jsonData.get("entries")) {
            val key = json.readValue(Vector2::class.java, entry[0])
            val value = json.readValue(String::class.java, entry[1])
            put(key, value)
        }
    }

    override fun equals(other: Any?) = when (other) {
        is LastSeenImprovement -> map == other.map
        is Map<*, *> -> map == other
        else -> false
    }

    override fun hashCode() = map.hashCode()
}

/** Compatibility kludge required for backward compatibility. Without this, Gdx won't even run our overridden `read` above. */
private class HashMapVector2 : LastSeenImprovement()
