package com.unciv.models

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.IsPartOfGameInfoSerialization
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly

/**
 *  Implements a specialized Map storing on-zero Integers.
 *  - All mutating methods will remove keys when their value is zeroed
 *  - [get] on a nonexistent key returns 0
 *  - The Json.Serializable implementation ensures compact format, it does not solve the non-string-key map problem.
 *  - Therefore, Deserialization works properly ***only*** with [K] === String.
 *    (ignoring this will return a deserialized map, but the keys will violate the compile-time type and BE strings)
 */
@InternalState
open class Counter<K>(
    fromMap: Map<K, Int>? = null
) : LinkedHashMap<K, Int>(fromMap?.size ?: 10), IsPartOfGameInfoSerialization, Json.Serializable {
    init {
        if (fromMap != null)
            for ((key, value) in fromMap)
                super.put(key, value)
    }

    override operator fun get(key: K): Int { // don't return null if empty
        return if (containsKey(key))
        // .toInt(), because GDX deserializes Counter values as *floats* for some reason
            super.get(key)!!.toInt()
        else 0
    }

    override fun put(key: K, value: Int): Int? {
        if (value == 0) return remove(key) // No objects of this sort left, no need to count
        return super.put(key, value)
    }

    fun add(key: K, value: Int) {
        put(key, get(key) + value)
    }

    fun add(other: Counter<K>) {
        for ((key, value) in other) add(key, value)
    }
    operator fun plusAssign(other: Counter<K>) = add(other)

    fun remove(other: Counter<K>) {
        for ((key, value) in other) add(key, -value)
    }
    operator fun minusAssign(other: Counter<K>) = remove(other)

    @Readonly
    /** Creates a new instance (does not modify) */
    operator fun times(amount: Int): Counter<K> {
        val newCounter = Counter<K>()
        for (key in keys) newCounter[key] = this[key] * amount
        return newCounter
    }

    @Readonly 
    operator fun plus(other: Counter<K>): Counter<K> {
        @LocalState val clone = clone()
        clone.add(other)
        return clone
    }

    @Readonly fun sumValues() = values.sum()

    @Readonly override fun clone() = Counter(this)

    companion object {
        val ZERO: Counter<String> = object : Counter<String>() {
            override fun put(key: String, value: Int): Int? {
                throw UnsupportedOperationException("Do not modify Counter.ZERO")
            }
        }

    }

    override fun write(json: Json) {
        for ((key, value) in entries) {
            val name = if (key is String) key else key.toString()
            json.writeValue(name, value, Int::class.java)
        }
    }

    override fun read(json: Json, jsonData: JsonValue) {
        for (entry in jsonData) {
            @Suppress("UNCHECKED_CAST")
            // Default Gdx does the same. If K is NOT String, then Gdx would still store String keys. And we can't reify K to check..
            val key = entry.name as K
            val value = if (entry.isValue) entry.asInt() else entry.getInt("value")
            put(key, value)
        }
    }
}
