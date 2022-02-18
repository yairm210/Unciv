package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.tile.*
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.toPercent
import kotlin.math.abs
import kotlin.math.min

open class TileInfo {
    @Transient
    lateinit var tileMap: TileMap

    @Transient
    lateinit var ruleset: Ruleset  // a tile can be a tile with a ruleset, even without a map.

    @Transient
    private var isCityCenterInternal = false

    @Transient
    var owningCity: CityInfo? = null
        private set

    fun setOwningCity(city:CityInfo?){
        owningCity = city
        isCityCenterInternal = getCity()?.location == position
    }

    @Transient
    private lateinit var baseTerrainObject: Terrain

    // These are for performance - checked with every tile movement and "canEnter" check, which makes them performance-critical
    @Transient
    var isLand = false

    @Transient
    var isWater = false

    @Transient
    var isOcean = false

    // This will be called often - farm can be built on Hill and tundra if adjacent to fresh water
    // and farms on adjacent to fresh water tiles will have +1 additional Food after researching Civil Service
    @delegate:Transient
    val isAdjacentToFreshwater: Boolean by lazy {
        matchesTerrainFilter("River") || matchesTerrainFilter("Fresh water")
                || neighbors.any { it.matchesTerrainFilter("Fresh water") }
    }

    var militaryUnit: MapUnit? = null
    var civilianUnit: MapUnit? = null
    var airUnits = ArrayList<MapUnit>()

    var position: Vector2 = Vector2.Zero
    lateinit var baseTerrain: String
    val terrainFeatures: ArrayList<String> = ArrayList()


    var naturalWonder: String? = null
    var resource: String? = null
    var resourceAmount: Int = 0
    var improvement: String? = null
    var improvementInProgress: String? = null

    var roadStatus = RoadStatus.None
    var turnsToImprovement: Int = 0

    fun isHill() = baseTerrain == Constants.hill || terrainFeatures.contains(Constants.hill)

    var hasBottomRightRiver = false
    var hasBottomRiver = false
    var hasBottomLeftRiver = false

    private var continent = -1

    val latitude: Float
        get() = HexMath.getLatitude(position)
    val longitude: Float
        get() = HexMath.getLongitude(position)

    fun clone(): TileInfo {
        val toReturn = TileInfo()
        toReturn.tileMap = tileMap
        toReturn.ruleset = ruleset
        toReturn.isCityCenterInternal = isCityCenterInternal
        toReturn.owningCity = owningCity
        toReturn.baseTerrainObject = baseTerrainObject
        toReturn.isLand = isLand
        toReturn.isWater = isWater
        toReturn.isOcean = isOcean
        if (militaryUnit != null) toReturn.militaryUnit = militaryUnit!!.clone()
        if (civilianUnit != null) toReturn.civilianUnit = civilianUnit!!.clone()
        for (airUnit in airUnits) toReturn.airUnits.add(airUnit.clone())
        toReturn.position = position.cpy()
        toReturn.baseTerrain = baseTerrain
        toReturn.terrainFeatures.addAll(terrainFeatures)
        toReturn.naturalWonder = naturalWonder
        toReturn.resource = resource
        toReturn.resourceAmount = resourceAmount
        toReturn.improvement = improvement
        toReturn.improvementInProgress = improvementInProgress
        toReturn.roadStatus = roadStatus
        toReturn.turnsToImprovement = turnsToImprovement
        toReturn.hasBottomLeftRiver = hasBottomLeftRiver
        toReturn.hasBottomRightRiver = hasBottomRightRiver
        toReturn.hasBottomRiver = hasBottomRiver
        toReturn.continent = continent
        return toReturn
    }

    fun containsGreatImprovement(): Boolean {
        return getTileImprovement()?.isGreatImprovement() == true
    }

    fun containsUnfinishedGreatImprovement(): Boolean {
        if (improvementInProgress == null) return false
        return ruleset.tileImprovements[improvementInProgress!!]!!.isGreatImprovement()
    }
    //region pure functions

    /** Returns military, civilian and air units in tile */
    fun getUnits() = sequence {
        if (militaryUnit != null) yield(militaryUnit!!)
        if (civilianUnit != null) yield(civilianUnit!!)
        if (airUnits.isNotEmpty()) yieldAll(airUnits)
    }

    /** This is for performance reasons of canPassThrough() - faster than getUnits().firstOrNull() */
    fun getFirstUnit(): MapUnit? {
        if (militaryUnit != null) return militaryUnit!!
        if (civilianUnit != null) return civilianUnit!!
        if (airUnits.isNotEmpty()) return airUnits.first()
        return null
    }

    /** Return null if military/air units on tile, or no civilian */
    fun getUnguardedCivilian(): MapUnit? {
        if (militaryUnit != null) return null
        if (airUnits.isNotEmpty()) return null
        if (civilianUnit != null) return civilianUnit!!
        return null
    }

    fun getCity(): CityInfo? = owningCity

    fun getLastTerrain(): Terrain = when {
        terrainFeatures.isNotEmpty() -> ruleset.terrains[terrainFeatures.last()]
                ?: getBaseTerrain()  // defense against rare edge cases involving baseTerrain Hill deprecation
        naturalWonder != null -> getNaturalWonder()
        else -> getBaseTerrain()
    }

