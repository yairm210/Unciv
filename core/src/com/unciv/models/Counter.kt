package com.unciv.models

import com.unciv.logic.IsPartOfGameInfoSerialization

open class Counter<K>(
    fromMap: Map<K, Int>? = null
) : LinkedHashMap<K, Int>(fromMap?.size ?: 10), IsPartOfGameInfoSerialization {
    init {
        if (fromMap != null)
            for ((key, value) in fromMap)
                put(key, value)
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

    operator fun times(amount: Int): Counter<K> {
        val newCounter = Counter<K>()
        for (key in keys) newCounter[key] = this[key] * amount
        return newCounter
    }

    operator fun plus(other: Counter<K>) = clone().apply { add(other) }

    fun sumValues() = values.sum()

    override fun clone(): Counter<K> {
        val newCounter = Counter<K>()
        newCounter.add(this)
        return newCounter
    }

    companion object {
        val ZERO: Counter<String> = object : Counter<String>() {
            override fun put(key: String, value: Int): Int? {
                throw UnsupportedOperationException("Do not modify Counter.ZERO")
            }
        }
    }
}
