package com.unciv.models.stats

import com.unciv.models.gamebasics.tr


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

    fun clear() {
        production = 0f
        food = 0f
        gold = 0f
        science = 0f
        culture = 0f
        happiness = 0f
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

    operator fun plus(stat: Stats): Stats {
        val clone = clone()
        clone.add(stat)
        return clone
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

    fun get(stat:Stat):Float{
        return this.toHashMap()[stat]!!
    }

    private fun setStats(hashMap:HashMap<Stat, Float>){
        culture=hashMap[Stat.Culture]!!
        gold=hashMap[Stat.Gold]!!
        production=hashMap[Stat.Production]!!
        food=hashMap[Stat.Food]!!
        happiness=hashMap[Stat.Happiness]!!
        science=hashMap[Stat.Science]!!
    }

    fun equals(otherStats: Stats):Boolean{
        return culture==otherStats.culture
                && gold==otherStats.gold
                && production==otherStats.production
                && food==otherStats.food
                && happiness==otherStats.happiness
                && science==otherStats.science
    }
}

class StatMap:LinkedHashMap<String,Stats>(){
    fun add(source:String,stats:Stats){
        if(!containsKey(source)) put(source,stats)
        else get(source)!!.add(stats)
    }
}