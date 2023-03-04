package com.unciv.logic.city

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.managers.CityEspionageManager
import com.unciv.logic.city.managers.CityExpansionManager
import com.unciv.logic.city.managers.CityInfoConquestFunctions
import com.unciv.logic.city.managers.CityPopulationManager
import com.unciv.logic.city.managers.CityReligionManager
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import java.util.*
import kotlin.math.roundToInt

enum class CityFlags {
    WeLoveTheKing,
    ResourceDemand,
    Resistance
}


class City : IsPartOfGameInfoSerialization {
    @Suppress("JoinDeclarationAndAssignment")
    @Transient
    lateinit var civ: Civilization

    @Transient
    private lateinit var centerTile: Tile  // cached for better performance

    @Transient
    val range = 2

    @Transient
    lateinit var tileMap: TileMap

    @Transient
    lateinit var tilesInRange: HashSet<Tile>

    @Transient
    // This is so that military units can enter the city, even before we decide what to do with it
    var hasJustBeenConquered = false

    var location: Vector2 = Vector2.Zero
    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var foundingCiv = ""
    // This is so that cities in resistance that are recaptured aren't in resistance anymore
    var previousOwner = ""
    var turnAcquired = 0
    var health = 200


    var population = CityPopulationManager()
    var cityConstructions = CityConstructions()
    var expansion = CityExpansionManager()
    var religion = CityReligionManager()
    var espionage = CityEspionageManager()

    @Transient  // CityStats has no serializable fields
    var cityStats = CityStats(this)

    /** All tiles that this city controls */
    var tiles = HashSet<Vector2>()

    /** Tiles that have population assigned to them */
    var workedTiles = HashSet<Vector2>()

    /** Tiles that the population in them won't be reassigned */
    var lockedTiles = HashSet<Vector2>()
    var manualSpecialists = false
    var isBeingRazed = false
    var attackedThisTurn = false
    var hasSoldBuildingThisTurn = false
    var isPuppet = false
    var updateCitizens = false  // flag so that on startTurn() the Governor reassigns Citizens
    var cityAIFocus: CityFocus = CityFocus.NoFocus
    var avoidGrowth: Boolean = false
    @Transient var currentGPPBonus: Int = 0  // temporary variable saved for rankSpecialist()

    /** The very first found city is the _original_ capital,
     * while the _current_ capital can be any other city after the original one is captured.
     * It is important to distinguish them since the original cannot be razed and defines the Domination Victory. */
    var isOriginalCapital = false

    /** For We Love the King Day */
    var demandedResource = ""

    internal var flagsCountdown = HashMap<String, Int>()

    fun hasDiplomaticMarriage(): Boolean = foundingCiv == ""

    //region pure functions
    fun clone(): City {
        val toReturn = City()
        toReturn.location = location
        toReturn.id = id
        toReturn.name = name
        toReturn.health = health
        toReturn.population = population.clone()
        toReturn.cityConstructions = cityConstructions.clone()
        toReturn.expansion = expansion.clone()
        toReturn.religion = religion.clone()
        toReturn.tiles = tiles
        toReturn.workedTiles = workedTiles
        toReturn.lockedTiles = lockedTiles
        toReturn.isBeingRazed = isBeingRazed
        toReturn.attackedThisTurn = attackedThisTurn
        toReturn.foundingCiv = foundingCiv
        toReturn.turnAcquired = turnAcquired
        toReturn.isPuppet = isPuppet
        toReturn.isOriginalCapital = isOriginalCapital
        toReturn.flagsCountdown.putAll(flagsCountdown)
        toReturn.demandedResource = demandedResource
        toReturn.updateCitizens = updateCitizens
        toReturn.cityAIFocus = cityAIFocus
        toReturn.avoidGrowth = avoidGrowth
        toReturn.manualSpecialists = manualSpecialists
        return toReturn
    }

