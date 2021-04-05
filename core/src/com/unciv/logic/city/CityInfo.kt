package com.unciv.logic.city

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.Counter
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.StatMap
import com.unciv.models.stats.Stats
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

class CityInfo {
    @Transient
    lateinit var civInfo: CivilizationInfo

    @Transient
    lateinit private var centerTileInfo: TileInfo  // cached for better performance

    @Transient
    val range = 2

    @Transient
    lateinit var tileMap: TileMap

    @Transient
    lateinit var tilesInRange: HashSet<TileInfo>

    @Transient
    var hasJustBeenConquered = false  // this is so that military units can enter the city, even before we decide what to do with it

    var location: Vector2 = Vector2.Zero
    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var foundingCiv = ""
    var previousOwner = "" // This is so that cities in resistance that re recaptured aren't in resistance anymore
    var turnAcquired = 0
    var health = 200
    var resistanceCounter = 0

    var religion = CityInfoReligionManager()
    var population = PopulationManager()
    var cityConstructions = CityConstructions()
    var expansion = CityExpansionManager()
    var cityStats = CityStats()

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
     * It is important to distinct them since the original cannot be razed and defines the Domination Victory. */
    var isOriginalCapital = false

    constructor()   // for json parsing, we need to have a default constructor
    constructor(civInfo: CivilizationInfo, cityLocation: Vector2) {  // new city!
        this.civInfo = civInfo
        foundingCiv = civInfo.civName
        turnAcquired = civInfo.gameInfo.turns
        this.location = cityLocation
        setTransients()

        setNewCityName(civInfo)

        isOriginalCapital = civInfo.citiesCreated == 0
        civInfo.citiesCreated++

        civInfo.cities = civInfo.cities.toMutableList().apply { add(this@CityInfo) }

        if (civInfo.cities.size == 1) cityConstructions.addBuilding(capitalCityIndicator())

        civInfo.policies.tryAddLegalismBuildings()

        for (unique in civInfo.getMatchingUniques("Gain a free [] []")) {
            val freeBuildingName = unique.params[0]
            if (matchesFilter(unique.params[1])) {
                if (!cityConstructions.isBuilt(freeBuildingName))
                    cityConstructions.addBuilding(freeBuildingName)
            }
        }

        expansion.reset()


        tryUpdateRoadStatus()

        val tile = getCenterTile()
        for (terrainFeature in tile.terrainFeatures.filter { getRuleset().tileImprovements.containsKey("Remove $it") })
            tile.terrainFeatures.remove(terrainFeature)

        tile.improvement = null
        tile.improvementInProgress = null

        workedTiles = hashSetOf() //reassign 1st working tile
        population.autoAssignPopulation()
        cityStats.update()

        triggerCitiesSettledNearOtherCiv()
    }

    private fun setNewCityName(civInfo: CivilizationInfo) {
        val nationCities = civInfo.nation.cities
        val cityNameIndex = civInfo.citiesCreated % nationCities.size
        val cityName = nationCities[cityNameIndex]

        val cityNameRounds = civInfo.citiesCreated / nationCities.size
        val cityNamePrefix = if (cityNameRounds == 0) ""
        else if (cityNameRounds == 1) "New "
        else "Neo "

        name = cityNamePrefix + cityName
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
        toReturn.tiles = tiles
        toReturn.workedTiles = workedTiles
        toReturn.lockedTiles = lockedTiles
        toReturn.isBeingRazed = isBeingRazed
        toReturn.attackedThisTurn = attackedThisTurn
        toReturn.resistanceCounter = resistanceCounter
        toReturn.foundingCiv = foundingCiv
        toReturn.turnAcquired = turnAcquired
        toReturn.isPuppet = isPuppet
        toReturn.isOriginalCapital = isOriginalCapital
        toReturn.religion = CityInfoReligionManager().apply { putAll(religion) }
        return toReturn
    }


    fun getCenterTile(): TileInfo = centerTileInfo
    fun getTiles(): Sequence<TileInfo> = tiles.asSequence().map { tileMap[it] }
    fun getWorkableTiles() = tilesInRange.asSequence().filter { it.getOwner() == civInfo }
    fun isWorked(tileInfo: TileInfo) = workedTiles.contains(tileInfo.position)

    fun isCapital(): Boolean = cityConstructions.builtBuildings.contains(capitalCityIndicator())
    fun capitalCityIndicator(): String = getRuleset().buildings.values.first { it.uniques.contains("Indicates the capital city") }.name

