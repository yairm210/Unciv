package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.stats.Stat
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts

class GameSpeed : RulesetObject() {
    var modifier: Float = 1f
    var goldCostModifier: Float = -1f
    //var foodCostModifier: Float = -1f
    var productionCostModifier: Float = -1f
    var scienceCostModifier: Float = -1f
    var cultureCostModifier: Float = -1f
    var faithCostModifier: Float = -1f
    var goldGiftModifier: Float = -1f
    var barbarianModifier: Float = -1f
    var improvementBuildLengthModifier: Float = -1f
    var goldenAgeLengthModifier: Float = -1f
    var religiousPressureAdjacentCity: Int = 6
    var peaceDealDuration: Int = 10
    var dealDuration: Int = 30
    var turnIncrements: ArrayList<HashMap<String, Float>> = ArrayList()

    val yearsToTurnObject: ArrayList<YearsPerTurn> by lazy { initYearsToTurn(turnIncrements) }

    fun statCostModifiers(): HashMap<Stat, Float> {
        val costModifiers: HashMap<Stat, Float> = HashMap()
        costModifiers[Stat.Food] = 1f
        costModifiers[Stat.Production] = productionCostModifier
        costModifiers[Stat.Gold] = goldCostModifier
        costModifiers[Stat.Science] = scienceCostModifier
        costModifiers[Stat.Faith] = faithCostModifier
        costModifiers[Stat.Happiness] = 1f
        return costModifiers
    }

    companion object {
        const val DEFAULT: String = "Quick"
        const val DEFAULTFORSIMULATION: String = "Standard"
    }

    override fun getUniqueTarget(): UniqueTarget = UniqueTarget.Speed

    override fun makeLink(): String = "GameSpeed/$name"
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
        yield(FormattedLine("Barbarian spawn modifier: [${barbarianModifier * 100}]%${Fonts.strength}"))
        yield(FormattedLine("Golden age length modifier: [${goldenAgeLengthModifier * 100}]%${Fonts.happiness}"))
        yield(FormattedLine("Adjacent city religious pressure: [$religiousPressureAdjacentCity]${Fonts.faith}"))
        yield(FormattedLine("Peace deal duration: [$peaceDealDuration] turns${Fonts.turn}"))
    }.toList()

    fun numTotalTurns(): Int = yearsToTurnObject.last().toTurn

    private fun initYearsToTurn(yearsToTurn: ArrayList<HashMap<String, Float>>): ArrayList<YearsPerTurn> {
        val yptList = ArrayList<YearsPerTurn>()

        for (incrementMap: HashMap<String, Float> in yearsToTurn) {
            if (incrementMap["turnsPerIncrement"] == null || incrementMap["yearsPerTurn"] == null) continue
            val runningSum = incrementMap["turnsPerIncrement"]!!.toInt() + if (yptList.size > 0) yptList.last().toTurn else 0
            yptList.add(YearsPerTurn(incrementMap["yearsPerTurn"]!!, runningSum))
        }
        return yptList
    }

    fun initDefaultPercents() {
        // checks for a non-negative `modifier` value when loading a mod ruleset
        // this way the user should see an error popup
        if (goldCostModifier < 0f) goldCostModifier = modifier
        if (productionCostModifier < 0f) productionCostModifier = modifier
        if (scienceCostModifier < 0f) scienceCostModifier = modifier
        if (cultureCostModifier < 0f) cultureCostModifier = modifier
        if (faithCostModifier < 0f) faithCostModifier = modifier
        if (improvementBuildLengthModifier < 0f) improvementBuildLengthModifier = modifier
        if (goldGiftModifier < 0f) goldGiftModifier = modifier
        if (barbarianModifier < 0f) barbarianModifier = modifier
        if (goldenAgeLengthModifier < 0f) goldenAgeLengthModifier = modifier
    }
}
class YearsPerTurn {
    var yearInterval: Float = 0f
    var toTurn: Int = 0

    constructor(yearsPerTurn: Float, turnsPerIncrement: Int) {
        this.yearInterval = yearsPerTurn
        this.toTurn = turnsPerIncrement
    }
}
