package com.unciv.logic.automation.city

import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.automation.unit.WorkerAutomation
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.CityAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.BFS
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.MilestoneType
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.nation.PersonalityValue
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.victoryscreen.RankingType
import yairm210.purity.annotations.Readonly
import kotlin.math.max
import kotlin.math.sqrt

class ConstructionAutomation(val cityConstructions: CityConstructions) {

    private val city = cityConstructions.city
    private val civInfo = city.civ

    private val relativeCostEffectiveness = ArrayList<ConstructionChoice>()
    private val cityState = city.state
    private val cityStats = city.cityStats

    private val personality = civInfo.getPersonality()

    private val constructionsToAvoid = personality.getMatchingUniques(UniqueType.WillNotBuild, cityState)
        .map{ it.params[0] }
    
    @Readonly
    private fun shouldAvoidConstruction (construction: IConstruction): Boolean {
        val stateForConditionals = cityState
        for (toAvoid in constructionsToAvoid) {
            if (construction is Building && construction.matchesFilter(toAvoid, stateForConditionals))
                return true
            if (construction is BaseUnit && construction.matchesFilter(toAvoid, stateForConditionals))
                return true
        }
        return false
    }

    private val disabledAutoAssignConstructions: Set<String> =
        if (civInfo.isHuman()) GUI.getSettings().disabledAutoAssignConstructions
        else emptySet()

    private val buildableBuildings = hashMapOf<String, Boolean>()
    private val buildableUnits = hashMapOf<String, Boolean>()
    private val buildings = city.getRuleset().buildings.values.asSequence()
        .filterNot { it.name in disabledAutoAssignConstructions || shouldAvoidConstruction(it) }

    private val nonWonders = buildings.filterNot { it.isAnyWonder() }
        .filterNot { buildableBuildings[it.name] == false } // if we already know that this building can't be built here then don't even consider it

    private val units = city.getRuleset().units.values.asSequence()
        .filterNot { buildableUnits[it.name] == false || // if we already know that this unit can't be built here then don't even consider it
            it.name in disabledAutoAssignConstructions || shouldAvoidConstruction(it) }

    private val civUnits = civInfo.units.getCivUnits()
    private val militaryUnits = civUnits.count { it.baseUnit.isMilitary }
    private val workers = civUnits.count { it.cache.hasUniqueToBuildImprovements}.toFloat()
    private val cities = civInfo.cities.size
    private val allTechsAreResearched = civInfo.tech.allTechsAreResearched()

    private val isAtWar = civInfo.isAtWar()
    private val buildingsForVictory = civInfo.gameInfo.getEnabledVictories().values
            .mapNotNull { civInfo.victoryManager.getNextMilestone(it) }
            .filter { it.type == MilestoneType.BuiltBuilding || it.type == MilestoneType.BuildingBuiltGlobally }
            .map { it.params[0] }

    private val spaceshipParts = civInfo.gameInfo.spaceResources


    private val averageProduction = civInfo.cities.map { it.cityStats.currentCityStats.production }.average()
    private val cityIsOverAverageProduction = city.cityStats.currentCityStats.production >= averageProduction

    private data class ConstructionChoice(val choice: String, var choiceModifier: Float,
                                          val remainingWork: Int, val production: Int)

    private fun addChoice(choices: ArrayList<ConstructionChoice>, choice: String, choiceModifier: Float) {
        choices.add(ConstructionChoice(choice, choiceModifier,
            cityConstructions.getRemainingWork(choice), cityConstructions.productionForConstruction(choice)))
    }


    private fun <T:INonPerpetualConstruction> Sequence<T>.filterBuildable(): Sequence<T> {
        return this.filter {
            val cache = if (it is Building) buildableBuildings else buildableUnits
            if (cache[it.name] == null) {
                cache[it.name] = it.isBuildable(cityConstructions)
            }
            cache[it.name]!!
        }
    }