    fun isConnectedToCapital(connectionTypePredicate: (Set<String>) -> Boolean = { true }): Boolean {
        val mediumTypes = civInfo.citiesConnectedToCapitalToMediums[this] ?: return false
        return connectionTypePredicate(mediumTypes)
    }

    fun isInResistance() = resistanceCounter > 0


    fun getRuleset() = civInfo.gameInfo.ruleSet

    fun getCityResources(): ResourceSupplyList {
        val cityResources = ResourceSupplyList()

        for (tileInfo in getTiles().filter { it.resource != null }) {
            val resource = tileInfo.getTileResource()
            val amount = getTileResourceAmount(tileInfo) * civInfo.getResourceModifier(resource)
            if (amount > 0) cityResources.add(resource, amount, "Tiles")
        }
        for (tileInfo in getTiles()) {
            if (tileInfo.improvement == null) continue
            val tileImprovement = tileInfo.getTileImprovement()
            for (unique in tileImprovement!!.uniqueObjects)
                if (unique.placeholderText == "Provides [] []") {
                    val resource = getRuleset().tileResources[unique.params[1]] ?: continue
                    cityResources.add(resource, unique.params[0].toInt() * civInfo.getResourceModifier(resource), "Tiles")
                }
        }
        for (building in cityConstructions.getBuiltBuildings()) {
            for ((resourceName, amount) in building.getResourceRequirements()) {
                val resource = getRuleset().tileResources[resourceName]!!
                cityResources.add(resource, -amount, "Buildings")
            }
            for (unique in building.uniqueObjects.filter { it.placeholderText == "Consumes [] []" }) {
                val resource = getRuleset().tileResources[unique.params[1]]
                if (resource != null) cityResources.add(resource, -unique.params[0].toInt(), "Buildings")
            }
        }
        for (unique in cityConstructions.builtBuildingUniqueMap.getUniques("Provides [] []")) { // E.G "Provides [1] [Iron]"
            val resource = getRuleset().tileResources[unique.params[1]]
            if (resource != null) {
                cityResources.add(resource, unique.params[0].toInt() * civInfo.getResourceModifier(resource), "Tiles")
            }
        }

        return cityResources
    }

