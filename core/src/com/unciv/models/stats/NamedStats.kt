package com.unciv.models.stats


open class NamedStats : Stats(), INamed {
    override lateinit var name: String

    override fun toString() = if (::name.isInitialized) name else "?"
    
    fun cloneStats(): Stats {
        return clone()
    }
}
