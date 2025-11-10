package com.unciv.logic.civilization.transients

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.StatMap
import com.unciv.models.stats.Stats
import com.unciv.ui.components.extensions.toPercent
import yairm210.purity.annotations.Readonly
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** CivInfo class was getting too crowded */
class CivInfoStatsForNextTurn(val civInfo: Civilization) {

    @Transient
    /** Happiness for next turn */
    var happiness = 0

    @Transient
    var statsForNextTurn = Stats()

    @Readonly
    private fun getUnitMaintenance(): Int {
        val baseUnitCost = 0.5f
        var freeUnits = 3
        for (unique in civInfo.getMatchingUniques(UniqueType.FreeUnits, civInfo.state)) {
            freeUnits += unique.params[0].toInt()
        }

        var unitsToPayFor = civInfo.units.getCivUnits()
        if (civInfo.hasUnique(UniqueType.UnitsInCitiesNoMaintenance))
            unitsToPayFor = unitsToPayFor.filterNot {
                it.getTile().isCityCenter() && it.canGarrison()
            }

        // Each unit starts with 1f aka 100% of cost, and then the discount is added.
        // Note all discounts are in the form of -X%, such as -25 for 25% reduction

        val costsToPay = ArrayList<Float>()

        // We IGNORE the conditionals when we get them civ-wide, so we won't need to do the same thing for EVERY unit in the civ.
        // This leads to massive memory and CPU time savings when calculating the maintenance!
        val civwideDiscountUniques = civInfo.getMatchingUniques(UniqueType.UnitMaintenanceDiscount, GameContext.IgnoreConditionals)
            .toList().asSequence()

        for (unit in unitsToPayFor) {
            val stateForConditionals = unit.cache.state
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

        if (!civInfo.isHuman())
            cost *= civInfo.gameInfo.getDifficulty().aiUnitMaintenanceModifier

        return cost.toInt()
    }

    @Readonly
    private fun getTransportationUpkeep(): Stats {
        val transportationUpkeep = Stats()
        // we no longer use .flatMap, because there are a lot of tiles and keeping them all in a list
        // just to go over them once is a waste of memory - there are low-end phones who don't have much ram

        val ignoredTileTypes =
            civInfo.getMatchingUniques(UniqueType.NoImprovementMaintenanceInSpecificTiles)
                .map { it.params[0] }.toHashSet() // needs to be .toHashSet()ed,
        // Because we go over every tile in every city and check if it's in this list, which can get real heavy.

        fun addMaintenanceUniques(road: TileImprovement, type: UniqueType, state: GameContext) {
            for (unique in road.getMatchingUniques(type, state))
                transportationUpkeep.add(Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
        }

        for (city in civInfo.cities) {
            for (tile in city.getTiles()) {
                if (tile.isCityCenter()) continue
                if (tile.getUnpillagedRoad() == RoadStatus.None) continue // Cheap checks before pricey checks
                if (ignoredTileTypes.any { tile.matchesFilter(it, civInfo) }) continue
                val road = tile.getUnpillagedRoadImprovement()!!  // covered by RoadStatus.None test
                val gameContext = GameContext(civInfo, tile = tile)
                addMaintenanceUniques(road, UniqueType.ImprovementMaintenance, gameContext)
                addMaintenanceUniques(road, UniqueType.ImprovementAllMaintenance, gameContext)
            }
        }

        // tabulate neutral roads
        for (position in civInfo.neutralRoads) {
            val tile = civInfo.gameInfo.tileMap[position]
            if (tile.getUnpillagedRoad() == RoadStatus.None) continue // Cheap checks before pricey checks
            val road = tile.getUnpillagedRoadImprovement()!!  // covered by RoadStatus.None test
            val gameContext = GameContext(civInfo, tile = tile)
            addMaintenanceUniques(road, UniqueType.ImprovementAllMaintenance, gameContext)
        }

        for (unique in civInfo.getMatchingUniques(UniqueType.RoadMaintenance))
            transportationUpkeep.timesInPlace(unique.params[0].toPercent())

        return transportationUpkeep
    }

    @Readonly
    fun getUnitSupply(): Int {
        /* TotalSupply = BaseSupply + NumCities*modifier + Population*modifier
        * In civ5, it seems population modifier is always 0.5, so i hardcoded it down below */
        var supply = getBaseUnitSupply() + getUnitSupplyFromCities() + getUnitSupplyFromPop()

        if (civInfo.isMajorCiv() && civInfo.playerType == PlayerType.AI)
            supply = (supply*(1f + civInfo.getDifficulty().aiUnitSupplyModifier)).toInt()
        return supply
    }

    @Readonly
    fun getBaseUnitSupply(): Int {
        return civInfo.getDifficulty().unitSupplyBase +
            civInfo.getMatchingUniques(UniqueType.BaseUnitSupply).sumOf { it.params[0].toInt() }
    }
    @Readonly
    fun getUnitSupplyFromCities(): Int {
        return civInfo.cities.size *
            (civInfo.getDifficulty().unitSupplyPerCity
                    + civInfo.getMatchingUniques(UniqueType.UnitSupplyPerCity).sumOf { it.params[0].toInt() })
    }
    @Readonly
    fun getUnitSupplyFromPop(): Int {
        var totalSupply = civInfo.cities.sumOf { it.population.population } * civInfo.gameInfo.ruleset.modOptions.constants.unitSupplyPerPopulation

        for (unique in civInfo.getMatchingUniques(UniqueType.UnitSupplyPerPop)) {
            val applicablePopulation = civInfo.cities
                .filter { it.matchesFilter(unique.params[2]) }
                .sumOf { it.population.population / unique.params[1].toInt() }
            totalSupply += unique.params[0].toDouble() * applicablePopulation
        }
        return totalSupply.toInt()
    }
    @Readonly fun getUnitSupplyDeficit(): Int = max(0,civInfo.units.getCivUnitsSize() - getUnitSupply())

    /** Per each supply missing, a player gets -10% production. Capped at -70%. */
    @Readonly fun getUnitSupplyProductionPenalty(): Float = -min(getUnitSupplyDeficit() * 10f, 70f)

    @Readonly
    fun getStatMapForNextTurn(): StatMap {
        val statMap = StatMap()
        for (city in civInfo.cities) {
            for (entry in city.cityStats.finalStatList)
                statMap.add(entry.key, entry.value)
        }

        //City-States bonuses
        for (otherCiv in civInfo.getKnownCivs()) {
            if (!otherCiv.isCityState) continue
            if (otherCiv.getDiplomacyManager(civInfo)!!.relationshipIgnoreAfraid() != RelationshipLevel.Ally)
                continue
            for (unique in civInfo.getMatchingUniques(UniqueType.CityStateStatPercent)) {
                val stats = Stats()
                stats.add(
                    Stat.valueOf(unique.params[0]),
                    otherCiv.stats.statsForNextTurn[Stat.valueOf(unique.params[0])] * unique.params[1].toFloat() / 100f
                )
                statMap.add(
                    Constants.cityStates,
                    stats
                )
            }
        }

        statMap["Transportation upkeep"] = getTransportationUpkeep() * -1
        statMap["Unit upkeep"] = Stats(gold = -getUnitMaintenance().toFloat())


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

        for ((key, value) in getGlobalStatsFromUniques())
            statMap.add(key,value)

        return statMap
    }


    fun getHappinessBreakdown(): HashMap<String, Float> {
        val statMap = HashMap<String, Float>()

        fun HashMap<String, Float>.add(key:String, value: Float) {
            if (!containsKey(key)) put(key, value)
            else put(key, value+get(key)!!)
        }

        statMap["Base happiness"] = civInfo.getDifficulty().baseHappiness.toFloat()

        var happinessPerUniqueLuxury = 4f + civInfo.getDifficulty().extraHappinessPerLuxury
        for (unique in civInfo.getMatchingUniques(UniqueType.BonusHappinessFromLuxury))
            happinessPerUniqueLuxury += unique.params[0].toInt()

        val ownedLuxuries = civInfo.getCivResourceSupply().map { it.resource }
            .filter { it.resourceType == ResourceType.Luxury }

        val relevantLuxuries = civInfo.getCivResourceSupply().asSequence()
            .map { it.resource }
            .count { it.resourceType == ResourceType.Luxury
                    && it.getMatchingUniques(UniqueType.ObsoleteWith)
                .none { unique -> civInfo.tech.isResearched(unique.params[0]) } }
        statMap["Luxury resources"] = relevantLuxuries * happinessPerUniqueLuxury

        val happinessBonusForCityStateProvidedLuxuries =
            civInfo.getMatchingUniques(UniqueType.CityStateLuxuryHappiness).sumOf { it.params[0].toInt() } / 100f

        val luxuriesProvidedByCityStates = civInfo.getKnownCivs().asSequence()
            .filter { it.isCityState && it.allyCiv == civInfo }
            .flatMap { it.getCivResourceSupply().map { res -> res.resource } }
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
            for ((key, value) in city.cityStats.happinessList.toList())
                statMap.add(key, value)
        }

        val transportUpkeep = getTransportationUpkeep()
        if (transportUpkeep.happiness != 0f)
            statMap["Transportation Upkeep"] = -transportUpkeep.happiness

        for ((key, value) in getGlobalStatsFromUniques())
            statMap.add(key,value.happiness)

        return statMap
    }

    @Readonly
    private fun getGlobalStatsFromUniques():StatMap {
        val statMap = StatMap()
        if (civInfo.religionManager.religion != null) {
            for (unique in civInfo.religionManager.religion!!.founderBeliefUniqueMap.getMatchingUniques(
                UniqueType.StatsFromGlobalCitiesFollowingReligion, civInfo.state
            ))
                statMap.add(
                    "Religion",
                    unique.stats * civInfo.religionManager.numberOfCitiesFollowingThisReligion()
                )

            for (unique in civInfo.religionManager.religion!!.founderBeliefUniqueMap.getMatchingUniques(
                UniqueType.StatsFromGlobalFollowers, civInfo.state
            ))
                statMap.add(
                    "Religion",
                    unique.stats * civInfo.religionManager.numberOfFollowersFollowingThisReligion(
                        unique.params[2]
                    ).toFloat() / unique.params[1].toFloat()
                )
        }

        for (unique in civInfo.getMatchingUniques(UniqueType.StatsPerPolicies)) {
            val amount = civInfo.policies.getAdoptedPolicies()
                .count { !Policy.isBranchCompleteByName(it) } / unique.params[1].toInt()
            statMap.add("Policies", unique.stats.times(amount))
        }

        for (unique in civInfo.getMatchingUniques(UniqueType.Stats))
            if (unique.sourceObjectType != UniqueTarget.Building && unique.sourceObjectType != UniqueTarget.Wonder)
                statMap.add(unique.getSourceNameForUser(), unique.stats)

        for (unique in civInfo.getMatchingUniques(UniqueType.StatsPerStat)) {
            val amount = civInfo.getStatReserve(Stat.valueOf(unique.params[2])) / unique.params[1].toInt()
            statMap.add("Stats", unique.stats.times(amount))
        }

        val statsPerNaturalWonder = Stats(happiness = 1f)

        for (unique in civInfo.getMatchingUniques(UniqueType.StatsFromNaturalWonders))
            statsPerNaturalWonder.add(unique.stats)

        statMap.add("Natural Wonders", statsPerNaturalWonder.times(civInfo.naturalWonders.size))

        if (statMap.contains(Constants.cityStates)) {
            for (unique in civInfo.getMatchingUniques(UniqueType.BonusStatsFromCityStates)) {
                val bonusPercent = unique.params[0].toPercent()
                val bonusStat = Stat.valueOf(unique.params[1])
                statMap[Constants.cityStates]!![bonusStat] *= bonusPercent
            }
        }

        return statMap
    }

}