    fun chooseNextConstruction() {
        if (cityConstructions.getCurrentConstruction() !is PerpetualConstruction) return  // don't want to be stuck on these forever
        
        addBuildingChoices()

        if (!city.isPuppet) {
            addSpaceshipPartChoice()
            addWorkerChoice()
            addWorkBoatChoice()
            addMilitaryUnitChoice()
        }

        val chosenConstruction: String =
            if (relativeCostEffectiveness.isEmpty()) { // choose one of the special constructions instead
                // add science!
                when {
                    PerpetualConstruction.science.isBuildable(cityConstructions) && !allTechsAreResearched -> PerpetualConstruction.science.name
                    PerpetualConstruction.gold.isBuildable(cityConstructions) -> PerpetualConstruction.gold.name
                    PerpetualConstruction.culture.isBuildable(cityConstructions) && !civInfo.policies.allPoliciesAdopted(true) -> PerpetualConstruction.culture.name
                    PerpetualConstruction.faith.isBuildable(cityConstructions) -> PerpetualConstruction.faith.name
                    else -> PerpetualConstruction.idle.name
                }
            } else { relativeCostEffectiveness.maxBy { (it.choiceModifier / it.remainingWork.coerceAtLeast(1)).coerceAtLeast(0f) }.choice }
            //TODO: All bad things are build anyways at the moment, maybe let's stop doing that and chose perpetual construction instead

        // Do not notify while in resistance (you can't do anything about it) - still notify for puppets ("annex already!")
        // Also do not notify while city screen open - might be a buying spree, not helpful
        // Also do not notify when the decision hasn't changed - duh!
        val noNotification = city.isInResistance()
            || civInfo.isAI() // Optimization: addNotification filters anyway, but saves a string builder and a CityAction instantiation
            || cityConstructions.currentConstructionFromQueue == chosenConstruction
            || UncivGame.Current.screen is CityScreen
        cityConstructions.currentConstructionFromQueue = chosenConstruction
        if (noNotification) return

        civInfo.addNotification(
            "[${city.name}] has started working on [$chosenConstruction]",
            CityAction.withLocation(city),
            NotificationCategory.Production,
            NotificationIcon.Construction
        )
    }

    private fun addMilitaryUnitChoice() {
        if (!isAtWar && !cityIsOverAverageProduction) return // don't make any military units here. Infrastructure first!
        // There is a risk however, that these cities run out of things to build, and start to construct nothing
        if (civInfo.stats.getUnitSupplyDeficit() > 0) return // we don't want more units if it's already hurting our empire
        // todo: add worker disbandment and consumption of great persons if under attack & short on unit supply
        if (!isAtWar && (civInfo.stats.statsForNextTurn.gold < 0 || militaryUnits > max(7, cities * 5))) return
        if (civInfo.gold < -50) return

        val militaryUnit = Automation.chooseMilitaryUnit(city, units) ?: return
        val unitsToCitiesRatio = cities.toFloat() / (militaryUnits + 1)
        // most buildings and civ units contribute the the civ's growth, military units are anti-growth
        var modifier = 1 + sqrt(unitsToCitiesRatio) / 2
        if (isAtWar) modifier *= 2

        if (Automation.afraidOfBarbarians(civInfo)) modifier = 2f // military units are pro-growth if pressured by barbs
        if (!cityIsOverAverageProduction) modifier /= 5 // higher production cities will deal with this

        val civilianUnit = city.getCenterTile().civilianUnit
        if (civilianUnit != null && civilianUnit.hasUnique(UniqueType.FoundCity)
                && city.getCenterTile().getTilesInDistance(city.getExpandRange()).none { it.militaryUnit?.civ == civInfo })
            modifier = 5f // there's a settler just sitting here, doing nothing - BAD

        if (!civInfo.isAIOrAutoPlaying()) modifier /= 2 // Players prefer to make their own unit choices usually
        modifier *= personality.modifierFocus(PersonalityValue.Military, .3f)
        addChoice(relativeCostEffectiveness, militaryUnit, modifier)
    }

