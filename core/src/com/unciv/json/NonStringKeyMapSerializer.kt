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
) : Serializer<MT>  {

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
        val entries = jsonData.get("entries")
        readNewFormat(entries, json, result)
        return result
    }


    private fun readNewFormat(entries: JsonValue, json: Json, result: MT) {
        var entry = entries.child
        while (entry != null) {
            val key = json.readValue(keyClass, entry.child)

            // 4.6.10 moved the Encampment class, but deserialization of old games which had the old
            // full package name written out depended on the class loader finding it under the serialized name...
            // This kludge steps in and allows both fully qualified class names until a better way is found
            // See #9367
            val isOldEncampment = entry.child.next.child.run {
                    name == "class" && isString && asString() == "com.unciv.logic.Encampment"
                }
            val value = if (isOldEncampment)
                json.readValue(Encampment::class.java, entry.child.next.child.next)
            else json.readValue<Any>(null, entry.child.next)

            result[key!!] = value!!

            entry = entry.next
        }
    }
}
