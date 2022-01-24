package com.unciv.logic.city

import com.unciv.UncivGame
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.RoadStatus
import com.unciv.models.Counter
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMapTyped
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.StatMap
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.toPercent
import kotlin.math.min


/** Holds and calculates [Stats] for a city.
 * 
 * No field needs to be saved, all are calculated on the fly,
 * so its field in [CityInfo] is @Transient and no such annotation is needed here.
 */
class CityStats(val cityInfo: CityInfo) {
    //region Fields, Transient 

    var baseStatList = LinkedHashMap<String, Stats>()

    var statPercentBonusList = LinkedHashMap<String, Stats>()

    // Computed from baseStatList and statPercentBonusList - this is so the players can see a breakdown
    var finalStatList = LinkedHashMap<String, Stats>()

    var happinessList = LinkedHashMap<String, Float>()

    var statsFromTiles = Stats()

    var foodEaten = 0f

    var currentCityStats: Stats = Stats()  // This is so we won't have to calculate this multiple times - takes a lot of time, especially on phones

    //endregion
    //region Pure Functions


    private fun getStatsFromTradeRoute(): Stats {
        val stats = Stats()
        if (!cityInfo.isCapital() && cityInfo.isConnectedToCapital()) {
            val civInfo = cityInfo.civInfo
            stats.gold = civInfo.getCapital().population.population * 0.15f + cityInfo.population.population * 1.1f - 1 // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
            for (unique in cityInfo.getMatchingUniques("[] from each Trade Route"))
                stats.add(unique.stats)
            if (civInfo.hasUnique("Gold from all trade routes +25%")) stats.gold *= 1.25f // Machu Picchu speciality
        }
        return stats
    }

    private fun getStatsFromProduction(production: Float): Stats {
        val stats = Stats()

        when (cityInfo.cityConstructions.currentConstructionFromQueue) {
            "Gold" -> stats.gold += production / 4
            "Science" -> stats.science += production * getScienceConversionRate()
        }
        return stats
    }

    fun getScienceConversionRate(): Float {
        var conversionRate = 1 / 4f
        if (cityInfo.civInfo.hasUnique("Production to science conversion in cities increased by 33%"))
            conversionRate *= 1.33f
        return conversionRate
    }

    private fun getStatPercentBonusesFromRailroad(): Stats {
        val stats = Stats()
        val railroadImprovement = RoadStatus.Railroad.improvement(cityInfo.getRuleset())
            ?: return stats // for mods
        val techEnablingRailroad = railroadImprovement.techRequired
        // If we conquered enemy cities connected by railroad, but we don't yet have that tech,
        // we shouldn't get bonuses, it's as if the tracks are laid out but we can't operate them.
        if ( (techEnablingRailroad == null || cityInfo.civInfo.tech.isResearched(techEnablingRailroad))
                && (cityInfo.isCapital() || isConnectedToCapital(RoadStatus.Railroad)))
            stats.production += 25f
        return stats
    }


    private fun getStatsFromCityStates(): Stats {
        val stats = Stats()

        for (otherCiv in cityInfo.civInfo.getKnownCivs()) {
            val relationshipLevel = otherCiv.getDiplomacyManager(cityInfo.civInfo).relationshipLevel()
            if (otherCiv.isCityState() && relationshipLevel >= RelationshipLevel.Friend) {
                val eraInfo = cityInfo.civInfo.getEra()

                if (eraInfo.undefinedCityStateBonuses()) {
                    // Deprecated, assume Civ V values for compatibility
                    if (otherCiv.cityStateType == CityStateType.Maritime && relationshipLevel == RelationshipLevel.Ally)
                        stats.food += 1
                    if (otherCiv.cityStateType == CityStateType.Maritime && cityInfo.isCapital())
                        stats.food += 2
                } else {
                    for (bonus in eraInfo.getCityStateBonuses(otherCiv.cityStateType, relationshipLevel)) {
                        if (bonus.isOfType(UniqueType.CityStateStatsPerCity)
                            && cityInfo.matchesFilter(bonus.params[1])
                            && bonus.conditionalsApply(otherCiv, cityInfo)
                        ) stats.add(bonus.stats)
                    }
                }
            }
        }
        
        for (unique in cityInfo.civInfo.getMatchingUniques(UniqueType.BonusStatsFromCityStates)) {
            stats[Stat.valueOf(unique.params[1])] *= unique.params[0].toPercent()
        }

        return stats
    }