    fun getTileResourceAmount(tileInfo: TileInfo): Int {
        if (tileInfo.resource == null) return 0
        val resource = tileInfo.getTileResource()
        if (resource.revealedBy != null && !civInfo.tech.isResearched(resource.revealedBy!!)) return 0

        // Even if the improvement exists (we conquered an enemy city or somesuch) or we have a city on it, we won't get the resource until the correct tech is researched
        if (resource.improvement != null) {
            val improvement = getRuleset().tileImprovements[resource.improvement!!]!!
            if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!)) return 0
        }

        if (resource.improvement == tileInfo.improvement || tileInfo.isCityCenter()
                // Per https://gaming.stackexchange.com/questions/53155/do-manufactories-and-customs-houses-sacrifice-the-strategic-or-luxury-resources
                || (resource.resourceType == ResourceType.Strategic && tileInfo.containsGreatImprovement())) {
            var amountToAdd = 1
            if (resource.resourceType == ResourceType.Strategic) {
                amountToAdd = 2
            }
            if (resource.resourceType == ResourceType.Luxury
                    && containsBuildingUnique("Provides 1 extra copy of each improved luxury resource near this City"))
                amountToAdd *= 2

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

    fun containsBuildingUnique(unique: String) = cityConstructions.getBuiltBuildings().any { it.uniques.contains(unique) }

    fun getGreatPersonMap(): StatMap {
        val stats = StatMap()
        for ((specialist, amount) in population.getNewSpecialists())
            if (getRuleset().specialists.containsKey(specialist)) // To solve problems in total remake mods
                stats.add("Specialists", getRuleset().specialists[specialist]!!.greatPersonPoints.times(amount))

        val buildingStats = Stats()
        for (building in cityConstructions.getBuiltBuildings())
            if (building.greatPersonPoints != null)
                buildingStats.add(building.greatPersonPoints!!)
        if (!buildingStats.isEmpty())
            stats["Buildings"] = buildingStats

        for (entry in stats) {
            for (unique in civInfo.getMatchingUniques("[] is earned []% faster")) {
                val unit = civInfo.gameInfo.ruleSet.units[unique.params[0]]
                if (unit == null) continue
                val greatUnitUnique = unit.uniqueObjects.firstOrNull { it.placeholderText == "Great Person - []" }
                if (greatUnitUnique == null) continue
                val statName = greatUnitUnique.params[0]
                val stat = Stat.values().firstOrNull { it.name == statName }
                // this is not very efficient, and if it causes problems we can try and think of a way of improving it
                if (stat != null) entry.value.add(stat, entry.value.get(stat) * unique.params[1].toFloat() / 100)
            }

            for (unique in civInfo.getMatchingUniques("+[]% great person generation in all cities")
                    + cityConstructions.builtBuildingUniqueMap.getUniques("+[]% great person generation in this city"))
                stats[entry.key] = stats[entry.key]!!.times(1 + (unique.params[0].toFloat() / 100))
        }

        return stats
    }

    fun getGreatPersonPoints(): Stats {
        val stats = Stats()
        for (entry in getGreatPersonMap().values)
            stats.add(entry)
        return stats
    }

    internal fun getMaxHealth() = 200 + cityConstructions.getBuiltBuildings().sumBy { it.cityHealth }

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
        cityStats.cityInfo = this
        cityConstructions.cityInfo = this
        cityConstructions.setTransients()
        religion.cityInfo = this
    }

    fun startTurn() {
        // Construct units at the beginning of the turn,
        // so they won't be generated out in the open and vulnerable to enemy attacks before you can control them
        cityConstructions.constructIfEnough()
        cityStats.update()
        tryUpdateRoadStatus()
        attackedThisTurn = false
        if (isInResistance()) {
            resistanceCounter--
            if (!isInResistance())
                civInfo.addNotification("The resistance in [$name] has ended!", location, "StatIcons/Resistance")
        }

        if (isPuppet) reassignPopulation()
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
            population.population--
            if (population.population <= 0) { // there are strange cases where we get to -1
                civInfo.addNotification("[$name] has been razed to the ground!", location, "OtherIcons/Fire")
                destroyCity()
            } else { //if not razed yet:
                if (population.foodStored >= population.getFoodToNextPopulation()) { //if surplus in the granary...
                    population.foodStored = population.getFoodToNextPopulation() - 1 //...reduce below the new growth threshold
                }
            }
        } else population.nextTurn(foodForNextTurn())

        if (getRuleset().hasReligion()) religion.getAffectedBySurroundingCities()

        if (this in civInfo.cities) { // city was not destroyed
            health = min(health + 20, getMaxHealth())
            population.unassignExtraPopulation()
        }
    }

    fun destroyCity() {
        for (airUnit in getCenterTile().airUnits.toList()) airUnit.destroy() //Destroy planes stationed in city

        // Edge case! What if a water unit is in a city, and you raze the city?
        // Well, the water unit has to return to the water!
        for (unit in getCenterTile().getUnits()) {
            if (!unit.movement.canPassThrough(getCenterTile()))
                unit.movement.teleportToClosestMoveableTile()
        }

        // The relinquish ownership MUST come before removing the city,
        // because it updates the city stats which assumes there is a capital, so if you remove the capital it crashes
        getTiles().forEach { expansion.relinquishOwnership(it) }
        civInfo.cities = civInfo.cities.toMutableList().apply { remove(this@CityInfo) }
        getCenterTile().improvement = "City ruins"

        if (isCapital() && civInfo.cities.isNotEmpty()) { // Move the capital if destroyed (by a nuke or by razing)
            val capitalCityBuilding = getRuleset().buildings.values.first { it.uniques.contains("Indicates the capital city") }
            civInfo.cities.first().cityConstructions.addBuilding(capitalCityBuilding.name)
        }
    }

    fun annexCity()  = CityInfoConquestFunctions(this).annexCity()

    /** This happens when we either puppet OR annex, basically whenever we conquer a city and don't liberate it */
    fun puppetCity(conqueringCiv: CivilizationInfo)  = CityInfoConquestFunctions(this).puppetCity(conqueringCiv)

    /* Liberating is returning a city to its founder - makes you LOSE warmongering points **/
    fun liberateCity(conqueringCiv: CivilizationInfo)  = CityInfoConquestFunctions(this).liberateCity(conqueringCiv)

    fun moveToCiv(newCivInfo: CivilizationInfo)  = CityInfoConquestFunctions(this).moveToCiv(newCivInfo)

    internal fun tryUpdateRoadStatus() {
        if (getCenterTile().roadStatus == RoadStatus.None) {
            val roadImprovement = getRuleset().tileImprovements["Road"]
            if (roadImprovement != null && roadImprovement.techRequired in civInfo.tech.techsResearched)
                getCenterTile().roadStatus = RoadStatus.Road
        } else if (getCenterTile().roadStatus != RoadStatus.Railroad) {
            val railroadImprovement = getRuleset().tileImprovements["Railroad"]
            if (railroadImprovement != null && railroadImprovement.techRequired in civInfo.tech.techsResearched)
                getCenterTile().roadStatus = RoadStatus.Railroad
        }
    }

    fun getGoldForSellingBuilding(buildingName: String) = getRuleset().buildings[buildingName]!!.cost / 10

    fun sellBuilding(buildingName: String) {
        cityConstructions.removeBuilding(buildingName)
        civInfo.gold += getGoldForSellingBuilding(buildingName)
        hasSoldBuildingThisTurn = true

        population.unassignExtraPopulation() // If the building provided specialists, release them to other work
        population.autoAssignPopulation()
        cityStats.update()
        civInfo.updateDetailedCivResources() // this building could be a resource-requiring one
    }

    /*
     When someone settles a city within 6 tiles of another civ,
        this makes the AI unhappy and it starts a rolling event.
     The SettledCitiesNearUs flag gets added to the AI so it knows this happened,
        and on its turn it asks the player to stop (with a DemandToStopSettlingCitiesNear alert type)
     If the player says "whatever, I'm not promising to stop", they get a -10 modifier which gradually disappears in 40 turns
     If they DO agree, then if they keep their promise for ~100 turns they get a +10 modifier for keeping the promise,
     But if they don't keep their promise they get a -20 that will only fully disappear in 160 turns.
     There's a lot of triggering going on here.
     */
    private fun triggerCitiesSettledNearOtherCiv() {
        val citiesWithin6Tiles = civInfo.gameInfo.civilizations.filter { it.isMajorCiv() && it != civInfo }
                .flatMap { it.cities }
                .filter { it.getCenterTile().aerialDistanceTo(getCenterTile()) <= 6 }
        val civsWithCloseCities = citiesWithin6Tiles.map { it.civInfo }.distinct()
                .filter { it.knows(civInfo) && it.exploredTiles.contains(location) }
        for (otherCiv in civsWithCloseCities)
            otherCiv.getDiplomacyManager(civInfo).setFlag(DiplomacyFlags.SettledCitiesNearUs, 30)
    }

    fun canPurchase(construction: IConstruction): Boolean {
        if (construction is BaseUnit) {
            val tile = getCenterTile()
            if (construction.unitType.isCivilian())
                return tile.civilianUnit == null
            if (construction.unitType.isAirUnit())
                return tile.airUnits.filter { !it.isTransported }.size < 6
            else return tile.militaryUnit == null
        }
        return true
    }

    fun matchesFilter(filter: String): Boolean {
        return when {
            filter == "in this city" -> true
            filter == "in all cities" -> true
            filter == "in all coastal cities" && getCenterTile().isCoastalTile() -> true
            filter == "in capital" && isCapital() -> true
            filter == "in all cities with a world wonder" && cityConstructions.getBuiltBuildings().any { it.isWonder } -> true
            filter == "in all cities connected to capital" -> isConnectedToCapital()
            filter == "in all cities with a garrison" && getCenterTile().militaryUnit != null -> true
            else -> false
        }
    }

    //endregion
}

