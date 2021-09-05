package com.unciv.logic.civilization

import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.RoadStatus
import com.unciv.models.metadata.BASE_GAME_DURATION_TURNS
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Unique
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.StatMap
import com.unciv.models.stats.Stats
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** CivInfo class was getting too crowded */
class CivInfoStats(val civInfo: CivilizationInfo) {

    private fun getUnitMaintenance(): Int {
        val baseUnitCost = 0.5f
        var freeUnits = 3
        for (unique in civInfo.getMatchingUniques("[] units cost no maintenance")) {
            freeUnits += unique.params[0].toInt()
        }

        var unitsToPayFor = civInfo.getCivUnits()
        if (civInfo.hasUnique("Units in cities cost no Maintenance"))
        // Only land military units can truly "garrison"
            unitsToPayFor = unitsToPayFor.filterNot {
                it.getTile().isCityCenter() && it.canGarrison()
            }


        var numberOfUnitsToPayFor = max(0f, unitsToPayFor.count().toFloat() - freeUnits)


        for (unique in civInfo.getMatchingUniques("-[]% [] unit maintenance costs")) {
            val numberOfUnitsWithDiscount = min(
                numberOfUnitsToPayFor,
                unitsToPayFor.count { it.matchesFilter(unique.params[1]) }.toFloat()
            )
            numberOfUnitsToPayFor -= numberOfUnitsWithDiscount * unique.params[0].toFloat() / 100f
        }

        val turnLimit =
            BASE_GAME_DURATION_TURNS * civInfo.gameInfo.gameParameters.gameSpeed.modifier
        val gameProgress =
            min(civInfo.gameInfo.turns / turnLimit, 1f) // as game progresses Maintenance cost rises
        var cost = baseUnitCost * numberOfUnitsToPayFor * (1 + gameProgress)
        cost = cost.pow(1 + gameProgress / 3) // Why 3? To spread 1 to 1.33
        if (!civInfo.isPlayerCivilization())
            cost *= civInfo.gameInfo.getDifficulty().aiUnitMaintenanceModifier

        for (unique in civInfo.getMatchingUniques("-[]% unit upkeep costs")) {
            cost *= 1f - unique.params[0].toFloat() / 100f
        }

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
                if (tile.roadStatus == RoadStatus.None) continue // Cheap checks before pricy checks
                if (ignoredTileTypes.any { tile.matchesFilter(it, civInfo) }) continue

                transportationUpkeep += tile.roadStatus.upkeep
            }
        }
        for (unique in civInfo.getMatchingUniques("Maintenance on roads & railroads reduced by []%"))
            transportationUpkeep =
                (transportationUpkeep * (100f - unique.params[0].toInt()) / 100).toInt()

        return transportationUpkeep
    }

    fun getStatMapForNextTurn(): StatMap {
        val statMap = StatMap()
        for (city in civInfo.cities) {
            for (entry in city.cityStats.finalStatList)
                statMap.add(entry.key, entry.value)
        }

        //City-States bonuses
        for (otherCiv in civInfo.getKnownCivs()) {
            if (otherCiv.isCityState() && otherCiv.getDiplomacyManager(civInfo.civName)
                    .relationshipLevel() >= RelationshipLevel.Friend
            ) {
                val cityStateBonus = Stats()
                val eraInfo = civInfo.getEra()

                val relevantBonuses =
                    if (otherCiv.getDiplomacyManager(civInfo.civName).relationshipLevel() == RelationshipLevel.Friend)
                        eraInfo.friendBonus[otherCiv.cityStateType.name]
                    else eraInfo.allyBonus[otherCiv.cityStateType.name]

                if (relevantBonuses != null) {
                    for (bonus in relevantBonuses) {
                        val unique = Unique(bonus)
                        if (unique.placeholderText == "Provides [] [] per turn")
                            cityStateBonus.add(
                                Stat.valueOf(unique.params[1]),
                                unique.params[0].toFloat()
                            )
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
                        if (otherCiv.getDiplomacyManager(civInfo.civName)
                                .relationshipLevel() == RelationshipLevel.Ally
                        )
                            cityStateBonus.culture *= 2f
                    }
                }

                if (civInfo.hasUnique("Food and Culture from Friendly City-States are increased by 50%"))
                    cityStateBonus.culture *= 1.5f

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


        if (civInfo.hasUnique("50% of excess happiness added to culture towards policies")) {
            val happiness = civInfo.getHappiness()
            if (happiness > 0) statMap.add("Policies", Stats(culture = happiness / 2f))
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
        val goldDifferenceFromTrade = civInfo.diplomacy.values.sumBy { it.goldPerTurn() }
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
                .sumBy { it.params[0].toInt() } / 100f

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
                    civInfo.getMatchingUniques("Retain []% of the happiness from a luxury after the last copy has been traded away")
                        .sumBy { it.params[0].toInt() } / 100f

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
            statMap["Religion"] = 0f
            for (unique in civInfo.religionManager.religion!!.getBeliefs(BeliefType.Founder)
                .flatMap { it.uniqueObjects }) {
                if (unique.placeholderText == "[] for each global city following this religion") {
                    statMap["Religion"] =
                        statMap["Religion"]!! +
                                unique.stats.happiness * civInfo.religionManager.numberOfCitiesFollowingThisReligion()
                            .toFloat()
                }
                if (unique.placeholderText == "[] for every [] global followers []") {
                    statMap["Religion"] =
                        statMap["Religion"]!! +
                                unique.stats.happiness *
                                civInfo.religionManager.numberOfFollowersFollowingThisReligion(
                                    unique.params[2]
                                ).toFloat() /
                                unique.params[1].toFloat()
                }
            }
            if (statMap["Religion"] == 0f)
                statMap.remove("Religion")
        }

        //From city-states
        for (otherCiv in civInfo.getKnownCivs()) {
            if (otherCiv.isCityState() && otherCiv.getDiplomacyManager(civInfo)
                    .relationshipLevel() >= RelationshipLevel.Friend
            ) {
                val eraInfo = civInfo.getEra()
                val relevantBonuses =
                    if (otherCiv.getDiplomacyManager(civInfo).relationshipLevel() == RelationshipLevel.Friend)
                        eraInfo.friendBonus[otherCiv.cityStateType.name]
                    else eraInfo.allyBonus[otherCiv.cityStateType.name]

                if (relevantBonuses != null) {
                    for (bonus in relevantBonuses) {
                        if (bonus.getPlaceholderText() == "Provides [] Happiness") {
                            if (statMap.containsKey("City-States"))
                                statMap["City-States"] =
                                    statMap["City-States"]!! + bonus.getPlaceholderParameters()[0].toFloat()
                            else
                                statMap["City-States"] =
                                    bonus.getPlaceholderParameters()[0].toFloat()
                        }
                    }
                } else {
                    // Deprecated, assume Civ V values for compatibility
                    if (otherCiv.cityStateType == CityStateType.Mercantile) {
                        val happinessBonus = if (civInfo.getEraNumber() in 0..1) 2f else 3f
                        if (statMap.containsKey("City-States"))
                            statMap["City-States"] = statMap["City-States"]!! + happinessBonus
                        else
                            statMap["City-States"] = happinessBonus
                    }
                }
            }
        }

        return statMap
    }

}