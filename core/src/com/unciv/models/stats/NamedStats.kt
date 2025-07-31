package com.unciv.models.stats

import yairm210.purity.annotations.Readonly


open class NamedStats : Stats(), INamed {
    override lateinit var name: String

    @Readonly override fun toString(): String = name

    @Readonly fun cloneStats(): Stats = clone()
}