    @delegate:Transient
    val tileResource: TileResource by lazy {
        if (resource == null) throw Exception("No resource exists for this tile!")
        else if (!ruleset.tileResources.containsKey(resource!!)) throw Exception("Resource $resource does not exist in this ruleset!")
        else ruleset.tileResources[resource!!]!!
    }

    private fun getNaturalWonder(): Terrain =
            if (naturalWonder == null) throw Exception("No natural wonder exists for this tile!")
            else ruleset.terrains[naturalWonder!!]!!

    fun isCityCenter(): Boolean = isCityCenterInternal
    fun isNaturalWonder(): Boolean = naturalWonder != null
    fun isImpassible() = getLastTerrain().impassable

    fun getTileImprovement(): TileImprovement? = if (improvement == null) null else ruleset.tileImprovements[improvement!!]
    fun getTileImprovementInProgress(): TileImprovement? = if (improvementInProgress == null) null else ruleset.tileImprovements[improvementInProgress!!]

    fun getShownImprovement(viewingCiv: CivilizationInfo?): String? {
        return if (viewingCiv == null || viewingCiv.playerType == PlayerType.AI)
            improvement
        else
            viewingCiv.lastSeenImprovement[position]
    }


    // This is for performance - since we access the neighbors of a tile ALL THE TIME,
    // and the neighbors of a tile never change, it's much more efficient to save the list once and for all!
    @delegate:Transient
    val neighbors: Sequence<TileInfo> by lazy { getTilesAtDistance(1).toList().asSequence() }
    // We have to .toList() so that the values are stored together once for caching,
    // and the toSequence so that aggregations (like neighbors.flatMap{it.units} don't take up their own space

    /** Returns the left shared neighbor of [this] and [neighbor] (relative to the view direction [this]->[neighbor]), or null if there is no such tile. */
    fun getLeftSharedNeighbor(neighbor: TileInfo): TileInfo? {
        return tileMap.getClockPositionNeighborTile(this,(tileMap.getNeighborTileClockPosition(this, neighbor) - 2) % 12)
    }

    /** Returns the right shared neighbor [this] and [neighbor] (relative to the view direction [this]->[neighbor]), or null if there is no such tile. */
    fun getRightSharedNeighbor(neighbor: TileInfo): TileInfo? {
        return tileMap.getClockPositionNeighborTile(this,(tileMap.getNeighborTileClockPosition(this, neighbor) + 2) % 12)
    }

    @delegate:Transient
    val height : Int by lazy {
        getAllTerrains().flatMap { it.uniqueObjects }
            .filter { it.isOfType(UniqueType.VisibilityElevation) }
            .map { it.params[0].toInt() }.sum()
    }


    fun getBaseTerrain(): Terrain = baseTerrainObject

    fun getOwner(): CivilizationInfo? {
        val containingCity = getCity() ?: return null
        return containingCity.civInfo
    }

    fun isFriendlyTerritory(civInfo: CivilizationInfo): Boolean {
        val tileOwner = getOwner()
        return when {
            tileOwner == null -> false
            tileOwner == civInfo -> true
            !civInfo.knows(tileOwner) -> false
            else -> tileOwner.getDiplomacyManager(civInfo).isConsideredFriendlyTerritory()
        }
    }
    
    fun isEnemyTerritory(civInfo: CivilizationInfo): Boolean {
        val tileOwner = getOwner() ?: return false
        return civInfo.isAtWarWith(tileOwner)
    }

    fun getTerrainFeatures(): List<Terrain> = terrainFeatures.mapNotNull { ruleset.terrains[it] }
    fun getAllTerrains(): Sequence<Terrain> = sequence {
        yield(baseTerrainObject)
        if (naturalWonder != null) yield(getNaturalWonder())
        yieldAll(terrainFeatures.asSequence().mapNotNull { ruleset.terrains[it] })
    }

    fun isRoughTerrain() = getAllTerrains().any{ it.isRough() }

    fun hasUnique(unique: String) = getAllTerrains().any { it.hasUnique(unique) }
    fun hasUnique(uniqueType: UniqueType) = getAllTerrains().any { it.hasUnique(uniqueType) }

    fun getWorkingCity(): CityInfo? {
        val civInfo = getOwner() ?: return null
        return civInfo.cities.firstOrNull { it.isWorked(this) }
    }

    fun isWorked(): Boolean = getWorkingCity() != null
    fun providesYield() = getCity() != null && (isCityCenter() || isWorked()
            || getTileImprovement()?.hasUnique(UniqueType.TileProvidesYieldWithoutPopulation) == true
            || hasUnique(UniqueType.TileProvidesYieldWithoutPopulation))

    fun isLocked(): Boolean {
        val workingCity = getWorkingCity()
        return workingCity != null && workingCity.lockedTiles.contains(position)
    }

    fun getTileStats(observingCiv: CivilizationInfo?): Stats = getTileStats(getCity(), observingCiv)

