package com.unciv.logic.automation

import com.unciv.logic.city.City
import com.unciv.logic.city.CityFocus
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.BFS
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.nation.PersonalityValue
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.screens.victoryscreen.RankingType

object Automation {

    fun rankTileForCityWork(tile: Tile, city: City, localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)): Float {
        val stats = tile.stats.getTileStats(city, city.civ, localUniqueCache)
        return rankStatsForCityWork(stats, city, false, localUniqueCache)
    }

    fun rankSpecialist(specialist: String, city: City, localUniqueCache: LocalUniqueCache): Float {
        val stats = city.cityStats.getStatsOfSpecialist(specialist, localUniqueCache)
        var rank = rankStatsForCityWork(stats, city, true, localUniqueCache)
        // derive GPP score
        var gpp = 0f
        if (city.getRuleset().specialists.containsKey(specialist)) { // To solve problems in total remake mods
            val specialistInfo = city.getRuleset().specialists[specialist]!!
            gpp = specialistInfo.greatPersonPoints.sumValues().toFloat()
        }
        gpp = gpp * (100 + city.currentGPPBonus) / 100
        rank += gpp * 3 // GPP weight
        return rank
    }


    fun rankStatsForCityWork(stats: Stats, city: City, areWeRankingSpecialist: Boolean, localUniqueCache: LocalUniqueCache): Float {
        val cityAIFocus = city.getCityFocus()
        val yieldStats = stats.clone()
        val civPersonality = city.civ.getPersonality()
        val cityStatsObj = city.cityStats

        if (areWeRankingSpecialist) {
            // If you have the Food Bonus, count as 1 extra food production (base is 2food)
            for (unique in localUniqueCache.forCityGetMatchingUniques(city, UniqueType.FoodConsumptionBySpecialists))
                if (city.matchesFilter(unique.params[1]))
                    yieldStats.food -= (unique.params[0].toFloat() / 100f) * 2f // base 2 food per Pop
            // Specialist Happiness Percentage Change 0f-1f
            for (unique in localUniqueCache.forCityGetMatchingUniques(city, UniqueType.UnhappinessFromPopulationTypePercentageChange))
                if (city.matchesFilter(unique.params[2]) && unique.params[1] == "Specialists")
                    yieldStats.happiness -= (unique.params[0].toFloat() / 100f)  // relative val is negative, make positive
        }

        val surplusFood = city.cityStats.currentCityStats[Stat.Food]
        // If current Production converts Food into Production, then calculate increased Production Yield
        if (cityStatsObj.canConvertFoodToProduction(surplusFood, city.cityConstructions.getCurrentConstruction())) {
            // calculate delta increase of food->prod. This isn't linear
            yieldStats.production += cityStatsObj.getProductionFromExcessiveFood(surplusFood+yieldStats.food) - cityStatsObj.getProductionFromExcessiveFood(surplusFood)
            yieldStats.food = 0f  // all food goes to 0
        }
        // Apply base weights
        yieldStats.applyRankingWeights()

        if (surplusFood > 0 && city.avoidGrowth) {
            yieldStats.food = 0f // don't need more food!
        } else if (cityAIFocus in CityFocus.zeroFoodFocuses()) {
            // Focus on non-food/growth
            if (surplusFood < 0)
                yieldStats.food *= 8 // Starving, need Food, get to 0
            else
                yieldStats.food /= 2
        } else if (!city.avoidGrowth) {
            // NoFocus or Food/Growth Focus. Target +2 Food Surplus
            if (surplusFood < 2)
                yieldStats.food *= 8
            else if (city.population.population < 5)
                yieldStats.food *= 3
        }

        if (city.population.population < 5) {
            // "small city" - we care more about food and less about global problems like gold science and culture
            // Food already handled above. Science/Culture have low weights in Stats already
            yieldStats.gold /= 2 // it's barely worth anything at this point
        } else {
            if (city.civ.gold < 0 && city.civ.stats.statsForNextTurn.gold <= 0)
                yieldStats.gold *= 2 // We have a global problem

            if (city.tiles.size < 12)
                yieldStats.culture *= 2

            if (city.civ.getHappiness() < 0)
                yieldStats.happiness *= 2
        }

        for (stat in Stat.values()) {
            if (city.civ.wantsToFocusOn(stat))
                yieldStats[stat] *= 2f

            yieldStats[stat] *= civPersonality.scaledFocus(PersonalityValue[stat])
        }

        // Apply City focus
        cityAIFocus.applyWeightTo(yieldStats)

        return yieldStats.values.sum()
    }

    fun tryTrainMilitaryUnit(city: City) {
        if (city.isPuppet) return
        if ((city.cityConstructions.getCurrentConstruction() as? BaseUnit)?.isMilitary() == true)
            return // already training a military unit
        val chosenUnitName = chooseMilitaryUnit(city, city.civ.gameInfo.ruleset.units.values.asSequence())
        if (chosenUnitName != null)
            city.cityConstructions.currentConstructionFromQueue = chosenUnitName
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun providesUnneededCarryingSlots(baseUnit: BaseUnit, civInfo: Civilization): Boolean {
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
            civInfo.units.getCivUnits().count { it.matchesFilter(carryFilter) }
        val totalCarryingSlots = civInfo.units.getCivUnits().sumOf { getCarryAmount(it) }
        return totalCarriableUnits < totalCarryingSlots
    }

    fun chooseMilitaryUnit(city: City, availableUnits: Sequence<BaseUnit>): String? {
        val currentChoice = city.cityConstructions.getCurrentConstruction()
        if (currentChoice is BaseUnit && !currentChoice.isCivilian()) return city.cityConstructions.currentConstructionFromQueue

        // if not coastal, removeShips == true so don't even consider ships
        var removeShips = true
        var isMissingNavalUnitsForCityDefence = false

        fun isNavalMeleeUnit(unit: BaseUnit) = unit.isMelee() && unit.type.isWaterUnit()
        if (city.isCoastal()) {
            // in the future this could be simplified by assigning every distinct non-lake body of
            // water their own ID like a continent ID
            val findWaterConnectedCitiesAndEnemies =
                    BFS(city.getCenterTile()) { it.isWater || it.isCityCenter() }
            findWaterConnectedCitiesAndEnemies.stepToEnd()

            val numberOfOurConnectedCities = findWaterConnectedCitiesAndEnemies.getReachedTiles()
                .count { it.isCityCenter() && it.getOwner() == city.civ }
            val numberOfOurNavalMeleeUnits = findWaterConnectedCitiesAndEnemies.getReachedTiles().asSequence()
                .flatMap { it.getUnits() }
                .count { isNavalMeleeUnit(it.baseUnit) }
            isMissingNavalUnitsForCityDefence = numberOfOurConnectedCities > numberOfOurNavalMeleeUnits

            removeShips = findWaterConnectedCitiesAndEnemies.getReachedTiles().none {
                        (it.isCityCenter() && it.getOwner() != city.civ)
                                || (it.militaryUnit != null && it.militaryUnit!!.civ != city.civ)
                    } // there is absolutely no reason for you to make water units on this body of water.
        }

        val militaryUnits = availableUnits
            .filter { it.isMilitary() }
            .filterNot { removeShips && it.isWaterUnit() }
            .filter { allowSpendingResource(city.civ, it) }
            .filterNot {
                // filter out carrier-type units that can't attack if we don't need them
                (it.hasUnique(UniqueType.CarryAirUnits) && it.hasUnique(UniqueType.CannotAttack))
                        && providesUnneededCarryingSlots(it, city.civ)
            }
            // Only now do we filter out the constructable units because that's a heavier check
            .filter { it.isBuildable(city.cityConstructions) }
            .toList()

        val chosenUnit: BaseUnit
        if (!city.civ.isAtWar()
                && city.civ.cities.any { it.getCenterTile().militaryUnit == null }
                && militaryUnits.any { it.isRanged() } // this is for city defence so get a ranged unit if we can
        ) {
            chosenUnit = militaryUnits
                .filter { it.isRanged() }
                .maxByOrNull { it.cost }!!
        }
        else if (isMissingNavalUnitsForCityDefence && militaryUnits.any { isNavalMeleeUnit(it) }) {
            chosenUnit = militaryUnits
                .filter { isNavalMeleeUnit(it) }
                .maxBy { it.cost }
        }
        else { // randomize type of unit and take the most expensive of its kind
            val bestUnitsForType = hashMapOf<String, BaseUnit>()
            for (unit in militaryUnits) {
                if (bestUnitsForType[unit.unitType] == null || bestUnitsForType[unit.unitType]!!.cost < unit.cost) {
                    bestUnitsForType[unit.unitType] = unit
                }
            }
            // Check the maximum force evaluation for the shortlist so we can prune useless ones (ie scouts)
            val bestForce = bestUnitsForType.maxOfOrNull { it.value.getForceEvaluation() } ?: return null
            chosenUnit = bestUnitsForType.filterValues { it.uniqueTo != null || it.getForceEvaluation() > bestForce / 3 }.values.random()
        }
        return chosenUnit.name
    }

    /** Determines whether [civInfo] should be allocating military to fending off barbarians */
    fun afraidOfBarbarians(civInfo: Civilization): Boolean {
        if (civInfo.isCityState() || civInfo.isBarbarian())
            return false

        if (civInfo.gameInfo.gameParameters.noBarbarians)
            return false // If there are no barbarians we are not afraid

        val speed = civInfo.gameInfo.speed
        if (civInfo.gameInfo.turns > 200 * speed.barbarianModifier)
            return false // Very late in the game we are not afraid

        var multiplier = if (civInfo.gameInfo.gameParameters.ragingBarbarians) 1.3f
        else 1f // We're slightly more afraid of raging barbs

        // Past the early game we are less afraid
        if (civInfo.gameInfo.turns > 120 * speed.barbarianModifier * multiplier)
            multiplier /= 2

        // If we have a lot of, or no cities we are not afraid
        if (civInfo.cities.isEmpty() || civInfo.cities.size >= 4 * multiplier)
            return false

        // If we have vision of our entire starting continent (ish) we are not afraid
        civInfo.gameInfo.tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)
        val startingContinent = civInfo.getCapital(true)!!.getCenterTile().getContinent()
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
        civInfo: Civilization,
        city: City,
        construction: INonPerpetualConstruction
    ): Boolean {
        return allowCreateImprovementBuildings(civInfo, city, construction)
            && allowSpendingResource(civInfo, construction, city)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    /** Checks both feasibility of Buildings with a [UniqueType.CreatesOneImprovement] unique (appropriate tile available).
     *  Constructions without pass uncontested. */
    fun allowCreateImprovementBuildings(
        civInfo: Civilization,
        city: City,
        construction: INonPerpetualConstruction
    ): Boolean {
        if (construction !is Building) return true
        if (!construction.hasCreateOneImprovementUnique()) return true  // redundant but faster???
        val improvement = construction.getImprovementToCreate(city.getRuleset(), civInfo) ?: return true
        return city.getTiles().any {
            it.improvementFunctions.canBuildImprovement(improvement, civInfo)
        }
    }

    /** Determines whether the AI should be willing to spend strategic resources to build
     *  [construction] for [civInfo], assumes that we are actually able to do so. */
    fun allowSpendingResource(civInfo: Civilization, construction: INonPerpetualConstruction, cityInfo: City? = null): Boolean {
        // City states do whatever they want
        if (civInfo.isCityState())
            return true

        // Spaceships are always allowed
        if (construction.name in civInfo.gameInfo.spaceResources)
            return true

        val requiredResources = if (construction is BaseUnit)
            construction.getResourceRequirementsPerTurn(StateForConditionals(civInfo))
        else construction.getResourceRequirementsPerTurn(StateForConditionals(civInfo, cityInfo))
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
                    futureForBuildings += otherConstruction.getResourceRequirementsPerTurn(
                        StateForConditionals(civInfo, city))[resource]
                else
                    futureForUnits += otherConstruction.getResourceRequirementsPerTurn(
                        StateForConditionals(civInfo))[resource]
            }

            // Make sure we have some for space
            if (resource in civInfo.gameInfo.spaceResources && civResources[resource]!! - amount - futureForBuildings - futureForUnits
                < getReservedSpaceResourceAmount(civInfo)) {
                return false
            }

            // Assume buildings remain useful
            val neededForBuilding = civInfo.cache.lastEraResourceUsedForBuilding[resource] != null
            // Don't care about old units
            val neededForUnits = civInfo.cache.lastEraResourceUsedForUnit[resource] != null
                    && civInfo.cache.lastEraResourceUsedForUnit[resource]!! >= civInfo.getEraNumber()

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

    fun getReservedSpaceResourceAmount(civInfo: Civilization): Int {
        return if (civInfo.wantsToFocusOn(Victory.Focus.Science)) 3 else 2
    }

    fun threatAssessment(assessor: Civilization, assessed: Civilization): ThreatLevel {
        val powerLevelComparison =
            assessed.getStatForRanking(RankingType.Force) / assessor.getStatForRanking(RankingType.Force).toFloat()
        return when {
            powerLevelComparison > 2 -> ThreatLevel.VeryHigh
            powerLevelComparison > 1.5f -> ThreatLevel.High
            powerLevelComparison < 0.5f -> ThreatLevel.VeryLow
            powerLevelComparison < (1 / 1.5f) -> ThreatLevel.Low
            else -> ThreatLevel.Medium
        }
    }

    /** Support [UniqueType.CreatesOneImprovement] unique - find best tile for placement automation */
    fun getTileForConstructionImprovement(city: City, improvement: TileImprovement): Tile? {
        val localUniqueCache = LocalUniqueCache()
        return city.getTiles().filter {
            it.improvementFunctions.canBuildImprovement(improvement, city.civ)
        }.maxByOrNull {
            rankTileForCityWork(it, city, localUniqueCache)
        }
    }

    // Ranks a tile for any purpose except the expansion algorithm of cities
    internal fun rankTile(tile: Tile?, civInfo: Civilization,
                          localUniqueCache: LocalUniqueCache): Float {
        if (tile == null) return 0f
        val tileOwner = tile.getOwner()
        if (tileOwner != null && tileOwner != civInfo) return 0f // Already belongs to another civilization, useless to us
        val stats = tile.stats.getTileStats(null, civInfo, localUniqueCache)
        var rank = rankStatsValue(stats, civInfo)
        if (tile.improvement == null) rank += 0.5f // improvement potential!
        if (tile.isPillaged()) rank += 0.6f
        if (tile.hasViewableResource(civInfo)) {
            val resource = tile.tileResource
            if (resource.resourceType != ResourceType.Bonus) rank += 1f // for usage
            if (tile.improvement == null) rank += 1f // improvement potential - resources give lots when improved!
            if (tile.isPillaged()) rank += 1.1f // even better, repair is faster
        }
        return rank
    }

    // Ranks a tile for the expansion algorithm of cities
    internal fun rankTileForExpansion(tile: Tile, city: City,
                                      localUniqueCache: LocalUniqueCache): Int {
        // https://github.com/Gedemon/Civ5-DLL/blob/aa29e80751f541ae04858b6d2a2c7dcca454201e/CvGameCoreDLL_Expansion1/CvCity.cpp#L10301
        // Apparently this is not the full calculation. The exact tiles are also
        // dependent on which tiles are between the chosen tile and the city center
        // Exact details are not implemented, but can be found in CvAStar.cpp:2119,
        // function `InfluenceCost()`.
        // Implementing these will require an additional variable for each terrainType
        val distance = tile.aerialDistanceTo(city.getCenterTile())

        // Higher score means tile is less likely to be picked
        var score = distance * 100

        // Resources are good: less points
        if (tile.hasViewableResource(city.civ)) {
            if (tile.tileResource.resourceType != ResourceType.Bonus) score -= 105
            else if (distance <= 3) score -= 104
        } else {
            // Water tiles without resources aren't great
            if (tile.isWater) score += 25
            // Can't work it anyways
            if (distance > 3) score += 100
        }

        if (tile.naturalWonder != null) score -= 105

        // Straight up take the sum of all yields
        score -= tile.stats.getTileStats(city, city.civ, localUniqueCache).values.sum().toInt()

        // Check if we get access to better tiles from this tile
        var adjacentNaturalWonder = false

        for (adjacentTile in tile.neighbors.filter { it.getOwner() == null }) {
            val adjacentDistance = city.getCenterTile().aerialDistanceTo(adjacentTile)
            if (adjacentTile.hasViewableResource(city.civ) &&
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
        if (tile.neighbors.none { it.getCity() != null && it.getCity()!!.id == city.id })
            score += 1000

        return score
    }

    fun rankStatsValue(stats: Stats, civInfo: Civilization): Float {
        var rank = 0.0f
        rank += if (stats.food <= 2)
                    (stats.food * 1.2f) //food get more value to keep city growing
                else
                    (2.4f + (stats.food - 2) / 2) // 1.2 point for each food up to 2, from there on half a point

        rank += if (civInfo.gold < 0 && civInfo.stats.statsForNextTurn.gold <= 0)
                    stats.gold
                else
                    stats.gold / 3 // 3 gold is much worse than 2 production

        rank += stats.happiness
        rank += stats.production
        rank += stats.science
        rank += stats.culture
        rank += stats.faith
        return rank
    }
}

enum class ThreatLevel {
    VeryLow,
    Low,
    Medium,
    High,
    VeryHigh
}
