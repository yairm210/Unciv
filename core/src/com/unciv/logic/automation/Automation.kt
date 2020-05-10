package com.unciv.logic.automation

import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.models.stats.Stats
import kotlin.math.max
import kotlin.math.sqrt

object Automation {

    fun rankTileForCityWork(tile:TileInfo, city: CityInfo, foodWeight: Float = 1f): Float {
        val stats = tile.getTileStats(city, city.civInfo)
        return rankStatsForCityWork(stats, city, foodWeight)
    }

    private fun rankStatsForCityWork(stats: Stats, city: CityInfo, foodWeight: Float = 1f): Float {
        var rank = 0f
        if(city.population.population < 5){
            // "small city" - we care more about food and less about global problems like gold science and culture
            rank += stats.food * 1.2f * foodWeight
            rank += stats.production
            rank += stats.science/2
            rank += stats.culture/2
            rank += stats.gold / 5 // it's barely worth anything at this points
        }
        else{
            if (stats.food <= 2 || city.civInfo.getHappiness() > 5) rank += (stats.food * 1.2f * foodWeight) //food get more value to keep city growing
            else rank += ((2.4f + (stats.food - 2) / 2) * foodWeight) // 1.2 point for each food up to 2, from there on half a point

            if (city.civInfo.gold < 0 && city.civInfo.statsForNextTurn.gold <= 0) rank += stats.gold // we have a global problem
            else rank += stats.gold / 3 // 3 gold is worse than 2 production

            rank += stats.production
            rank += stats.science
            if (city.tiles.size < 12 || city.civInfo.victoryType() == VictoryType.Cultural){
                rank += stats.culture
            }
            else{
                rank += stats.culture / 2
            }
        }
        return rank
    }

    internal fun rankSpecialist(stats: Stats, cityInfo: CityInfo): Float {
        var rank = rankStatsForCityWork(stats, cityInfo)
        rank += 0.3f //GPP bonus
        return rank
    }

    fun trainMilitaryUnit(city: CityInfo) {
        val name = chooseMilitaryUnit(city)
        city.cityConstructions.currentConstructionFromQueue = name
    }

    fun chooseMilitaryUnit(city: CityInfo) : String {
        var militaryUnits = city.cityConstructions.getConstructableUnits().filter { !it.unitType.isCivilian() }
        if (militaryUnits.map { it.name }.contains(city.cityConstructions.currentConstructionFromQueue))
            return city.cityConstructions.currentConstructionFromQueue

        val findWaterConnectedCitiesAndEnemies = BFS(city.getCenterTile()){it.isWater || it.isCityCenter()}
        findWaterConnectedCitiesAndEnemies.stepToEnd()
        if(findWaterConnectedCitiesAndEnemies.tilesReached.keys.none {
                    (it.isCityCenter() && it.getOwner() != city.civInfo)
                            || (it.militaryUnit != null && it.militaryUnit!!.civInfo != city.civInfo)
                }) // there is absolutely no reason for you to make water units on this body of water.
            militaryUnits = militaryUnits.filter { it.unitType.isLandUnit() || it.unitType.isAirUnit() }

        val chosenUnit: BaseUnit
        if(!city.civInfo.isAtWar() && city.civInfo.cities.any { it.getCenterTile().militaryUnit==null}
                && militaryUnits.any { it.unitType== UnitType.Ranged }) // this is for city defence so get an archery unit if we can
            chosenUnit = militaryUnits.filter { it.unitType== UnitType.Ranged }.maxBy { it.cost }!!

        else{ // randomize type of unit and take the most expensive of its kind
            val chosenUnitType = militaryUnits.map { it.unitType }.distinct().filterNot{it==UnitType.Scout}.toList().random()
            chosenUnit = militaryUnits.filter { it.unitType==chosenUnitType }.maxBy { it.cost }!!
        }
        return chosenUnit.name
    }

    fun evaluteCombatStrength(civInfo: CivilizationInfo): Int {
        // Since units become exponentially stronger per combat strength increase, we square em all
        fun square(x:Int) = x*x
        val unitStrength =  civInfo.getCivUnits().map { square(max(it.baseUnit().strength, it.baseUnit().rangedStrength)) }.sum()
        return (sqrt(unitStrength.toDouble())).toInt() + 1 //avoid 0, becaus we divide by the result
    }

    fun threatAssessment(assessor:CivilizationInfo, assessed: CivilizationInfo): ThreatLevel {
        val powerLevelComparison = evaluteCombatStrength(assessed)/evaluteCombatStrength(assessor).toFloat()
        return when {
            powerLevelComparison>2 -> ThreatLevel.VeryHigh
            powerLevelComparison>1.5f -> ThreatLevel.High
            powerLevelComparison<(1/1.5f) -> ThreatLevel.Low
            powerLevelComparison<0.5f -> ThreatLevel.VeryLow
            else -> ThreatLevel.Medium
        }
    }

    internal fun rankTile(tile: TileInfo?, civInfo: CivilizationInfo): Float {
        if (tile == null) return 0f
        val tileOwner = tile.getOwner()
        if (tileOwner != null && tileOwner != civInfo) return 0f // Already belongs to another civilization, useless to us
        val stats = tile.getTileStats(null, civInfo)
        var rank = rankStatsValue(stats, civInfo)
        if (tile.improvement == null) rank += 0.5f // improvement potential!
        if (tile.hasViewableResource(civInfo)) rank += 1.0f
        return rank
    }

    @JvmStatic
    fun rankStatsValue(stats: Stats, civInfo: CivilizationInfo): Float {
        var rank = 0.0f
        if (stats.food <= 2) rank += (stats.food * 1.2f) //food get more value to keep city growing
        else rank += (2.4f + (stats.food - 2) / 2) // 1.2 point for each food up to 2, from there on half a point

        if (civInfo.gold < 0 && civInfo.statsForNextTurn.gold <= 0) rank += stats.gold
        else rank += stats.gold / 3 // 3 gold is much worse than 2 production

        rank += stats.production
        rank += stats.science
        rank += stats.culture
        return rank
    }
}

enum class ThreatLevel{
    VeryLow,
    Low,
    Medium,
    High,
    VeryHigh
}

