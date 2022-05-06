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
            val serializer = NonStringKeyMapSerializer(
                HashMapVector2::class.java,
                Vector2::class.java,
                { HashMapVector2<Any>() }
            )
            jsonSerializers.add(Pair(HashMapVector2::class.java, serializer))
        }
    }
}

