package com.unciv.logic.civilization

import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.RoadStatus
import com.unciv.models.metadata.BASE_GAME_DURATION_TURNS
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.stats.Stat
import com.unciv.models.stats.StatMap
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.toPercent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** CivInfo class was getting too crowded */
class CivInfoStats(val civInfo: CivilizationInfo) {

    private fun getUnitMaintenance(): Int {
        val baseUnitCost = 0.5f
        var freeUnits = 3
        for (unique in civInfo.getMatchingUniques(UniqueType.FreeUnits, StateForConditionals(civInfo))) {
            freeUnits += unique.params[0].toInt()
        }

        var unitsToPayFor = civInfo.getCivUnits()
        if (civInfo.hasUnique("Units in cities cost no Maintenance"))
        // Only land military units can truly "garrison"
            unitsToPayFor = unitsToPayFor.filterNot {
                it.getTile().isCityCenter() && it.canGarrison()
            }
        // Handle unit maintenance discounts
        // Have to capture global and per-unit
        // Free Garrison already removed above from sequence
        // Initialize maintenance cost per unit, default 1 aka 100%
        // Note all discounts are in the form of -X%, such as -25 for 25% reduction
        for (unit in unitsToPayFor){
            unit.maintenance = 1f
            for (unique in unit.getMatchingUniques(UniqueType.UnitMaintenanceDiscount)){
                unit.maintenance *= unique.params[0].toPercent()
            }
        }
        // Apply global discounts
        for (unique in civInfo.getMatchingUniques(UniqueType.UnitMaintenanceDiscountGlobal, StateForConditionals(civInfo))) {
            for (unit in unitsToPayFor.filter { it.matchesFilter(unique.params[1]) }) {
                unit.maintenance *= unique.params[0].toPercent()
            }
        }
        // Sort by descending maintenance, then drop most expensive X units to make them free
        // If more free than units left, returns empty sequence
        unitsToPayFor = unitsToPayFor.sortedByDescending { it.maintenance }.drop(freeUnits)
        val numberOfUnitsToPayFor = max(0.0, unitsToPayFor.sumOf { it.maintenance.toDouble() }).toFloat()

        val turnLimit =
            BASE_GAME_DURATION_TURNS * civInfo.gameInfo.gameParameters.gameSpeed.modifier
        val gameProgress =
            min(civInfo.gameInfo.turns / turnLimit, 1f) // as game progresses Maintenance cost rises
        var cost = baseUnitCost * numberOfUnitsToPayFor * (1 + gameProgress)
        cost = cost.pow(1 + gameProgress / 3) // Why 3? To spread 1 to 1.33
        if (!civInfo.isPlayerCivilization())
            cost *= civInfo.gameInfo.getDifficulty().aiUnitMaintenanceModifier


        return cost.toInt()
    }

    private fun getTransportationUpkeep(): Int {
        var transportationUpkeep = 0
        // we no longer use .flatMap, because there are a lot of tiles and keeping them all in a list
        // just to go over them once is a waste of memory - there are low-end phones who don't have much ram

        val ignoredTileTypes =
            civInfo.getMatchingUniques("No Maintenance costs for improvements in [] tiles")
                .map { it.params[0] }.toHashSet() // needs to be .toHashSet()ed,
        // Because we go over every tile in every city and check if it's in this list, which can get real heavy.

        for (city in civInfo.cities) {
            for (tile in city.getTiles()) {
                if (tile.isCityCenter()) continue
                if (tile.roadStatus == RoadStatus.None) continue // Cheap checks before pricey checks
                if (ignoredTileTypes.any { tile.matchesFilter(it, civInfo) }) continue

                transportationUpkeep += tile.roadStatus.upkeep
            }
        }
        for (unique in civInfo.getMatchingUniques("Maintenance on roads & railroads reduced by []%"))
            transportationUpkeep =
                (transportationUpkeep * (100f - unique.params[0].toInt()) / 100).toInt()

        return transportationUpkeep
    }