    fun getTileStats(city: CityInfo?, observingCiv: CivilizationInfo?): Stats {
        var stats = getBaseTerrain().cloneStats()

        for (terrainFeatureBase in getTerrainFeatures()) {
            when {
                terrainFeatureBase.hasUnique(UniqueType.NullifyYields) ->
                    return terrainFeatureBase.cloneStats()
                terrainFeatureBase.overrideStats -> stats = terrainFeatureBase.cloneStats()
                else -> stats.add(terrainFeatureBase)
            }
        }

        if (naturalWonder != null) {
            val wonderStats = getNaturalWonder().cloneStats()

            // Spain doubles tile yield
            if (city != null && city.civInfo.hasUnique("Tile yields from Natural Wonders doubled")) {
                wonderStats.timesInPlace(2f)
            }

            if (getNaturalWonder().overrideStats)
                stats = wonderStats
            else
                stats.add(wonderStats)
        }

        if (city != null) {
            var tileUniques = city.getMatchingUniques(UniqueType.StatsFromTiles)
                .filter { city.matchesFilter(it.params[2]) }
            tileUniques += city.getMatchingUniques(UniqueType.StatsFromObject)
            for (unique in tileUniques) {
                val tileType = unique.params[1]
                if (tileType == improvement) continue // This is added to the calculation in getImprovementStats. we don't want to add it twice
                if (matchesTerrainFilter(tileType, observingCiv)) 
                    stats.add(unique.stats)
                if (tileType == "Natural Wonder" && naturalWonder != null && city.civInfo.hasUnique("Tile yields from Natural Wonders doubled")) {
                    stats.add(unique.stats)
                }
            }

            for (unique in city.getMatchingUniques(UniqueType.StatsFromTilesWithout))
                if (
                    matchesTerrainFilter(unique.params[1]) &&
                    !matchesTerrainFilter(unique.params[2]) &&
                    city.matchesFilter(unique.params[3])
                )
                    stats.add(unique.stats)
        }

        if (isAdjacentToRiver()) stats.gold++

        if (observingCiv != null) {
            // resource base
            if (hasViewableResource(observingCiv)) stats.add(tileResource)

            val improvement = getTileImprovement()
            if (improvement != null)
                stats.add(getImprovementStats(improvement, observingCiv, city))

            if (isCityCenter()) {
                if (stats.food < 2) stats.food = 2f
                if (stats.production < 1) stats.production = 1f
            }

            if (stats.gold != 0f && observingCiv.goldenAges.isGoldenAge())
                stats.gold++
        }
        for ((stat, value) in stats)
            if (value < 0f) stats[stat] = 0f

        return stats
    }

    fun getTileStartScore(): Float {
        var sum = 0f
        for (tile in getTilesInDistance(2)) {
            val tileYield = tile.getTileStartYield(tile == this)
            sum += tileYield
            if (tile in neighbors)
                sum += tileYield
        }

        if (isHill())
            sum -= 2f
        if (isAdjacentToRiver())
            sum += 2f
        if (neighbors.any { it.baseTerrain == Constants.mountain })
            sum += 2f
        if (isCoastalTile())
            sum += 3f
        if (!isCoastalTile() && neighbors.any { it.isCoastalTile() })
            sum -= 7f

        return sum
    }

    private fun getTileStartYield(isCenter: Boolean): Float {
        var stats = getBaseTerrain().cloneStats()

        for (terrainFeatureBase in getTerrainFeatures()) {
            if (terrainFeatureBase.overrideStats)
                stats = terrainFeatureBase.cloneStats()
            else
                stats.add(terrainFeatureBase)
        }
        if (resource != null) stats.add(tileResource)

        if (stats.production < 0) stats.production = 0f
        if (isCenter) {
            if (stats.food < 2) stats.food = 2f
            if (stats.production < 1) stats.production = 1f
        }

        return stats.food + stats.production + stats.gold
    }

    // For dividing the map into Regions to determine start locations
    fun getTileFertility(checkCoasts: Boolean): Int {
        val terrains = getAllTerrains()
        var fertility = 0
        for (terrain in terrains) {
            if (terrain.hasUnique(UniqueType.OverrideFertility))
                return terrain.getMatchingUniques(UniqueType.OverrideFertility).first().params[0].toInt()
            else
                fertility += terrain.getMatchingUniques(UniqueType.AddFertility)
                    .sumOf { it.params[0].toInt() }
        }
        if (isAdjacentToRiver()) fertility += 1
        if (isAdjacentToFreshwater) fertility += 1 // meaning total +2 for river
        if (checkCoasts && isCoastalTile()) fertility += 2
        return fertility
    }

