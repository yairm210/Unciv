package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts

class GameSpeed : RulesetObject() {
    var modifier: Float = 1f
    var dealDuration: Int = 30
    var goldGiftMod: Float = -1f
    var barbPercent: Float = -1f
    var goldenAgePercent: Float = -1f
    var religiousPressureAdjacentCity: Int = 6
    var peaceDealDuration: Int = 10
    var turnIncrements: ArrayList<HashMap<String, Float>> = ArrayList<HashMap<String, Float>>()

    val yearsToTurnObject: ArrayList<YearsPerTurn> by lazy { initYearsToTurn(turnIncrements) }

    companion object {
        const val DEFAULT: String = "Quick"
        const val DEFAULTFORSIMULATION: String = "Standard"
    }

    override fun getUniqueTarget(): UniqueTarget = UniqueTarget.Speed

    override fun makeLink(): String = "GameSpeed/$name"
    override fun getCivilopediaTextHeader() = FormattedLine(name, header = 2)
    override fun getCivilopediaTextLines(ruleset: Ruleset) = sequence {
        yield(FormattedLine("General speed modifier: [${modifier * 100}]% ${Fonts.turn}"))
        yield(FormattedLine("Diplomatic deal duration: [$dealDuration] turns ${Fonts.turn}"))
        yield(FormattedLine("Gold gift influence gain modifier: [${goldGiftMod * 100}]% ${Fonts.gold}"))
        yield(FormattedLine("Barbarian modifier: [${barbPercent * 100}]% ${Fonts.strength}"))
        yield(FormattedLine("Golden age length modifier: [${goldenAgePercent * 100}]% ${Fonts.happiness}"))
        yield(FormattedLine("Adjacent city religious pressure: [$religiousPressureAdjacentCity] ${Fonts.faith}"))
        yield(FormattedLine("Peace deal duration: [$peaceDealDuration] turns ${Fonts.turn}"))
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
        // checks for a non-negative modifier value when loading a mod ruleset
        // this way the user should see an error popup
        if (goldGiftMod < 0f) goldGiftMod = modifier
        if (barbPercent < 0f) barbPercent = modifier
        if (goldenAgePercent < 0f) goldenAgePercent = modifier
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
