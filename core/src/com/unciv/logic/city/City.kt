package com.unciv.logic.city

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.MultiFilter
import com.unciv.logic.city.managers.CityConquestFunctions
import com.unciv.logic.city.managers.CityEspionageManager
import com.unciv.logic.city.managers.CityExpansionManager
import com.unciv.logic.city.managers.CityPopulationManager
import com.unciv.logic.city.managers.CityReligionManager
import com.unciv.logic.city.managers.SpyFleeReason
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.UnitPromotions
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.GameResource
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.models.stats.SubStat
import yairm210.purity.annotations.Readonly
import java.util.UUID
import kotlin.math.roundToInt

enum class CityFlags {
    WeLoveTheKing,
    ResourceDemand,
    Resistance
}


class City : IsPartOfGameInfoSerialization, INamed {
    @Transient
    lateinit var civ: Civilization

    @Transient
    private lateinit var centerTile: Tile  // cached for better performance

    @Transient
    lateinit var tileMap: TileMap

    @Transient
    lateinit var tilesInRange: HashSet<Tile>
    
    @Transient var state = GameContext.EmptyState

    @Transient
    // This is so that military units can enter the city, even before we decide what to do with it
    var hasJustBeenConquered = false

    var location: Vector2 = Vector2.Zero
    var id: String = UUID.randomUUID().toString()
    override var name: String = ""
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
    
    var resourceStockpiles = Counter<String>()

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
    var shouldReassignPopulation = false  // flag so that on startTurn() we reassign population
    
    var unitShouldUseSavedPromotion = HashMap<String, Boolean>()
    
    var unitToPromotions = HashMap<String, UnitPromotions>()

    /** Neighboring explored cities, in radius of 12 tiles */
    @delegate:Transient
    val neighboringCities: List<City> by lazy { 
        civ.gameInfo.getCities().filter { it != this && it.getCenterTile().isExplored(civ) && it.getCenterTile().aerialDistanceTo(getCenterTile()) <= 12 }.toList()
    }

    private var cityAIFocus: String = CityFocus.NoFocus.name
    @Readonly fun getCityFocus() = CityFocus.entries.firstOrNull { it.name == cityAIFocus } ?: CityFocus.NoFocus
    fun setCityFocus(cityFocus: CityFocus){ cityAIFocus = cityFocus.name }



    var avoidGrowth: Boolean = false
    @Transient var currentGPPBonus: Int = 0  // temporary variable saved for rankSpecialist()

    /** The very first found city is the _original_ capital,
     * while the _current_ capital can be any other city after the original one is captured.
     * It is important to distinguish them since the original cannot be razed and defines the Domination Victory. */
    var isOriginalCapital = false

    /** For We Love the King Day */
    var demandedResource = ""

    internal var flagsCountdown = HashMap<String, Int>()

    /** Persisted connected-to-capital (by any medium) to allow "disconnected" notifications after loading */
    // Unknown only exists to support older saves, so those do not generate spurious connected/disconnected messages.
    // The other names are chosen so serialization is compatible with a Boolean to allow easy replacement in the future.
    @Suppress("EnumEntryName")
    enum class ConnectedToCapitalStatus { Unknown, `false`, `true` }
    var connectedToCapitalStatus = ConnectedToCapitalStatus.Unknown