    private fun addWorkBoatChoice() {
        // Does the ruleset even have "Workboats"?
        val buildableWorkboatUnits = units
            .filter {
                it.hasUnique(UniqueType.CreateWaterImprovements)
                    && Automation.allowAutomatedConstruction(civInfo, city, it)
            }.filterBuildable()
            .toSet()
        if (buildableWorkboatUnits.isEmpty()) return

        // Is there already a Workboat nearby?
        // todo Still ignores whether that boat can reach the not-yet-found tile to improve
        val twoTurnsMovement = buildableWorkboatUnits.maxOf { it.movement } * 2
        @Readonly fun MapUnit.isOurWorkBoat() = cache.hasUniqueToCreateWaterImprovements
                && this.civ == this@ConstructionAutomation.civInfo
        val alreadyHasWorkBoat = city.getCenterTile().getTilesInDistance(twoTurnsMovement)
            .any { it.civilianUnit?.isOurWorkBoat() == true }
        if (alreadyHasWorkBoat) return

        // Define what makes a tile worth sending a Workboat to
        // todo Prepare for mods that allow improving water tiles without a resource?
        @Readonly
        fun Tile.isWorthImproving(): Boolean {
            if (getOwner() != civInfo) return false
            if (!WorkerAutomation.hasWorkableSeaResource(this, civInfo)) return false
            return WorkerAutomation.isNotBonusResourceOrWorkable(this, civInfo)
        }

        // Search for a tile justifying producing a Workboat
        // todo should workboatAutomationSearchMaxTiles depend on game state?
        @Readonly
        fun findTileWorthImproving(): Boolean {
            val searchMaxTiles = civInfo.gameInfo.ruleset.modOptions.constants.workboatAutomationSearchMaxTiles
            val bfs = BFS(city.getCenterTile()) {
                (it.isWater || it.isCityCenter())
                    && (it.getOwner() == null || it.isFriendlyTerritory(civInfo))
                    && it.isExplored(civInfo)  // Sending WB's through unexplored terrain would be cheating
            }
            do {
                val tile = bfs.nextStep() ?: break
                if (tile.isWorthImproving()) return true
            } while (bfs.size() < searchMaxTiles)
            return false
        }

        if (!findTileWorthImproving()) return

        addChoice(relativeCostEffectiveness, buildableWorkboatUnits.minBy { it.cost }.name, 6f) // Improving coastal luxuries etc. is quite important
    }

    private fun addWorkerChoice() {
        val workerEquivalents = units
            .filter {
                it.hasUnique(UniqueType.BuildImprovements)
                        && Automation.allowAutomatedConstruction(civInfo, city, it)
            }.filterBuildable()
        if (workerEquivalents.none()) return // for mods with no worker units

        // Dedicate 1 worker for the first city (CS), then 1.5 workers for the first 5 cities, from then on build one more worker for every city.
        val numberOfWorkersWeWant = if (cities <= 1) 1f else if  (cities <= 5) (cities * 1.8f) else 9f + 1.3f * (cities - 5)

        if (workers < numberOfWorkersWeWant) {
            val modifier = numberOfWorkersWeWant / (workers + 0.17f) // The worse our worker to city ratio is, the more desperate we are
            addChoice(relativeCostEffectiveness, workerEquivalents.minByOrNull { it.cost }!!.name, modifier)
        }
    }