    fun canBombard() = !attackedThisTurn && !isInResistance()
    fun getCenterTile(): Tile = centerTile
    fun getTiles(): Sequence<Tile> = tiles.asSequence().map { tileMap[it] }
    fun getWorkableTiles() = tilesInRange.asSequence().filter { it.getOwner() == civ }
    fun isWorked(tile: Tile) = workedTiles.contains(tile.position)

    fun isCapital(): Boolean = cityConstructions.getBuiltBuildings().any { it.hasUnique(UniqueType.IndicatesCapital) }
    fun isCoastal(): Boolean = centerTile.isCoastalTile()

    fun capitalCityIndicator(): String {
        val indicatorBuildings = getRuleset().buildings.values
            .asSequence()
            .filter { it.hasUnique(UniqueType.IndicatesCapital) }

        val civSpecificBuilding = indicatorBuildings.firstOrNull { it.uniqueTo == civ.civName }
        return civSpecificBuilding?.name ?: indicatorBuildings.first().name
    }

    fun isConnectedToCapital(connectionTypePredicate: (Set<String>) -> Boolean = { true }): Boolean {
        val mediumTypes = civ.cache.citiesConnectedToCapitalToMediums[this] ?: return false
        return connectionTypePredicate(mediumTypes)
    }

    fun hasFlag(flag: CityFlags) = flagsCountdown.containsKey(flag.name)
    fun getFlag(flag: CityFlags) = flagsCountdown[flag.name]!!

    fun isWeLoveTheKingDayActive() = hasFlag(CityFlags.WeLoveTheKing)
    fun isInResistance() = hasFlag(CityFlags.Resistance)
    fun isBlockaded(): Boolean {

        // Landlocked cities are not blockaded
        if (!isCoastal())
            return false

        // Coastal cities are blocked if every adjacent water tile is blocked
        for (tile in getCenterTile().neighbors) {

            // Consider only water tiles
            if (!tile.isWater)
                continue

            // One unblocked tile breaks whole city blockade
            if (!tile.isBlockaded())
                return false
        }

        // All tiles are blocked
        return true
    }

    fun getRuleset() = civ.gameInfo.ruleset

    fun getCityResources(): ResourceSupplyList {
        val cityResources = ResourceSupplyList()

        for (tileInfo in getTiles().filter { it.resource != null }) {
            val resource = tileInfo.tileResource
            val amount = getTileResourceAmount(tileInfo)
            if (amount > 0) cityResources.add(resource, "Tiles", amount)
        }

        for (tileInfo in getTiles()) {
            val stateForConditionals = StateForConditionals(civ, this, tile = tileInfo)
            if (tileInfo.getUnpillagedImprovement() == null) continue
            val tileImprovement = tileInfo.getUnpillagedTileImprovement()
            for (unique in tileImprovement!!.getMatchingUniques(UniqueType.ProvidesResources, stateForConditionals)) {
                val resource = getRuleset().tileResources[unique.params[1]] ?: continue
                cityResources.add(
                    resource, "Improvements",
                    unique.params[0].toInt()
                )
            }
            for (unique in tileImprovement.getMatchingUniques(UniqueType.ConsumesResources, stateForConditionals)) {
                val resource = getRuleset().tileResources[unique.params[1]] ?: continue
                cityResources.add(
                    resource, "Improvements",
                    -1 * unique.params[0].toInt()
                )
            }
        }

        val freeBuildings = civ.civConstructions.getFreeBuildings(id)
        for (building in cityConstructions.getBuiltBuildings()) {
            // Free buildings cost no resources
            if (building.name in freeBuildings) continue
            cityResources.subtractResourceRequirements(building.getResourceRequirements(), getRuleset(), "Buildings")
        }

        for (unique in getLocalMatchingUniques(UniqueType.ProvidesResources, StateForConditionals(civ, this))) { // E.G "Provides [1] [Iron]"
            val resource = getRuleset().tileResources[unique.params[1]]
                ?: continue
            cityResources.add(
                resource, "Buildings",
                unique.params[0].toInt()
            )
        }

        if (civ.isCityState() && isCapital() && civ.cityStateResource != null) {
            cityResources.add(
                getRuleset().tileResources[civ.cityStateResource]!!,
                "Mercantile City-State"
            )
        }

        return cityResources
    }

