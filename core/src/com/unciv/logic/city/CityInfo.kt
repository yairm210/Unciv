package com.unciv.logic.city

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.Proximity
import com.unciv.logic.civilization.ReligionState
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class CityFlags {
    WeLoveTheKing,
    ResourceDemand,
    Resistance
}

class CityInfo {
    @Suppress("JoinDeclarationAndAssignment")
    @Transient
    lateinit var civInfo: CivilizationInfo

    @Transient
    private lateinit var centerTileInfo: TileInfo  // cached for better performance

    @Transient
    val range = 2

    @Transient
    lateinit var tileMap: TileMap

    @Transient
    lateinit var tilesInRange: HashSet<TileInfo>

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

    var religion = CityInfoReligionManager()
    var population = PopulationManager()
    var cityConstructions = CityConstructions()
    var expansion = CityExpansionManager()

    @Transient  // CityStats has no serializable fields
    var cityStats = CityStats(this)

    /** All tiles that this city controls */
    var tiles = HashSet<Vector2>()

    /** Tiles that have population assigned to them */
    var workedTiles = HashSet<Vector2>()

    /** Tiles that the population in them won't be reassigned */
    var lockedTiles = HashSet<Vector2>()
    var isBeingRazed = false
    var attackedThisTurn = false
    var hasSoldBuildingThisTurn = false
    var isPuppet = false

    /** The very first found city is the _original_ capital,
     * while the _current_ capital can be any other city after the original one is captured.
     * It is important to distinguish them since the original cannot be razed and defines the Domination Victory. */
    var isOriginalCapital = false

    /** For We Love the King Day */
    var demandedResource = ""

    private var flagsCountdown = HashMap<String, Int>()

    constructor()   // for json parsing, we need to have a default constructor
    constructor(civInfo: CivilizationInfo, cityLocation: Vector2) {  // new city!
        this.civInfo = civInfo
        foundingCiv = civInfo.civName
        turnAcquired = civInfo.gameInfo.turns
        location = cityLocation
        setTransients()

        setNewCityName(civInfo)

        isOriginalCapital = civInfo.citiesCreated == 0
        if (isOriginalCapital) civInfo.hasEverOwnedOriginalCapital = true
        civInfo.citiesCreated++

        civInfo.cities = civInfo.cities.toMutableList().apply { add(this@CityInfo) }

        val startingEra = civInfo.gameInfo.gameParameters.startingEra

        addStartingBuildings(civInfo, startingEra)

        expansion.reset()

        tryUpdateRoadStatus()

        val tile = getCenterTile()
        for (terrainFeature in tile.terrainFeatures.filter {
            getRuleset().tileImprovements.containsKey(
                "Remove $it"
            )
        })
            tile.terrainFeatures.remove(terrainFeature)

        tile.improvement = null
        tile.improvementInProgress = null

        val ruleset = civInfo.gameInfo.ruleSet
        workedTiles = hashSetOf() //reassign 1st working tile

        population.setPopulation(ruleset.eras[startingEra]!!.settlerPopulation)

        if (civInfo.religionManager.religionState == ReligionState.Pantheon) {
            religion.addPressure(
                civInfo.religionManager.religion!!.name,
                200 * population.population
            )
        }

        population.autoAssignPopulation()
        cityStats.update()

        // Update proximity rankings for all civs
        for (otherCiv in civInfo.gameInfo.getAliveMajorCivs()) {
            if (civInfo.getProximity(otherCiv) != Proximity.Neighbors) // unless already neighbors
                civInfo.updateProximity(otherCiv,
                otherCiv.updateProximity(civInfo))
        }
        for (otherCiv in civInfo.gameInfo.getAliveCityStates()) {
            if (civInfo.getProximity(otherCiv) != Proximity.Neighbors) // unless already neighbors
                civInfo.updateProximity(otherCiv,
                    otherCiv.updateProximity(civInfo))
        }

        triggerCitiesSettledNearOtherCiv()

        // Seed resource demand countdown
        setFlag(CityFlags.ResourceDemand,
                (if (isOriginalCapital) 25 else 15) + Random().nextInt(10))
    }

