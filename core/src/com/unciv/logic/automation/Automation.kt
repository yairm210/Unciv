package com.unciv.logic.automation

import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.models.stats.Stats
import kotlin.math.max
import kotlin.math.sqrt

class Automation {

    internal fun rankTile(tile: TileInfo?, civInfo: CivilizationInfo): Float {
        if (tile == null) return 0.0f
        val stats = tile.getTileStats(null, civInfo)
        var rank = rankStatsValue(stats, civInfo)
        if (tile.improvement == null) rank += 0.5f // improvement potential!
        if (tile.hasViewableResource(civInfo)) rank += 1.0f
        return rank
    }

    internal fun rankSpecialist(stats: Stats?, civInfo: CivilizationInfo): Float {
        if (stats == null) return 0.0f
        var rank = rankStatsValue(stats, civInfo)
        rank += 0.3f //GPP bonus
        return rank
    }

    fun rankStatsValue(stats: Stats, civInfo: CivilizationInfo): Float {
        var rank = 0.0f
        if (stats.food <= 2) rank += (stats.food * 1.2f) //food get more value to keep city growing
        else rank += (2.4f + (stats.food - 2) / 2) // 1.2 point for each food up to 2, from there on half a point

        if (civInfo.gold < 0 && civInfo.statsForNextTurn.gold <= 0) rank += stats.gold
        else rank += stats.gold / 2

        rank += stats.production
        rank += stats.science
        rank += stats.culture
        return rank
    }

    fun trainMilitaryUnit(city: CityInfo) {
        val name = chooseMilitaryUnit(city)
        city.cityConstructions.currentConstruction = name
    }

    fun chooseMilitaryUnit(city: CityInfo) : String {
        var militaryUnits = city.cityConstructions.getConstructableUnits().filter { !it.unitType.isCivilian() }
        if (militaryUnits.map { it.name }.contains(city.cityConstructions.currentConstruction))
            return city.cityConstructions.currentConstruction

        val findWaterConnectedCitiesAndEnemies = BFS(city.getCenterTile()){it.isWater || it.isCityCenter()}
        findWaterConnectedCitiesAndEnemies.stepToEnd()
        if(findWaterConnectedCitiesAndEnemies.tilesReached.keys.none {
                    (it.isCityCenter() && it.getOwner() != city.civInfo)
                            || (it.militaryUnit != null && it.militaryUnit!!.civInfo != city.civInfo)
                }) // there is absolutely no reason for you to make water units on this body of water.
            militaryUnits = militaryUnits.filter { it.unitType.isLandUnit() }

        val chosenUnit: BaseUnit
        if(!city.civInfo.isAtWar() && city.civInfo.cities.any { it.getCenterTile().militaryUnit==null}
                && militaryUnits.any { it.unitType== UnitType.Ranged }) // this is for city defence so get an archery unit if we can
            chosenUnit = militaryUnits.filter { it.unitType== UnitType.Ranged }.maxBy { it.cost }!!

        else{ // randomize type of unit and take the most expensive of its kind
            val chosenUnitType = militaryUnits.map { it.unitType }.distinct().filterNot{it==UnitType.Scout}.random()
            chosenUnit = militaryUnits.filter { it.unitType==chosenUnitType }.maxBy { it.cost }!!
        }
        return chosenUnit.name
    }



    fun evaluteCombatStrength(civInfo: CivilizationInfo): Int {
        // Since units become exponentially stronger per combat strength increase, we square em all
        fun square(x:Int) = x*x
        val unitStrength =  civInfo.getCivUnits().map { square(max(it.baseUnit().strength, it.baseUnit().rangedStrength)) }.sum()
        val cityStrength = civInfo.cities.map { square(CityCombatant(it).getCityStrength()) }.sum()
        return (sqrt(unitStrength.toDouble()) /*+ sqrt(cityStrength.toDouble())*/).toInt() + 1 //avoid 0, becaus we divide by the result
    }

    fun threatAssessment(assessor:CivilizationInfo, assessed: CivilizationInfo): ThreatLevel {
        val powerLevelComparison = evaluteCombatStrength(assessed)/evaluteCombatStrength(assessor).toFloat()
        when{
            powerLevelComparison>2 -> return ThreatLevel.VeryHigh
            powerLevelComparison>1.5f -> return ThreatLevel.High
            powerLevelComparison<(1/1.5f) -> return ThreatLevel.Low
            powerLevelComparison<0.5f -> return ThreatLevel.VeryLow
            else -> return ThreatLevel.Medium
        }
    }

}

enum class ThreatLevel{
    VeryLow,
    Low,
    Medium,
    High,
    VeryHigh
}