    fun getTileResourceAmount(tile: Tile): Int {
        if (tile.resource == null) return 0
        val resource = tile.tileResource
        if (resource.revealedBy != null && !civ.tech.isResearched(resource.revealedBy!!)) return 0

        // Even if the improvement exists (we conquered an enemy city or somesuch) or we have a city on it, we won't get the resource until the correct tech is researched
        if (resource.getImprovements().any()) {
            if (!resource.getImprovements().any { improvementString ->
                val improvement = getRuleset().tileImprovements[improvementString]!!
                improvement.techRequired == null || civ.tech.isResearched(improvement.techRequired!!)
            }) return 0
        }

        if ((tile.getUnpillagedImprovement() != null && resource.isImprovedBy(tile.improvement!!)) || tile.isCityCenter()
            // Per https://gaming.stackexchange.com/questions/53155/do-manufactories-and-customs-houses-sacrifice-the-strategic-or-luxury-resources
            || resource.resourceType == ResourceType.Strategic && tile.containsUnpillagedGreatImprovement()
        ) {
            var amountToAdd = if (resource.resourceType == ResourceType.Strategic) tile.resourceAmount
                else 1
            if (resource.resourceType == ResourceType.Luxury
                && containsBuildingUnique(UniqueType.ProvidesExtraLuxuryFromCityResources)
            )
                amountToAdd += 1

            return amountToAdd
        }
        return 0
    }

    fun isGrowing() = foodForNextTurn() > 0
    fun isStarving() = foodForNextTurn() < 0

    fun foodForNextTurn() = cityStats.currentCityStats.food.roundToInt()


    fun containsBuildingUnique(uniqueType: UniqueType) =
        cityConstructions.getBuiltBuildings().flatMap { it.uniqueObjects }.any { it.isOfType(uniqueType) }

    fun getGreatPersonPercentageBonus(): Int{
        var allGppPercentageBonus = 0
        for (unique in getMatchingUniques(UniqueType.GreatPersonPointPercentage)) {
            if (!matchesFilter(unique.params[1])) continue
            allGppPercentageBonus += unique.params[0].toInt()
        }

        // Sweden UP
        for (otherCiv in civ.getKnownCivs()) {
            if (!civ.getDiplomacyManager(otherCiv).hasFlag(DiplomacyFlags.DeclarationOfFriendship))
                continue

            for (ourUnique in civ.getMatchingUniques(UniqueType.GreatPersonBoostWithFriendship))
                allGppPercentageBonus += ourUnique.params[0].toInt()
            for (theirUnique in otherCiv.getMatchingUniques(UniqueType.GreatPersonBoostWithFriendship))
                allGppPercentageBonus += theirUnique.params[0].toInt()
        }
        return allGppPercentageBonus
    }

    fun getGreatPersonPointsForNextTurn(): HashMap<String, Counter<String>> {
        val sourceToGPP = HashMap<String, Counter<String>>()

        val specialistsCounter = Counter<String>()
        for ((specialistName, amount) in population.getNewSpecialists())
            if (getRuleset().specialists.containsKey(specialistName)) { // To solve problems in total remake mods
                val specialist = getRuleset().specialists[specialistName]!!
                specialistsCounter.add(specialist.greatPersonPoints.times(amount))
            }
        sourceToGPP["Specialists"] = specialistsCounter

        val buildingsCounter = Counter<String>()
        for (building in cityConstructions.getBuiltBuildings())
            buildingsCounter.add(building.greatPersonPoints)
        sourceToGPP["Buildings"] = buildingsCounter

        val stateForConditionals = StateForConditionals(civInfo = civ, city = this)
        for ((_, gppCounter) in sourceToGPP) {
            for (unique in civ.getMatchingUniques(UniqueType.GreatPersonEarnedFaster, stateForConditionals)) {
                val unitName = unique.params[0]
                if (!gppCounter.containsKey(unitName)) continue
                gppCounter.add(unitName, gppCounter[unitName]!! * unique.params[1].toInt() / 100)
            }

            val allGppPercentageBonus = getGreatPersonPercentageBonus()

            for (unitName in gppCounter.keys)
                gppCounter.add(unitName, gppCounter[unitName]!! * allGppPercentageBonus / 100)
        }

        return sourceToGPP
    }