    private fun addStartingBuildings(civInfo: CivilizationInfo, startingEra: String) {
        val ruleset = civInfo.gameInfo.ruleSet
        if (civInfo.cities.size == 1) cityConstructions.addBuilding(capitalCityIndicator())

        // Add buildings and pop we get from starting in this era
        for (buildingName in ruleset.eras[startingEra]!!.settlerBuildings) {
            val building = ruleset.buildings[buildingName] ?: continue
            val uniqueBuilding = civInfo.getEquivalentBuilding(building)
            if (uniqueBuilding.isBuildable(cityConstructions))
                cityConstructions.addBuilding(uniqueBuilding.name)
        }

        civInfo.civConstructions.tryAddFreeBuildings()
        cityConstructions.addFreeBuildings()
    }

    private fun setNewCityName(civInfo: CivilizationInfo) {
        val nationCities = civInfo.nation.cities
        val cityNameIndex = civInfo.citiesCreated % nationCities.size
        val cityName = nationCities[cityNameIndex]

        val cityNameRounds = civInfo.citiesCreated / nationCities.size
        if (cityNameRounds > 0 && civInfo.hasUnique("\"Borrows\" city names from other civilizations in the game")) {
            name = borrowCityName()
            return
        }
        val cityNamePrefix = when (cityNameRounds) {
            0 -> ""
            1 -> "New "
            else -> "Neo "
        }

        name = cityNamePrefix + cityName
    }

    private fun borrowCityName(): String {
        val usedCityNames =
            civInfo.gameInfo.civilizations.flatMap { it.cities.map { city -> city.name } }
        // We take the last unused city name for each other civ in this game, skipping civs whose
        // names are exhausted, and choose a random one from that pool if it's not empty.
        var newNames = civInfo.gameInfo.civilizations
            .filter { it.isMajorCiv() && it != civInfo }
            .mapNotNull {
                it.nation.cities
                    .lastOrNull { city -> city !in usedCityNames }
            }
        if (newNames.isNotEmpty()) {
            return newNames.random()
        }

        // As per fandom wiki, once the names from the other nations in the game are exhausted,
        // names are taken from the rest of the nations in the ruleset
        newNames = getRuleset()
            .nations
            .filter { it.key !in civInfo.gameInfo.civilizations.map { civ -> civ.nation.name } }
            .values
            .map {
                it.cities
                    .filter { city -> city !in usedCityNames }
            }.flatten()
        if (newNames.isNotEmpty()) {
            return newNames.random()
        }
        // If for some reason we have used every single city name in the game,
        // (are we using some sort of baserule mod without city names?)
        // just return something so we at least have a name
        return "The City without a Name"
    }


    //region pure functions
    fun clone(): CityInfo {
        val toReturn = CityInfo()
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
        return toReturn
    }

    fun canBombard() = !attackedThisTurn && !isInResistance()
    fun getCenterTile(): TileInfo = centerTileInfo
    fun getTiles(): Sequence<TileInfo> = tiles.asSequence().map { tileMap[it] }
    fun getWorkableTiles() = tilesInRange.asSequence().filter { it.getOwner() == civInfo }
    fun isWorked(tileInfo: TileInfo) = workedTiles.contains(tileInfo.position)

    fun isCapital(): Boolean = cityConstructions.builtBuildings.contains(capitalCityIndicator())
    fun isCoastal(): Boolean = centerTileInfo.isCoastalTile()
    fun capitalCityIndicator(): String {
        val indicatorBuildings = getRuleset().buildings.values
            .asSequence()
            .filter { it.uniques.contains("Indicates the capital city") }

        val civSpecificBuilding = indicatorBuildings.firstOrNull { it.uniqueTo == civInfo.civName }
        return civSpecificBuilding?.name ?: indicatorBuildings.first().name
    }

    fun isConnectedToCapital(connectionTypePredicate: (Set<String>) -> Boolean = { true }): Boolean {
        val mediumTypes = civInfo.citiesConnectedToCapitalToMediums[this] ?: return false
        return connectionTypePredicate(mediumTypes)

    }

    fun hasFlag(flag: CityFlags) = flagsCountdown.containsKey(flag.name)
    fun getFlag(flag: CityFlags) = flagsCountdown[flag.name]!!

    fun isWeLoveTheKingDay() = hasFlag(CityFlags.WeLoveTheKing)
    fun isInResistance() = hasFlag(CityFlags.Resistance)