    @Readonly fun hasDiplomaticMarriage(): Boolean = foundingCiv == ""

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
        toReturn.resourceStockpiles = resourceStockpiles.clone()
        toReturn.isBeingRazed = isBeingRazed
        toReturn.attackedThisTurn = attackedThisTurn
        toReturn.foundingCiv = foundingCiv
        toReturn.turnAcquired = turnAcquired
        toReturn.isPuppet = isPuppet
        toReturn.isOriginalCapital = isOriginalCapital
        toReturn.flagsCountdown.putAll(flagsCountdown)
        toReturn.demandedResource = demandedResource
        toReturn.shouldReassignPopulation = shouldReassignPopulation
        toReturn.cityAIFocus = cityAIFocus
        toReturn.avoidGrowth = avoidGrowth
        toReturn.manualSpecialists = manualSpecialists
        toReturn.connectedToCapitalStatus = connectedToCapitalStatus
        toReturn.unitShouldUseSavedPromotion = unitShouldUseSavedPromotion
        toReturn.unitToPromotions = unitToPromotions
        return toReturn
    }

    @Readonly fun canBombard() = !attackedThisTurn && !isInResistance()
    @Readonly fun getCenterTile(): Tile = centerTile
    @Readonly fun getCenterTileOrNull(): Tile? = if (::centerTile.isInitialized) centerTile else null
    @Readonly fun getTiles(): Sequence<Tile> = tiles.asSequence().map { tileMap[it] }
    @Readonly fun getWorkableTiles() = tilesInRange.asSequence().filter { it.getOwner() == civ }
    @Readonly fun getWorkedTiles(): Sequence<Tile> = workedTiles.asSequence().map { tileMap[it] }
    @Readonly fun isWorked(tile: Tile) = workedTiles.contains(tile.position)

    @Readonly fun isCapital(): Boolean = cityConstructions.builtBuildingUniqueMap.hasUnique(UniqueType.IndicatesCapital, state)
    @Readonly fun isCoastal(): Boolean = centerTile.isCoastalTile()

    @Readonly fun getBombardRange(): Int = civ.gameInfo.ruleset.modOptions.constants.baseCityBombardRange
    @Readonly fun getWorkRange(): Int = civ.gameInfo.ruleset.modOptions.constants.cityWorkRange
    @Readonly fun getExpandRange(): Int = civ.gameInfo.ruleset.modOptions.constants.cityExpandRange

    @Readonly
    fun isConnectedToCapital(@Readonly connectionTypePredicate: (Set<String>) -> Boolean = { true }): Boolean {
        val mediumTypes = civ.cache.citiesConnectedToCapitalToMediums[this] ?: return false
        return connectionTypePredicate(mediumTypes)
    }

    @Readonly fun isGarrisoned() = getGarrison() != null
    @Readonly
    fun getGarrison(): MapUnit? =
            getCenterTile().militaryUnit?.takeIf {
                it.civ == this.civ && it.canGarrison()
            }

    @Readonly fun hasFlag(flag: CityFlags) = flagsCountdown.containsKey(flag.name)
    @Readonly fun getFlag(flag: CityFlags) = flagsCountdown[flag.name]!!

    @Readonly fun isWeLoveTheKingDayActive() = hasFlag(CityFlags.WeLoveTheKing)
    @Readonly fun isInResistance() = hasFlag(CityFlags.Resistance)
    @Readonly
    fun isBlockaded(): Boolean {
        // Coastal cities are blocked if every adjacent water tile is blocked
        if (!isCoastal()) return false
        return getCenterTile().neighbors.filter { it.isWater }.all {
            it.isBlockaded()
        }
    }

    @Readonly fun getRuleset() = civ.gameInfo.ruleset

    @Readonly fun getResourcesGeneratedByCity(civResourceModifiers: Map<String, Float>) = CityResources.getResourcesGeneratedByCity(this, civResourceModifiers)
    @Readonly fun getAvailableResourceAmount(resourceName: String) = CityResources.getAvailableResourceAmount(this, resourceName)

    @Readonly fun isGrowing() = foodForNextTurn() > 0
    @Readonly fun isStarving() = foodForNextTurn() < 0

    @Readonly fun foodForNextTurn() = cityStats.currentCityStats.food.roundToInt()

    @Readonly
    fun containsBuildingUnique(uniqueType: UniqueType, state: GameContext = this.state) =
        cityConstructions.builtBuildingUniqueMap.getMatchingUniques(uniqueType, state).any()

    @Readonly fun getGreatPersonPercentageBonus() = GreatPersonPointsBreakdown.getGreatPersonPercentageBonus(this)
    @Readonly fun getGreatPersonPoints() = GreatPersonPointsBreakdown(this).sum()

    fun gainStockpiledResource(resource: TileResource, amount: Int) {
        if (resource.isCityWide) resourceStockpiles.add(resource.name, amount)
        else civ.resourceStockpiles.add(resource.name, amount)
    }

    fun addStat(stat: Stat, amount: Int) {
        when (stat) {
            Stat.Production -> cityConstructions.addProductionPoints(amount)
            Stat.Food -> population.foodStored += amount
            else -> civ.addStat(stat, amount)
        }
    }

    fun addGameResource(stat: GameResource, amount: Int) {
        if (stat is TileResource) {
            if (!stat.isStockpiled) return
            gainStockpiledResource(stat, amount)
            return
        }
        when (stat) {
            Stat.Production -> cityConstructions.addProductionPoints(amount)
            Stat.Food, SubStat.StoredFood -> population.foodStored += amount
            else -> civ.addGameResource(stat, amount)
        }
    }
    
    @Readonly
    fun getStatReserve(stat: Stat): Int {
        return when (stat) {
            Stat.Production -> cityConstructions.getWorkDone(cityConstructions.getCurrentConstruction().name)
            Stat.Food -> population.foodStored
            else -> civ.getStatReserve(stat)
        }
    }

    @Readonly
    fun hasStatToBuy(stat: Stat, price: Int): Boolean {
        return when {
            civ.gameInfo.gameParameters.godMode -> true
            price == 0 -> true
            else -> getStatReserve(stat) >= price
        }
    }

    @Readonly internal fun getMaxHealth() = 200 + cityConstructions.getBuiltBuildings().sumOf { it.cityHealth }

    @Readonly fun getStrength() = cityConstructions.getBuiltBuildings().sumOf { it.cityStrength }.toFloat()

    // This should probably be configurable
    @Transient
    private val maxAirUnits = 6
    /** Gets max air units that can remain in the city untransported */
    @Readonly fun getMaxAirUnits() = maxAirUnits

    override fun toString() = name // for debug

    @Readonly fun isHolyCity(): Boolean = religion.religionThisIsTheHolyCityOf != null && !religion.isBlockedHolyCity
    @Readonly fun isHolyCityOf(religionName: String?) = isHolyCity() && religion.religionThisIsTheHolyCityOf == religionName

    @Readonly
    fun canBeDestroyed(justCaptured: Boolean = false): Boolean {
        if (civ.gameInfo.gameParameters.noCityRazing) return false

        val allowRazeCapital = civ.gameInfo.ruleset.modOptions.hasUnique(UniqueType.AllowRazeCapital)
        val allowRazeHolyCity = civ.gameInfo.ruleset.modOptions.hasUnique(UniqueType.AllowRazeHolyCity)

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
        state = GameContext(this)
        tilesInRange = getCenterTile().getTilesInDistance(getWorkRange()).toHashSet()
        population.city = this
        expansion.city = this
        expansion.setTransients()
        cityConstructions.city = this
        religion.setTransients(this)
        cityConstructions.setTransients()
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

    /** Apply worked tiles optimization (aka CityFocus) - Expensive!
     *
     *  If the next City.startTurn is soon enough, then use [reassignPopulationDeferred] instead.
     */
    fun reassignPopulation(resetLocked: Boolean = false) {
        if (resetLocked) {
            workedTiles = hashSetOf()
            lockedTiles = hashSetOf()
        } else if(cityAIFocus != CityFocus.Manual.name){
            workedTiles = lockedTiles
        }
        if (!manualSpecialists)
            population.specialistAllocations.clear()
        shouldReassignPopulation = false
        population.autoAssignPopulation()
    }

    /** Apply worked tiles optimization (aka CityFocus) -
     *  immediately for a human player whoes turn it is (interactive),
     *  or deferred to the next startTurn while nextTurn is running (for AI)
     *  @see shouldReassignPopulation
     */
    fun reassignPopulationDeferred() {
        // TODO - is this the best (or even correct) way to detect "interactive" UI calls?
        if (GUI.isMyTurn() && GUI.getViewingPlayer() == civ) reassignPopulation()
        else shouldReassignPopulation = true
    }

    fun destroyCity(overrideSafeties: Boolean = false) {
        // Original capitals and holy cities cannot be destroyed,
        // unless, of course, they are captured by a one-city-challenger.
        if (!canBeDestroyed() && !overrideSafeties) return

        // Destroy planes stationed in city
        for (airUnit in getCenterTile().airUnits.toList()) airUnit.destroy()

        // The relinquish ownership MUST come before removing the city,
        // because it updates the city stats which assumes there is a capital, so if you remove the capital it crashes
        for (tile in getTiles()) {
            expansion.relinquishOwnership(tile)
        }

        // Move the capital if destroyed (by a nuke or by razing)
        // Must be before removing existing capital because we may be annexing a puppet which means city stats update - see #8337
        if (isCapital()) civ.moveCapitalToNextLargest(null)

        civ.cities = civ.cities.toMutableList().apply { remove(this@City) }
        
        if (getRuleset().tileImprovements.containsKey("City ruins"))
            getCenterTile().setImprovement("City ruins")

        // Edge case! What if a water unit is in a city, and you raze the city?
        // Well, the water unit has to return to the water!
        for (unit in getCenterTile().getUnits().toList()) {
            if (!unit.movement.canPassThrough(getCenterTile()))
                unit.movement.teleportToClosestMoveableTile()
        }

        espionage.removeAllPresentSpies(SpyFleeReason.CityDestroyed)

        // Update proximity rankings for all civs
        for (otherCiv in civ.gameInfo.getAliveMajorCivs()) {
            civ.updateProximity(otherCiv,
                otherCiv.updateProximity(civ))
        }
        for (otherCiv in civ.gameInfo.getAliveCityStates()) {
            civ.updateProximity(otherCiv,
                otherCiv.updateProximity(civ))
        }
    }

    fun annexCity() = CityConquestFunctions(this).annexCity()

    /** This happens when we either puppet OR annex, basically whenever we conquer a city and don't liberate it */
    fun puppetCity(conqueringCiv: Civilization) =
        CityConquestFunctions(this).puppetCity(conqueringCiv)

    /* Liberating is returning a city to its founder - makes you LOSE warmongering points **/
    fun liberateCity(conqueringCiv: Civilization) =
        CityConquestFunctions(this).liberateCity(conqueringCiv)

    fun moveToCiv(newCivInfo: Civilization) =
        CityConquestFunctions(this).moveToCiv(newCivInfo)

    internal fun tryUpdateRoadStatus() {
        val requiredRoad = when{
            getRuleset().railroadImprovement?.let { it.techRequired == null || it.techRequired in civ.tech.techsResearched } == true -> RoadStatus.Railroad
            getRuleset().roadImprovement?.let { it.techRequired == null || it.techRequired in civ.tech.techsResearched } == true -> RoadStatus.Road
            else -> RoadStatus.None
        }
        getCenterTile().setRoadStatus(requiredRoad, civ)
    }

    @Readonly
    fun getGoldForSellingBuilding(buildingName: String) =
        getRuleset().buildings[buildingName]!!.cost / 10

    fun sellBuilding(buildingName: String) {
        sellBuilding(getRuleset().buildings[buildingName]!!)
    }

    fun sellBuilding(building: Building) {
        cityConstructions.removeBuilding(building)
        civ.addGold(getGoldForSellingBuilding(building.name))
        hasSoldBuildingThisTurn = true

        population.unassignExtraPopulation() // If the building provided specialists, release them to other work
        population.autoAssignPopulation() // also updates city stats
        civ.cache.updateCivResources() // this building could be a resource-requiring one
    }
    
    @Readonly
    fun canPlaceNewUnit(construction: BaseUnit): Boolean {
        val tile = getCenterTile()
        return when {
            construction.isCivilian() -> tile.civilianUnit == null
            construction.movesLikeAirUnits -> return true // Dealt with in MapUnit.getRejectionReasons
            else -> tile.militaryUnit == null
        }
    }

    /** Implements [UniqueParameterType.CityFilter][com.unciv.models.ruleset.unique.UniqueParameterType.CityFilter] */
    @Readonly
    fun matchesFilter(filter: String, viewingCiv: Civilization? = civ, multiFilter: Boolean = true): Boolean {
        return if (multiFilter)
            MultiFilter.multiFilter(filter, { matchesSingleFilter(it, viewingCiv) })
        else matchesSingleFilter(filter, viewingCiv)
    }

    @Readonly
    private fun matchesSingleFilter(filter: String, viewingCiv: Civilization? = civ): Boolean {
        return when (filter) {
            "in this city" -> true // Filtered by the way uniques are found
            "in all cities" -> true
            in Constants.all -> true
            "in your cities", "Your" -> viewingCiv == civ
            "in all coastal cities", "Coastal" -> isCoastal()
            "in capital", "Capital" -> isCapital()
            "in all non-occupied cities", "Non-occupied" -> !cityStats.hasExtraAnnexUnhappiness() || isPuppet
            "in all cities with a world wonder" -> cityConstructions.getBuiltBuildings()
                .any { it.isWonder }
            "in all cities connected to capital" -> isConnectedToCapital()
            "in all cities with a garrison", "Garrisoned" -> isGarrisoned()
            "in all cities in which the majority religion is a major religion" ->
                religion.getMajorityReligionName() != null
                && religion.getMajorityReligion()!!.isMajorReligion()
            "in all cities in which the majority religion is an enhanced religion" ->
                religion.getMajorityReligionName() != null
                && religion.getMajorityReligion()!!.isEnhancedReligion()
            "in non-enemy foreign cities" ->
                viewingCiv != null && viewingCiv != civ
                && !civ.isAtWarWith(viewingCiv)
            "in enemy cities", "Enemy" -> civ.isAtWarWith(viewingCiv ?: civ)
            "in foreign cities", "Foreign" -> viewingCiv != null && viewingCiv != civ
            "in annexed cities", "Annexed" -> foundingCiv != civ.civName && !isPuppet
            "in puppeted cities", "Puppeted" -> isPuppet
            "in resisting cities", "Resisting" -> isInResistance()
            "in cities being razed", "Razing" -> isBeingRazed
            "in holy cities", "Holy" -> isHolyCity()
            "in City-State cities" -> civ.isCityState
            // This is only used in communication to the user indicating that only in cities with this
            // religion a unique is active. However, since religion uniques only come from the city itself,
            // this will always be true when checked.
            "in cities following this religion" -> true
            "in cities following our religion" -> viewingCiv?.religionManager?.religion == religion.getMajorityReligion()
            else -> civ.matchesFilter(filter, state, false)
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
    @Readonly
    fun getMatchingUniques(
        uniqueType: UniqueType,
        gameContext: GameContext = state,
        includeCivUniques: Boolean = true
    ): Sequence<Unique> {
        return if (includeCivUniques)
            civ.getMatchingUniques(uniqueType, gameContext) +
                getLocalMatchingUniques(uniqueType, gameContext)
        else (
            cityConstructions.builtBuildingUniqueMap.getUniques(uniqueType)
                + religion.getUniques(uniqueType)
            ).filter {
                !it.isTimedTriggerable && it.conditionalsApply(gameContext)
            }.flatMap { it.getMultiplied(gameContext) }
    }

    // Uniques special to this city
    @Readonly
    fun getLocalMatchingUniques(uniqueType: UniqueType, gameContext: GameContext = state): Sequence<Unique> {
        val uniques = cityConstructions.builtBuildingUniqueMap.getUniques(uniqueType).filter { it.isLocalEffect } +
            religion.getUniques(uniqueType)
        return uniques.filter { !it.isTimedTriggerable && it.conditionalsApply(gameContext) }
                .flatMap { it.getMultiplied(gameContext) }
    }

    // Uniques coming from this city, but that should be provided globally
    @Readonly
    fun getMatchingUniquesWithNonLocalEffects(uniqueType: UniqueType, gameContext: GameContext = state): Sequence<Unique> {
        val uniques = cityConstructions.builtBuildingUniqueMap.getUniques(uniqueType)
        // Memory performance showed that this function was very memory intensive, thus we only create the filter if needed
        return if (uniques.any()) uniques.filter { !it.isLocalEffect && !it.isTimedTriggerable
            && it.conditionalsApply(gameContext) }.flatMap { it.getMultiplied(gameContext) }
        else uniques
    }

    //endregion
}
