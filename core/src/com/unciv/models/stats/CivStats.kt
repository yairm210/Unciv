package com.unciv.models.stats

open class CivStats {
    @JvmField  var gold = 0f
    @JvmField  var science = 0f
    @JvmField  var culture = 0f
    @JvmField  var happiness = 0f

    fun add(other: CivStats) {
        gold += other.gold
        science += other.science
        happiness += other.happiness
        culture += other.culture
    }
}
