package com.unciv.models

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

open class Counter<K> : LinkedHashMap<K, Int>() {

    override operator fun get(key: K): Int? { // don't return null if empty
        if (containsKey(key))
        // .toInt(), because GDX deserializes Counter values as *floats* for some reason
            return super.get(key)!!.toInt()
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
        for (key in other.keys) add(key, other[key]!!)
    }

    fun remove(other: Counter<K>) {
        for (key in other.keys) add(key, -other[key]!!)
    }

    fun times(amount:Int): Counter<K> {
        val newCounter = Counter<K>()
        for (key in keys) newCounter[key] = this[key]!! * amount
        return newCounter
    }
    
    fun sumValues(): Int {
        return this.map { it.value }.sum()
    }

    override fun clone(): Counter<K> {
        val newCounter = Counter<K>()
        newCounter.add(this)
        return newCounter
    }
}

class MultiHashMap<K, V> : LinkedHashMap<K, ArrayList<V>>() {
    fun add(key: K, value: V) {
        var existingList = get(key)
        if (existingList == null) {
            existingList = ArrayList()
            this[key] = existingList
        }
        existingList.add(value)
    }
}