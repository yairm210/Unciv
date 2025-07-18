package com.unciv.models.ruleset

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.abs

class Speed : RulesetObject(), IsPartOfGameInfoSerialization {
    var modifier: Float = 1f
    var goldCostModifier: Float = modifier
    var productionCostModifier: Float = modifier
    var scienceCostModifier: Float = modifier
    var cultureCostModifier: Float = modifier
    var faithCostModifier: Float = modifier
    var goldGiftModifier: Float = modifier
    var cityStateTributeScalingInterval: Float = 6.5f
    var barbarianModifier: Float = modifier
    var improvementBuildLengthModifier: Float = modifier
    var goldenAgeLengthModifier: Float = modifier
    var religiousPressureAdjacentCity: Int = 6
    var peaceDealDuration: Int = 10
    var dealDuration: Int = 30
    var startYear: Float = -4000f
    var turns: ArrayList<HashMap<String, Float>> = ArrayList()

    // These could be private but for RulesetValidator checking it
    data class YearsPerTurn(val yearInterval: Float, val untilTurn: Int) {
        internal constructor(rawRow: HashMap<String, Float>) : this(rawRow["yearsPerTurn"]!!, rawRow["untilTurn"]!!.toInt())
    }
    val yearsPerTurn: ArrayList<YearsPerTurn> by lazy { turns.mapTo(ArrayList()) { YearsPerTurn(it) } }

    /** End of defined turn range, used for starting Era's `startPercent` calculation */
    fun numTotalTurns(): Int = yearsPerTurn.last().untilTurn

    /** Calculate a Year from a turn number.
     *
     *  Note that years can have fractional parts and the integer part of the year for two consecutive turns _can_ be equal,
     *  but Unciv currently has no way to display that. This is left as Float to enable such display in the future,
     *  maybe as months, or even the 18+1 'months' of the mayan Haab'.
     *
     *  @param turn The logical turn number, any offset from starting in an advanced Era already added in
     */
    fun turnToYear(turn: Int): Float {
        var year = startYear
        var intervalStartTurn = 0
        val lastIntervalEndTurn = numTotalTurns()
        for ((turnLength, intervalEndTurn) in yearsPerTurn) {
            if (intervalStartTurn >= turn) break // ensure year isn't projected backwards for negative `turn`
            if (turn <= intervalEndTurn || intervalEndTurn == lastIntervalEndTurn) {
                // We can interpolate linearly within this interval and are done
                year += (turn - intervalStartTurn) * turnLength
                break
            }
            // Accumulate total length in years of this interval and move on to the following intervals.
            year += (intervalEndTurn - intervalStartTurn) * turnLength
            intervalStartTurn = intervalEndTurn
        }
        return year
    }

    val statCostModifiers: EnumMap<Stat, Float> by lazy {
        val map = EnumMap<Stat, Float>(Stat::class.java)
        for (stat in Stat.entries) {
            val modifier = when (stat) {
                Stat.Production -> productionCostModifier
                Stat.Gold -> goldCostModifier
                Stat.Science -> scienceCostModifier
                Stat.Faith -> faithCostModifier
                Stat.Culture -> cultureCostModifier
                else -> 1f
            }
            map[stat] = modifier
        }

        map
    }

    companion object {
        const val DEFAULT: String = "Quick"
        const val DEFAULTFORSIMULATION: String = "Standard"
    }

    // Note: Speed uniques will be treated as part of GlobalUniques
    override fun getUniqueTarget(): UniqueTarget = UniqueTarget.Speed

    override fun makeLink(): String = "Speed/$name"
    override fun getCivilopediaTextHeader() = FormattedLine(name, header = 2)
    override fun getCivilopediaTextLines(ruleset: Ruleset) = sequence {
        yield(FormattedLine("General speed modifier: [${modifier * 100}]%${Fonts.turn}"))
        yield(FormattedLine("Production cost modifier: [${productionCostModifier * 100}]%${Fonts.production}"))
        yield(FormattedLine("Gold cost modifier: [${goldCostModifier * 100}]%${Fonts.gold}"))
        yield(FormattedLine("Science cost modifier: [${scienceCostModifier * 100}]%${Fonts.science}"))
        yield(FormattedLine("Culture cost modifier: [${cultureCostModifier * 100}]%${Fonts.culture}"))
        yield(FormattedLine("Faith cost modifier: [${faithCostModifier * 100}]%${Fonts.faith}"))
        yield(FormattedLine("Improvement build length modifier: [${improvementBuildLengthModifier * 100}]%${Fonts.turn}"))
        yield(FormattedLine("Diplomatic deal duration: [$dealDuration] turns${Fonts.turn}"))
        yield(FormattedLine("Gold gift influence gain modifier: [${goldGiftModifier * 100}]%${Fonts.gold}"))
        yield(FormattedLine("City-state tribute scaling interval: [${cityStateTributeScalingInterval}] turns${Fonts.turn}"))
        yield(FormattedLine("Barbarian spawn modifier: [${barbarianModifier * 100}]%${Fonts.strength}"))
        yield(FormattedLine("Golden age length modifier: [${goldenAgeLengthModifier * 100}]%${Fonts.happiness}"))
        yield(FormattedLine("Adjacent city religious pressure: [$religiousPressureAdjacentCity]${Fonts.faith}"))
        yield(FormattedLine("Peace deal duration: [$peaceDealDuration] turns${Fonts.turn}"))
        yield(FormattedLine("Start year: [" + ("{[${abs(startYear).toInt()}] " + (if (startYear < 0) "BC" else "AD") + "}]").tr()))
        yieldAll(uniquesToCivilopediaTextLines())
    }.toList()
    override fun getSortGroup(ruleset: Ruleset): Int = (modifier * 1000).toInt()
}
