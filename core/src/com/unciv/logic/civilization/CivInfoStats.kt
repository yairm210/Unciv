package com.unciv.logic.civilization

import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.RoadStatus
import com.unciv.models.metadata.BASE_GAME_DURATION_TURNS
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.StatMap
import com.unciv.models.stats.Stats
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** CivInfo class was getting too crowded */
class CivInfoStats(val civInfo: CivilizationInfo) {

    private fun getUnitMaintenance(): Int {
        val baseUnitCost = 0.5f
        val freeUnits = 3
        var unitsToPayFor = civInfo.getCivUnits()
        if (civInfo.hasUnique("Units in cities cost no Maintenance"))
        // Only land military units can truly "garrison"
            unitsToPayFor = unitsToPayFor.filterNot {
                it.getTile().isCityCenter() && it.canGarrison()
            }


        var numberOfUnitsToPayFor = max(0f, unitsToPayFor.count().toFloat() - freeUnits)


        for (unique in civInfo.getMatchingUniques("-[]% [] unit maintenance costs")) {
            val numberOfUnitsWithDiscount = min(numberOfUnitsToPayFor, unitsToPayFor.count { it.matchesFilter(unique.params[1]) }.toFloat())
            numberOfUnitsToPayFor -= numberOfUnitsWithDiscount * unique.params[0].toFloat() / 100
        }

        val turnLimit = BASE_GAME_DURATION_TURNS * civInfo.gameInfo.gameParameters.gameSpeed.modifier
        val gameProgress = min(civInfo.gameInfo.turns / turnLimit, 1f) // as game progresses Maintenance cost rises
        var cost = baseUnitCost * numberOfUnitsToPayFor * (1 + gameProgress)
        cost = cost.pow(1 + gameProgress / 3) // Why 3? To spread 1 to 1.33
        if (!civInfo.isPlayerCivilization())
            cost *= civInfo.gameInfo.getDifficulty().aiUnitMaintenanceModifier
        if (civInfo.hasUnique("-33% unit upkeep costs")) cost *= 0.66f
        return cost.toInt()
    }

    private fun getTransportationUpkeep(): Int {
        var transportationUpkeep = 0
        // we no longer use .flatMap, because there are a lot of tiles and keeping them all in a list
        // just to go over them once is a waste of memory - there are low-end phones who don't have much ram

        val ignoredTileTypes = civInfo.getMatchingUniques("No Maintenance costs for improvements in []")
                .map { it.params[0] }.toHashSet() // needs to be .toHashSet()ed,
        // Because we go over every tile in every city and check if it's in this list, which can get real heavy.

        // accounting for both the old way and the new way of doing no maintenance in hills
        val ignoreHillTiles = civInfo.hasUnique("No Maintenance costs for improvements in Hills") || "Hills" in ignoredTileTypes

        for (city in civInfo.cities) {
            for (tile in city.getTiles()) {
                if (tile.isCityCenter()) continue
                if (ignoreHillTiles && tile.isHill()) continue

                if (tile.terrainFeature in ignoredTileTypes || tile.baseTerrain in ignoredTileTypes) {
                    continue
                }

                val tileUpkeep =
                        when (tile.roadStatus) {
                            RoadStatus.Road -> 1
                            RoadStatus.Railroad -> 2
                            RoadStatus.None -> 0
                        }
                transportationUpkeep += tileUpkeep
            }
        }
        for (unique in civInfo.getMatchingUniques("Maintenance on roads & railroads reduced by []%"))
            transportationUpkeep = (transportationUpkeep * (100f - unique.params[0].toInt()) / 100).toInt()

        return transportationUpkeep
    }

