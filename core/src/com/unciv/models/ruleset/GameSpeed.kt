package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.UniqueTarget

class GameSpeed : RulesetObject(), IHasUniques {
    var modifier: Float = 1f
    var dealDuration: Int = 30
    var growthPercent: Float = -1f
    var trainPercent: Float = -1f
    var constructPercent: Float = -1f
    var createPercent: Float = -1f
    var researchPercent: Float = -1f
    var goldPercent: Float = -1f
    var goldGiftMod: Float = -1f
    var buildPercent: Float = -1f
    var improvementPercent: Float = -1f
    var greatPeoplePercent: Float = -1f
    var culturePercent: Float = -1f
    var faithPercent: Float = -1f
    var barbPercent: Float = -1f
    var featureProductionPercent: Float = -1f
    var unitDiscoverPercent: Float = -1f
    var unitHurryPercent: Float = -1f
    var unitTradePercent: Float = -1f
    var goldenAgePercent: Float = -1f
    var hurryPercent: Float = -1f
    var inflationPercent: Float = 0.3f
    var inflationOffset: Int = -90
    var religiousPressureAdjacentCity: Int = 6
    var victoryDelayPercent: Float = -1f
    var minorCivElectionFreqMod: Float = -1f
    var opinionDurationPercent: Float = -1f
    var spyRatePercent: Float = 1f
    var peaceDealDuration: Int = 10
    var relationshipDuration: Int = 50
    //var turnIncrements: HashMap<String, List<Int>> = HashMap<String, List<Int>>()
    var turnIncrements: ArrayList<HashMap<String, Float>> = ArrayList<HashMap<String, Float>>()

    val yearsToTurnObject: ArrayList<YearsPerTurn> by lazy { initYearsToTurn(turnIncrements) }

    override fun getUniqueTarget(): UniqueTarget = UniqueTarget.Speed

    override fun makeLink(): String = "Speed/$name"

    fun numTotalTurns(): Int = yearsToTurnObject.sumOf { it.toTurn }

    private fun initYearsToTurn(yearsToTurn: ArrayList<HashMap<String, Float>>): ArrayList<YearsPerTurn> {
        val yptList = ArrayList<YearsPerTurn>()

        for (incrementMap: HashMap<String, Float> in yearsToTurn) {
            if (incrementMap == null || incrementMap["turnsPerIncrement"] == null || incrementMap["yearsPerIncrement"] == null) continue
            val runningSum = incrementMap["turnsPerIncrement"]!!.toInt() + if (yptList.size > 0) yptList.last().toTurn else 0
            yptList.add(YearsPerTurn(incrementMap["yearsPerIncrement"]!!, runningSum))
        }

        //for ((str, increment) in yearsToTurn) {
        //    val runningSum = increment[1] + if (yptList.size > 0) yptList.last().toTurn else 0
        //    yptList.add(YearsPerTurn(increment[0] as Float, runningSum))
        //}

        return yptList
    }

    fun initDefaultPercents() {
        assert(modifier > 0f)
        if (growthPercent < 0f) growthPercent = modifier
        if (trainPercent < 0f) trainPercent = modifier
        if (constructPercent < 0f) constructPercent = modifier
        if (createPercent < 0f) createPercent = modifier
        if (researchPercent < 0f) researchPercent = modifier
        if (goldPercent < 0f) goldPercent = modifier
        if (goldGiftMod < 0f) goldGiftMod = modifier
        if (buildPercent < 0f) buildPercent = modifier
        if (improvementPercent < 0f) improvementPercent = modifier
        if (greatPeoplePercent < 0f) greatPeoplePercent = modifier
        if (culturePercent < 0f) culturePercent = modifier
        if (faithPercent < 0f) faithPercent = modifier
        if (barbPercent < 0f) barbPercent = modifier
        if (featureProductionPercent < 0f) featureProductionPercent = modifier
        if (unitDiscoverPercent < 0f) unitDiscoverPercent = modifier
        if (unitHurryPercent < 0f) unitHurryPercent = modifier
        if (unitTradePercent < 0f) unitTradePercent = modifier
        if (goldenAgePercent < 0f) goldenAgePercent = modifier
        if (hurryPercent < 0f) hurryPercent = modifier
        if (victoryDelayPercent < 0f) victoryDelayPercent = modifier
        if (minorCivElectionFreqMod < 0f) minorCivElectionFreqMod = modifier
        if (opinionDurationPercent < 0f) opinionDurationPercent = modifier
        if (spyRatePercent < 0f) spyRatePercent = modifier
    }
}
class YearsPerTurn {
    var yearInterval: Float = 0f
    var toTurn: Int = 0

    constructor(unitsPerIncrement: Float, turnsPerIncrement: Int) {
        this.yearInterval = unitsPerIncrement
        this.toTurn = turnsPerIncrement
    }
}