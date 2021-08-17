package com.unciv.models.stats

import com.unciv.models.translations.tr
import kotlin.reflect.KMutableProperty0

/**
 * A container for the seven basic ["currencies"][Stat] in Unciv,
 * **Mutable**, allowing for easy merging of sources and applying bonuses.
 */
open class Stats(
    var production: Float = 0f,
    var food: Float = 0f,
    var gold: Float = 0f,
    var science: Float = 0f,
    var culture: Float = 0f,
    var happiness: Float = 0f,
    var faith: Float = 0f
) {
    // This is what facilitates indexed access by [Stat] or add(Stat,Float)
    // without additional memory allocation or expensive conditionals
    @delegate:Transient
    private val mapView: Map<Stat, KMutableProperty0<Float>> by lazy {
        mapOf(
            Stat.Production to ::production,
            Stat.Food to ::food,
            Stat.Gold to ::gold,
            Stat.Science to ::science,
            Stat.Culture to ::culture,
            Stat.Happiness to ::happiness,
            Stat.Faith to ::faith
        )
    }

    /** Indexed read of a value for a given [Stat], e.g. `this.gold == this[Stat.Gold]` */
    operator fun get(stat: Stat) = mapView[stat]!!.get()
    /** Indexed write of a value for a given [Stat], e.g. `this.gold += 1f` is equivalent to `this[Stat.Gold] += 1f` */
    operator fun set(stat: Stat, value: Float) = mapView[stat]!!.set(value)

    /** Compares two instances. Not callable via `==`. */
    // This is an overload, not an override conforming to the kotlin conventions of `equals(Any?)`,
    // so do not rely on it to be called for the `==` operator! A tad more efficient, though.
    @Suppress("CovariantEquals")    // historical reasons to keep this function signature
    fun equals(otherStats: Stats): Boolean {
        return production == otherStats.production
                && food == otherStats.food
                && gold == otherStats.gold
                && science == otherStats.science
                && culture == otherStats.culture
                && happiness == otherStats.happiness
                && faith == otherStats.faith
    }

    /** @return a new instance containing the same values as `this` */
    fun clone() = Stats(production, food, gold, science, culture, happiness, faith)

    /** @return `true` if all values are zero */
    fun isEmpty() = (
            production == 0f
            && food == 0f
            && gold == 0f
            && science == 0f
            && culture == 0f
            && happiness == 0f
            && faith == 0f )

    /** Reset all values to zero (in place) */
    fun clear() {
        production = 0f
        food = 0f
        gold = 0f
        science = 0f
        culture = 0f
        happiness = 0f
        faith = 0f
    }

    /** Adds each value of another [Stats] instance to this one in place */
    fun add(other: Stats) {
        production += other.production
        food += other.food
        gold += other.gold
        science += other.science
        culture += other.culture
        happiness += other.happiness
        faith += other.faith
    }

    /** @return a new [Stats] instance containing the sum of its operands value by value */
    operator fun plus(stats: Stats) = clone().apply { add(stats) }

    /** Adds the [value] parameter to the instance value specified by [stat] in place
     * @return `this` to allow chaining */
    fun add(stat: Stat, value: Float): Stats {
        mapView[stat]!!.set(value + mapView[stat]!!.get())
        return this
    }

    /** @return The result of multiplying each value of this instance by [number] as a new instance */
    operator fun times(number: Int) = times(number.toFloat())
    /** @return The result of multiplying each value of this instance by [number] as a new instance */
    operator fun times(number: Float) = Stats(
        production * number,
        food * number,
        gold * number,
        science * number,
        culture * number,
        happiness * number,
        faith * number
    )

    /** Multiplies each value of this instance by [number] in place */
    fun timesInPlace(number: Float) {
        production *= number
        food *= number
        gold *= number
        science *= number
        culture *= number
        happiness *= number
        faith *= number
    }
    
    operator fun div(number: Float) = Stats(
        production / number,
        food / number,
        gold / number,
        science / number,
        culture / number,
        happiness / number,
        faith / number
    )

    /** ***Not*** only a debug helper. It returns a string representing the content, already _translated_.
     * 
     * Example output: `+1 Production, -1 Food`.
     */
    override fun toString(): String {
        return toHashMap().filter { it.value != 0f }
                .map { (if (it.value > 0) "+" else "") + it.value.toInt() + " " + it.key.toString().tr() }.joinToString()
    }

    /** @return a Map copy of the values in this instance, can be used to iterate over the values */
    fun toHashMap(): HashMap<Stat, Float> {
        return linkedMapOf(
            Stat.Production to production,
            Stat.Food to food,
            Stat.Gold to gold,
            Stat.Science to science,
            Stat.Culture to culture,
            Stat.Happiness to happiness,
            Stat.Faith to faith
        )
    }


    companion object {
        private val allStatNames = Stat.values().joinToString("|") { it.name }
        private val statRegexPattern = "([+-])(\\d+) ($allStatNames)"
        private val statRegex = Regex(statRegexPattern)
        private val entireStringRegexPattern = Regex("$statRegexPattern(, $statRegexPattern)*")

        /** Tests a given string whether it is a valid representation of [Stats],
         * close to what [toString] would produce.
         * - Values _must_ carry a sign - "1 Gold" tests `false`, "+1 Gold" is OK.
         * - Separator is ", " - comma space - the space is _not_ optional.
         * - Stat names must be untranslated and match case.
         * - Order is not important.
         * @see [parse]
         */
        fun isStats(string: String): Boolean {
            if (string.isEmpty() || string[0] !in "+-") return false // very quick negative check before the heavy Regex
            return entireStringRegexPattern.matches(string)
        }

        /** Parses a string to a [Stats] instance
         * - Values _must_ carry a sign - "1 Gold" will not parse, "+1 Gold" is OK.
         * - Separator is ", " - comma space - the space is _not_ optional.
         * - Stat names must be untranslated and match case.
         * - Order is not important.
         * @see [isStats]
         */
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