    private fun getStatPercentBonusesFromPuppetCity(): Stats {
        val stats = Stats()
        if (cityInfo.isPuppet) {
            stats.science -= 25f
            stats.culture -= 25f
        }
        return stats
    }

    private fun getGrowthBonusFromPoliciesAndWonders(): Float {
        var bonus = 0f
        // "[amount]% growth [cityFilter]"
        for (unique in cityInfo.getMatchingUniques(UniqueType.GrowthPercentBonus)) {
            if (!unique.conditionalsApply(cityInfo.civInfo, cityInfo)) continue
            if (cityInfo.matchesFilter(unique.params[1]))
                bonus += unique.params[0].toFloat()
        }
        return bonus / 100
    }

    fun hasExtraAnnexUnhappiness(): Boolean {
        if (cityInfo.civInfo.civName == cityInfo.foundingCiv || cityInfo.isPuppet) return false
        return !cityInfo.containsBuildingUnique(UniqueType.RemoveAnnexUnhappiness)
    }

    fun getStatsOfSpecialist(specialistName: String): Stats {
        val specialist = cityInfo.getRuleset().specialists[specialistName]
            ?: return Stats()
        val stats = specialist.cloneStats()
        for (unique in cityInfo.getMatchingUniques(UniqueType.StatsFromSpecialist))
            if (cityInfo.matchesFilter(unique.params[1]))
                stats.add(unique.stats)
        for (unique in cityInfo.civInfo.getMatchingUniques(UniqueType.StatsFromObject))
            if (unique.params[1] == specialistName)
                stats.add(unique.stats)
        return stats
    }

    private fun getStatsFromSpecialists(specialists: Counter<String>): Stats {
        val stats = Stats()
        for (entry in specialists.filter { it.value > 0 })
            stats.add(getStatsOfSpecialist(entry.key) * entry.value)
        return stats
    }


    private fun getStatsFromUniquesBySource():StatMap {
        val sourceToStats = StatMap()
        fun addUniqueStats(unique:Unique) =
            sourceToStats.add(unique.sourceObjectType?.name ?: "", unique.stats)

        for (unique in cityInfo.getMatchingUniques(UniqueType.Stats))
            addUniqueStats(unique)

        for (unique in cityInfo.getMatchingUniques(UniqueType.StatsPerCity))
            if (cityInfo.matchesFilter(unique.params[1]))
                addUniqueStats(unique)

        // "[stats] per [amount] population [cityFilter]"
        for (unique in cityInfo.getMatchingUniques(UniqueType.StatsPerPopulation))
            if (cityInfo.matchesFilter(unique.params[2])) {
                val amountOfEffects = (cityInfo.population.population / unique.params[1].toInt()).toFloat()
                sourceToStats.add(unique.sourceObjectType?.name ?: "", unique.stats.times(amountOfEffects))
            }

        for (unique in cityInfo.getMatchingUniques(UniqueType.StatsFromXPopulation))
            if (cityInfo.population.population >= unique.params[1].toInt())
                addUniqueStats(unique)

        for (unique in cityInfo.getMatchingUniques(UniqueType.StatsFromCitiesOnSpecificTiles))
            if (cityInfo.getCenterTile().matchesTerrainFilter(unique.params[1]))
                addUniqueStats(unique)

        // Deprecated since 3.18.14
            for (unique in cityInfo.getMatchingUniques(UniqueType.StatsFromCitiesBefore))
                if (!cityInfo.civInfo.hasTechOrPolicy(unique.params[1]))
                    addUniqueStats(unique)
        //

        renameStatmapKeys(sourceToStats)

        return sourceToStats
    }

    private fun renameStatmapKeys(statMap: StatMap){
        fun rename(source: String, displayedSource: String) {
            if (!statMap.containsKey(source)) return
            statMap.add(displayedSource, statMap[source]!!)
            statMap.remove(source)
        }
        rename("Wonder", "Wonders")
        rename("Building", "Buildings")
        rename("Policy", "Policies")
    }


    private fun getStatPercentBonusesFromGoldenAge(isGoldenAge: Boolean): Stats {
        val stats = Stats()
        if (isGoldenAge) {
            stats.production += 20f
            stats.culture += 20f
        }
        return stats
    }