    fun getStatMapForNextTurn(): StatMap {
        val statMap = StatMap()
        for (city in civInfo.cities) {
            for (entry in city.cityStats.finalStatList)
                statMap.add(entry.key, entry.value)
        }

        //City-States culture bonus
        for (otherCiv in civInfo.getKnownCivs()) {
            if (otherCiv.isCityState() && otherCiv.cityStateType == CityStateType.Cultured
                    && otherCiv.getDiplomacyManager(civInfo.civName).relationshipLevel() >= RelationshipLevel.Friend) {
                val cultureBonus = Stats()
                var culture = 3f * (civInfo.getEraNumber() + 1)
                if (civInfo.hasUnique("Food and Culture from Friendly City-States are increased by 50%"))
                    culture *= 1.5f
                cultureBonus.add(Stat.Culture, culture)
                statMap.add("City-States", cultureBonus)
            }


            if (otherCiv.isCityState() && otherCiv.getDiplomacyManager(civInfo.civName).relationshipLevel() >= RelationshipLevel.Ally) {
                val sciencePercentage = civInfo
                        .getMatchingUniques("Allied City-States provide Science equal to []% of what they produce for themselves")
                        .sumBy { it.params[0].toInt() }
                statMap.add("City-States",Stats().apply { science = otherCiv.statsForNextTurn.science * (sciencePercentage/100f) })
            }
        }

        statMap["Transportation upkeep"] = Stats().apply { gold = -getTransportationUpkeep().toFloat() }
        statMap["Unit upkeep"] = Stats().apply { gold = -getUnitMaintenance().toFloat() }

        if (civInfo.hasUnique("50% of excess happiness added to culture towards policies")) {
            val happiness = civInfo.getHappiness()
            if (happiness > 0) statMap.add("Policies", Stats().apply { culture = happiness / 2f })
        }

        // negative gold hurts science
        // if we have - or 0, then the techs will never be complete and the tech button
        // will show a negative number of turns and int.max, respectively
        if (statMap.values.map { it.gold }.sum() < 0 && civInfo.gold < 0) {
            val scienceDeficit = max(statMap.values.map { it.gold }.sum(),
                    1 - statMap.values.map { it.science }.sum())// Leave at least 1
            statMap["Treasury deficit"] = Stats().apply { science = scienceDeficit }
        }
        val goldDifferenceFromTrade = civInfo.diplomacy.values.sumBy { it.goldPerTurn() }
        if (goldDifferenceFromTrade != 0)
            statMap["Trade"] = Stats().apply { gold = goldDifferenceFromTrade.toFloat() }

        return statMap
    }


    fun getHappinessBreakdown(): HashMap<String, Float> {
        val statMap = HashMap<String, Float>()
        statMap["Base happiness"] = civInfo.getDifficulty().baseHappiness.toFloat()

        // TODO - happinessPerUnique should be difficulty-dependent, 5 on Settler and Chieftian and 4 on other difficulties (should be parameter, not in code)
        var happinessPerUniqueLuxury = 4f + civInfo.getDifficulty().extraHappinessPerLuxury
        for (unique in civInfo.getMatchingUniques("+1 happiness from each type of luxury resource"))
            happinessPerUniqueLuxury += 1
        statMap["Luxury resources"] = civInfo.getCivResources().map { it.resource }
                .count { it.resourceType === ResourceType.Luxury } * happinessPerUniqueLuxury

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
                    civInfo.policies.getAdoptedPolicies().count { !it.endsWith("Complete") } / 2
        }

        var happinessPerNaturalWonder = 1f
        if (civInfo.hasUnique("Double Happiness from Natural Wonders"))
            happinessPerNaturalWonder *= 2

        statMap["Natural Wonders"] = happinessPerNaturalWonder * civInfo.naturalWonders.size

        //From city-states
        for (otherCiv in civInfo.getKnownCivs()) {
            if (otherCiv.isCityState() && otherCiv.cityStateType == CityStateType.Mercantile
                    && otherCiv.getDiplomacyManager(civInfo).relationshipLevel() >= RelationshipLevel.Friend) {
                if (statMap.containsKey("City-States"))
                    statMap["City-States"] = statMap["City-States"]!! + 3f
                else
                    statMap["City-States"] = 3f
            }
        }

        return statMap
    }

}