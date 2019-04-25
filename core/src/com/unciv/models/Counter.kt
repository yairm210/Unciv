package com.unciv.models

import java.util.*

class Counter<K> : LinkedHashMap<K, Int>() {

    override operator fun get(key: K): Int? { // don't return null if empty
        if (containsKey(key))
            return super.get(key)
        else return 0
    }

    fun add(key: K, value: Int) {
        if (!containsKey(key))
            put(key, value)
        else
            put(key, get(key)!! + value)
        if (get(key) == 0) remove(key) // No objects of this sort left, no need to count
    }

    fun add(other: Counter<K>) {
        for (key in other.keys) {
            add(key, other[key]!!)
        }
    }

    fun remove(other: Counter<K>) {
        for (key in other.keys) {
            add(key, -other[key]!!)
        }
    }

    override fun clone(): Counter<K> {
        val newCounter = Counter<K>()
        newCounter.add(this)
        return newCounter
    }
}