    fun getGreatPersonPoints(): Counter<String> {
        val gppCounter = Counter<String>()
        for (entry in getGreatPersonPointsForNextTurn().values)
            gppCounter.add(entry)
        return gppCounter
    }

    fun addStat(stat: Stat, amount: Int) {
        when (stat) {
            Stat.Production -> cityConstructions.addProductionPoints(amount)
            Stat.Food -> population.foodStored += amount
            else -> civ.addStat(stat, amount)
        }
    }

    fun getStatReserve(stat: Stat): Int {
        return when (stat) {
            Stat.Food -> population.foodStored
            else -> civ.getStatReserve(stat)
        }
    }

    internal fun getMaxHealth() =
        200 + cityConstructions.getBuiltBuildings().sumOf { it.cityHealth }

    override fun toString() = name // for debug

    fun isHolyCity(): Boolean = religion.religionThisIsTheHolyCityOf != null && !religion.isBlockedHolyCity
    fun isHolyCityOf(religionName: String?) = isHolyCity() && religion.religionThisIsTheHolyCityOf == religionName

    fun canBeDestroyed(justCaptured: Boolean = false): Boolean {
        if (civ.gameInfo.gameParameters.noCityRazing) return false

        val allowRazeCapital = civ.gameInfo.ruleset.modOptions.uniques.contains(ModOptionsConstants.allowRazeCapital)
        val allowRazeHolyCity = civ.gameInfo.ruleset.modOptions.uniques.contains(ModOptionsConstants.allowRazeHolyCity)

        if (isOriginalCapital && !allowRazeCapital) return false
        if (isHolyCity() && !allowRazeHolyCity) return false
        if (isCapital() && !justCaptured && !allowRazeCapital) return false

        return true
    }

    //endregion

    //region state-changing functions
    fun setTransients(civInfo: Civilization) {
        this.civ = civInfo
        tileMap = civInfo.gameInfo.tileMap
        centerTile = tileMap[location]
        tilesInRange = getCenterTile().getTilesInDistance(3).toHashSet()
        population.city = this
        expansion.city = this
        expansion.setTransients()
        cityConstructions.city = this
        cityConstructions.setTransients()
        religion.setTransients(this)
        espionage.setTransients(this)
    }

    fun setFlag(flag: CityFlags, amount: Int) {
        flagsCountdown[flag.name] = amount
    }

    fun removeFlag(flag: CityFlags) {
        flagsCountdown.remove(flag.name)
    }

    fun resetWLTKD() {
        // Removes the flags for we love the king & resource demand
        // The resource demand flag will automatically be readded with 15 turns remaining, see startTurn()
        removeFlag(CityFlags.WeLoveTheKing)
        removeFlag(CityFlags.ResourceDemand)
        demandedResource = ""
    }

    // Reassign all Specialists and Unlock all tiles
    // Mainly for automated cities, Puppets, just captured
    fun reassignAllPopulation() {
        manualSpecialists = false
        reassignPopulation(resetLocked = true)
    }

    fun reassignPopulation(resetLocked: Boolean = false) {
        if (resetLocked) {
            workedTiles = hashSetOf()
            lockedTiles = hashSetOf()
        } else {
            workedTiles = lockedTiles
        }
        if (!manualSpecialists)
            population.specialistAllocations.clear()
        population.autoAssignPopulation()
    }


