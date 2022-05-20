package com.unciv.json

import com.badlogic.gdx.math.Vector2

/**
 * @see NonStringKeyMapSerializer
 */
class HashMapVector2<T> : HashMap<Vector2, T>() {
    companion object {
        fun createSerializer(): NonStringKeyMapSerializer<MutableMap<Vector2, Any>, Vector2> {
            @Suppress("UNCHECKED_CAST") // kotlin can't tell that HashMapVector2 is also a MutableMap within generics
            val mapClass = HashMapVector2::class.java as Class<MutableMap<Vector2, Any>>
            return NonStringKeyMapSerializer(
                mapClass,
                Vector2::class.java
            ) { HashMapVector2() }
        }

        fun getSerializerClass(): Class<MutableMap<Vector2, Any>> {
            @Suppress("UNCHECKED_CAST") // kotlin can't tell that HashMapVector2 is also a MutableMap within generics
            return HashMapVector2::class.java as Class<MutableMap<Vector2, Any>>
        }
    }
}

