package com.unciv.logic.automation

import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stats
import kotlin.math.max
import kotlin.math.sqrt

object Automation {

    fun rankTileForCityWork(tile: TileInfo, city: CityInfo, foodWeight: Float = 1f): Float {
        val stats = tile.getTileStats(city, city.civInfo)
        return rankStatsForCityWork(stats, city, foodWeight)
    }

    private fun rankStatsForCityWork(stats: Stats, city: CityInfo, foodWeight: Float = 1f): Float {
        var rank = 0f
        if (city.population.population < 5) {
            // "small city" - we care more about food and less about global problems like gold science and culture
            rank += stats.food * 1.2f * foodWeight
            rank += stats.production
            rank += stats.science / 2
            rank += stats.culture / 2
            rank += stats.gold / 5 // it's barely worth anything at this point
        } else {
            if (stats.food <= 2 || city.civInfo.getHappiness() > 5) rank += stats.food * 1.2f * foodWeight // food get more value to keep city growing
            else rank += (2.4f + (stats.food - 2) / 2) * foodWeight // 1.2 point for each food up to 2, from there on half a point

            if (city.civInfo.gold < 0 && city.civInfo.statsForNextTurn.gold <= 0)
                rank += stats.gold // we have a global problem
            else rank += stats.gold / 3 // 3 gold is worse than 2 production

            rank += stats.production
            rank += stats.science
            if (city.tiles.size < 12 || city.civInfo.victoryType() == VictoryType.Cultural) {
                rank += stats.culture
            } else rank += stats.culture / 2
        }
        return rank
    }

    internal fun rankSpecialist(stats: Stats, cityInfo: CityInfo): Float {
        var rank = rankStatsForCityWork(stats, cityInfo)
        rank += 0.3f //GPP bonus
        return rank
    }

    fun tryTrainMilitaryUnit(city: CityInfo) {
        val chosenUnitName = chooseMilitaryUnit(city)
        if (chosenUnitName != null)
            city.cityConstructions.currentConstructionFromQueue = chosenUnitName
    }

    fun chooseMilitaryUnit(city: CityInfo): String? {
        var militaryUnits =
            city.cityConstructions.getConstructableUnits().filter { !it.isCivilian() }
        if (militaryUnits.map { it.name }
                .contains(city.cityConstructions.currentConstructionFromQueue))
            return city.cityConstructions.currentConstructionFromQueue

        // This is so that the AI doesn't use all its aluminum on units and have none left for spaceship parts
        val aluminum = city.civInfo.getCivResourcesByName()["Aluminum"]
        if (aluminum != null && aluminum < 2) // mods may have no aluminum
            militaryUnits.filter { !it.getResourceRequirements().containsKey("Aluminum") }

        val findWaterConnectedCitiesAndEnemies =
            BFS(city.getCenterTile()) { it.isWater || it.isCityCenter() }
        findWaterConnectedCitiesAndEnemies.stepToEnd()
        if (findWaterConnectedCitiesAndEnemies.getReachedTiles().none {
                (it.isCityCenter() && it.getOwner() != city.civInfo)
                        || (it.militaryUnit != null && it.militaryUnit!!.civInfo != city.civInfo)
            }) // there is absolutely no reason for you to make water units on this body of water.
            militaryUnits = militaryUnits.filter { !it.isWaterUnit() }

        val chosenUnit: BaseUnit
        if (!city.civInfo.isAtWar() 
            && city.civInfo.cities.any { it.getCenterTile().militaryUnit == null }
            && militaryUnits.any { it.isRanged() } // this is for city defence so get a ranged unit if we can
        ) {
            chosenUnit = militaryUnits
                .filter { it.isRanged() }
                .maxByOrNull { it.cost }!!
        } else { // randomize type of unit and take the most expensive of its kind
            val availableTypes = militaryUnits
                .map { it.unitType }
                .distinct()
                .toList()
            if (availableTypes.isEmpty()) return null
            val randomType = availableTypes.random()
            chosenUnit = militaryUnits
                .filter { it.unitType == randomType }
                .maxByOrNull { it.cost }!!
        }
        return chosenUnit.name
    }