    fun getImprovementStats(improvement: TileImprovement, observingCiv: CivilizationInfo, city: CityInfo?): Stats {
        val stats = improvement.cloneStats()
        if (hasViewableResource(observingCiv) && tileResource.improvement == improvement.name
            && tileResource.improvementStats != null
        )
            stats.add(tileResource.improvementStats!!.clone()) // resource-specific improvement

        val conditionalState = StateForConditionals(civInfo = observingCiv, cityInfo = city)
        for (unique in improvement.getMatchingUniques(UniqueType.Stats, conditionalState)) {
            stats.add(unique.stats)
        }

        if (city != null) {
            val tileUniques = city.getMatchingUniques(UniqueType.StatsFromTiles, conditionalState)
                .filter { city.matchesFilter(it.params[2]) }
            val improvementUniques =
                improvement.getMatchingUniques(UniqueType.ImprovementStatsOnTile, conditionalState)

            for (unique in tileUniques + improvementUniques) {
                if (improvement.matchesFilter(unique.params[1])
                    // Freshwater and non-freshwater cannot be moved to matchesUniqueFilter since that creates an endless feedback.
                    // If you're attempting that, check that it works!
                    || unique.params[1] == "Fresh water" && isAdjacentToFreshwater
                    || unique.params[1] == "non-fresh water" && !isAdjacentToFreshwater
                )
                    stats.add(unique.stats)
            }

            for (unique in city.getMatchingUniques(UniqueType.StatsFromObject)) {
                if (improvement.matchesFilter(unique.params[1])) {
                    stats.add(unique.stats)
                }
            }
        }

        for (unique in improvement.getMatchingUniques(UniqueType.ImprovementStatsForAdjacencies)) {
            val adjacent = unique.params[1]
            val numberOfBonuses = neighbors.count {
                it.matchesFilter(adjacent, observingCiv)
                        || it.roadStatus.name == adjacent
            }
            stats.add(unique.stats.times(numberOfBonuses.toFloat()))
        }

        for (unique in observingCiv.getMatchingUniques(UniqueType.AllStatsPercentFromObject) +
                observingCiv.getMatchingUniques(UniqueType.AllStatsSignedPercentFromObject)
        )
            if (improvement.matchesFilter(unique.params[1]))
                stats.timesInPlace(unique.params[0].toPercent())

        return stats
    }