class CityInfoReligionManager: Counter<String>(){
    @Transient
    lateinit var cityInfo: CityInfo

    fun getNumberOfFollowers(): Counter<String> {
        val totalInfluence = values.sum()
        val population = cityInfo.population.population
        if (totalInfluence > 100 * population) {
            val toReturn = Counter<String>()
            for ((key, value) in this)
                if (value > 100)
                    toReturn.add(key, value / 100)
            return toReturn
        }

        val toReturn = Counter<String>()

        for ((key, value) in this) {
            val percentage = value.toFloat() / totalInfluence
            val relativePopulation = (percentage * population).roundToInt()
            toReturn.add(key, relativePopulation)
        }
        return toReturn
    }

    fun getMajorityReligion():String? {
        val followersPerReligion = getNumberOfFollowers()
        if (followersPerReligion.isEmpty()) return null
        val religionWithMaxFollowers = followersPerReligion.maxByOrNull { it.value }!!
        if (religionWithMaxFollowers.value >= cityInfo.population.population) return religionWithMaxFollowers.key
        else return null
    }

    fun getAffectedBySurroundingCities() {
        val allCitiesWithin10Tiles = cityInfo.civInfo.gameInfo.civilizations.asSequence().flatMap { it.cities }
                .filter { it != cityInfo && it.getCenterTile().aerialDistanceTo(cityInfo.getCenterTile()) <= 10 }
        for (city in allCitiesWithin10Tiles) {
            val majorityReligionOfCity = city.religion.getMajorityReligion()
            if (majorityReligionOfCity == null) continue
            else add(majorityReligionOfCity, 6) // todo - when holy cities are implemented, *5
        }
    }
}