    fun getUnitSupply(): Int {
        /* TotalSupply = BaseSupply + NumCities*modifier + Population*modifier
        * In civ5, it seems population modifier is always 0.5, so i hardcoded it down below */
        var supply = getBaseUnitSupply() + getUnitSupplyFromCities() + getUnitSupplyFromPop()

        if (civInfo.isMajorCiv() && civInfo.playerType == PlayerType.AI)
            supply = (supply*(1f + civInfo.getDifficulty().aiUnitSupplyModifier)).toInt()
        return supply
    }

    fun getBaseUnitSupply(): Int = civInfo.getDifficulty().unitSupplyBase
    fun getUnitSupplyFromCities(): Int = civInfo.cities.size * civInfo.getDifficulty().unitSupplyPerCity
    fun getUnitSupplyFromPop(): Int = civInfo.cities.sumOf { it.population.population } / 2
    fun getUnitSupplyDeficit(): Int = max(0,civInfo.getCivUnitsSize() - getUnitSupply())

    /** Per each supply missing, a player gets -10% production. Capped at -70%. */
    fun getUnitSupplyProductionPenalty(): Float = -min(getUnitSupplyDeficit() * 10f, 70f)

    fun getStatMapForNextTurn(): StatMap {
        val statMap = StatMap()
        for (city in civInfo.cities) {
            for (entry in city.cityStats.finalStatList)
                statMap.add(entry.key, entry.value)
        }

        //City-States bonuses
        for (otherCiv in civInfo.getKnownCivs()) {
            val relationshipLevel = otherCiv.getDiplomacyManager(civInfo.civName).relationshipLevel()
            if (otherCiv.isCityState() && relationshipLevel >= RelationshipLevel.Friend) {
                val cityStateBonus = Stats()
                val eraInfo = civInfo.getEra()

                if (!eraInfo.undefinedCityStateBonuses()) {
                    for (bonus in eraInfo.getCityStateBonuses(otherCiv.cityStateType, relationshipLevel)) {
                        if (bonus.isOfType(UniqueType.CityStateStatsPerTurn) && bonus.conditionalsApply(otherCiv))
                            cityStateBonus.add(bonus.stats)
                    }
                } else {
                    // Deprecated, assume Civ V values for compatibility
                    if (otherCiv.cityStateType == CityStateType.Cultured) {
                        cityStateBonus.culture =
                            when {
                                civInfo.getEraNumber() in 0..1 -> 3f
                                civInfo.getEraNumber() in 2..3 -> 6f
                                else -> 13f
                            }
                        if (relationshipLevel == RelationshipLevel.Ally)
                            cityStateBonus.culture *= 2f
                    }
                }

                for (unique in civInfo.getMatchingUniques("[]% [] from City-States")) {
                    cityStateBonus[Stat.valueOf(unique.params[1])] *= unique.params[0].toPercent()
                }

                statMap.add("City-States", cityStateBonus)
            }

            if (otherCiv.isCityState())
                for (unique in civInfo.getMatchingUniques("Allied City-States provide [] equal to []% of what they produce for themselves")) {
                    if (otherCiv.getDiplomacyManager(civInfo.civName)
                            .relationshipLevel() != RelationshipLevel.Ally
                    ) continue
                    statMap.add(
                        "City-States",
                        Stats().add(
                            Stat.valueOf(unique.params[0]),
                            otherCiv.statsForNextTurn[Stat.valueOf(unique.params[0])] * unique.params[1].toFloat() / 100f
                        )
                    )
                }
        }

        statMap["Transportation upkeep"] = Stats(gold = -getTransportationUpkeep().toFloat())
        statMap["Unit upkeep"] = Stats(gold = -getUnitMaintenance().toFloat())

        if (civInfo.religionManager.religion != null) {
            for (unique in civInfo.religionManager.religion!!.getBeliefs(BeliefType.Founder)
                .flatMap { it.uniqueObjects }) {
                if (unique.placeholderText == "[] for each global city following this religion") {
                    statMap.add(
                        "Religion",
                        unique.stats.times(civInfo.religionManager.numberOfCitiesFollowingThisReligion())
                    )
                }
            }
            for (unique in civInfo.religionManager.religion!!.getFounderUniques())
                if (unique.placeholderText == "[] for every [] global followers []")
                    statMap.add(
                        "Religion",
                        unique.stats *
                                civInfo.religionManager.numberOfFollowersFollowingThisReligion(
                                    unique.params[2]
                                ).toFloat() /
                                unique.params[1].toFloat()
                    )
        }

        // Deprecated since 3.16.15
            if (civInfo.hasUnique(UniqueType.ExcessHappinessToCultureDeprecated)) {
                val happiness = civInfo.getHappiness()
                if (happiness > 0) statMap.add("Policies", Stats(culture = happiness / 2f))
            }
        //
        
        if (civInfo.getHappiness() > 0) {
            val excessHappinessConversion = Stats()
            for (unique in civInfo.getMatchingUniques("[]% of excess happiness converted to []")) {
                
                excessHappinessConversion.add(Stat.valueOf(unique.params[1]), (unique.params[0].toFloat() / 100f * civInfo.getHappiness()))
            }
            statMap.add("Policies", excessHappinessConversion)
        }

        // negative gold hurts science
        // if we have - or 0, then the techs will never be complete and the tech button
        // will show a negative number of turns and int.max, respectively
        if (statMap.values.map { it.gold }.sum() < 0 && civInfo.gold < 0) {
            val scienceDeficit = max(statMap.values.map { it.gold }.sum(),
                1 - statMap.values.map { it.science }.sum()
            )// Leave at least 1
            statMap["Treasury deficit"] = Stats(science = scienceDeficit)
        }
        val goldDifferenceFromTrade = civInfo.diplomacy.values.sumOf { it.goldPerTurn() }
        if (goldDifferenceFromTrade != 0)
            statMap["Trade"] = Stats(gold = goldDifferenceFromTrade.toFloat())

        return statMap
    }