    /** Returns true if the [improvement] can be built on this [TileInfo] */
    fun canBuildImprovement(improvement: TileImprovement, civInfo: CivilizationInfo): Boolean {
        return when {
            improvement.uniqueTo != null && improvement.uniqueTo != civInfo.civName -> false
            improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!) -> false
            getOwner() != civInfo && !(
                improvement.hasUnique(UniqueType.CanBuildOutsideBorders)
                    || ( // citadel can be built only next to or within own borders
                        improvement.hasUnique(UniqueType.CanBuildJustOutsideBorders)
                        && neighbors.any { it.getOwner() == civInfo } && civInfo.cities.isNotEmpty()
                    )
                ) -> false
            improvement.getMatchingUniques(UniqueType.ObsoleteWith).any {
                civInfo.tech.isResearched(it.params[0])
            } -> return false
            improvement.getMatchingUniques(UniqueType.CannotBuildOnTile, StateForConditionals(civInfo=civInfo)).any {
                matchesTerrainFilter(it.params[0], civInfo)
            } -> false
            improvement.getMatchingUniques(UniqueType.ConsumesResources).any {
                civInfo.getCivResourcesByName()[it.params[1]]!! < it.params[0].toInt()
            } -> false
            // Calling this function does double the check for 'cannot be build on tile', but this is unavoidable.
            // Only in this function do we have the civInfo of the civ, so only here we can check whether
            // conditionals apply. Additionally, the function below is also called when determining if
            // an improvement can be on the tile in the given ruleset, in which case we do want to
            // assume that all conditionals apply, which is done automatically when we don't include
            // any state for conditionals. Therefore, duplicating the check is the easiest option.
            else -> canImprovementBeBuiltHere(improvement, hasViewableResource(civInfo))
        }
    }

    fun isAdjacentTo(terrainFilter:String): Boolean {
        if (terrainFilter == "Fresh water" && isAdjacentToFreshwater) return true
        return if (terrainFilter == "River") isAdjacentToRiver()
        else neighbors.any { neighbor -> neighbor.matchesFilter(terrainFilter) }
    }

    /** Without regards to what CivInfo it is, a lot of the checks are just for the improvement on the tile.
     *  Doubles as a check for the map editor.
     */
    fun canImprovementBeBuiltHere(improvement: TileImprovement, resourceIsVisible: Boolean = resource != null): Boolean {
        val topTerrain = getLastTerrain()

        return when {
            improvement.name == this.improvement -> false
            isCityCenter() -> false
            improvement.getMatchingUniques(UniqueType.CannotBuildOnTile).any {
                unique -> matchesTerrainFilter(unique.params[0])
            } -> false
            // Road improvements can change on tiles with irremovable improvements - nothing else can, though.
            RoadStatus.values().none { it.name == improvement.name || it.removeAction == improvement.name }
                    && getTileImprovement().let { it != null && it.hasUnique("Irremovable") } -> false

            // Terrain blocks BUILDING improvements - removing things (such as fallout) is fine
            !improvement.name.startsWith(Constants.remove) &&
                getAllTerrains().any { it.getMatchingUniques(UniqueType.RestrictedBuildableImprovements)
                .any { unique -> !improvement.matchesFilter(unique.params[0]) } } -> false

            // Decide cancelImprovementOrder earlier, otherwise next check breaks it
            improvement.name == Constants.cancelImprovementOrder -> (this.improvementInProgress != null)
            // Tiles with no terrains, and no turns to build, are like great improvements - they're placeable
            improvement.terrainsCanBeBuiltOn.isEmpty() && improvement.turnsToBuild == 0 && isLand -> true
            improvement.terrainsCanBeBuiltOn.contains(topTerrain.name) -> true
            improvement.uniqueObjects.filter { it.type == UniqueType.MustBeNextTo }.any {
                !isAdjacentTo(it.params[0])
            } -> false
            !isWater && RoadStatus.values().any { it.name == improvement.name && it > roadStatus } -> true
            improvement.name == roadStatus.removeAction -> true
            topTerrain.unbuildable && !improvement.isAllowedOnFeature(topTerrain.name) -> false
            // DO NOT reverse this &&. isAdjacentToFreshwater() is a lazy which calls a function, and reversing it breaks the tests.
            improvement.hasUnique(UniqueType.ImprovementBuildableByFreshWater) && isAdjacentToFreshwater -> true

            // If an unique of this type exists, we want all to match (e.g. Hill _and_ Forest would be meaningful).
            improvement.uniqueObjects.filter { it.placeholderText == "Can only be built on [] tiles" }.let {
                it.any() && it.all { unique -> matchesTerrainFilter(unique.params[0]) }
            } -> true

            else -> resourceIsVisible && tileResource.improvement == improvement.name
        }
    }

    /**
     * Implementation of _`tileFilter`_
     * @see <a href="https://github.com/yairm210/Unciv/wiki/uniques#user-content-tilefilter">tileFilter</a>
     */
    fun matchesFilter(filter: String, civInfo: CivilizationInfo? = null): Boolean {
        if (matchesTerrainFilter(filter, civInfo)) return true
        if (improvement != null && ruleset.tileImprovements[improvement]!!.matchesFilter(filter)) return true
        return improvement == null && filter == "unimproved"
    }

    fun matchesTerrainFilter(filter: String, observingCiv: CivilizationInfo? = null): Boolean {
        return when (filter) {
            "All" -> true
            baseTerrain -> true
            "Water" -> isWater
            "Land" -> isLand
            "Coastal" -> isCoastalTile()
            "River" -> isAdjacentToRiver()
            naturalWonder -> true
            "Open terrain" -> !isRoughTerrain()
            "Rough terrain" -> isRoughTerrain()
            "Foreign Land", "Foreign" -> observingCiv != null && !isFriendlyTerritory(observingCiv)
            "Friendly Land", "Friendly" -> observingCiv != null && isFriendlyTerritory(observingCiv)
            "Enemy Land", "Enemy" -> observingCiv != null && isEnemyTerritory(observingCiv)
            resource -> observingCiv != null && hasViewableResource(observingCiv)
            "Water resource" -> isWater && observingCiv != null && hasViewableResource(observingCiv)
            "Natural Wonder" -> naturalWonder != null
            "Featureless" -> terrainFeatures.isEmpty()
            "Fresh Water" -> isAdjacentToFreshwater
            else -> {
                if (terrainFeatures.contains(filter)) return true
                if (hasUnique(filter)) return true
                // Resource type check is last - cannot succeed if no resource here
                if (resource == null) return false
                // Checks 'luxury resource', 'strategic resource' and 'bonus resource' - only those that are visible of course
                // not using hasViewableResource as observingCiv is often not passed in,
                // and we want to be able to at least test for non-strategic in that case.
                val resourceObject = tileResource
                if (resourceObject.resourceType.name + " resource" != filter) return false // filter match
                if (resourceObject.revealedBy == null) return true  // no need for tech
                if (observingCiv == null) return false  // can't check tech
                return observingCiv.tech.isResearched(resourceObject.revealedBy!!)
            }
        }
    }

    fun hasImprovementInProgress() = improvementInProgress != null

    @delegate:Transient
    private val _isCoastalTile: Boolean by lazy { neighbors.any { it.baseTerrain == Constants.coast } }
    fun isCoastalTile() = _isCoastalTile

    fun hasViewableResource(civInfo: CivilizationInfo): Boolean =
            resource != null && (tileResource.revealedBy == null || civInfo.tech.isResearched(
                tileResource.revealedBy!!))

    fun getViewableTilesList(distance: Int): List<TileInfo> =
            tileMap.getViewableTiles(position, distance)

    fun getTilesInDistance(distance: Int): Sequence<TileInfo> =
            tileMap.getTilesInDistance(position, distance)

    fun getTilesInDistanceRange(range: IntRange): Sequence<TileInfo> =
            tileMap.getTilesInDistanceRange(position, range)

    fun getTilesAtDistance(distance: Int): Sequence<TileInfo> =
            tileMap.getTilesAtDistance(position, distance)

    fun getDefensiveBonus(): Float {
        var bonus = getLastTerrain().defenceBonus
        val tileImprovement = getTileImprovement()
        if (tileImprovement != null) {
            for (unique in tileImprovement.getMatchingUniques(UniqueType.DefensiveBonus))
                bonus += unique.params[0].toFloat() / 100
        }
        return bonus
    }

    fun aerialDistanceTo(otherTile: TileInfo): Int {
        val xDelta = position.x - otherTile.position.x
        val yDelta = position.y - otherTile.position.y
        val distance = maxOf(abs(xDelta), abs(yDelta), abs(xDelta - yDelta))

        var wrappedDistance = Float.MAX_VALUE
        if (tileMap.mapParameters.worldWrap) {
            val otherTileUnwrappedPos = tileMap.getUnWrappedPosition(otherTile.position)
            val xDeltaWrapped = position.x - otherTileUnwrappedPos.x
            val yDeltaWrapped = position.y - otherTileUnwrappedPos.y
            wrappedDistance = maxOf(abs(xDeltaWrapped), abs(yDeltaWrapped), abs(xDeltaWrapped - yDeltaWrapped))
        }

        return min(distance, wrappedDistance).toInt()
    }

    fun canBeSettled(): Boolean {
        if (isWater || isImpassible())
            return false
        if (getTilesInDistance(2).any { it.isCityCenter() } ||
                getTilesAtDistance(3).any { it.isCityCenter() && it.getContinent() == getContinent() })
                    return false
        return true
    }

    /** Shows important properties of this tile for debugging _only_, it helps to see what you're doing */
    override fun toString(): String {
        val lineList = arrayListOf("TileInfo @$position")
        if (!this::baseTerrain.isInitialized) return lineList[0] + ", uninitialized"
        if (isCityCenter()) lineList += getCity()!!.name
        lineList += baseTerrain
        for (terrainFeature in terrainFeatures) lineList += terrainFeature
        if (resource != null) {
            lineList += if (tileResource.resourceType == ResourceType.Strategic)
                    "{$resourceAmount} {$resource}"
                else
                    resource!!
        }
        if (naturalWonder != null) lineList += naturalWonder!!
        if (roadStatus !== RoadStatus.None && !isCityCenter()) lineList += roadStatus.name
        if (improvement != null) lineList += improvement!!
        if (civilianUnit != null) lineList += civilianUnit!!.name + " - " + civilianUnit!!.civInfo.civName
        if (militaryUnit != null) lineList += militaryUnit!!.name + " - " + militaryUnit!!.civInfo.civName
        if (this::baseTerrainObject.isInitialized && isImpassible()) lineList += Constants.impassable
        return lineList.joinToString()
    }

    /** The two tiles have a river between them */
    fun isConnectedByRiver(otherTile: TileInfo): Boolean {
        if (otherTile == this) throw Exception("Should not be called to compare to self!")

        return when (tileMap.getNeighborTileClockPosition(this, otherTile)) {
            2 -> otherTile.hasBottomLeftRiver // we're to the bottom-left of it
            4 -> hasBottomRightRiver // we're to the top-left of it
            6 -> hasBottomRiver // we're directly above it
            8 -> hasBottomLeftRiver // we're to the top-right of it
            10 -> otherTile.hasBottomRightRiver // we're to the bottom-right of it
            12 -> otherTile.hasBottomRiver // we're directly below it
            else -> throw Exception("Should never call this function on a non-neighbor!")
        }
    }

    @delegate:Transient
    private val isAdjacentToRiverLazy by lazy { neighbors.any { isConnectedByRiver(it) } }
    fun isAdjacentToRiver() = isAdjacentToRiverLazy

    /**
     * @returns whether units of [civInfo] can pass through this tile, considering only civ-wide filters.
     * Use [UnitMovementAlgorithms.canPassThrough] to check whether a specific unit can pass through a tile.
     */
    fun canCivPassThrough(civInfo: CivilizationInfo): Boolean {
        val tileOwner = getOwner()
        // comparing the CivInfo objects is cheaper than comparing strings
        if (tileOwner == null || tileOwner == civInfo) return true
        if (isCityCenter() && civInfo.isAtWarWith(tileOwner)
                && !getCity()!!.hasJustBeenConquered) return false
        if (!civInfo.canPassThroughTiles(tileOwner)) return false
        return true
    }

    fun toMarkup(viewingCiv: CivilizationInfo?): ArrayList<FormattedLine> {
        val lineList = ArrayList<FormattedLine>()
        val isViewableToPlayer = viewingCiv == null || UncivGame.Current.viewEntireMapForDebug
                || viewingCiv.viewableTiles.contains(this)

        if (isCityCenter()) {
            val city = getCity()!!
            var cityString = city.name.tr()
            if (isViewableToPlayer) cityString += " (${city.health})"
            lineList += FormattedLine(cityString)
            if (UncivGame.Current.viewEntireMapForDebug || city.civInfo == viewingCiv)
                lineList += city.cityConstructions.getProductionMarkup(ruleset)
        }
        lineList += FormattedLine(baseTerrain, link="Terrain/$baseTerrain")
        for (terrainFeature in terrainFeatures)
            lineList += FormattedLine(terrainFeature, link="Terrain/$terrainFeature")
        if (resource != null && (viewingCiv == null || hasViewableResource(viewingCiv)))
            lineList += if (tileResource.resourceType == ResourceType.Strategic)
                    FormattedLine("{$resource} ($resourceAmount)", link="Resource/$resource")
                else
                    FormattedLine(resource!!, link="Resource/$resource")
        if (resource != null && viewingCiv != null && hasViewableResource(viewingCiv)) {
            val tileImprovement = ruleset.tileImprovements[tileResource.improvement]
            if (tileImprovement?.techRequired != null
                && !viewingCiv.tech.isResearched(tileImprovement.techRequired!!)) {
                lineList += FormattedLine(
                    "Requires [${tileImprovement.techRequired}]",
                    link="Technology/${tileImprovement.techRequired}",
                    color= "#FAA"
                )
            }
        }
        if (naturalWonder != null)
            lineList += FormattedLine(naturalWonder!!, link="Terrain/$naturalWonder")
        if (roadStatus !== RoadStatus.None && !isCityCenter())
            lineList += FormattedLine(roadStatus.name, link="Improvement/${roadStatus.name}")
        val shownImprovement = getShownImprovement(viewingCiv)
        if (shownImprovement != null)
            lineList += FormattedLine(shownImprovement, link="Improvement/$shownImprovement")
        if (improvementInProgress != null && isViewableToPlayer) {
            val line = "{$improvementInProgress}" +
                if (turnsToImprovement > 0) " - $turnsToImprovement${Fonts.turn}" else " ({Under construction})"
            lineList += FormattedLine(line, link="Improvement/$improvementInProgress")
        }
        if (civilianUnit != null && isViewableToPlayer)
            lineList += FormattedLine(civilianUnit!!.name.tr() + " - " + civilianUnit!!.civInfo.civName.tr(),
                link="Unit/${civilianUnit!!.name}")
        if (militaryUnit != null && isViewableToPlayer) {
            val milUnitString = militaryUnit!!.name.tr() +
                (if (militaryUnit!!.health < 100) "(" + militaryUnit!!.health + ")" else "") +
                " - " + militaryUnit!!.civInfo.civName.tr()
            lineList += FormattedLine(milUnitString, link="Unit/${militaryUnit!!.name}")
        }
        val defenceBonus = getDefensiveBonus()
        if (defenceBonus != 0f) {
            var defencePercentString = (defenceBonus * 100).toInt().toString() + "%"
            if (!defencePercentString.startsWith("-")) defencePercentString = "+$defencePercentString"
            lineList += FormattedLine("[$defencePercentString] to unit defence")
        }
        if (isImpassible()) lineList += FormattedLine(Constants.impassable)

        return lineList
    }

    fun hasEnemyInvisibleUnit(viewingCiv: CivilizationInfo): Boolean {
        val unitsInTile = getUnits()
        if (unitsInTile.none()) return false
        if (unitsInTile.first().civInfo != viewingCiv &&
                unitsInTile.firstOrNull { it.isInvisible(viewingCiv) } != null) {
            return true
        }
        return false
    }

    fun hasConnection(civInfo: CivilizationInfo) =
            roadStatus != RoadStatus.None || forestOrJungleAreRoads(civInfo)


    private fun forestOrJungleAreRoads(civInfo: CivilizationInfo) =
            civInfo.nation.forestsAndJunglesAreRoads
                    && (terrainFeatures.contains(Constants.jungle) || terrainFeatures.contains(Constants.forest))
                    && isFriendlyTerritory(civInfo)

    fun getRulesetIncompatibility(ruleset: Ruleset): HashSet<String> {
        val out = HashSet<String>()
        if (!ruleset.terrains.containsKey(baseTerrain))
            out.add("Base terrain [$baseTerrain] does not exist in ruleset!")
        for (terrainFeature in terrainFeatures.filter { !ruleset.terrains.containsKey(it) })
            out.add("Terrain feature [$terrainFeature] does not exist in ruleset!")
        if (resource != null && !ruleset.tileResources.containsKey(resource))
            out.add("Resource [$resource] does not exist in ruleset!")
        if (improvement != null && !improvement!!.startsWith(TileMap.startingLocationPrefix)
            && !ruleset.tileImprovements.containsKey(improvement))
            out.add("Improvement [$improvement] does not exist in ruleset!")
        return out
    }

    fun getContinent() = continent

    //endregion

    //region state-changing functions
    fun setTransients() {
        setTerrainTransients()
        setUnitTransients(true)
    }

    fun setTerrainTransients() {
        // Uninitialized tilemap - when you're displaying a tile in the civilopedia or map editor
        if (::tileMap.isInitialized) convertHillToTerrainFeature()
        if (!ruleset.terrains.containsKey(baseTerrain))
            throw Exception("Terrain $baseTerrain does not exist in ruleset!")
        baseTerrainObject = ruleset.terrains[baseTerrain]!!
        isWater = getBaseTerrain().type == TerrainType.Water
        isLand = getBaseTerrain().type == TerrainType.Land
        isOcean = baseTerrain == Constants.ocean

        // Resource amounts missing - Old save or bad mapgen?
        if (::tileMap.isInitialized && resource != null && tileResource.resourceType == ResourceType.Strategic && resourceAmount == 0) {
            // Let's assume it's a small deposit
            setTileResource(tileResource, majorDeposit = false)
        }
    }

    fun setUnitTransients(unitCivTransients: Boolean) {
        for (unit in getUnits()) {
            unit.currentTile = this
            if (unitCivTransients)
                unit.assignOwner(tileMap.gameInfo.getCivilization(unit.owner), false)
            unit.setTransients(ruleset)
        }
    }

    fun stripUnits() {
        for (unit in this.getUnits()) removeUnit(unit)
    }
    
    fun setTileResource(newResource: TileResource, majorDeposit: Boolean = false) {
        resource = newResource.name

        if (newResource.resourceType != ResourceType.Strategic) return

        for (unique in newResource.getMatchingUniques(UniqueType.ResourceAmountOnTiles)) {
            if (matchesTerrainFilter(unique.params[0])) {
                resourceAmount = unique.params[1].toInt()
                return
            }
        }

        resourceAmount = when (tileMap.mapParameters.mapResources) {
            MapResources.sparse -> {
                if (majorDeposit) newResource.majorDepositAmount.sparse
                else newResource.minorDepositAmount.sparse
            }
            MapResources.abundant -> {
                if (majorDeposit) newResource.majorDepositAmount.abundant
                else newResource.minorDepositAmount.abundant
            }
            else -> {
                if (majorDeposit) newResource.majorDepositAmount.default
                else newResource.minorDepositAmount.default
            }
        }
    }


    /** If the unit isn't in the ruleset we can't even know what type of unit this is! So check each place
     * This works with no transients so can be called from gameInfo.setTransients with no fear
     */
    fun removeUnit(mapUnit: MapUnit) {
        when {
            airUnits.contains(mapUnit) -> airUnits.remove(mapUnit)
            civilianUnit == mapUnit -> civilianUnit = null
            else -> militaryUnit = null
        }
    }

    fun startWorkingOnImprovement(improvement: TileImprovement, civInfo: CivilizationInfo) {
        improvementInProgress = improvement.name
        turnsToImprovement = if (civInfo.gameInfo.gameParameters.godMode) 1 else improvement.getTurnsToBuild(civInfo)
    }

    fun stopWorkingOnImprovement() {
        improvementInProgress = null
        turnsToImprovement = 0
    }

    fun normalizeToRuleset(ruleset: Ruleset) {
        if (!ruleset.terrains.containsKey(naturalWonder)) naturalWonder = null
        if (naturalWonder != null) {
            val naturalWonder = ruleset.terrains[naturalWonder]!!
            baseTerrain = naturalWonder.turnsInto!!
            terrainFeatures.clear()
            resource = null
            improvement = null
        }

        for (terrainFeature in terrainFeatures.toList()) {
            val terrainFeatureObject = ruleset.terrains[terrainFeature]
            if (terrainFeatureObject == null) {
                terrainFeatures.remove(terrainFeature)
                continue
            }

            if (terrainFeatureObject.occursOn.isNotEmpty() && !terrainFeatureObject.occursOn.contains(baseTerrain))
                terrainFeatures.remove(terrainFeature)
        }


        if (resource != null && !ruleset.tileResources.containsKey(resource)) resource = null
        if (resource != null) {
            val resourceObject = ruleset.tileResources[resource]!!
            if (resourceObject.terrainsCanBeFoundOn.none { it == baseTerrain || terrainFeatures.contains(it) })
                resource = null
        }

        // If we're checking this at gameInfo.setTransients, we can't check the top terrain
        if (improvement != null && ::baseTerrainObject.isInitialized) normalizeTileImprovement(ruleset)
        if (isWater || isImpassible())
            roadStatus = RoadStatus.None
    }

    private fun normalizeTileImprovement(ruleset: Ruleset) {
        val improvementObject = ruleset.tileImprovements[improvement]
        if (improvementObject == null) {
            improvement = null
            return
        }
        improvement = null // Unset, and check if it can be reset. If so, do it, if not, invalid.
        if (canImprovementBeBuiltHere(improvementObject)
                // Allow building 'other' improvements like city ruins, barb encampments, Great Improvements etc
                || (improvementObject.terrainsCanBeBuiltOn.isEmpty()
                        && ruleset.tileResources.values.none { it.improvement == improvementObject.name }
                        && !isImpassible() && isLand))
            improvement = improvementObject.name
    }

    private fun convertHillToTerrainFeature() {
        if (baseTerrain == Constants.hill &&
                ruleset.terrains[Constants.hill]?.type == TerrainType.TerrainFeature) {
            val mostCommonBaseTerrain = neighbors.filter { it.isLand && !it.isImpassible() }
                    .groupBy { it.baseTerrain }.maxByOrNull { it.value.size }
            baseTerrain = mostCommonBaseTerrain?.key ?: Constants.grassland
            //We have to add hill as first terrain feature
            val copy = terrainFeatures.toTypedArray()
            terrainFeatures.clear()
            terrainFeatures.add(Constants.hill)
            terrainFeatures.addAll(copy)
        }
    }

    /**
     * Assign a continent ID to this tile.
     *
     * Should only be set once at map generation.
     * @param continent Numeric ID >= 0
     * @throws Exception when tile already has a continent ID
     */
    fun setContinent(continent: Int) {
        if (this.continent != -1)
            throw Exception("Continent already assigned @ $position")
        this.continent = continent
    }

    /** Clear continent ID, for map editor */
    fun clearContinent() { continent = -1 }

    //endregion
}
