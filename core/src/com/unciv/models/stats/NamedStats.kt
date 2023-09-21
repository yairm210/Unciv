package com.unciv.models.stats

import kotlinx.serialization.Required


@kotlinx.serialization.Serializable
open class NamedStats : Stats(), INamed {
    @Required
    override lateinit var name: String

    override fun toString(): String {
        return name
    }

    fun cloneStats(): Stats {
        return clone()
    }
}