    fun getHappinessBreakdown(): HashMap<String, Float> {
        val statMap = HashMap<String, Float>()
        statMap["Base happiness"] = civInfo.getDifficulty().baseHappiness.toFloat()

        var happinessPerUniqueLuxury = 4f + civInfo.getDifficulty().extraHappinessPerLuxury
        for (unique in civInfo.getMatchingUniques("+[] happiness from each type of luxury resource"))
            happinessPerUniqueLuxury += unique.params[0].toInt()

        val ownedLuxuries = civInfo.getCivResources().map { it.resource }
            .filter { it.resourceType == ResourceType.Luxury }

        statMap["Luxury resources"] = civInfo.getCivResources()
            .map { it.resource }
            .count { it.resourceType === ResourceType.Luxury } * happinessPerUniqueLuxury

        val happinessBonusForCityStateProvidedLuxuries =
            civInfo.getMatchingUniques("Happiness from Luxury Resources gifted by City-States increased by []%")
                .sumOf { it.params[0].toInt() } / 100f

        val luxuriesProvidedByCityStates = civInfo.getKnownCivs().asSequence()
            .filter { it.isCityState() && it.getAllyCiv() == civInfo.civName }
            .flatMap { it.getCivResources().map { res -> res.resource } }
            .distinct()
            .count { it.resourceType === ResourceType.Luxury && ownedLuxuries.contains(it) }

        statMap["City-State Luxuries"] =
            happinessPerUniqueLuxury * luxuriesProvidedByCityStates * happinessBonusForCityStateProvidedLuxuries

        val luxuriesAllOfWhichAreTradedAway = civInfo.detailedCivResources
            .filter {
                it.amount < 0 && it.resource.resourceType == ResourceType.Luxury
                        && (it.origin == "Trade" || it.origin == "Trade request")
            }
            .map { it.resource }
            .filter { !ownedLuxuries.contains(it) }

        statMap["Traded Luxuries"] =
            luxuriesAllOfWhichAreTradedAway.count() * happinessPerUniqueLuxury *
                    civInfo.getMatchingUniques(UniqueType.RetainHappinessFromLuxury)
                        .sumOf { it.params[0].toInt() } / 100f

        for (city in civInfo.cities) {
            // There appears to be a concurrency problem? In concurrent thread in ConstructionsTable.getConstructionButtonDTOs
            // Literally no idea how, since happinessList is ONLY replaced, NEVER altered.
            // Oh well, toList() should solve the problem, wherever it may come from.
            for ((key, value) in city.cityStats.happinessList.toList()) {
                if (statMap.containsKey(key))
                    statMap[key] = statMap[key]!! + value
                else statMap[key] = value
            }
        }

        if (civInfo.hasUnique("Provides 1 happiness per 2 additional social policies adopted")) {
            if (!statMap.containsKey("Policies")) statMap["Policies"] = 0f
            statMap["Policies"] = statMap["Policies"]!! +
                    civInfo.policies.getAdoptedPolicies()
                        .count { !Policy.isBranchCompleteByName(it) } / 2
        }

        var happinessPerNaturalWonder = 1f
        if (civInfo.hasUnique("Double Happiness from Natural Wonders"))
            happinessPerNaturalWonder *= 2

        statMap["Natural Wonders"] = happinessPerNaturalWonder * civInfo.naturalWonders.size

        if (civInfo.religionManager.religion != null) {
            var religionHappiness = 0f
            for (unique in civInfo.religionManager.religion!!.getBeliefs(BeliefType.Founder)
                .flatMap { it.uniqueObjects }) {
                if (unique.placeholderText == "[] for each global city following this religion") {
                    val followingCities =
                        civInfo.religionManager.numberOfCitiesFollowingThisReligion()
                    religionHappiness += unique.stats.happiness * followingCities
                }
                if (unique.placeholderText == "[] for every [] global followers []") {
                    val followers =
                        civInfo.religionManager.numberOfFollowersFollowingThisReligion(unique.params[2])
                    religionHappiness +=
                        unique.stats.happiness * (followers / unique.params[1].toInt())
                }
            }
            if (religionHappiness > 0) statMap["Religion"] = religionHappiness
        }

        //From city-states
        var cityStatesHappiness = 0f
        for (otherCiv in civInfo.getKnownCivs()) {
            val relationshipLevel = otherCiv.getDiplomacyManager(civInfo).relationshipLevel()
            if (!otherCiv.isCityState() || relationshipLevel < RelationshipLevel.Friend) continue

            val eraInfo = civInfo.getEra()
            // Deprecated, assume Civ V values for compatibility
            if (!eraInfo.undefinedCityStateBonuses()) {
                for (bonus in eraInfo.getCityStateBonuses(otherCiv.cityStateType, relationshipLevel)) {
                    if (!bonus.conditionalsApply(otherCiv)) continue
                    if (bonus.isOfType(UniqueType.CityStateHappiness))
                        cityStatesHappiness += bonus.params[0].toFloat()
                }
            } else if (otherCiv.cityStateType == CityStateType.Mercantile) {
                // compatibility mode for
                cityStatesHappiness += if (civInfo.getEraNumber() in 0..1) 2f else 3f
            }
        }

        // Just in case
        if (cityStatesHappiness > 0) {
            for (unique in civInfo.getMatchingUniques("[]% [] from City-States")) {
                if (unique.params[1] == Stat.Happiness.name)
                    cityStatesHappiness *= unique.params[0].toPercent()
            }
        }

        if (cityStatesHappiness > 0) statMap["City-States"] = cityStatesHappiness

        return statMap
    }

}