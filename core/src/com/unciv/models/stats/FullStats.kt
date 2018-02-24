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
        val FS = FullStats()
        FS.add(this)
        return FS
    }

    fun getMinus(): FullStats {
        val stats = FullStats()
        stats.food = -food
        stats.food = -food
        stats.gold = -gold
        stats.science = -science
        stats.culture = -culture
        stats.happiness = -happiness
        return stats;
    }

    operator fun times(number: Float): FullStats{
        val FS = FullStats()
        FS.production = production * number
        FS.food = food*number
        FS.gold = gold*number
        FS.science = science*number
        FS.culture = culture*number
        FS.happiness = happiness*number
        return FS
    }

    override fun toString(): String {
        return toDict().filter{it.value!=0}.map{it.key+": "+it.value}.joinToString(",")
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