    fun destroyCity(overrideSafeties: Boolean = false) {
        // Original capitals and holy cities cannot be destroyed,
        // unless, of course, they are captured by a one-city-challenger.
        if (!canBeDestroyed() && !overrideSafeties) return

        for (airUnit in getCenterTile().airUnits.toList()) airUnit.destroy() //Destroy planes stationed in city

        // The relinquish ownership MUST come before removing the city,
        // because it updates the city stats which assumes there is a capital, so if you remove the capital it crashes
        for (tile in getTiles()) {
            expansion.relinquishOwnership(tile)
        }

        // Move the capital if destroyed (by a nuke or by razing)
        // Must be before removing existing capital because we may be annexing a puppet which means city stats update - see #8337
        if (isCapital() && civ.cities.size > 1) {
            civ.moveCapitalToNextLargest()
        }

        civ.cities = civ.cities.toMutableList().apply { remove(this@City) }
        getCenterTile().changeImprovement("City ruins")

        // Edge case! What if a water unit is in a city, and you raze the city?
        // Well, the water unit has to return to the water!
        for (unit in getCenterTile().getUnits().toList()) {
            if (!unit.movement.canPassThrough(getCenterTile()))
                unit.movement.teleportToClosestMoveableTile()
        }


        // Update proximity rankings for all civs
        for (otherCiv in civ.gameInfo.getAliveMajorCivs()) {
            civ.updateProximity(otherCiv,
                otherCiv.updateProximity(civ))
        }
        for (otherCiv in civ.gameInfo.getAliveCityStates()) {
            civ.updateProximity(otherCiv,
                otherCiv.updateProximity(civ))
        }

        civ.gameInfo.cityDistances.setDirty()
    }

    fun annexCity() = CityInfoConquestFunctions(this).annexCity()

    /** This happens when we either puppet OR annex, basically whenever we conquer a city and don't liberate it */
    fun puppetCity(conqueringCiv: Civilization) =
        CityInfoConquestFunctions(this).puppetCity(conqueringCiv)

    /* Liberating is returning a city to its founder - makes you LOSE warmongering points **/
    fun liberateCity(conqueringCiv: Civilization) =
        CityInfoConquestFunctions(this).liberateCity(conqueringCiv)

    fun moveToCiv(newCivInfo: Civilization) =
        CityInfoConquestFunctions(this).moveToCiv(newCivInfo)

    internal fun tryUpdateRoadStatus() {
        if (getCenterTile().roadStatus == RoadStatus.None) {
            val roadImprovement = RoadStatus.Road.improvement(getRuleset())
            if (roadImprovement != null && roadImprovement.techRequired in civ.tech.techsResearched)
                getCenterTile().roadStatus = RoadStatus.Road
        } else if (getCenterTile().roadStatus != RoadStatus.Railroad) {
            val railroadImprovement = RoadStatus.Railroad.improvement(getRuleset())
            if (railroadImprovement != null && railroadImprovement.techRequired in civ.tech.techsResearched)
                getCenterTile().roadStatus = RoadStatus.Railroad
        }
    }

    fun getGoldForSellingBuilding(buildingName: String) =
        getRuleset().buildings[buildingName]!!.cost / 10

    fun sellBuilding(buildingName: String) {
        cityConstructions.removeBuilding(buildingName)
        civ.addGold(getGoldForSellingBuilding(buildingName))
        hasSoldBuildingThisTurn = true

        population.unassignExtraPopulation() // If the building provided specialists, release them to other work
        population.autoAssignPopulation()
        cityStats.update()
        civ.cache.updateCivResources() // this building could be a resource-requiring one
    }

