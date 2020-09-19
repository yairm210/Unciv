package com.unciv.models.stats

import com.unciv.models.translations.tr


open class Stats() {
    var production: Float = 0f
    var food: Float = 0f
    var gold: Float = 0f
    var science: Float = 0f
    var culture: Float = 0f
    var happiness: Float = 0f
    var faith: Float = 0f

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
        faith = 0f
    }

    fun add(other: Stats) {
        // Doing this through the hashmap is nicer code but is SUPER INEFFICIENT!
        production += other.production
        food += other.food
        gold += other.gold
        science += other.science
        culture += other.culture
        happiness += other.happiness
        faith += other.faith
    }


    fun add(stat: Stat, value: Float): Stats {
        val hashMap = toHashMap()
        hashMap[stat] = hashMap[stat]!! + value
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
        for (stat in Stat.values()) hashMap[stat] = number * hashMap[stat]!!
        return Stats(hashMap)
    }

    override fun toString(): String {
        return toHashMap().filter { it.value != 0f }
                .map { (if (it.value > 0) "+" else "") + it.value.toInt() + " " + it.key.toString().tr() }.joinToString()
    }

    fun toHashMap(): HashMap<Stat, Float> {
        return linkedMapOf(Stat.Production to production,
                Stat.Culture to culture,
                Stat.Gold to gold,
                Stat.Food to food,
                Stat.Happiness to happiness,
                Stat.Science to science,
                Stat.Faith to faith
        )
    }

    fun get(stat: Stat): Float {
        return this.toHashMap()[stat]!!
    }

    private fun setStats(hashMap: HashMap<Stat, Float>) {
        culture = hashMap[Stat.Culture]!!
        gold = hashMap[Stat.Gold]!!
        production = hashMap[Stat.Production]!!
        food = hashMap[Stat.Food]!!
        happiness = hashMap[Stat.Happiness]!!
        science = hashMap[Stat.Science]!!
        faith = hashMap[Stat.Faith]!!
    }

    fun equals(otherStats: Stats): Boolean {
        return culture == otherStats.culture
                && gold == otherStats.gold
                && production == otherStats.production
                && food == otherStats.food
                && happiness == otherStats.happiness
                && science == otherStats.science
                && faith == otherStats.faith
    }

    companion object {
        private val allStatNames = Stat.values().joinToString("|") { it.name }
        private val statRegexPattern = "([+-])(\\d+) ($allStatNames)"
        private val statRegex = Regex(statRegexPattern)
        private val entireStringRegexPattern = Regex("$statRegexPattern(, $statRegexPattern)*")
        fun isStats(string:String): Boolean = entireStringRegexPattern.matches(string)
        fun parse(string:String):Stats{
            val toReturn = Stats()
            val statsWithBonuses = string.split(", ")
            for(statWithBonuses in statsWithBonuses){
                val match = statRegex.matchEntire(statWithBonuses)!!
                val statName = match.groupValues[3]
                val statAmount = match.groupValues[2].toFloat() * (if(match.groupValues[1]=="-") -1 else 1)
                toReturn.add(Stat.valueOf(statName), statAmount)
            }
            return toReturn
        }
    }
}

class StatMap:LinkedHashMap<String,Stats>() {
    fun add(source: String, stats: Stats) {
        if (!containsKey(source)) put(source, stats)
        else put(source, get(source)!! + stats)
        // This CAN'T be get(source)!!.add() because the initial stats we get are sometimes from other places -
        // for instance the Cities is from the currentCityStats and if we add to that we change the value in the cities themselves!
    }
}