    fun evaluateCombatStrength(civInfo: CivilizationInfo): Int {
        // Since units become exponentially stronger per combat strength increase, we square em all
        fun square(x: Int) = x * x
        val unitStrength = civInfo.getCivUnits()
            .map { square(max(it.baseUnit().strength, it.baseUnit().rangedStrength)) }.sum()
        return sqrt(unitStrength.toDouble()).toInt() + 1 //avoid 0, because we divide by the result
    }

    fun threatAssessment(assessor: CivilizationInfo, assessed: CivilizationInfo): ThreatLevel {
        val powerLevelComparison =
            evaluateCombatStrength(assessed) / evaluateCombatStrength(assessor).toFloat()
        return when {
            powerLevelComparison > 2 -> ThreatLevel.VeryHigh
            powerLevelComparison > 1.5f -> ThreatLevel.High
            powerLevelComparison < (1 / 1.5f) -> ThreatLevel.Low
            powerLevelComparison < 0.5f -> ThreatLevel.VeryLow
            else -> ThreatLevel.Medium
        }
    }

    // Ranks a tile for any purpose except the expansion algorithm of cities
    internal fun rankTile(tile: TileInfo?, civInfo: CivilizationInfo): Float {
        if (tile == null) return 0f
        val tileOwner = tile.getOwner()
        if (tileOwner != null && tileOwner != civInfo) return 0f // Already belongs to another civilization, useless to us
        val stats = tile.getTileStats(null, civInfo)
        var rank = rankStatsValue(stats, civInfo)
        if (tile.improvement == null) rank += 0.5f // improvement potential!
        if (tile.hasViewableResource(civInfo)) {
            val resource = tile.getTileResource()
            if (resource.resourceType != ResourceType.Bonus) rank += 1f // for usage
            if (tile.improvement == null) rank += 1f // improvement potential - resources give lots when improved!
        }
        return rank
    }
    
    // Ranks a tile for the expansion algorithm of cities
    internal fun rankTileForExpansion(tile: TileInfo, cityInfo: CityInfo): Int {
        // https://github.com/Gedemon/Civ5-DLL/blob/aa29e80751f541ae04858b6d2a2c7dcca454201e/CvGameCoreDLL_Expansion1/CvCity.cpp#L10301
        // Apparently this is not the full calculation. The exact tiles are also
        // dependent on which tiles are between the chosen tile and the city center
        // Exact details are not implemented, but can be found in CvAStar.cpp:2119,
        // function `InfluenceCost()`.
        // Implementing these will require an additional variable for each terrainType
        val distance = tile.aerialDistanceTo(cityInfo.getCenterTile())

        // Higher score means tile is less likely to be picked
        var score = distance * 100

        // Resources are good: less points
        if (tile.hasViewableResource(cityInfo.civInfo)) {
            if (tile.getTileResource().resourceType != ResourceType.Bonus) score -= 105
            else if (distance <= 3) score -= 104
            
        } else {
            // Water tiles without resources aren't great
            if (tile.isWater) score += 25
            // Can't work it anyways
            if (distance > 3) score += 100
        }

        // Improvements are good: less points
        if (tile.improvement != null &&
            tile.getImprovementStats(tile.getTileImprovement()!!, cityInfo.civInfo, cityInfo).toHashMap().values.sum() > 0f
        ) score -= 5

        // The original checks if the tile has a road, but adds a score of 0 if it does.
        // Therefore, this check is removed here.
        
        if (tile.naturalWonder != null) score -= 105

        // Straight up take the sum of all yields
        score -= tile.getTileStats(null, cityInfo.civInfo).toHashMap().values.sum().toInt()

        // Check if we get access to better tiles from this tile
        var adjacentNaturalWonder = false

        for (adjacentTile in tile.neighbors.filter { it.getOwner() == null }) {
            val adjacentDistance = cityInfo.getCenterTile().aerialDistanceTo(adjacentTile)
            if (adjacentTile.hasViewableResource(cityInfo.civInfo) &&
                (adjacentDistance < 3 ||
                    adjacentTile.getTileResource().resourceType != ResourceType.Bonus
                )
            ) score -= 1
            if (adjacentTile.naturalWonder != null) {
                if (adjacentDistance < 3) adjacentNaturalWonder = true
                score -= 1
            }
        }
        if (adjacentNaturalWonder) score -= 1

        // Tiles not adjacent to owned land are very hard to acquire
        if (tile.neighbors.none { it.getCity() != null && it.getCity()!!.id == cityInfo.id })
            score += 1000
        
        return score
    }

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
