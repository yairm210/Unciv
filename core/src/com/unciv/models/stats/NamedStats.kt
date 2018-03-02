package com.unciv.models.stats


open class NamedStats : Stats(), INamed {
    override lateinit var name: String

    override fun toString(): String {
        return name
    }
}
