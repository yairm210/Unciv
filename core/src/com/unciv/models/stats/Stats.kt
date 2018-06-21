package com.unciv.models.stats

import com.unciv.ui.utils.tr


open class Stats() {
    var production: Float=0f
    var food: Float=0f
    var gold: Float=0f
    var science: Float=0f
    var culture: Float=0f
    var happiness: Float=0f

    constructor(hashMap: HashMap<Stat, Float>) : this() {
        setStats(hashMap)
    }

    fun add(other: Stats) {
        // Doing this through the hashmap is nicer code but is SUPER INEFFICIENT!
        production += other.production
        food += other.food
        gold += other.gold
        science += other.science
        culture += other.culture
        happiness += other.happiness
    }

    fun add(stat:Stat, value:Float): Stats {
        val hashMap = toHashMap()
        hashMap[stat] = hashMap[stat]!!+value
        setStats(hashMap)
        return this
    }

    fun clone(): Stats {
        val stats = Stats()
        stats.add(this)
        return stats
    }

    operator fun times(number: Float): Stats {
        val hashMap = toHashMap()
        for(stat in Stat.values()) hashMap[stat]= number * hashMap[stat]!!
        return Stats(hashMap)
    }

    override fun toString(): String {
        return toHashMap().filter { it.value != 0f }
                .map {  (if(it.value>0)"+" else "") + it.value.toInt()+" "+it.key.toString().tr() }.joinToString()
    }

    fun toHashMap(): HashMap<Stat, Float> {
        return hashMapOf(Stat.Production to production,
                Stat.Culture to culture,
                Stat.Gold to gold,
                Stat.Food to food,
                Stat.Happiness to happiness,
                Stat.Science to science)
    }

    private fun setStats(hashMap:HashMap<Stat, Float>){
        culture=hashMap[Stat.Culture]!!
        gold=hashMap[Stat.Gold]!!
        production=hashMap[Stat.Production]!!
        food=hashMap[Stat.Food]!!
        happiness=hashMap[Stat.Happiness]!!
        science=hashMap[Stat.Science]!!
    }
}
