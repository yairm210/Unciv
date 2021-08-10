package com.unciv.models.stats

import com.unciv.models.translations.tr
import kotlin.reflect.KMutableProperty0


open class Stats(
    var production: Float = 0f,
    var food: Float = 0f,
    var gold: Float = 0f,
    var science: Float = 0f,
    var culture: Float = 0f,
    var happiness: Float = 0f,
    var faith: Float = 0f
) {
    @Transient
    private val mapView: Map<Stat, KMutableProperty0<Float>> = mapOf(
        Stat.Production to ::production,
        Stat.Food to ::food,
        Stat.Gold to ::gold,
        Stat.Science to ::science,
        Stat.Culture to ::culture,
        Stat.Happiness to ::happiness,
        Stat.Faith to ::faith
    )

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
        mapView[stat]!!.set(value + mapView[stat]!!.get())
        return this
    }

    operator fun plus(stats: Stats) = clone().apply { add(stats) }

    fun clone() = Stats(production, food, gold, science, culture, happiness, faith)

    operator fun times(number: Int) = times(number.toFloat())

    operator fun times(number: Float) = Stats(
        production * number,
        food * number,
        gold * number,
        science * number,
        culture * number,
        happiness * number,
        faith * number
    )
    
    fun timesInPlace(number: Float) {
        production *= number
        food *= number
        gold *= number
        science *= number
        culture *= number
        happiness *= number
        faith *= number
    }

    fun isEmpty() = (
            production == 0f
            && food == 0f
            && gold == 0f
            && science == 0f
            && culture == 0f
            && happiness == 0f
            && faith == 0f )

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

    operator fun get(stat: Stat) = mapView[stat]!!.get()
    operator fun set(stat: Stat, value: Float) = mapView[stat]!!.set(value)

    fun equals(otherStats: Stats): Boolean {
        return production == otherStats.production
                && food == otherStats.food
                && gold == otherStats.gold
                && science == otherStats.science
                && culture == otherStats.culture
                && happiness == otherStats.happiness
                && faith == otherStats.faith
    }

    companion object {
        private val allStatNames = Stat.values().joinToString("|") { it.name }
        private val statRegexPattern = "([+-])(\\d+) ($allStatNames)"
        private val statRegex = Regex(statRegexPattern)
        private val entireStringRegexPattern = Regex("$statRegexPattern(, $statRegexPattern)*")

        fun isStats(string: String): Boolean {
            if (string.isEmpty() || string[0] !in "+-") return false // very quick negative check before the heavy Regex
            return entireStringRegexPattern.matches(string)
        }

        fun parse(string: String): Stats {
            val toReturn = Stats()
            val statsWithBonuses = string.split(", ")
            for(statWithBonuses in statsWithBonuses){
                val match = statRegex.matchEntire(statWithBonuses)!!
                val statName = match.groupValues[3]
                val statAmount = match.groupValues[2].toFloat() * (if (match.groupValues[1] == "-") -1 else 1)
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