    /** @return the number of tiles 4 out from this city that could hold a city, ie how lonely this city is */
    fun getFrontierScore() = getCenterTile().getTilesAtDistance(4).count { it.canBeSettled() && (it.getOwner() == null || it.getOwner() == civInfo ) }

    fun getRuleset() = civInfo.gameInfo.ruleSet

    fun getCityResources(): ResourceSupplyList {
        val cityResources = ResourceSupplyList()

        for (tileInfo in getTiles().filter { it.resource != null }) {
            val resource = tileInfo.tileResource
            val amount = getTileResourceAmount(tileInfo) * civInfo.getResourceModifier(resource)
            if (amount > 0) cityResources.add(resource, amount, "Tiles")
        }
        
        for (tileInfo in getTiles()) {
            if (tileInfo.improvement == null) continue
            val tileImprovement = tileInfo.getTileImprovement()
            for (unique in tileImprovement!!.uniqueObjects) {
                if (unique.isOfType(UniqueType.ProvidesResources)) {
                    if (!unique.conditionalsApply(civInfo, this)) continue
                    val resource = getRuleset().tileResources[unique.params[1]] ?: continue
                    cityResources.add(
                        resource,
                        unique.params[0].toInt() * civInfo.getResourceModifier(resource),
                        "Improvements"
                    )
                }
                if (unique.isOfType(UniqueType.ConsumesResources)) {
                    val resource = getRuleset().tileResources[unique.params[1]] ?: continue
                    cityResources.add(
                        resource,
                        -1 * unique.params[0].toInt(),
                        "Improvements"
                    )
                }
            }
        }
        
        for (building in cityConstructions.getBuiltBuildings()) {
            // Free buildings cost no resources
            if (building.name in civInfo.civConstructions.getFreeBuildings(id))
                continue
            for ((resourceName, amount) in building.getResourceRequirements()) {
                val resource = getRuleset().tileResources[resourceName]!!
                cityResources.add(resource, -amount, "Buildings")
            }
        }
        
        for (unique in getLocalMatchingUniques(UniqueType.ProvidesResources)) { // E.G "Provides [1] [Iron]"
            if (!unique.conditionalsApply(civInfo, this)) continue
            val resource = getRuleset().tileResources[unique.params[1]]
            if (resource != null) {
                cityResources.add(
                    resource, 
                    unique.params[0].toInt() * civInfo.getResourceModifier(resource), 
                    "Tiles"
                )
            }
        }
        if (civInfo.isCityState() && isCapital() && civInfo.cityStateResource != null) {
            cityResources.add(
                getRuleset().tileResources[civInfo.cityStateResource]!!,
                1,
                "Mercantile City-State"
            )
        }

        return cityResources
    }

