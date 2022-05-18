package com.unciv.logic.automation

import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stats
import com.unciv.ui.victoryscreen.RankingType

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
            if (city.tiles.size < 12 || city.civInfo.wantsToFocusOn(Victory.Focus.Culture)) {
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

    private fun providesUnneededCarryingSlots(baseUnit: BaseUnit, civInfo: CivilizationInfo): Boolean {
        // Simplified, will not work for crazy mods with more than one carrying filter for a unit
        val carryUnique = baseUnit.getMatchingUniques(UniqueType.CarryAirUnits).first()
        val carryFilter = carryUnique.params[1]

        fun getCarryAmount(mapUnit: MapUnit): Int {
            val mapUnitCarryUnique =
                mapUnit.getMatchingUniques(UniqueType.CarryAirUnits).firstOrNull() ?: return 0
            if (mapUnitCarryUnique.params[1] != carryFilter) return 0 //Carries a different type of unit
            return mapUnitCarryUnique.params[0].toInt() +
                    mapUnit.getMatchingUniques(UniqueType.CarryExtraAirUnits)
                        .filter { it.params[1] == carryFilter }.sumOf { it.params[0].toInt() }
        }

        val totalCarriableUnits =
            civInfo.getCivUnits().count { it.matchesFilter(carryFilter) }
        val totalCarryingSlots = civInfo.getCivUnits().sumOf { getCarryAmount(it) }
        return totalCarriableUnits < totalCarryingSlots
    }

    fun chooseMilitaryUnit(city: CityInfo): String? {
        val currentChoice = city.cityConstructions.getCurrentConstruction()
        if (currentChoice is BaseUnit && !currentChoice.isCivilian()) return city.cityConstructions.currentConstructionFromQueue

        var militaryUnits = city.getRuleset().units.values.asSequence()
            .filter { !it.isCivilian() }
            .filter { allowSpendingResource(city.civInfo, it) }

        val findWaterConnectedCitiesAndEnemies =
            BFS(city.getCenterTile()) { it.isWater || it.isCityCenter() }
        findWaterConnectedCitiesAndEnemies.stepToEnd()
        if (findWaterConnectedCitiesAndEnemies.getReachedTiles().none {
                (it.isCityCenter() && it.getOwner() != city.civInfo)
                        || (it.militaryUnit != null && it.militaryUnit!!.civInfo != city.civInfo)
            }) // there is absolutely no reason for you to make water units on this body of water.
            militaryUnits = militaryUnits.filter { !it.isWaterUnit() }


        val carryingOnlyUnits = militaryUnits.filter { it.hasUnique(UniqueType.CarryAirUnits)
                && it.hasUnique(UniqueType.CannotAttack) }.toList()

        for (unit in carryingOnlyUnits)
            if (providesUnneededCarryingSlots(unit, city.civInfo))
                militaryUnits = militaryUnits.filterNot { it == unit }

        // Only now do we filter out the constructable units because that's a heavier check
        militaryUnits = militaryUnits.filter { it.isBuildable(city.cityConstructions) }.toList().asSequence() // gather once because we have a .any afterwards

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
            if (availableTypes.none()) return null
            val bestUnitsForType = availableTypes.map { type -> militaryUnits
                    .filter { unit -> unit.unitType == type }
                    .maxByOrNull { unit -> unit.cost }!! }
            // Check the maximum force evaluation for the shortlist so we can prune useless ones (ie scouts)
            val bestForce = bestUnitsForType.maxOf { it.getForceEvaluation() }
            chosenUnit = bestUnitsForType.filter { it.uniqueTo != null || it.getForceEvaluation() > bestForce / 3 }.toList().random()
        }
        return chosenUnit.name
    }

    /** Determines whether [civInfo] should be allocating military to fending off barbarians */
    fun afraidOfBarbarians(civInfo: CivilizationInfo): Boolean {
        if (civInfo.isCityState() || civInfo.isBarbarian())
            return false

        // If there are no barbarians we are not afraid
        if (civInfo.gameInfo.gameParameters.noBarbarians)
            return false

        // Very late in the game we are not afraid
        if (civInfo.gameInfo.turns > 200 * civInfo.gameInfo.gameParameters.gameSpeed.modifier)
            return false

        var multiplier = if (civInfo.gameInfo.gameParameters.ragingBarbarians) 1.3f
        else 1f // We're slightly more afraid of raging barbs

        // Past the early game we are less afraid
        if (civInfo.gameInfo.turns > 120 * civInfo.gameInfo.gameParameters.gameSpeed.modifier * multiplier)
            multiplier /= 2

        // If we have a lot of, or no cities we are not afraid
        if (civInfo.cities.isEmpty() || civInfo.cities.size >= 4 * multiplier)
            return false

        // If we have vision of our entire starting continent (ish) we are not afraid
        civInfo.gameInfo.tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)
        val startingContinent = civInfo.getCapital().getCenterTile().getContinent()
        val startingContinentSize = civInfo.gameInfo.tileMap.continentSizes[startingContinent]
        if (startingContinentSize != null && startingContinentSize < civInfo.viewableTiles.size * multiplier)
            return false

        // Otherwise we're afraid
        return true
    }

    /** Checks both feasibility of Buildings with a CreatesOneImprovement unique
     *  and resource scarcity making a construction undesirable.
      */
    fun allowAutomatedConstruction(
        civInfo: CivilizationInfo,
        cityInfo: CityInfo,
        construction: INonPerpetualConstruction
    ): Boolean {
        return allowCreateImprovementBuildings(civInfo, cityInfo, construction)
                && allowSpendingResource(civInfo, construction)
    }

    /** Checks both feasibility of Buildings with a [UniqueType.CreatesOneImprovement] unique (appropriate tile available).
     *  Constructions without pass uncontested. */
    private fun allowCreateImprovementBuildings(
        civInfo: CivilizationInfo,
        cityInfo: CityInfo,
        construction: INonPerpetualConstruction
    ): Boolean {
        if (construction !is Building) return true
        if (!construction.hasCreateOneImprovementUnique()) return true  // redundant but faster???
        val improvement = construction.getImprovementToCreate(cityInfo.getRuleset()) ?: return true
        return cityInfo.getTiles().any {
            it.canBuildImprovement(improvement, civInfo)
        }
    }

    /** Determines whether the AI should be willing to spend strategic resources to build
     *  [construction] for [civInfo], assumes that we are actually able to do so. */
    fun allowSpendingResource(civInfo: CivilizationInfo, construction: INonPerpetualConstruction): Boolean {
        // City states do whatever they want
        if (civInfo.isCityState())
            return true

        // Spaceships are always allowed
        if (construction.name in civInfo.gameInfo.spaceResources)
            return true

        val requiredResources = construction.getResourceRequirements()
        // Does it even require any resources?
        if (requiredResources.isEmpty())
            return true

        val civResources = civInfo.getCivResourcesByName()

        // Rule of thumb: reserve 2-3 for spaceship, then reserve half each for buildings and units
        // Assume that no buildings provide any resources
        for ((resource, amount) in requiredResources) {

            // Also count things under construction
            var futureForUnits = 0
            var futureForBuildings = 0

            for (city in civInfo.cities) {
                val otherConstruction = city.cityConstructions.getCurrentConstruction()
                if (otherConstruction is Building)
                    futureForBuildings += otherConstruction.getResourceRequirements()[resource] ?: 0
                else
                    futureForUnits += otherConstruction.getResourceRequirements()[resource] ?: 0
            }

            // Make sure we have some for space
            if (resource in civInfo.gameInfo.spaceResources && civResources[resource]!! - amount - futureForBuildings - futureForUnits
                < getReservedSpaceResourceAmount(civInfo)) {
                return false
            }

            // Assume buildings remain useful
            val neededForBuilding = civInfo.lastEraResourceUsedForBuilding[resource] != null
            // Don't care about old units
            val neededForUnits = civInfo.lastEraResourceUsedForUnit[resource] != null
                    && civInfo.lastEraResourceUsedForUnit[resource]!! >= civInfo.getEraNumber()

            // No need to save for both
            if (!neededForBuilding || !neededForUnits) {
                continue
            }

            val usedForUnits = civInfo.detailedCivResources.filter { it.resource.name == resource && it.origin == "Units" }.sumOf { -it.amount }
            val usedForBuildings = civInfo.detailedCivResources.filter { it.resource.name == resource && it.origin == "Buildings" }.sumOf { -it.amount }

            if (construction is Building) {
                // Will more than half the total resources be used for buildings after this construction?
                if (civResources[resource]!! + usedForUnits < usedForBuildings + amount + futureForBuildings) {
                    return false
                }
            } else {
                // Will more than half the total resources be used for units after this construction?
                if (civResources[resource]!! + usedForBuildings < usedForUnits + amount + futureForUnits) {
                    return false
                }
            }
        }
        return true
    }

    fun getReservedSpaceResourceAmount(civInfo: CivilizationInfo): Int {
        return if (civInfo.wantsToFocusOn(Victory.Focus.Science)) 3 else 2
    }

    fun threatAssessment(assessor: CivilizationInfo, assessed: CivilizationInfo): ThreatLevel {
        val powerLevelComparison =
            assessed.getStatForRanking(RankingType.Force) / assessor.getStatForRanking(RankingType.Force).toFloat()
        return when {
            powerLevelComparison > 2 -> ThreatLevel.VeryHigh
            powerLevelComparison > 1.5f -> ThreatLevel.High
            powerLevelComparison < (1 / 1.5f) -> ThreatLevel.Low
            powerLevelComparison < 0.5f -> ThreatLevel.VeryLow
            else -> ThreatLevel.Medium
        }
    }

    /** Support [UniqueType.CreatesOneImprovement] unique - find best tile for placement automation */
    fun getTileForConstructionImprovement(cityInfo: CityInfo,  improvement: TileImprovement): TileInfo? {
        return cityInfo.getTiles().filter {
            it.canBuildImprovement(improvement, cityInfo.civInfo)
        }.maxByOrNull {
            rankTileForCityWork(it, cityInfo)
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
            val resource = tile.tileResource
            if (resource.resourceType != ResourceType.Bonus) rank += 1f // for usage
            if (tile.improvement == null) rank += 1f // improvement potential - resources give lots when improved!
        }
        return rank
    }

    // Ranks a tile for the expansion algorithm of cities
    internal fun rankTileForExpansion(tile: TileInfo, cityInfo: CityInfo,
                                      localUniqueCache: LocalUniqueCache): Int {
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
            if (tile.tileResource.resourceType != ResourceType.Bonus) score -= 105
            else if (distance <= 3) score -= 104
        } else {
            // Water tiles without resources aren't great
            if (tile.isWater) score += 25
            // Can't work it anyways
            if (distance > 3) score += 100
        }

        // Improvements are good: less points
        if (tile.improvement != null &&
            tile.getImprovementStats(tile.getTileImprovement()!!, cityInfo.civInfo, cityInfo).values.sum() > 0f
        ) score -= 5

        if (tile.naturalWonder != null) score -= 105

        // Straight up take the sum of all yields
        score -= tile.getTileStats(cityInfo, cityInfo.civInfo, localUniqueCache).values.sum().toInt()

        // Check if we get access to better tiles from this tile
        var adjacentNaturalWonder = false

        for (adjacentTile in tile.neighbors.filter { it.getOwner() == null }) {
            val adjacentDistance = cityInfo.getCenterTile().aerialDistanceTo(adjacentTile)
            if (adjacentTile.hasViewableResource(cityInfo.civInfo) &&
                (adjacentDistance < 3 ||
                    adjacentTile.tileResource.resourceType != ResourceType.Bonus
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