    fun canPlaceNewUnit(construction: BaseUnit): Boolean {
        val tile = getCenterTile()
        return when {
            construction.isCivilian() -> tile.civilianUnit == null
            construction.movesLikeAirUnits() -> tile.airUnits.count { !it.isTransported } < 6
            else -> tile.militaryUnit == null
        }
    }

    /** Implements [UniqueParameterType.CityFilter][com.unciv.models.ruleset.unique.UniqueParameterType.CityFilter] */
    fun matchesFilter(filter: String, viewingCiv: Civilization = civ): Boolean {
        return when (filter) {
            "in this city" -> true
            "in all cities" -> true // Filtered by the way uniques are found
            "in other cities" -> true // Filtered by the way uniques are found
            "in all coastal cities" -> isCoastal()
            "in capital" -> isCapital()
            "in all non-occupied cities" -> !cityStats.hasExtraAnnexUnhappiness() || isPuppet
            "in all cities with a world wonder" -> cityConstructions.getBuiltBuildings()
                .any { it.isWonder }
            "in all cities connected to capital" -> isConnectedToCapital()
            "in all cities with a garrison" -> getCenterTile().militaryUnit != null
            "in all cities in which the majority religion is a major religion" ->
                religion.getMajorityReligionName() != null
                && religion.getMajorityReligion()!!.isMajorReligion()
            "in all cities in which the majority religion is an enhanced religion" ->
                religion.getMajorityReligionName() != null
                && religion.getMajorityReligion()!!.isEnhancedReligion()
            "in non-enemy foreign cities" ->
                viewingCiv != civ
                && !civ.isAtWarWith(viewingCiv)
            "in foreign cities" -> viewingCiv != civ
            "in annexed cities" -> foundingCiv != civ.civName && !isPuppet
            "in puppeted cities" -> isPuppet
            "in holy cities" -> isHolyCity()
            "in City-State cities" -> civ.isCityState()
            // This is only used in communication to the user indicating that only in cities with this
            // religion a unique is active. However, since religion uniques only come from the city itself,
            // this will always be true when checked.
            "in cities following this religion" -> true
            else -> false
        }
    }

    // So everywhere in the codebase there were continuous calls to either
    // `cityConstructions.builtBuildingUniqueMap.getUniques()` or `cityConstructions.builtBuildingMap.getAllUniques()`,
    // which was fine as long as those were the only uniques that cities could provide.
    // However, with the introduction of religion, cities might also get uniques from the religion the city follows.
    // Adding both calls to `builtBuildingsUniqueMap` and `Religion` every time is not really modular and also ugly, so something had to be done.
    // Looking at all the use cases, the following functions were written to handle all findMatchingUniques() problems.
    // Sadly, due to the large disparity between use cases, there needed to be lots of functions.

    // Finds matching uniques provided from both local and non-local sources.
    fun getMatchingUniques(
        uniqueType: UniqueType,
        stateForConditionals: StateForConditionals = StateForConditionals(civ, this)
    ): Sequence<Unique> {
        return civ.getMatchingUniques(uniqueType, stateForConditionals, this) +
                getLocalMatchingUniques(uniqueType, stateForConditionals)
    }

    fun getLocalMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals = StateForConditionals(civ, this)): Sequence<Unique> {
        return (
            cityConstructions.builtBuildingUniqueMap.getUniques(uniqueType).filter { !it.isAntiLocalEffect }
            + religion.getUniques().filter { it.isOfType(uniqueType) }
        ).filter {
            it.conditionalsApply(stateForConditionals)
        }
    }


    fun getMatchingUniquesWithNonLocalEffects(uniqueType: UniqueType, stateForConditionals: StateForConditionals): Sequence<Unique> {
        val uniques = cityConstructions.builtBuildingUniqueMap.getUniques(uniqueType)
        // Memory performance showed that this function was very memory intensive, thus we only create the filter if needed
        return if (uniques.any()) uniques.filter { !it.isLocalEffect && it.conditionalsApply(stateForConditionals) }
        else uniques
    }

    //endregion
}
