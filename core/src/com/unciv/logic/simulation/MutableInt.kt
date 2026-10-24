package com.unciv.logic.simulation

import com.unciv.models.translations.tr

class MutableInt(var value: Int = 0) {
    fun inc() { ++value }
    fun get(): Int { return value }
    fun set(newValue: Int) { value = newValue }
    fun add(addend: Int) { value += addend }

    override fun toString(): String {
        return value.tr()
    }
}