    private fun getStatsPercentBonusesFromUniquesBySource(currentConstruction: IConstruction):StatMap {
        val sourceToStats = StatMap()
        fun addUniqueStats(unique: Unique, stat:Stat, amount:Float) =
            sourceToStats.add(unique.sourceObjectType?.name ?: "",
                Stats().add(stat, amount))

        for (unique in cityInfo.getMatchingUniques(UniqueType.StatPercentBonus)) {
            addUniqueStats(unique, Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
        }


        for (unique in cityInfo.getMatchingUniques(UniqueType.StatPercentBonusCities)) {
            if (cityInfo.matchesFilter(unique.params[2]))
                addUniqueStats(unique, Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
        }


        val uniquesToCheck =
            if (currentConstruction is Building && currentConstruction.isAnyWonder()) {
                cityInfo.getMatchingUniques(UniqueType.PercentProductionWonders)
            } else if (currentConstruction is Building && !currentConstruction.isAnyWonder()) {
                cityInfo.getMatchingUniques(UniqueType.PercentProductionBuildings)
            } else if (currentConstruction is BaseUnit) {
                cityInfo.getMatchingUniques(UniqueType.PercentProductionUnits)
            } else { // Science/Gold production
                sequenceOf()
            }

        for (unique in uniquesToCheck) {
            if (constructionMatchesFilter(currentConstruction, unique.params[1])
                && cityInfo.matchesFilter(unique.params[2])
            )
                addUniqueStats(unique, Stat.Production, unique.params[0].toFloat())
        }


        for (unique in cityInfo.getMatchingUniques(UniqueType.StatPercentFromReligionFollowers))
            addUniqueStats(unique, Stat.valueOf(unique.params[1]),
                min(
                    unique.params[0].toFloat() * cityInfo.religion.getFollowersOfMajorityReligion(),
                    unique.params[2].toFloat()
                ))

        if (currentConstruction is Building
            && cityInfo.civInfo.cities.isNotEmpty()
            && cityInfo.civInfo.getCapital().cityConstructions.builtBuildings.contains(currentConstruction.name))
            for(unique in cityInfo.getMatchingUniques("+25% Production towards any buildings that already exist in the Capital"))
            addUniqueStats(unique, Stat.Production, 25f)

        renameStatmapKeys(sourceToStats)

        return sourceToStats
    }

    private fun getStatPercentBonusesFromUnitSupply(): Stats {
        val stats = Stats()
        val supplyDeficit = cityInfo.civInfo.stats().getUnitSupplyDeficit()
        if (supplyDeficit > 0)
            stats.production = cityInfo.civInfo.stats().getUnitSupplyProductionPenalty()
        return stats
    }

    private fun constructionMatchesFilter(construction: IConstruction, filter: String): Boolean {
        if (construction is Building) return construction.matchesFilter(filter)
        if (construction is BaseUnit) return construction.matchesFilter(filter)
        return false
    }

    fun isConnectedToCapital(roadType: RoadStatus): Boolean {
        if (cityInfo.civInfo.cities.count() < 2) return false// first city!

        // Railroad, or harbor from railroad
        return if (roadType == RoadStatus.Railroad)
                cityInfo.isConnectedToCapital {
                    roadTypes ->
                    roadTypes.any { it.contains(RoadStatus.Railroad.name) }
                }
            else cityInfo.isConnectedToCapital()
    }

    private fun getBuildingMaintenanceCosts(citySpecificUniques: Sequence<Unique>): Float {
        // Same here - will have a different UI display.
        var buildingsMaintenance = cityInfo.cityConstructions.getMaintenanceCosts().toFloat() // this is AFTER the bonus calculation!
        if (!cityInfo.civInfo.isPlayerCivilization()) {
            buildingsMaintenance *= cityInfo.civInfo.gameInfo.getDifficulty().aiBuildingMaintenanceModifier
        }

        // e.g. "-[50]% maintenance costs for buildings [in this city]"
        // Deprecated since 3.18.17
            for (unique in cityInfo.getMatchingUniques(UniqueType.DecrasedBuildingMaintenanceDeprecated, localUniques=citySpecificUniques)) {
                buildingsMaintenance *= (1f - unique.params[0].toFloat() / 100)
            }
        //
        for (unique in cityInfo.getMatchingUniques(UniqueType.BuildingMaintenance, localUniques = citySpecificUniques)) {
            buildingsMaintenance *= unique.params[0].toPercent()
        }

        return buildingsMaintenance
    }

    //endregion
    //region State-Changing Methods

    fun updateTileStats() {
        val stats = Stats()
        for (cell in cityInfo.tilesInRange
            .filter {
                cityInfo.location == it.position
                        || cityInfo.isWorked(it)
                        || it.owningCity == cityInfo && (it.getTileImprovement()
                    ?.hasUnique(UniqueType.TileProvidesYieldWithoutPopulation) == true
                        || it.hasUnique(UniqueType.TileProvidesYieldWithoutPopulation))
            })
            stats.add(cell.getTileStats(cityInfo, cityInfo.civInfo))
        statsFromTiles = stats
    }


    // needs to be a separate function because we need to know the global happiness state
    // in order to determine how much food is produced in a city!
    fun updateCityHappiness(statsFromBuildings: Stats) {
        val civInfo = cityInfo.civInfo
        val newHappinessList = LinkedHashMap<String, Float>()
        var unhappinessModifier = civInfo.getDifficulty().unhappinessModifier
        if (!civInfo.isPlayerCivilization())
            unhappinessModifier *= civInfo.gameInfo.getDifficulty().aiUnhappinessModifier

        var unhappinessFromCity = -3f // -3 happiness per city
        if (civInfo.hasUnique("Unhappiness from number of Cities doubled"))
            unhappinessFromCity *= 2f //doubled for the Indian

        newHappinessList["Cities"] = unhappinessFromCity * unhappinessModifier

        var unhappinessFromCitizens = cityInfo.population.population.toFloat()
        var unhappinessFromSpecialists = cityInfo.population.getNumberOfSpecialists().toFloat()

        // Deprecated since 3.16.11
            for (unique in civInfo.getMatchingUniques("Specialists only produce []% of normal unhappiness"))
                unhappinessFromSpecialists *= (1f - unique.params[0].toFloat() / 100f)
        //

        for (unique in cityInfo.getMatchingUniques(UniqueType.UnhappinessFromSpecialistsPercentageChange)) {
            if (cityInfo.matchesFilter(unique.params[1]))
                unhappinessFromSpecialists *= unique.params[0].toPercent()
        }

        unhappinessFromCitizens -= cityInfo.population.getNumberOfSpecialists()
            .toFloat() - unhappinessFromSpecialists

        if (cityInfo.isPuppet)
            unhappinessFromCitizens *= 1.5f
        else if (hasExtraAnnexUnhappiness())
            unhappinessFromCitizens *= 2f

        for (unique in cityInfo.getMatchingUniques(UniqueType.UnhappinessFromPopulationPercentageChange))
            if (cityInfo.matchesFilter(unique.params[1]))
                unhappinessFromCitizens *= unique.params[0].toPercent()

        newHappinessList["Population"] = -unhappinessFromCitizens * unhappinessModifier

        if (hasExtraAnnexUnhappiness()) newHappinessList["Occupied City"] = -2f //annexed city

        val happinessFromSpecialists =
            getStatsFromSpecialists(cityInfo.population.getNewSpecialists()).happiness.toInt()
                .toFloat()
        if (happinessFromSpecialists > 0) newHappinessList["Specialists"] = happinessFromSpecialists

        newHappinessList["Buildings"] = statsFromBuildings.happiness.toInt().toFloat()

        newHappinessList["Tile yields"] = statsFromTiles.happiness

        val happinessBySource = getStatsFromUniquesBySource()
        for ((source, stats) in happinessBySource)
            if (stats.happiness != 0f) {
                if (!newHappinessList.containsKey(source)) newHappinessList[source] = 0f
                newHappinessList[source] = newHappinessList[source]!! + stats.happiness
            }

        // we don't want to modify the existing happiness list because that leads
        // to concurrency problems if we iterate on it while changing
        happinessList = newHappinessList
    }

    private fun updateBaseStatList(statsFromBuildings: Stats) {
        val newBaseStatList =
            StatMap() // we don't edit the existing baseStatList directly, in order to avoid concurrency exceptions

        newBaseStatList["Population"] = Stats(
            science = cityInfo.population.population.toFloat(),
            production = cityInfo.population.getFreePopulation().toFloat()
        )
        newBaseStatList["Tile yields"] = statsFromTiles
        newBaseStatList["Specialists"] =
            getStatsFromSpecialists(cityInfo.population.getNewSpecialists())
        newBaseStatList["Trade routes"] = getStatsFromTradeRoute()
        newBaseStatList["Buildings"] = statsFromBuildings
        newBaseStatList["City-States"] = getStatsFromCityStates()

        val statMap = getStatsFromUniquesBySource()
        for ((source, stats) in statMap)
            newBaseStatList.add(source, stats)

        baseStatList = newBaseStatList
    }


    private fun updateStatPercentBonusList(currentConstruction: IConstruction, localBuildingUniques: Sequence<Unique>) {
        val newStatPercentBonusList = StatMap()

        newStatPercentBonusList["Golden Age"] = getStatPercentBonusesFromGoldenAge(cityInfo.civInfo.goldenAges.isGoldenAge())
                .plus(cityInfo.cityConstructions.getStatPercentBonuses()) // This function is to be deprecated but it'll take a while.
        newStatPercentBonusList["Railroads"] = getStatPercentBonusesFromRailroad()  // Name chosen same as tech, for translation, but theoretically independent
        newStatPercentBonusList["Puppet City"] = getStatPercentBonusesFromPuppetCity()
        newStatPercentBonusList["Unit Supply"] = getStatPercentBonusesFromUnitSupply()

        for ((source, stats) in getStatsPercentBonusesFromUniquesBySource(currentConstruction))
            newStatPercentBonusList.add(source, stats)

        if (UncivGame.Current.superchargedForDebug) {
            val stats = Stats()
            for (stat in Stat.values()) stats[stat] = 10000f
            newStatPercentBonusList["Supercharged"] = stats
        }

        statPercentBonusList = newStatPercentBonusList
    }

    /** Does not update tile stats - instead, updating tile stats updates this */
    fun update(currentConstruction: IConstruction = cityInfo.cityConstructions.getCurrentConstruction(),
               updateTileStats:Boolean = true) {
        if (updateTileStats) updateTileStats()

        // We calculate this here for concurrency reasons
        // If something needs this, we pass this through as a parameter
        val localBuildingUniques = cityInfo.cityConstructions.builtBuildingUniqueMap.getAllUniques()
        
        // Is This line really necessary? There is only a single unique that actually uses this, 
        // and it is passed to functions at least 3 times for that
        // It's the only reason `cityInfo.getMatchingUniques` has a localUniques parameter,
        // which clutters readability, and also the only reason `CityInfo.getAllLocalUniques()`
        // exists in the first place, though that could be useful for the previous line too.
        val citySpecificUniques = cityInfo.getAllLocalUniques()

        // We need to compute Tile yields before happiness

        val statsFromBuildings = cityInfo.cityConstructions.getStats() // this is performance heavy, so calculate once
        updateBaseStatList(statsFromBuildings)
        updateCityHappiness(statsFromBuildings)
        updateStatPercentBonusList(currentConstruction, localBuildingUniques)

        updateFinalStatList(currentConstruction, citySpecificUniques) // again, we don't edit the existing currentCityStats directly, in order to avoid concurrency exceptions

        val newCurrentCityStats = Stats()
        for (stat in finalStatList.values) newCurrentCityStats.add(stat)
        currentCityStats = newCurrentCityStats

        cityInfo.civInfo.updateStatsForNextTurn()
    }

    private fun updateFinalStatList(currentConstruction: IConstruction, citySpecificUniques: Sequence<Unique>) {
        val newFinalStatList = StatMap() // again, we don't edit the existing currentCityStats directly, in order to avoid concurrency exceptions

        for (entry in baseStatList)
            newFinalStatList[entry.key] = entry.value.clone()

        val statPercentBonusesSum = Stats()
        for (bonus in statPercentBonusList.values) statPercentBonusesSum.add(bonus)

        for (entry in newFinalStatList.values)
            entry.production *= statPercentBonusesSum.production.toPercent()

        val statsFromProduction = getStatsFromProduction(newFinalStatList.values.map { it.production }.sum())
        baseStatList = LinkedHashMap(baseStatList).apply { put("Construction", statsFromProduction) } // concurrency-safe addition
        newFinalStatList["Construction"] = statsFromProduction

        for (entry in newFinalStatList.values) {
            entry.gold *= statPercentBonusesSum.gold.toPercent()
            entry.culture *= statPercentBonusesSum.culture.toPercent()
            entry.food *= statPercentBonusesSum.food.toPercent()
        }

        // AFTER we've gotten all the gold stats figured out, only THEN do we plonk that gold into Science
        if (cityInfo.getRuleset().modOptions.uniques.contains(ModOptionsConstants.convertGoldToScience)) {
            val amountConverted = (newFinalStatList.values.sumOf { it.gold.toDouble() }
                    * cityInfo.civInfo.tech.goldPercentConvertedToScience).toInt().toFloat()
            if (amountConverted > 0) // Don't want you converting negative gold to negative science yaknow
                newFinalStatList["Gold -> Science"] = Stats(science = amountConverted, gold = -amountConverted)
        }
        for (entry in newFinalStatList.values) {
            entry.science *= statPercentBonusesSum.science.toPercent()
        }

        /* Okay, food calculation is complicated.
        First we see how much food we generate. Then we apply production bonuses to it.
        Up till here, business as usual.
        Then, we deduct food eaten (from the total produced).
        Now we have the excess food, to which "growth" modifiers apply
        Some policies have bonuses for growth only, not general food production. */

        updateFoodEaten()
        newFinalStatList["Population"]!!.food -= foodEaten

        var totalFood = newFinalStatList.values.map { it.food }.sum()

        // Apply growth modifier only when positive food
        if (totalFood > 0) {
            // Since growth bonuses are special, (applied afterwards) they will be displayed separately in the user interface as well.
            // All bonuses except We Love The King do apply even when unhappy
            val foodFromGrowthBonuses = Stats(food = getGrowthBonusFromPoliciesAndWonders() * totalFood)
            newFinalStatList.add("Growth bonus", foodFromGrowthBonuses)
            val happiness = cityInfo.civInfo.getHappiness()
            if (happiness < 0) {
                // Unhappiness -75% to -100%
                val foodReducedByUnhappiness = if (happiness <= -10) Stats(food = totalFood * -1)
                    else Stats(food = (totalFood * -3) / 4)
                newFinalStatList.add("Unhappiness", foodReducedByUnhappiness)
            } else if (cityInfo.isWeLoveTheKingDay()) {
                // We Love The King Day +25%, only if not unhappy
                val weLoveTheKingFood = Stats(food = totalFood / 4)
                newFinalStatList.add("We Love The King Day", weLoveTheKingFood)
            }
            // recalculate only when all applied - growth bonuses are not multiplicative
            // bonuses can allow a city to grow even with -100% unhappiness penalty, this is intended
            totalFood = newFinalStatList.values.map { it.food }.sum()
        }

        val buildingsMaintenance = getBuildingMaintenanceCosts(citySpecificUniques) // this is AFTER the bonus calculation!
        newFinalStatList["Maintenance"] = Stats(gold = -buildingsMaintenance.toInt().toFloat())

        if (totalFood > 0 
            && currentConstruction is INonPerpetualConstruction 
            && currentConstruction.hasUnique(UniqueType.ConvertFoodToProductionWhenConstructed)
        ) {
            newFinalStatList["Excess food to production"] = Stats(production = totalFood, food = -totalFood)
        }

        if (cityInfo.isInResistance())
            newFinalStatList.clear()  // NOPE

        if (newFinalStatList.values.map { it.production }.sum() < 1)  // Minimum production for things to progress
            newFinalStatList["Production"] = Stats(production = 1f)
        finalStatList = newFinalStatList
    }

    private fun updateFoodEaten() {
        foodEaten = cityInfo.population.population.toFloat() * 2
        var foodEatenBySpecialists = 2f * cityInfo.population.getNumberOfSpecialists()

        // Deprecated since 3.16.11
            for (unique in cityInfo.civInfo.getMatchingUniques(UniqueType.FoodConsumptionBySpecialistsDeprecated))
                foodEatenBySpecialists *= 1f - unique.params[0].toFloat() / 100f
        //

        for (unique in cityInfo.getMatchingUniques(UniqueType.FoodConsumptionBySpecialists))
            if (cityInfo.matchesFilter(unique.params[1]))
                foodEatenBySpecialists *= unique.params[0].toPercent()

        foodEaten -= 2f * cityInfo.population.getNumberOfSpecialists() - foodEatenBySpecialists
    }

    //endregion
}
