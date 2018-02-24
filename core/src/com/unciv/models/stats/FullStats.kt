package com.unciv.models.stats

import java.util.HashMap


open class FullStats : CivStats()
{
    @JvmField var production: Float = 0f
    @JvmField var food: Float = 0f

    fun add(other: FullStats) {
        production += other.production
        food += other.food
        super.add(other)
    }

    fun clone():FullStats {
        val stats = FullStats()
        stats.add(this)
        return stats
    }

    fun getMinus(): FullStats {
        val stats = FullStats()
        stats.food = -food
        stats.food = -food
        stats.gold = -gold
        stats.science = -science
        stats.culture = -culture
        stats.happiness = -happiness
        return stats
    }

    operator fun times(number: Float): FullStats{
        val stats = FullStats()
        stats.production = production * number
        stats.food = food*number
        stats.gold = gold*number
        stats.science = science*number
        stats.culture = culture*number
        stats.happiness = happiness*number
        return stats
    }

    override fun toString(): String {
        return toDict().filter{it.value!=0}.map{it.key+": "+it.value}.joinToString()
    }

    fun toDict(): HashMap<String, Int> {
        return hashMapOf("Production" to production.toInt(),
                "Food" to food.toInt(),
                "Gold" to gold.toInt(),
                "Science" to science.toInt(),
                "Culture" to culture.toInt()
        )
    }

}
