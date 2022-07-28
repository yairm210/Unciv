package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.RoadStatus
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.StatMap
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.extensions.toPercent
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
        if (civInfo.hasUnique(UniqueType.UnitsInCitiesNoMaintenance))
            unitsToPayFor = unitsToPayFor.filterNot {
                it.getTile().isCityCenter() && it.canGarrison()
            }

        // Each unit starts with 1f aka 100% of cost, and then the discount is added.
        // Note all discounts are in the form of -X%, such as -25 for 25% reduction

        val costsToPay = ArrayList<Float>()

        // We IGNORE the conditionals when we get them civ-wide, so we won't need to do the same thing for EVERY unit in the civ.
        // This leads to massive memory and CPU time savings when calculating the maintenance!
        val civwideDiscountUniques = civInfo.getMatchingUniques(UniqueType.UnitMaintenanceDiscount, StateForConditionals.IgnoreConditionals)
            .toList().asSequence()

        for (unit in unitsToPayFor) {
            val stateForConditionals = StateForConditionals(civInfo = civInfo, unit = unit)
            var unitMaintenance = 1f
            val uniquesThatApply = unit.getMatchingUniques(
                UniqueType.UnitMaintenanceDiscount,
                stateForConditionals
            ) + civwideDiscountUniques.filter { it.conditionalsApply(stateForConditionals) }
            for (unique in uniquesThatApply) {
                unitMaintenance *= unique.params[0].toPercent()
            }
            costsToPay.add(unitMaintenance)
        }

        // Sort by descending maintenance, then drop most expensive X units to make them free
        // If more free than units left, runs sum on empty sequence
        costsToPay.sortDescending()
        val numberOfUnitsToPayFor = max(0.0, costsToPay.asSequence().drop(freeUnits).sumOf { it.toDouble() } ).toFloat()

        // as game progresses Maintenance cost rises
        val turnLimit = civInfo.gameInfo.speed.numTotalTurns().toFloat()
        val gameProgress = min(civInfo.gameInfo.turns / turnLimit, 1f)

        var cost = baseUnitCost * numberOfUnitsToPayFor * (1 + gameProgress)
        cost = cost.pow(1 + gameProgress / 3) // Why 3? To spread 1 to 1.33

        if (!civInfo.isPlayerCivilization())
            cost *= civInfo.gameInfo.getDifficulty().aiUnitMaintenanceModifier

        return cost.toInt()
    }

    private fun getTransportationUpkeep(): Int {
        var transportationUpkeep = 0f
        // we no longer use .flatMap, because there are a lot of tiles and keeping them all in a list
        // just to go over them once is a waste of memory - there are low-end phones who don't have much ram

        val ignoredTileTypes =
            civInfo.getMatchingUniques(UniqueType.NoImprovementMaintenanceInSpecificTiles)
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
        for (unique in civInfo.getMatchingUniques(UniqueType.RoadMaintenance))
            transportationUpkeep *= unique.params[0].toPercent()

        return transportationUpkeep.toInt()
    }

    fun getUnitSupply(): Int {
        /* TotalSupply = BaseSupply + NumCities*modifier + Population*modifier
        * In civ5, it seems population modifier is always 0.5, so i hardcoded it down below */
        var supply = getBaseUnitSupply() + getUnitSupplyFromCities() + getUnitSupplyFromPop()

        if (civInfo.isMajorCiv() && civInfo.playerType == PlayerType.AI)
            supply = (supply*(1f + civInfo.getDifficulty().aiUnitSupplyModifier)).toInt()
        return supply
    }

    fun getBaseUnitSupply(): Int {
        return civInfo.getDifficulty().unitSupplyBase +
            civInfo.getMatchingUniques(UniqueType.BaseUnitSupply).sumOf { it.params[0].toInt() }
    }
    fun getUnitSupplyFromCities(): Int {
        return civInfo.cities.size *
            (civInfo.getDifficulty().unitSupplyPerCity + civInfo.getMatchingUniques(UniqueType.UnitSupplyPerCity).sumOf { it.params[0].toInt() })
    }
    fun getUnitSupplyFromPop(): Int {
        var totalSupply = civInfo.cities.sumOf { it.population.population } * civInfo.gameInfo.ruleSet.modOptions.constants.unitSupplyPerPopulation

        for (unique in civInfo.getMatchingUniques(UniqueType.UnitSupplyPerPop)) {
            val applicablePopulation = civInfo.cities
                .filter { it.matchesFilter(unique.params[1]) }
                .sumOf { it.population.population }
            totalSupply += unique.params[0].toDouble() * applicablePopulation
        }
        return totalSupply.toInt()
    }
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

                for (unique in civInfo.getMatchingUniques(UniqueType.StatBonusPercentFromCityStates)) {
                    cityStateBonus[Stat.valueOf(unique.params[1])] *= unique.params[0].toPercent()
                }

                statMap.add(Constants.cityStates, cityStateBonus)
            }

            if (otherCiv.isCityState())
                for (unique in civInfo.getMatchingUniques(UniqueType.CityStateStatPercent)) {
                    if (otherCiv.getDiplomacyManager(civInfo.civName)
                            .relationshipLevel() != RelationshipLevel.Ally
                    ) continue
                    statMap.add(
                        Constants.cityStates,
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
            for (unique in civInfo.religionManager.religion!!.getFounderUniques()) {
                if (unique.isOfType(UniqueType.StatsFromGlobalCitiesFollowingReligion)) {
                    statMap.add(
                        "Religion",
                        unique.stats * civInfo.religionManager.numberOfCitiesFollowingThisReligion()
                    )
                }
                if (unique.isOfType(UniqueType.StatsFromGlobalFollowers))
                    statMap.add(
                        "Religion",
                        unique.stats * civInfo.religionManager.numberOfFollowersFollowingThisReligion(unique.params[2]).toFloat() / unique.params[1].toFloat()
                    )
            }
        }

        if (civInfo.getHappiness() > 0) {
            val excessHappinessConversion = Stats()
            for (unique in civInfo.getMatchingUniques(UniqueType.ExcessHappinessToGlobalStat)) {
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
        for (unique in civInfo.getMatchingUniques(UniqueType.BonusHappinessFromLuxury))
            happinessPerUniqueLuxury += unique.params[0].toInt()

        val ownedLuxuries = civInfo.getCivResources().map { it.resource }
            .filter { it.resourceType == ResourceType.Luxury }

        val relevantLuxuries = civInfo.getCivResources().asSequence()
            .map { it.resource }
            .count { it.resourceType == ResourceType.Luxury
                    && it.getMatchingUniques(UniqueType.ObsoleteWith)
                .none { unique -> civInfo.tech.isResearched(unique.params[0]) } }
        statMap["Luxury resources"] = relevantLuxuries * happinessPerUniqueLuxury

        val happinessBonusForCityStateProvidedLuxuries =
            civInfo.getMatchingUniques(UniqueType.CityStateLuxuryHappiness).sumOf { it.params[0].toInt() } / 100f

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
            luxuriesAllOfWhichAreTradedAway.size * happinessPerUniqueLuxury *
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

        if (civInfo.hasUnique(UniqueType.HappinessPer2Policies)) {
            if (!statMap.containsKey("Policies")) statMap["Policies"] = 0f
            statMap["Policies"] = statMap["Policies"]!! +
                    civInfo.policies.getAdoptedPolicies()
                        .count { !Policy.isBranchCompleteByName(it) } / 2
        }

        var happinessPerNaturalWonder = 1f
        if (civInfo.hasUnique(UniqueType.DoubleHappinessFromNaturalWonders))
            happinessPerNaturalWonder *= 2

        statMap["Natural Wonders"] = happinessPerNaturalWonder * civInfo.naturalWonders.size

        if (civInfo.religionManager.religion != null) {
            var religionHappiness = 0f
            for (unique in civInfo.religionManager.religion!!.getBeliefs(BeliefType.Founder)
                .flatMap { it.uniqueObjects }
            ) {
                if (unique.type == UniqueType.StatsFromGlobalCitiesFollowingReligion) {
                    val followingCities =
                        civInfo.religionManager.numberOfCitiesFollowingThisReligion()
                    religionHappiness += unique.stats.happiness * followingCities
                }
                if (unique.type == UniqueType.StatsFromGlobalFollowers) {
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
            for (unique in civInfo.getMatchingUniques(UniqueType.StatBonusPercentFromCityStates)) {
                if (unique.params[1] == Stat.Happiness.name)
                    cityStatesHappiness *= unique.params[0].toPercent()
            }
        }

        if (cityStatesHappiness > 0) statMap[Constants.cityStates] = cityStatesHappiness

        return statMap
    }

}
