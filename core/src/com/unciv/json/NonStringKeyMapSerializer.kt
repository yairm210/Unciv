package com.unciv.json

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.Json.Serializer
import com.badlogic.gdx.utils.JsonValue

/**
 * A [Serializer] for gdx's [Json] that serializes a map that does not have [String] as its key class.
 *
 * Exists solely because [Json] always serializes any map by converting its key to [String], so when you load it again,
 * all your keys are [String], messing up value retrieval.
 *
 *
 * To work around that, we have to use a custom serializer, and for that we have to have a specific class to add the serializer for,
 * since generics are not available at runtime.
 */
class NonStringKeyMapSerializer<MT, KT>(
    private val mapClass: Class<MT>,
    private val keyClass: Class<KT>,
    private val mutableMapFactory: () -> MT
) : Serializer<MT> {

    override fun write(json: Json?, `object`: MT?, knownType: Class<*>?) {
        json?.writeObjectStart()
        json?.writeType(mapClass)
        json?.writeArrayStart("entries")
        for ((key, value) in `object` as Map<*, *>) {
            json?.writeArrayStart()
            json?.writeValue(key)
            json?.writeValue(value)
            json?.writeArrayEnd()
        }
        json?.writeArrayEnd()
        json?.writeObjectEnd()
    }

    @Suppress("UNCHECKED_CAST")
    override fun read(json: Json?, jsonData: JsonValue?, type: Class<*>?): MT {
        val result = mutableMapFactory() as MutableMap<Any, Any>
        val entries = jsonData?.get("entries")
        var entry = entries!!.child
        while (entry != null) {
            val key = json?.readValue(keyClass, entry.child)
            val value = json?.readValue<Any>(null, entry.child.next)
            result.put(key!!, value!!)

            entry = entry.next
        }
        return result as MT
    }
}