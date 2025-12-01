package com.unciv.json

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexCoord
import yairm210.purity.annotations.Pure

/**
 *  Dedicated HashMap with [Vector2] keys for [Civilization.lastSeenImprovement][com.unciv.logic.civilization.Civilization.lastSeenImprovement].
 *
 *  Deals with the problem that serialization uses map keys converted to strings as json object field names,
 *   and generic deserialization can't convert them back,
 *   by implementing Json.Serializable and parsing the key string explicitly.
 *  See git history for previous more complicated solutions.
 */
class LastSeenImprovement(
    private val map: HashMap<HexCoord, String> = hashMapOf()
) : MutableMap<HexCoord, String> by map, IsPartOfGameInfoSerialization, Json.Serializable {
    override fun write(json: Json) {
        for ((key, value) in entries) {
            val name = key.toPrettyString()
            json.writeValue(name, value, String::class.java)
        }
    }

    override fun read(json: Json, jsonData: JsonValue) {
        for (entry in jsonData) {
            val key = entry.name.toHexCoord()
            val value = if (entry.isValue) entry.asString() else entry.getString("value")
            put(key, value)
        }
    }

    @Pure
    private fun String.toVector2(): Vector2 {
        val (x, y) = removeSurrounding("(", ")").split(',')
        return Vector2(x.toFloat(), y.toFloat())
    }
    @Pure
    private fun String.toHexCoord(): HexCoord {
        val (x, y) = removeSurrounding("(", ")").split(',')
        return HexCoord(x.toFloat().toInt(),y.toFloat().toInt())
    }

    override fun equals(other: Any?) = when (other) {
        is LastSeenImprovement -> map == other.map
        is Map<*, *> -> map == other
        else -> false
    }

    override fun hashCode() = map.hashCode()
}
