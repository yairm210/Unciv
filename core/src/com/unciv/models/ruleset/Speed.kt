package com.unciv.models.ruleset

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.civilopediascreen.FormattedLine
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
    var peaceNegotiationDuration: Int = 10
    var dealDuration: Int = 30
    var startYear: Float = -4000f
    var turns: ArrayList<HashMap<String, Float>> = ArrayList()

    val yearsPerTurn: ArrayList<YearsPerTurn> by lazy {
        ArrayList<YearsPerTurn>().apply {
            turns.forEach { this.add(YearsPerTurn(it["yearsPerTurn"]!!, it["untilTurn"]!!.toInt())) }
        }
    }

    val statCostModifiers: HashMap<Stat, Float> by lazy {
        val map = HashMap<Stat, Float>()
        for (stat in Stat.values()) {
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

    // Note: Speed is IHasUniques, but no implementation reads them, thus no UniqueType accepts this target
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
    }.toList()
    override fun getSortGroup(ruleset: Ruleset): Int = (modifier * 1000).toInt()

    fun numTotalTurns(): Int = yearsPerTurn.last().untilTurn
}

class YearsPerTurn {
    var yearInterval: Float = 0f
    var untilTurn: Int = 0

    constructor(yearsPerTurn: Float, turnsPerIncrement: Int) {
        this.yearInterval = yearsPerTurn
        this.untilTurn = turnsPerIncrement
    }
}
