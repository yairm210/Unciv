package com.unciv.json

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import java.util.HashMap

/**
 * @see NonStringKeyMapSerializer
 */
class HashMapVector2<T> : HashMap<Vector2, T>() {
    companion object {
        init {
            @Suppress("UNCHECKED_CAST") // kotlin can't tell that HashMapVector2 is also a MutableMap within generics
            val mapClass = HashMapVector2::class.java as Class<MutableMap<Vector2, Any>>
            val serializer = NonStringKeyMapSerializer(
                mapClass,
                Vector2::class.java,
                { HashMapVector2<Any>() }
            )
            jsonSerializers.add(Pair(mapClass, serializer))
        }
    }
}