    fun getTileResourceAmount(tileInfo: TileInfo): Int {
        if (tileInfo.resource == null) return 0
        val resource = tileInfo.tileResource
        if (resource.revealedBy != null && !civInfo.tech.isResearched(resource.revealedBy!!)) return 0

        // Even if the improvement exists (we conquered an enemy city or somesuch) or we have a city on it, we won't get the resource until the correct tech is researched
        if (resource.improvement != null) {
            val improvement = getRuleset().tileImprovements[resource.improvement!!]!!
            if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!)) return 0
        }

        if (resource.improvement == tileInfo.improvement || tileInfo.isCityCenter()
            // Per https://gaming.stackexchange.com/questions/53155/do-manufactories-and-customs-houses-sacrifice-the-strategic-or-luxury-resources
            || resource.resourceType == ResourceType.Strategic && tileInfo.containsGreatImprovement()
        ) {
            var amountToAdd = if (resource.resourceType == ResourceType.Strategic) tileInfo.resourceAmount
                else 1
            if (resource.resourceType == ResourceType.Luxury
                && containsBuildingUnique("Provides 1 extra copy of each improved luxury resource near this City")
            )
                amountToAdd += 1

            return amountToAdd
        }
        return 0
    }

    fun isGrowing() = foodForNextTurn() > 0
    fun isStarving() = foodForNextTurn() < 0

    private fun foodForNextTurn() = cityStats.currentCityStats.food.roundToInt()

    /** Take null to mean infinity. */
    fun getNumTurnsToNewPopulation(): Int? {
        if (!isGrowing()) return null
        val roundedFoodPerTurn = foodForNextTurn().toFloat()
        val remainingFood = population.getFoodToNextPopulation() - population.foodStored
        var turnsToGrowth = ceil(remainingFood / roundedFoodPerTurn).toInt()
        if (turnsToGrowth < 1) turnsToGrowth = 1
        return turnsToGrowth
    }

    /** Take null to mean infinity. */
    fun getNumTurnsToStarvation(): Int? {
        if (!isStarving()) return null
        return population.foodStored / -foodForNextTurn() + 1
    }

    fun containsBuildingUnique(unique: String) =
        cityConstructions.getBuiltBuildings().any { it.uniques.contains(unique) }

    fun containsBuildingUnique(uniqueType: UniqueType) =
        cityConstructions.getBuiltBuildings().flatMap { it.uniqueObjects }.any { it.isOfType(uniqueType) }

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

        for ((_, gppCounter) in sourceToGPP) {
            for (unique in civInfo.getMatchingUniques("[] is earned []% faster")) {
                val unitName = unique.params[0]
                if (!gppCounter.containsKey(unitName)) continue
                gppCounter.add(unitName, gppCounter[unitName]!! * unique.params[1].toInt() / 100)
            }

            var allGppPercentageBonus = 0
            for (unique in getMatchingUniques("[]% great person generation []")) {
                if (!matchesFilter(unique.params[1])) continue
                allGppPercentageBonus += unique.params[0].toInt()
            }

            // Sweden UP
            for (otherCiv in civInfo.getKnownCivs()) {
                if (!civInfo.getDiplomacyManager(otherCiv)
                        .hasFlag(DiplomacyFlags.DeclarationOfFriendship)
                ) continue

                for (ourUnique in civInfo.getMatchingUniques("When declaring friendship, both parties gain a []% boost to great person generation"))
                    allGppPercentageBonus += ourUnique.params[0].toInt()
                for (theirUnique in otherCiv.getMatchingUniques("When declaring friendship, both parties gain a []% boost to great person generation"))
                    allGppPercentageBonus += theirUnique.params[0].toInt()
            }

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
            else -> civInfo.addStat(stat, amount)
        }
    }

    fun getStatReserve(stat: Stat): Int {
        return when (stat) {
            Stat.Food -> population.foodStored
            else -> civInfo.getStatReserve(stat)
        }
    }

    internal fun getMaxHealth() =
        200 + cityConstructions.getBuiltBuildings().sumOf { it.cityHealth }

    override fun toString() = name // for debug
    //endregion

    //region state-changing functions
    fun setTransients() {
        tileMap = civInfo.gameInfo.tileMap
        centerTileInfo = tileMap[location]
        tilesInRange = getCenterTile().getTilesInDistance(3).toHashSet()
        population.cityInfo = this
        expansion.cityInfo = this
        expansion.setTransients()
        cityConstructions.cityInfo = this
        cityConstructions.setTransients()
        religion.setTransients(this)
    }

    fun startTurn() {
        // Construct units at the beginning of the turn,
        // so they won't be generated out in the open and vulnerable to enemy attacks before you can control them
        cityConstructions.constructIfEnough()
        cityConstructions.addFreeBuildings()
        
        cityStats.update()
        tryUpdateRoadStatus()
        attackedThisTurn = false

        if (isPuppet) reassignPopulation()

        // The ordering is intentional - you get a turn without WLTKD even if you have the next resource already
        if (!hasFlag(CityFlags.WeLoveTheKing))
            tryWeLoveTheKing()
        nextTurnFlags()
    }

    // cf DiplomacyManager nextTurnFlags
    private fun nextTurnFlags() {
        for (flag in flagsCountdown.keys.toList()) {
            if (flagsCountdown[flag]!! > 0)
                flagsCountdown[flag] = flagsCountdown[flag]!! - 1

            if (flagsCountdown[flag] == 0) {
                flagsCountdown.remove(flag)

                when (flag) {
                    CityFlags.ResourceDemand.name -> {
                        demandNewResource()
                    }
                    CityFlags.WeLoveTheKing.name -> {
                        civInfo.addNotification(
                                "We Love The King Day in [$name] has ended.",
                                location, NotificationIcon.City)
                        demandNewResource()
                    }
                    CityFlags.Resistance.name -> {
                        civInfo.addNotification(
                                "The resistance in [$name] has ended!",
                                location,"StatIcons/Resistance")
                    }
                }
            }
        }
    }

    fun setFlag(flag: CityFlags, amount: Int) {
        flagsCountdown[flag.name] = amount
    }

    fun reassignPopulation() {
        var foodWeight = 1f
        var foodPerTurn = 0f
        while (foodWeight < 3 && foodPerTurn <= 0) {
            workedTiles = hashSetOf()
            population.specialistAllocations.clear()
            for (i in 0..population.population)
                population.autoAssignPopulation(foodWeight)
            cityStats.update()

            foodPerTurn = foodForNextTurn().toFloat()
            foodWeight += 0.5f
        }
    }

    fun endTurn() {
        val stats = cityStats.currentCityStats

        cityConstructions.endTurn(stats)
        expansion.nextTurn(stats.culture)
        if (isBeingRazed) {
            val removedPopulation =
                1 + civInfo.getMatchingUniques("Cities are razed [] times as fast")
                    .sumOf { it.params[0].toInt() - 1 }
            population.addPopulation(-1 * removedPopulation)
            if (population.population <= 0) {
                civInfo.addNotification(
                    "[$name] has been razed to the ground!",
                    location,
                    "OtherIcons/Fire"
                )
                destroyCity()
            } else { //if not razed yet:
                if (population.foodStored >= population.getFoodToNextPopulation()) { //if surplus in the granary...
                    population.foodStored =
                        population.getFoodToNextPopulation() - 1 //...reduce below the new growth threshold
                }
            }
        } else population.nextTurn(foodForNextTurn())

        // This should go after the population change, as that might impact the amount of followers in this city
        if (civInfo.gameInfo.isReligionEnabled()) religion.endTurn()

        if (this in civInfo.cities) { // city was not destroyed
            health = min(health + 20, getMaxHealth())
            population.unassignExtraPopulation()
        }
    }

    fun destroyCity(overrideSafeties: Boolean = false) {
        // Original capitals and holy cities cannot be destroyed,
        // unless, of course, they are captured by a one-city-challenger.
        if (!canBeDestroyed() && !overrideSafeties) return

        for (airUnit in getCenterTile().airUnits.toList()) airUnit.destroy() //Destroy planes stationed in city

        // The relinquish ownership MUST come before removing the city,
        // because it updates the city stats which assumes there is a capital, so if you remove the capital it crashes
        getTiles().forEach { expansion.relinquishOwnership(it) }
        civInfo.cities = civInfo.cities.toMutableList().apply { remove(this@CityInfo) }
        getCenterTile().improvement = "City ruins"

        // Edge case! What if a water unit is in a city, and you raze the city?
        // Well, the water unit has to return to the water!
        for (unit in getCenterTile().getUnits().toList()) {
            if (!unit.movement.canPassThrough(getCenterTile()))
                unit.movement.teleportToClosestMoveableTile()
        }

        if (isCapital() && civInfo.cities.isNotEmpty()) { // Move the capital if destroyed (by a nuke or by razing)
            civInfo.cities.first().cityConstructions.addBuilding(capitalCityIndicator())
        }

        // Update proximity rankings for all civs
        for (otherCiv in civInfo.gameInfo.getAliveMajorCivs()) {
            civInfo.updateProximity(otherCiv,
                otherCiv.updateProximity(civInfo))
        }
        for (otherCiv in civInfo.gameInfo.getAliveCityStates()) {
            civInfo.updateProximity(otherCiv,
                otherCiv.updateProximity(civInfo))
        }
    }

    fun annexCity() = CityInfoConquestFunctions(this).annexCity()

    /** This happens when we either puppet OR annex, basically whenever we conquer a city and don't liberate it */
    fun puppetCity(conqueringCiv: CivilizationInfo) =
        CityInfoConquestFunctions(this).puppetCity(conqueringCiv)

    /* Liberating is returning a city to its founder - makes you LOSE warmongering points **/
    fun liberateCity(conqueringCiv: CivilizationInfo) =
        CityInfoConquestFunctions(this).liberateCity(conqueringCiv)

    fun moveToCiv(newCivInfo: CivilizationInfo) =
        CityInfoConquestFunctions(this).moveToCiv(newCivInfo)

    internal fun tryUpdateRoadStatus() {
        if (getCenterTile().roadStatus == RoadStatus.None) {
            val roadImprovement = RoadStatus.Road.improvement(getRuleset())
            if (roadImprovement != null && roadImprovement.techRequired in civInfo.tech.techsResearched)
                getCenterTile().roadStatus = RoadStatus.Road
        } else if (getCenterTile().roadStatus != RoadStatus.Railroad) {
            val railroadImprovement = RoadStatus.Railroad.improvement(getRuleset())
            if (railroadImprovement != null && railroadImprovement.techRequired in civInfo.tech.techsResearched)
                getCenterTile().roadStatus = RoadStatus.Railroad
        }
    }

    fun getGoldForSellingBuilding(buildingName: String) =
        getRuleset().buildings[buildingName]!!.cost / 10

    fun sellBuilding(buildingName: String) {
        cityConstructions.removeBuilding(buildingName)
        civInfo.addGold(getGoldForSellingBuilding(buildingName))
        hasSoldBuildingThisTurn = true

        population.unassignExtraPopulation() // If the building provided specialists, release them to other work
        population.autoAssignPopulation()
        cityStats.update()
        civInfo.updateDetailedCivResources() // this building could be a resource-requiring one
    }

    private fun demandNewResource() {
        val candidates = getRuleset().tileResources.values.filter {
            // 1. Luxury
            it.resourceType == ResourceType.Luxury &&
            // 2. Not a city-state only resource eg jewelry
            !it.hasUnique(UniqueType.CityStateOnlyResource) &&
            // 3. Not the same as last
            it.name != demandedResource &&
            // 4. Not found nearby
            getCenterTile().getTilesInDistance(3).none { nearTile -> nearTile.resource == it.name }
        }.shuffled()

        // Keep trying until we get one that actually exists on the map
        val chosenResource = candidates.firstOrNull { tileMap.values.any { tile -> tile.resource == it.name } }

        // This shouldn't happen normally but perhaps in mods with few luxury types
        if (chosenResource == null) {
            demandedResource = ""
            setFlag(CityFlags.ResourceDemand, 10 + Random().nextInt(10))
            return
        }

        demandedResource = chosenResource.name
        civInfo.addNotification("[$name] demands [$demandedResource]!", location, NotificationIcon.City)
    }

    private fun tryWeLoveTheKing() {
        if (demandedResource == "") return
        if (civInfo.getCivResourcesByName()[demandedResource]!! > 0) {
            setFlag(CityFlags.WeLoveTheKing, 20)
            civInfo.addNotification(
                    "Because they have [$demandedResource], the citizens of [$name] are celebrating We Love The King Day!",
                    location, NotificationIcon.City, NotificationIcon.Happiness)
        }
    }

    /*
     When someone settles a city within 6 tiles of another civ, this makes the AI unhappy and it starts a rolling event.
     The SettledCitiesNearUs flag gets added to the AI so it knows this happened,
        and on its turn it asks the player to stop (with a DemandToStopSettlingCitiesNear alert type)
     If the player says "whatever, I'm not promising to stop", they get a -10 modifier which gradually disappears in 40 turns
     If they DO agree, then if they keep their promise for ~100 turns they get a +10 modifier for keeping the promise,
     But if they don't keep their promise they get a -20 that will only fully disappear in 160 turns.
     There's a lot of triggering going on here.
     */
    private fun triggerCitiesSettledNearOtherCiv() {
        val citiesWithin6Tiles =
            civInfo.gameInfo.civilizations.asSequence()
                .filter { it.isMajorCiv() && it != civInfo }
                .flatMap { it.cities }
                .filter { it.getCenterTile().aerialDistanceTo(getCenterTile()) <= 6 }
        val civsWithCloseCities = 
            citiesWithin6Tiles
                .map { it.civInfo }
                .distinct()
                .filter { it.knows(civInfo) && it.exploredTiles.contains(location) }
        for (otherCiv in civsWithCloseCities)
            otherCiv.getDiplomacyManager(civInfo).setFlag(DiplomacyFlags.SettledCitiesNearUs, 30)
    }

    fun canPlaceNewUnit(construction: BaseUnit): Boolean {
        val tile = getCenterTile()
        return when {
            construction.isCivilian() -> tile.civilianUnit == null
            construction.movesLikeAirUnits() -> tile.airUnits.count { !it.isTransported } < 6
            else -> tile.militaryUnit == null
        }
    }

    // When adding here, add to UniqueParameterType.cityFilterStrings
    fun matchesFilter(filter: String, viewingCiv: CivilizationInfo = civInfo): Boolean {
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
                viewingCiv != civInfo
                && !civInfo.isAtWarWith(viewingCiv)
            "in foreign cities" -> viewingCiv != civInfo
            "in annexed cities" -> foundingCiv != civInfo.civName && !isPuppet
            "in holy cities" -> religion.religionThisIsTheHolyCityOf != null
            "in City-State cities" -> civInfo.isCityState()
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
        placeholderText: String,
        // We might have this cached to avoid concurrency problems. If we don't, just get it directly
        localUniques: Sequence<Unique> = getLocalMatchingUniques(placeholderText),
    ): Sequence<Unique> {
        // The localUniques might not be filtered when passed as a parameter, so we filter it anyway
        // The time loss shouldn't be that large I don't think
        return civInfo.getMatchingUniques(placeholderText, this) +
                localUniques.filter { it.placeholderText == placeholderText }
    }

    // Finds matching uniques provided from both local and non-local sources.
    fun getMatchingUniques(
        uniqueType: UniqueType,
        stateForConditionals: StateForConditionals? = null,
    ): Sequence<Unique> {
        return civInfo.getMatchingUniques(uniqueType, stateForConditionals, this) +
            getLocalMatchingUniques(uniqueType, stateForConditionals)
    }

    // Matching uniques provided by sources in the city itself
    fun getLocalMatchingUniques(placeholderText: String): Sequence<Unique> {
        return cityConstructions.builtBuildingUniqueMap.getUniques(placeholderText)
            .filter { !it.isAntiLocalEffect } +
                religion.getUniques().filter { it.placeholderText == placeholderText }
    }

    fun getLocalMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals? = null): Sequence<Unique> {
        return (
            cityConstructions.builtBuildingUniqueMap.getUniques(uniqueType)
                .filter { !it.isAntiLocalEffect }
            + religion.getUniques().filter { it.isOfType(uniqueType) }
        ).filter {
            it.conditionalsApply(stateForConditionals)
        }
    }

    // Get all uniques that originate from this city
    fun getAllLocalUniques(): Sequence<Unique> {
        return cityConstructions.builtBuildingUniqueMap.getAllUniques() + religion.getUniques()
    }

    // Get all matching uniques that don't apply to only this city
    fun getMatchingUniquesWithNonLocalEffects(placeholderText: String): Sequence<Unique> {
        return cityConstructions.builtBuildingUniqueMap.getUniques(placeholderText)
            .filter { !it.isLocalEffect }
        // Note that we don't query religion here, as those only have local effects
    }


    fun getMatchingUniquesWithNonLocalEffects(uniqueType: UniqueType): Sequence<Unique> {
        return cityConstructions.builtBuildingUniqueMap.getUniques(uniqueType)
            .filter { !it.isLocalEffect }
        // Note that we don't query religion here, as those only have local effects
    }

    // Get all uniques that don't apply to only this city
    fun getAllUniquesWithNonLocalEffects(): Sequence<Unique> {
        return cityConstructions.builtBuildingUniqueMap.getAllUniques()
            .filter { !it.isLocalEffect }
        // Note that we don't query religion here, as those only have local effects
    }

    fun isHolyCity(): Boolean = religion.religionThisIsTheHolyCityOf != null

    fun canBeDestroyed(justCaptured: Boolean = false): Boolean {
        return !isOriginalCapital && !isHolyCity() && (!isCapital() || justCaptured)
    }

    fun getForceEvaluation(): Int {
        // Same as for units, so higher values count more
        return CityCombatant(this).getCityStrength().toFloat().pow(1.5f).toInt()
    }


    fun getNeighbouringCivs(): Set<String> {
        val tilesList: HashSet<TileInfo> = getTiles().toHashSet()
        val cityPositionList: ArrayList<TileInfo> = arrayListOf()

        for (tiles in tilesList)
            for (tile in tiles.neighbors)
                if (!tilesList.contains(tile))
                    cityPositionList.add(tile)

        return cityPositionList
            .asSequence()
            .mapNotNull { it.getOwner()?.civName }
            .toSet()
    }

    //endregion
}