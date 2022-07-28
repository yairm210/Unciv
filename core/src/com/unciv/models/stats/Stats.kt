package com.unciv.models.stats

import com.unciv.models.translations.tr
import kotlin.reflect.KMutableProperty0

/**
 * A container for the seven basic ["currencies"][Stat] in Unciv,
 * **Mutable**, allowing for easy merging of sources and applying bonuses.
 *
 * Supports e.g. `for ((key,value) in <Stats>)` - the [iterator] will skip zero values automatically.
 *
 * Also possible: `<Stats>`.[values].sum() and similar aggregates over a Sequence<Float>.
 */
open class Stats(
    var production: Float = 0f,
    var food: Float = 0f,
    var gold: Float = 0f,
    var science: Float = 0f,
    var culture: Float = 0f,
    var happiness: Float = 0f,
    var faith: Float = 0f
): Iterable<Stats.StatValuePair> {

    // This is what facilitates indexed access by [Stat] or add(Stat,Float)
    // without additional memory allocation or expensive conditionals
    private fun statToProperty(stat: Stat):KMutableProperty0<Float>{
        return when(stat){
            Stat.Production -> ::production
            Stat.Food -> ::food
            Stat.Gold -> ::gold
            Stat.Science -> ::science
            Stat.Culture -> ::culture
            Stat.Happiness -> ::happiness
            Stat.Faith -> ::faith
        }
    }

    /** Indexed read of a value for a given [Stat], e.g. `this.gold == this[Stat.Gold]` */
    operator fun get(stat: Stat): Float {
        return statToProperty(stat).get()
    }
    /** Indexed write of a value for a given [Stat], e.g. `this.gold += 1f` is equivalent to `this[Stat.Gold] += 1f` */
    operator fun set(stat: Stat, value: Float) = statToProperty(stat).set(value)

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
        set(stat, value + get(stat))
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

    operator fun div(number: Float) = times(1/number)

    /** Apply weighting for Production Ranking */
    fun applyRankingWeights(){
        food *= 14
        production *= 12
        gold *= 8 // 3 gold worth about 2 production
        science *= 7
        culture *= 6
        happiness *= 10 // base
        faith *= 5
    }

    /** ***Not*** only a debug helper. It returns a string representing the content, already _translated_.
     *
     * Example output: `+1 Production, -1 Food`.
     */
    override fun toString(): String {
        return this.joinToString {
            (if (it.value > 0) "+" else "") + it.value.toInt() + " " + it.key.toString().tr()
        }
    }

    // For display in diplomacy window
    fun toStringWithDecimals(): String {
        return this.joinToString {
            (if (it.value > 0) "+" else "") + it.value.toString().removeSuffix(".0") + " " + it.key.toString().tr()
        }
    }

    // function that removes the icon from the Stats object since the circular icons all appear the same
    // delete this and replace above instances with toString() once the text-coloring-affecting-font-icons bug is fixed (e.g., in notification text)
    fun toStringWithoutIcons(): String {
        return this.joinToString {
            it.value.toInt().toString() + " " + it.key.name.tr().substring(startIndex = 1)
        }
    }

    /** Represents one [key][Stat]/[value][Float] pair returned by the [iterator] */
    data class StatValuePair (val key: Stat, val value: Float)

    /** Enables iteration over the non-zero [Stat]/value [pairs][StatValuePair].
     * Explicit use unnecessary - [Stats] is [iterable][Iterable] directly.
     * @see iterator */
    fun asSequence() = sequence {
        if (production != 0f) yield(StatValuePair(Stat.Production, production))
        if (food != 0f) yield(StatValuePair(Stat.Food, food))
        if (gold != 0f) yield(StatValuePair(Stat.Gold, gold))
        if (science != 0f) yield(StatValuePair(Stat.Science, science))
        if (culture != 0f) yield(StatValuePair(Stat.Culture, culture))
        if (happiness != 0f) yield(StatValuePair(Stat.Happiness, happiness))
        if (faith != 0f) yield(StatValuePair(Stat.Faith, faith))
    }

    /** Enables aggregates over the values, never empty */
    // Property syntax to emulate Map.values pattern
    // Doesn't skip zero values as it's meant for sum() or max() where the overhead would be higher than any gain
    val values
        get() = sequence {
            yield(production)
            yield(food)
            yield(gold)
            yield(science)
            yield(culture)
            yield(happiness)
            yield(faith)
        }

    /** Returns an iterator over the elements of this object, wrapped as [StatValuePair]s */
    override fun iterator(): Iterator<StatValuePair> = asSequence().iterator()


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