    private fun addSpaceshipPartChoice() {
        if (!cityIsOverAverageProduction) return // don't waste time building them in low-production cities
        if (!civInfo.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts)) return
        val spaceshipPart = (nonWonders + units).filter { it.name in spaceshipParts }.filterBuildable().firstOrNull()
            ?: return
        val modifier = 20f //We're weighing Apollo program according to personality. If we decided to invest in that, we might as well commit to it.
        addChoice(relativeCostEffectiveness, spaceshipPart.name, modifier)
    }

    private fun addBuildingChoices() {
        val localUniqueCache = LocalUniqueCache()
        for (building in buildings.filterBuildable()) {
            if (building.isWonder && city.isPuppet) continue
            // We shouldn't try to build wonders in undeveloped cities and empires
            if (building.isWonder && (!cityIsOverAverageProduction || civInfo.cities.sumOf { it.population.population } < 12)) continue
            addChoice(relativeCostEffectiveness, building.name, getValueOfBuilding(building, localUniqueCache))
        }
    }

    private fun getValueOfBuilding(building: Building, localUniqueCache: LocalUniqueCache): Float {
        var value = 0f
        value += applyBuildingStats(building, localUniqueCache)
        value += getMilitaryBuildingValue(building)
        value += getVictoryBuildingValue(building)
        value += getOnetimeUniqueBonuses(building)
        return value
    }

    @Readonly
    private fun getOnetimeUniqueBonuses(building: Building): Float {
        var value = 0f
        if (building.isWonder) {
            // Buildings generally don't have these uniques, and Wonders generally only one of these, so we can save some time by not checking every building for every unique
            if (!building.isNationalWonder) value -= civInfo.gameInfo.getAliveMajorCivs().sortedByDescending { it.getStatForRanking(RankingType.Technologies) }.indexOf(civInfo)
            // Wonders are a one-time occurence: value less if someone is going to build them before us anyways
            value += when {
                building.hasUnique(UniqueType.OneTimeFreePolicy) || building.hasUnique(UniqueType.OneTimeAmountFreePolicies) -> civInfo.getPersonality().culture
                building.hasUnique(UniqueType.OneTimeFreeTech) || building.hasUnique(UniqueType.OneTimeAmountFreeTechs) -> civInfo.getPersonality().science
                building.hasUnique(UniqueType.OneTimeAmountFreeUnits) || building.hasUnique(UniqueType.OneTimeFreeUnit) -> civInfo.getPersonality().production //Pyramids, Louvre
                building.hasUnique(UniqueType.OneTimeFreeGreatPerson) -> civInfo.getPersonality().science // Will pick scientist
                building.hasUnique(UniqueType.OneTimeEnterGoldenAge) || building.hasUnique(UniqueType.GoldenAgeLength) || building.hasUnique(UniqueType.OneTimeEnterGoldenAgeTurns) -> civInfo.getPersonality().expansion // Relatively more important on many cities
                building.hasUnique(UniqueType.EnemyUnitsSpendExtraMovement) -> civInfo.getPersonality().declareWar
                building.hasUnique(UniqueType.OneTimeGainPopulation) || building.hasUnique(UniqueType.OneTimeGainPopulationRandomCity) -> civInfo.getPersonality().food
                building.hasUnique(UniqueType.StatPercentFromTradeRoutes) -> civInfo.getPersonality().gold
                building.hasUnique(UniqueType.Strength) -> civInfo.getPersonality().military
                building.hasUnique(UniqueType.StatPercentBonusCities) -> civInfo.getPersonality().culture // Sistine Chapel in base game, but players seem to "expect" culture civs to build more wonders in general
                else -> 0f
            }
        } else value += when {
            building.hasUnique(UniqueType.CreatesOneImprovement) -> 5f // District-type buildings, should be weighed by the stats (incl. adjacencies) of the improvement
            building.hasUnique(UniqueType.ProvidesResources) -> 2f // Should be weighed by how much we need the resources
            building.hasUnique(UniqueType.StatPercentFromObjectToResource) -> 1.5f // Should be weighed by the amount of active improvementFilter/buildingFilter in the city
            else -> 0f
        }
        return value
    }
    
    @Readonly
    private fun getVictoryBuildingValue(building: Building): Float {
        var value = 0f
        if (!cityIsOverAverageProduction) return value
        if (building.hasUnique(UniqueType.TriggersCulturalVictory)
            || building.hasUnique(UniqueType.TriggersVictory)) value += 20f // if we're this close to actually winning, we don't care what your preferred victory type is
        if (building.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts)) value += 10f * personality.modifierFocus(PersonalityValue.Science, .3f)
        return value
    }

    @Readonly
    private fun getMilitaryBuildingValue(building: Building): Float {
        var value = 0f
        var warModifier = if (isAtWar) 1f else .5f
        // If this city is the closest city to another civ, that makes it a likely candidate for attack
        if (civInfo.getKnownCivs()
                    .mapNotNull { NextTurnAutomation.getClosestCities(civInfo, it) }
                    .any { it.city1 == city })
            warModifier *= 2f
        value += warModifier * building.cityHealth.toFloat() / city.getMaxHealth() * personality.inverseModifierFocus(PersonalityValue.Aggressive, .3f)
        value += warModifier * building.cityStrength.toFloat() / (city.getStrength() + 3) * personality.inverseModifierFocus(PersonalityValue.Aggressive, .3f) // The + 3 here is to reduce the priority of building walls immedietly

        for (experienceUnique in building.getMatchingUniques(UniqueType.UnitStartingExperience, cityState)) {
            var modifier = experienceUnique.params[1].toFloat() / 5
            modifier *= if (cityIsOverAverageProduction) 1f else 0.2f // You shouldn't be cranking out units anytime soon
            modifier *= personality.modifierFocus(PersonalityValue.Military, 0.3f)
            modifier *= personality.modifierFocus(PersonalityValue.Aggressive, 0.2f).coerceAtLeast(1f) // Defensive civs can still want a good military
            value += modifier
        }
        if (building.hasUnique(UniqueType.EnablesNuclearWeapons) && !civInfo.hasUnique(UniqueType.EnablesNuclearWeapons))
            value += 4f * personality.modifierFocus(PersonalityValue.Military, 0.3f)
        return value
    }

    private fun applyBuildingStats(building: Building, localUniqueCache: LocalUniqueCache): Float {
        val buildingStats = getStatDifferenceFromBuilding(building.name, localUniqueCache)
        buildingStats.add(getBuildingStatsFromUniques(building, buildingStats))

        buildingStats.food *= 3

        buildingStats.production *= 2

        buildingStats.gold *= 2 // Everything's weighed by rankStatsValue, which ranks gold at 0.3, let's make that 0.6 (vs Science being 1)

        if (civInfo.getHappiness() < 10 || civInfo.getHappiness() < civInfo.cities.size)
            buildingStats.happiness *= 3

        if (city.cityStats.currentCityStats.culture < 2) {
            buildingStats.culture *= 2 // We need to start growing borders
        }

        for (stat in Stat.entries) {
            buildingStats[stat] *= personality.scaledFocus(PersonalityValue[stat])
        }

        return Automation.rankStatsValue(buildingStats.clone(), civInfo)
    }

    // NOT readonly safe, since it alters the tile ownership of real tiles
    private fun getStatDifferenceFromBuilding(building: String, localUniqueCache: LocalUniqueCache): Stats {
        val newCity = city.clone()
        newCity.setTransients(city.civ) // Will break the owned tiles. Needs to be reverted before leaving this function
        //todo: breaks city connection; trade route gold is currently not considered for markets etc.
        newCity.cityStats.update(updateCivStats = false, localUniqueCache = localUniqueCache, calculateGrowthModifiers = false) // Don't consider growth penalties for food values (we can work more mines/specialists instead of farms)
        val oldStats = newCity.cityStats.currentCityStats
        newCity.cityConstructions.builtBuildings.add(building)
        newCity.cityConstructions.setTransients()
        newCity.cityStats.update(updateCivStats = false, localUniqueCache = LocalUniqueCache(), calculateGrowthModifiers = false) // Establish new localUniqueCache (for tile yield uniques)
        city.expansion.setTransients() // Revert owned tiles to original city
        return newCity.cityStats.currentCityStats - oldStats
    }

    @Readonly
    private fun getBuildingStatsFromUniques(building: Building, buildingStats: Stats) : Stats {
        val stats = Stats()
        for (unique in building.getMatchingUniques(UniqueType.StatPercentBonusCities, cityState)) {
            val statType = Stat.valueOf(unique.params[1])
            val relativeAmount = unique.params[0].toFloat() / 100f
            val amount = civInfo.stats.statsForNextTurn[statType] * relativeAmount
            stats[statType] += amount
        }

        for (unique in building.getMatchingUniques(UniqueType.CarryOverFood, cityState)) {
            if (city.matchesFilter(unique.params[1]) && unique.params[0].toInt() != 0) {
                val foodGain = cityStats.currentCityStats.food + buildingStats.food
                val relativeAmount = unique.params[0].toFloat() / 100f
                stats[Stat.Food] += foodGain * relativeAmount // Essentialy gives us the food per turn this unique saves us
            }
        }
        return stats
    }
}
