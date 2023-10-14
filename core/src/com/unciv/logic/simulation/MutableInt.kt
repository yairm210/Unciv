package com.unciv.logic.simulation

class MutableInt(var value: Int = 0) {
    fun inc() { ++value }
    fun get(): Int { return value }
    fun set(newValue: Int) { value = newValue }

    override fun toString(): String {
        return value.toString()
    }
}
