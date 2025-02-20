package com.unciv.logic.city.managers

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.automation.Automation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toPercent
import com.unciv.utils.withItem
import com.unciv.utils.withoutItem
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

class CityPopulationManager : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var city: City

    var population = 1
        private set
    var foodStored = 0

    val specialistAllocations = Counter<String>()

    fun getNewSpecialists() = specialistAllocations 


    //region pure functions
    fun clone(): CityPopulationManager {
        val toReturn = CityPopulationManager()
        toReturn.specialistAllocations.add(specialistAllocations)
        toReturn.population = population
        toReturn.foodStored = foodStored
        return toReturn
    }

    fun getNumberOfSpecialists() = getNewSpecialists().values.sum()

    fun getFreePopulation(): Int {
        val workingPopulation = city.workedTiles.size
        return population - workingPopulation - getNumberOfSpecialists()
    }

    fun getFoodToNextPopulation(): Int {
        // civ v math, civilization.wikia
        var foodRequired = 15 + 6 * (population - 1) + floor((population - 1).toDouble().pow(1.8))

        foodRequired *= city.civ.gameInfo.speed.modifier

        if (city.civ.isCityState)
            foodRequired *= 1.5f
        if (!city.civ.isHuman())
            foodRequired *= city.civ.gameInfo.getDifficulty().aiCityGrowthModifier
        return foodRequired.toInt()
    }

    /** Take null to mean infinity. */
    fun getNumTurnsToStarvation(): Int? {
        if (!city.isStarving()) return null
        return foodStored / -city.foodForNextTurn() + 1
    }


    /** Take null to mean infinity. */
    fun getNumTurnsToNewPopulation(): Int? {
        if (!city.isGrowing()) return null
        val roundedFoodPerTurn = city.foodForNextTurn().toFloat()
        val remainingFood = getFoodToNextPopulation() - foodStored
        var turnsToGrowth = ceil(remainingFood / roundedFoodPerTurn).toInt()
        if (turnsToGrowth < 1) turnsToGrowth = 1
        return turnsToGrowth
    }


    //endregion

    /** Implements [UniqueParameterType.PopulationFilter][com.unciv.models.ruleset.unique.UniqueParameterType.PopulationFilter] */
    fun getPopulationFilterAmount(filter: String): Int {
        return when (filter) {
            "Specialists" -> getNumberOfSpecialists()
            "Population" -> population
            "Followers of the Majority Religion", "Followers of this Religion" -> city.religion.getFollowersOfMajorityReligion()
            "Unemployed" -> getFreePopulation()
            else -> specialistAllocations[filter]
        }
    }


    fun nextTurn(food: Int) {
        foodStored += food
        if (food < 0)
            city.civ.addNotification("[${city.name}] is starving!",
                city.location, NotificationCategory.Cities, NotificationIcon.Growth, NotificationIcon.Death)
        if (foodStored < 0) {        // starvation!
            if (population > 1) addPopulation(-1)
            foodStored = 0
        }
        val foodNeededToGrow = getFoodToNextPopulation()
        if (foodStored < foodNeededToGrow) return

        // What if the stores are already over foodNeededToGrow but NullifiesGrowth is in effect?
        // We could simply test food==0 - but this way NullifiesStat(food) will still allow growth:
        if (city.getMatchingUniques(UniqueType.NullifiesGrowth).any())
            return

        // Hard block growth when using Avoid Growth, cap stored food
        if (city.avoidGrowth) {
            foodStored = foodNeededToGrow
            return
        }

        // growth!
        foodStored -= foodNeededToGrow
        val percentOfFoodCarriedOver =
            city.getMatchingUniques(UniqueType.CarryOverFood)
                .filter { city.matchesFilter(it.params[1]) }
                .sumOf { it.params[0].toInt() }
                .coerceAtMost(95)  // Try to avoid runaway food gain in mods, just in case
        foodStored += (foodNeededToGrow * percentOfFoodCarriedOver / 100f).toInt()
        addPopulation(1)
        city.shouldReassignPopulation = true
        city.civ.addNotification("[${city.name}] has grown!", city.location,
            NotificationCategory.Cities, NotificationIcon.Growth)
    }

    fun addPopulation(count: Int) {
        val changedAmount = count.coerceAtLeast(1 - population)
        population += changedAmount
        val freePopulation = getFreePopulation()
        if (freePopulation < 0) {
            unassignExtraPopulation()
            city.cityStats.update()
        } else {
            autoAssignPopulation()
        }

        if (city.civ.gameInfo.isReligionEnabled())
            city.religion.updatePressureOnPopulationChange(changedAmount)
    }

    fun setPopulation(count: Int) {
        addPopulation(-population + count)
    }

    /** Only assigns free population */
    internal fun autoAssignPopulation() {
        city.cityStats.update()  // calculate current stats with current assignments
        val freePopulation = getFreePopulation()
        if (freePopulation <= 0) return

        val cityStats = city.cityStats.currentCityStats
        city.currentGPPBonus = city.getGreatPersonPercentageBonus()  // pre-calculate for use in Automation.rankSpecialist
        var specialistFoodBonus = 2f  // See CityStats.calcFoodEaten()
        for (unique in city.getMatchingUniques(UniqueType.FoodConsumptionBySpecialists))
            if (city.matchesFilter(unique.params[1]))
                specialistFoodBonus *= unique.params[0].toPercent()
        specialistFoodBonus = 2f - specialistFoodBonus

        val tilesToEvaluate = city.getWorkableTiles()
            .filter { !it.isBlockaded() }.toList().asSequence()

        val localUniqueCache = LocalUniqueCache()
        // Calculate stats once - but the *ranking of those stats* is dynamic and depends on what the city needs
        val tileStats = tilesToEvaluate
                .filterNot { it.providesYield() }
                .associateWith { it.stats.getTileStats(city, city.civ, localUniqueCache)}

        val maxSpecialists = getMaxSpecialists().asSequence()

        repeat(freePopulation) {
            //evaluate tiles
            val bestTileAndRank = tilesToEvaluate
                .filterNot { it.providesYield() } // Changes with every tile assigned
                .associateWith { Automation.rankStatsForCityWork(tileStats[it]!!, city, false, localUniqueCache) }
                // We need to make sure that we work the same tiles as last turn on a tile
                // so that our workers know to prioritize this tile and don't move to the other tile
                // This was just the easiest way I could think of.
                .maxWithOrNull( compareBy({ it.value }, { it.key.longitude }, { it.key.latitude }))
            val bestTile = bestTileAndRank?.key
            val valueBestTile = bestTileAndRank?.value ?: 0f

            val bestJobAndRank = if (city.manualSpecialists) null
                else maxSpecialists
                    .filter { specialistAllocations[it.key] < it.value }
                    .map { it.key }
                    .associateWith { Automation.rankSpecialist(it, city, localUniqueCache) }
                    .maxByOrNull { it.value }
            val bestJob = bestJobAndRank?.key
            val valueBestSpecialist = bestJobAndRank?.value ?: 0f

            //assign population
            if (valueBestTile > valueBestSpecialist) {
                if (bestTile != null) {
                    city.workedTiles = city.workedTiles.withItem(bestTile.position)
                    cityStats.food += tileStats[bestTile]!!.food
                }
            } else if (bestJob != null) {
                specialistAllocations.add(bestJob, 1)
                cityStats.food += specialistFoodBonus
            }
        }
        city.cityStats.update()
    }

    fun stopWorkingTile(position: Vector2) {
        city.workedTiles = city.workedTiles.withoutItem(position)
        city.lockedTiles.remove(position)
    }

    fun unassignExtraPopulation() {
        for (tile in city.workedTiles.map { city.tileMap[it] }) {
            if (tile.getOwner() != city.civ || tile.getWorkingCity() != city
                    || tile.aerialDistanceTo(city.getCenterTile()) > city.getWorkRange())
                city.population.stopWorkingTile(tile.position)
        }

        // unassign specialists that cannot be (e.g. the city was captured and one of the specialist buildings was destroyed)
        for ((specialistName, maxAmount) in getMaxSpecialists())
            if (specialistAllocations[specialistName] > maxAmount)
                specialistAllocations[specialistName] = maxAmount

        val localUniqueCache = LocalUniqueCache()

        while (getFreePopulation() < 0) {
            //evaluate tiles
            val worstWorkedTile: Tile? = if (city.workedTiles.isEmpty()) null
            else {
                city.workedTiles.asSequence()
                        .map { city.tileMap[it] }
                        .minByOrNull {
                            Automation.rankTileForCityWork(it, city, localUniqueCache)
                            +(if (it.isLocked()) 10 else 0)
                        }!!
            }
            val valueWorstTile = if (worstWorkedTile == null) 0f
            else Automation.rankTileForCityWork(worstWorkedTile, city, localUniqueCache)

            //evaluate specialists
            val worstAutoJob: String? = if (city.manualSpecialists) null else specialistAllocations.keys
                    .minByOrNull { Automation.rankSpecialist(it, city, localUniqueCache) }
            var valueWorstSpecialist = 0f
            if (worstAutoJob != null)
                valueWorstSpecialist = Automation.rankSpecialist(worstAutoJob, city, localUniqueCache)


            // un-assign population
            when {
                worstAutoJob != null && worstWorkedTile != null -> {
                    // choose between removing a specialist and removing a tile
                    if (valueWorstTile < valueWorstSpecialist) {
                        stopWorkingTile(worstWorkedTile.position)
                    }
                    else
                        specialistAllocations.add(worstAutoJob, -1)
                }
                worstAutoJob != null -> specialistAllocations.add(worstAutoJob, -1)
                worstWorkedTile != null -> stopWorkingTile(worstWorkedTile.position)
                else -> {
                    // It happens when "city.manualSpecialists == true"
                    //  and population goes below the number of specialists, e.g. city is razing.
                    // Let's give a chance to do the work automatically at least.
                    val worstJob = specialistAllocations.keys.minByOrNull {
                        Automation.rankSpecialist(it, city, localUniqueCache) }
                        ?: break // sorry, we can do nothing about that
                    specialistAllocations.add(worstJob, -1)
                }
            }
        }
    }

    fun getMaxSpecialists(): Counter<String> {
        val counter = Counter<String>()
        for (building in city.cityConstructions.getBuiltBuildings())
            counter.add(building.newSpecialists())
        return counter
    }
}
