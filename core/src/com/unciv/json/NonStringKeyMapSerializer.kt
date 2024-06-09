package com.unciv.json

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.Json.Serializer
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.automation.civilization.Encampment

/**
 * A [Serializer] for gdx's [Json] that serializes a map that does not have [String] as its key class.
 *
 * Exists solely because [Json] always serializes any map by converting its key to [String], so when you load it again,
 * all your keys are [String], messing up value retrieval.
 *
 * To work around that, we have to use a custom serializer. A custom serializer in Json is only added for a specific class
 * and only checks for direct equality, and since we can't just do `HashMap<Any, *>::class.java`, only `HashMap::class.java`,
 * we have to create a completely new class and use that class as [mapClass] here.
 *
 * @param MT Must be a type that extends [MutableMap]
 * @param KT Must be the key type of [MT]
 */
class NonStringKeyMapSerializer<MT: MutableMap<KT, Any>, KT>(
    private val mapClass: Class<MT>,
    private val keyClass: Class<KT>,
    private val mutableMapFactory: () -> MT
) : Serializer<MT> {

    override fun write(json: Json, toWrite: MT, knownType: Class<*>) {
        json.writeObjectStart()
        json.writeType(mapClass)
        json.writeArrayStart("entries")
        for ((key, value) in toWrite) {
            json.writeArrayStart()
            json.writeValue(key)
            json.writeValue(value, null)
            json.writeArrayEnd()
        }
        json.writeArrayEnd()
        json.writeObjectEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): MT {
        val result = mutableMapFactory()
        var entry = jsonData.get("entries").child
        while (entry != null) {
            val key = json.readValue(keyClass, entry.child)
            val value = json.readValue<Any>(null, entry.child.next)

            result[key!!] = value!!

            entry = entry.next
        }
        return result
    }
}
