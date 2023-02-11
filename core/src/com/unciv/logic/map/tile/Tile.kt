package com.unciv.logic.map.tile

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.MapResources
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

open class Tile : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var tileMap: TileMap

    @Transient
    lateinit var ruleset: Ruleset  // a tile can be a tile with a ruleset, even without a map.

    @Transient
    val improvementFunctions = TileInfoImprovementFunctions(this)

    @Transient
    val stats = TileStatFunctions(this)

    @Transient
    private var isCityCenterInternal = false

    @Transient
    var owningCity: City? = null
        private set

    fun setOwningCity(city:City?){
        if (city != null) {
            if (roadStatus != RoadStatus.None && roadOwner != "") {
                // remove previous neutral tile owner
                getRoadOwner()!!.neutralRoads.remove(this.position)
            }
            roadOwner = city.civ.civName // only when taking control, otherwise last owner
        } else {
            if (roadStatus != RoadStatus.None && owningCity != null) {
                // previous tile owner still owns road, add to tracker
                owningCity!!.civ.neutralRoads.add(this.position)
            }
        }
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

    var militaryUnit: MapUnit? = null
    var civilianUnit: MapUnit? = null
    var airUnits = ArrayList<MapUnit>()

    var position: Vector2 = Vector2.Zero
    lateinit var baseTerrain: String
    var terrainFeatures: List<String> = listOf()
        private set

    var exploredBy = HashSet<String>()

    @Transient
    var terrainFeatureObjects: List<Terrain> = listOf()
        private set

    @Transient
    /** Saves a sequence of a list */
    var allTerrains: Sequence<Terrain> = sequenceOf()
        private set

    @Transient
    var terrainUniqueMap = UniqueMap()
        private set

    @Transient
    /** Between 0.0 and 1.0 - For map generation use only */
    var humidity:Double? = null

    @Transient
    /** Between -1.0 and 1.0 - For map generation use only */
    var temperature:Double? = null

    var naturalWonder: String? = null
    var resource: String? = null
        set(value) {
            tileResourceCache = null
            field = value
        }
    var resourceAmount: Int = 0
    var improvement: String? = null
    var improvementInProgress: String? = null
    var improvementIsPillaged = false

    var roadStatus = RoadStatus.None
    var roadIsPillaged = false
    var roadOwner: String = "" // either who last built the road or last owner of tile
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

    fun clone(): Tile {
        val toReturn = Tile()
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
        toReturn.terrainFeatures = terrainFeatures // immutable lists can be directly passed around
        toReturn.terrainFeatureObjects = terrainFeatureObjects
        toReturn.naturalWonder = naturalWonder
        toReturn.resource = resource
        toReturn.resourceAmount = resourceAmount
        toReturn.improvement = improvement
        toReturn.improvementInProgress = improvementInProgress
        toReturn.improvementIsPillaged = improvementIsPillaged
        toReturn.roadStatus = roadStatus
        toReturn.roadIsPillaged = roadIsPillaged
        toReturn.roadOwner = roadOwner
        toReturn.turnsToImprovement = turnsToImprovement
        toReturn.hasBottomLeftRiver = hasBottomLeftRiver
        toReturn.hasBottomRightRiver = hasBottomRightRiver
        toReturn.hasBottomRiver = hasBottomRiver
        toReturn.continent = continent
        toReturn.exploredBy.addAll(exploredBy)
        return toReturn
    }

    //region pure functions

    fun containsGreatImprovement(): Boolean {
        return getTileImprovement()?.isGreatImprovement() == true
    }

    fun containsUnpillagedGreatImprovement(): Boolean {
        return getUnpillagedTileImprovement()?.isGreatImprovement() == true
    }

    fun containsUnfinishedGreatImprovement(): Boolean {
        if (improvementInProgress == null) return false
        return ruleset.tileImprovements[improvementInProgress!!]!!.isGreatImprovement()
    }

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

    /** Return null if military on tile, or no civilian */
    fun getUnguardedCivilian(attacker: MapUnit): MapUnit? {
        if (militaryUnit != null && militaryUnit != attacker) return null
        if (civilianUnit != null) return civilianUnit!!
        return null
    }

    fun getCity(): City? = owningCity

    fun getLastTerrain(): Terrain = when {
        terrainFeatures.isNotEmpty() -> ruleset.terrains[terrainFeatures.last()]
                ?: getBaseTerrain()  // defense against rare edge cases involving baseTerrain Hill deprecation
        naturalWonder != null -> getNaturalWonder()
        else -> getBaseTerrain()
    }

    @Transient
    private var tileResourceCache: TileResource? = null
    val tileResource: TileResource
        get() {
            if (tileResourceCache == null) {
                if (resource == null) throw Exception("No resource exists for this tile!")
                if (!ruleset.tileResources.containsKey(resource!!)) throw Exception("Resource $resource does not exist in this ruleset!")
                tileResourceCache = ruleset.tileResources[resource!!]!!
            }
            return tileResourceCache!!
        }

    internal fun getNaturalWonder(): Terrain =
            if (naturalWonder == null) throw Exception("No natural wonder exists for this tile!")
            else ruleset.terrains[naturalWonder!!]!!

    fun isVisible(player: Civilization): Boolean {
        if (UncivGame.Current.viewEntireMapForDebug)
            return true
        return player.viewableTiles.contains(this)
    }

    fun isExplored(player: Civilization): Boolean {
        if (UncivGame.Current.viewEntireMapForDebug || player.isSpectator())
            return true
        return exploredBy.contains(player.civName) || player.exploredTiles.contains(position)
    }

    fun setExplored(player: Civilization, isExplored: Boolean) {
        if (isExplored) {
            exploredBy.add(player.civName)
            player.exploredTiles.add(position)
        } else {
            exploredBy.remove(player.civName)
            player.exploredTiles.remove(position)
        }
    }

    fun isCityCenter(): Boolean = isCityCenterInternal
    fun isNaturalWonder(): Boolean = naturalWonder != null
    fun isImpassible() = getLastTerrain().impassable

    fun getTileImprovement(): TileImprovement? = if (improvement == null) null else ruleset.tileImprovements[improvement!!]
    fun getUnpillagedTileImprovement(): TileImprovement? = if (getUnpillagedImprovement() == null) null else ruleset.tileImprovements[improvement!!]
    fun getTileImprovementInProgress(): TileImprovement? = if (improvementInProgress == null) null else ruleset.tileImprovements[improvementInProgress!!]

    fun getImprovementToPillage(): TileImprovement? {
        if (canPillageTileImprovement())
            return ruleset.tileImprovements[improvement]!!
        if (canPillageRoad())
            return ruleset.tileImprovements[roadStatus.name]!!
        return null
    }
    // same as above, but slightly quicker
    fun getImprovementToPillageName(): String? {
        if (canPillageTileImprovement())
            return improvement
        if (canPillageRoad())
            return roadStatus.name
        return null
    }
    fun getImprovementToRepair(): TileImprovement? {
        if (improvement != null && improvementIsPillaged)
            return ruleset.tileImprovements[improvement]!!
        if (roadStatus != RoadStatus.None && roadIsPillaged)
            return ruleset.tileImprovements[roadStatus.name]!!
        return null
    }
    fun canPillageTile(): Boolean {
        return canPillageTileImprovement() || canPillageRoad()
    }
    fun canPillageTileImprovement(): Boolean {
        return improvement != null && !improvementIsPillaged
                && !ruleset.tileImprovements[improvement]!!.hasUnique(UniqueType.Unpillagable)
                && !ruleset.tileImprovements[improvement]!!.hasUnique(UniqueType.Irremovable)
    }
    fun canPillageRoad(): Boolean {
        return roadStatus != RoadStatus.None && !roadIsPillaged
                && !ruleset.tileImprovements[roadStatus.name]!!.hasUnique(UniqueType.Unpillagable)
                && !ruleset.tileImprovements[roadStatus.name]!!.hasUnique(UniqueType.Irremovable)
    }
    fun getUnpillagedImprovement(): String? = if (improvementIsPillaged) null else improvement
    fun getUnpillagedRoad(): RoadStatus = if (roadIsPillaged) RoadStatus.None else roadStatus
    fun getUnpillagedRoadImprovement(): TileImprovement? {
        return if (getUnpillagedRoad() == RoadStatus.None) null
        else ruleset.tileImprovements[getUnpillagedRoad().name]
    }

    fun changeImprovement(improvementStr: String?) {
        improvementIsPillaged = false
        improvement = improvementStr
    }

    // function handling when adding a road to the tile
    fun addRoad(roadType: RoadStatus, unitCivInfo: Civilization) {
        roadStatus = roadType
        roadIsPillaged = false
        if (getOwner() == null) {
            roadOwner = unitCivInfo.civName // neutral tile, use building unit
            unitCivInfo.neutralRoads.add(this.position)
        } else {
            roadOwner = getOwner()!!.civName
        }
    }

    // function handling when removing a road from the tile
    fun removeRoad() {
        roadIsPillaged = false
        if (roadStatus == RoadStatus.None) return
        roadStatus = RoadStatus.None
        if (owningCity == null)
            getRoadOwner()?.neutralRoads?.remove(this.position)
    }

    fun getShownImprovement(viewingCiv: Civilization?): String? {
        return if (viewingCiv == null || viewingCiv.playerType == PlayerType.AI || viewingCiv.isSpectator())
            improvement
        else
            viewingCiv.lastSeenImprovement[position]
    }


    // This is for performance - since we access the neighbors of a tile ALL THE TIME,
    // and the neighbors of a tile never change, it's much more efficient to save the list once and for all!
    @delegate:Transient
    val neighbors: Sequence<Tile> by lazy { getTilesAtDistance(1).toList().asSequence() }
    // We have to .toList() so that the values are stored together once for caching,
    // and the toSequence so that aggregations (like neighbors.flatMap{it.units} don't take up their own space

    /** Returns the left shared neighbor of `this` and [neighbor] (relative to the view direction `this`->[neighbor]), or null if there is no such tile. */
    fun getLeftSharedNeighbor(neighbor: Tile): Tile? {
        return tileMap.getClockPositionNeighborTile(this,(tileMap.getNeighborTileClockPosition(this, neighbor) - 2) % 12)
    }

    /** Returns the right shared neighbor of `this` and [neighbor] (relative to the view direction `this`->[neighbor]), or null if there is no such tile. */
    fun getRightSharedNeighbor(neighbor: Tile): Tile? {
        return tileMap.getClockPositionNeighborTile(this,(tileMap.getNeighborTileClockPosition(this, neighbor) + 2) % 12)
    }

    fun getRow() = HexMath.getRow(position)
    fun getColumn() = HexMath.getColumn(position)

    @delegate:Transient
    val tileHeight : Int by lazy { // for e.g. hill+forest this is 2, since forest is visible above units
        if (terrainHasUnique(UniqueType.BlocksLineOfSightAtSameElevation)) unitHeight + 1
        else unitHeight
    }

    @delegate:Transient
    val unitHeight : Int by lazy { // for e.g. hill+forest this is 1, since only hill provides height for units
        allTerrains.flatMap { it.getMatchingUniques(UniqueType.VisibilityElevation) }
            .map { it.params[0].toInt() }.sum()
    }


    fun getBaseTerrain(): Terrain = baseTerrainObject

    fun getOwner(): Civilization? {
        val containingCity = getCity() ?: return null
        return containingCity.civ
    }

    fun getRoadOwner(): Civilization? {
        return if (roadOwner != "")
            tileMap.gameInfo.getCivilization(roadOwner)
        else
            getOwner()
    }

    fun isFriendlyTerritory(civInfo: Civilization): Boolean {
        val tileOwner = getOwner()
        return when {
            tileOwner == null -> false
            tileOwner == civInfo -> true
            !civInfo.knows(tileOwner) -> false
            else -> tileOwner.getDiplomacyManager(civInfo).isConsideredFriendlyTerritory()
        }
    }

    fun isEnemyTerritory(civInfo: Civilization): Boolean {
        val tileOwner = getOwner() ?: return false
        return civInfo.isAtWarWith(tileOwner)
    }

    fun isRoughTerrain() = allTerrains.any{ it.isRough() }

    /** Checks whether any of the TERRAINS of this tile has a certain unique */
    fun terrainHasUnique(uniqueType: UniqueType) = terrainUniqueMap.getUniques(uniqueType).any()
    /** Get all uniques of this type that any TERRAIN on this tile has */
    fun getTerrainMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals = StateForConditionals(tile=this) ): Sequence<Unique> {
        return terrainUniqueMap.getMatchingUniques(uniqueType, stateForConditionals)
    }

    /** Get all uniques of this type that any part of this tile has: terrains, improvement, resource */
    fun getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals = StateForConditionals(tile=this)): Sequence<Unique> {
        var uniques = getTerrainMatchingUniques(uniqueType, stateForConditionals)
        if (getUnpillagedImprovement() != null){
            val tileImprovement = getTileImprovement()
            if (tileImprovement != null) {
                uniques += tileImprovement.getMatchingUniques(uniqueType, stateForConditionals)
            }
        }
        if (resource != null)
            uniques += tileResource.getMatchingUniques(uniqueType, stateForConditionals)
        return uniques
    }

    fun getWorkingCity(): City? {
        val civInfo = getOwner() ?: return null
        return civInfo.cities.firstOrNull { it.isWorked(this) }
    }

    fun isBlockaded(): Boolean {
        val owner = getOwner() ?: return false
        if (isLand) {
            val unit = militaryUnit ?: return false
            return owner.isAtWarWith(unit.civ)
        } else if (isWater) {

            // If tile has friendly military on it - then not blockaded!
            if (militaryUnit?.civ == owner)
                return false

            // Enemy water units blockade all adjacent tiles
            // So we need to check tile + water neighbors
            val tiles = sequence {
                yield(this@Tile)
                yieldAll(neighbors.filter { it.isWater })
            }

            for (tile in tiles) {
                if (tile.militaryUnit == null)
                    continue
                if (owner.isAtWarWith(tile.militaryUnit!!.civ))
                    return true
            }
            return false
        }
        return false
    }

    fun isWorked(): Boolean = getWorkingCity() != null
    fun providesYield() = getCity() != null && (isCityCenter() || isWorked()
            || getUnpillagedTileImprovement()?.hasUnique(UniqueType.TileProvidesYieldWithoutPopulation) == true
            || terrainHasUnique(UniqueType.TileProvidesYieldWithoutPopulation))

    fun isLocked(): Boolean {
        val workingCity = getWorkingCity()
        return workingCity != null && workingCity.lockedTiles.contains(position)
    }

    // For dividing the map into Regions to determine start locations
    fun getTileFertility(checkCoasts: Boolean): Int {
        var fertility = 0
        for (terrain in allTerrains) {
            if (terrain.hasUnique(UniqueType.OverrideFertility))
                return terrain.getMatchingUniques(UniqueType.OverrideFertility).first().params[0].toInt()
            else
                fertility += terrain.getMatchingUniques(UniqueType.AddFertility)
                    .sumOf { it.params[0].toInt() }
        }
        if (isAdjacentToRiver()) fertility += 1
        if (isAdjacentTo(Constants.freshWater)) fertility += 1 // meaning total +2 for river
        if (checkCoasts && isCoastalTile()) fertility += 2
        return fertility
    }

    // This should be the only adjacency function
    fun isAdjacentTo(terrainFilter:String): Boolean {
        // Rivers are odd, as they aren't technically part of any specific tile but still count towards adjacency
        if (terrainFilter == "River") return isAdjacentToRiver()
        if (terrainFilter == Constants.freshWater && isAdjacentToRiver()) return true
        return (neighbors + this).any { neighbor -> neighbor.matchesFilter(terrainFilter) }
    }

    /** Implements [UniqueParameterType.TileFilter][com.unciv.models.ruleset.unique.UniqueParameterType.TileFilter] */
    fun matchesFilter(filter: String, civInfo: Civilization? = null): Boolean {
        if (matchesTerrainFilter(filter, civInfo)) return true
        if (improvement != null && !improvementIsPillaged && ruleset.tileImprovements[improvement]!!.matchesFilter(filter)) return true
        return improvement == null && filter == "unimproved"
    }

    /** Implements [UniqueParameterType.TerrainFilter][com.unciv.models.ruleset.unique.UniqueParameterType.TerrainFilter] */
    fun matchesTerrainFilter(filter: String, observingCiv: Civilization? = null): Boolean {
        return when (filter) {
            "All" -> true
            baseTerrain -> true
            "Water" -> isWater
            "Land" -> isLand
            Constants.coastal -> isCoastalTile()
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
            Constants.freshWaterFilter -> isAdjacentTo(Constants.freshWater)
            else -> {
                if (terrainFeatures.contains(filter)) return true
                if (terrainUniqueMap.getUniques(filter).any()) return true

                // Resource type check is last - cannot succeed if no resource here
                if (resource == null) return false

                // Checks 'luxury resource', 'strategic resource' and 'bonus resource' - only those that are visible of course
                // not using hasViewableResource as observingCiv is often not passed in,
                // and we want to be able to at least test for non-strategic in that case.
                val resourceObject = tileResource
                val hasResourceWithFilter =
                        tileResource.name == filter
                                || tileResource.hasUnique(filter)
                                || tileResource.resourceType.name + " resource" == filter
                if (!hasResourceWithFilter) return false

                // Now that we know that this resource matches the filter - can the observer see that there's a resource here?
                if (resourceObject.revealedBy == null) return true  // no need for tech
                if (observingCiv == null) return false  // can't check tech
                return observingCiv.tech.isResearched(resourceObject.revealedBy!!)
            }
        }
    }

    fun hasImprovementInProgress() = improvementInProgress != null && turnsToImprovement > 0

    @delegate:Transient
    private val _isCoastalTile: Boolean by lazy { neighbors.any { it.baseTerrain == Constants.coast } }
    fun isCoastalTile() = _isCoastalTile

    fun hasViewableResource(civInfo: Civilization): Boolean =
            resource != null && (tileResource.revealedBy == null || civInfo.tech.isResearched(
                tileResource.revealedBy!!))

    fun getViewableTilesList(distance: Int): List<Tile> =
            tileMap.getViewableTiles(position, distance)

    fun getTilesInDistance(distance: Int): Sequence<Tile> =
            tileMap.getTilesInDistance(position, distance)

    fun getTilesInDistanceRange(range: IntRange): Sequence<Tile> =
            tileMap.getTilesInDistanceRange(position, range)

    fun getTilesAtDistance(distance: Int): Sequence<Tile> =
            tileMap.getTilesAtDistance(position, distance)

    fun getDefensiveBonus(): Float {
        var bonus = baseTerrainObject.defenceBonus
        if (terrainFeatureObjects.isNotEmpty()) {
            val otherTerrainBonus = terrainFeatureObjects.maxOf { it.defenceBonus }
            if (otherTerrainBonus != 0f) bonus = otherTerrainBonus  // replaces baseTerrainObject
        }
        if (naturalWonder != null) bonus += getNaturalWonder().defenceBonus
        val tileImprovement = getUnpillagedTileImprovement()
        if (tileImprovement != null) {
            for (unique in tileImprovement.getMatchingUniques(UniqueType.DefensiveBonus, StateForConditionals(tile = this)))
                bonus += unique.params[0].toFloat() / 100
        }
        return bonus
    }

    fun aerialDistanceTo(otherTile: Tile): Int {
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
        val modConstants = tileMap.gameInfo.ruleset.modOptions.constants
        if (isWater || isImpassible())
            return false
        if (getTilesInDistance(modConstants.minimalCityDistanceOnDifferentContinents)
                .any { it.isCityCenter() && it.getContinent() != getContinent() }
            || getTilesInDistance(modConstants.minimalCityDistance)
                .any { it.isCityCenter() && it.getContinent() == getContinent() }
        ) {
            return false
        }
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
        if (civilianUnit != null) lineList += civilianUnit!!.name + " - " + civilianUnit!!.civ.civName
        if (militaryUnit != null) lineList += militaryUnit!!.name + " - " + militaryUnit!!.civ.civName
        if (this::baseTerrainObject.isInitialized && isImpassible()) lineList += Constants.impassable
        return lineList.joinToString()
    }

    /** The two tiles have a river between them */
    fun isConnectedByRiver(otherTile: Tile): Boolean {
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
    fun canCivPassThrough(civInfo: Civilization): Boolean {
        val tileOwner = getOwner()
        // comparing the CivInfo objects is cheaper than comparing strings
        if (tileOwner == null || tileOwner == civInfo) return true
        if (isCityCenter() && civInfo.isAtWarWith(tileOwner)
                && !getCity()!!.hasJustBeenConquered) return false
        if (!civInfo.diplomacyFunctions.canPassThroughTiles(tileOwner)) return false
        return true
    }

    fun hasEnemyInvisibleUnit(viewingCiv: Civilization): Boolean {
        val unitsInTile = getUnits()
        if (unitsInTile.none()) return false
        if (unitsInTile.first().civ != viewingCiv &&
                unitsInTile.firstOrNull { it.isInvisible(viewingCiv) } != null) {
            return true
        }
        return false
    }

    fun hasConnection(civInfo: Civilization) =
            getUnpillagedRoad() != RoadStatus.None || forestOrJungleAreRoads(civInfo)


    private fun forestOrJungleAreRoads(civInfo: Civilization) =
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
        if (improvement != null && !ruleset.tileImprovements.containsKey(improvement))
            out.add("Improvement [$improvement] does not exist in ruleset!")
        if (naturalWonder != null && !ruleset.terrains.containsKey(naturalWonder))
            out.add("Natural Wonder [$naturalWonder] does not exist in ruleset!")
        return out
    }

    fun getContinent() = continent

    /** Checks if this tile is marked as target tile for a building with a [UniqueType.CreatesOneImprovement] unique */
    fun isMarkedForCreatesOneImprovement() =
        turnsToImprovement < 0 && improvementInProgress != null
    /** Checks if this tile is marked as target tile for a building with a [UniqueType.CreatesOneImprovement] unique creating a specific [improvement] */
    fun isMarkedForCreatesOneImprovement(improvement: String) =
        turnsToImprovement < 0 && improvementInProgress == improvement

    //endregion
    //region state-changing functions

    fun setTransients() {
        setTerrainTransients()
        setUnitTransients(true)
        setOwnerTransients()
    }

    fun setTerrainTransients() {
        if (!ruleset.terrains.containsKey(baseTerrain))
            throw Exception("Terrain $baseTerrain does not exist in ruleset!")
        baseTerrainObject = ruleset.terrains[baseTerrain]!!
        setTerrainFeatures(terrainFeatures)
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

    fun setOwnerTransients() {
        if (owningCity == null && roadOwner != "")
            getRoadOwner()!!.neutralRoads.add(this.position)
    }

    fun stripUnits() {
        for (unit in this.getUnits()) removeUnit(unit)
    }

    /**
     * Sets this tile's [resource] and, if [newResource] is a Strategic resource, [resourceAmount] fields.
     *
     * [resourceAmount] is determined by [MapParameters.mapResources] and [majorDeposit], and
     * if the latter is `null` a random choice between major and minor deposit is made, approximating
     * the frequency [MapRegions][com.unciv.logic.map.mapgenerator.MapRegions] would use.
     * A randomness source ([rng]) can optionally be provided for that step (not used otherwise).
     */
    fun setTileResource(newResource: TileResource, majorDeposit: Boolean? = null, rng: Random = Random.Default) {
        resource = newResource.name

        if (newResource.resourceType != ResourceType.Strategic) return

        for (unique in newResource.getMatchingUniques(UniqueType.ResourceAmountOnTiles, StateForConditionals(tile = this))) {
            if (matchesTerrainFilter(unique.params[0])) {
                resourceAmount = unique.params[1].toInt()
                return
            }
        }

        val majorDepositFinal = majorDeposit ?: (rng.nextDouble() < approximateMajorDepositDistribution())
        val depositAmounts = if (majorDepositFinal) newResource.majorDepositAmount else newResource.minorDepositAmount
        resourceAmount = when (tileMap.mapParameters.mapResources) {
            MapResources.sparse -> depositAmounts.sparse
            MapResources.abundant -> depositAmounts.abundant
            else -> depositAmounts.default
        }
    }

    private fun approximateMajorDepositDistribution(): Double {
        // We can't replicate the MapRegions resource distributor, so let's try to get
        // a close probability of major deposits per tile
        var probability = 0.0
        for (unique in allTerrains.flatMap { it.getMatchingUniques(UniqueType.MajorStrategicFrequency) }) {
            val frequency = unique.params[0].toIntOrNull() ?: continue
            if (frequency <= 0) continue
            // The unique param is literally "every N tiles", so to get a probability p=1/f
            probability += 1.0 / frequency
        }
        return if (probability == 0.0) 0.04  // This is the default of 1 per 25 tiles
            else probability
    }

    fun setTerrainFeatures(terrainFeatureList:List<String>) {
        terrainFeatures = terrainFeatureList
        terrainFeatureObjects = terrainFeatureList.mapNotNull { ruleset.terrains[it] }
        allTerrains = sequence {
            yield(baseTerrainObject) // There is an assumption here that base terrains do not change
            if (naturalWonder != null) yield(getNaturalWonder())
            yieldAll(terrainFeatureObjects)
        }.toList().asSequence() //Save in memory, and return as sequence

        val newUniqueMap = UniqueMap()
        for (terrain in allTerrains)
            newUniqueMap.addUniques(terrain.uniqueObjects)
        terrainUniqueMap = newUniqueMap
    }

    fun addTerrainFeature(terrainFeature:String) =
        setTerrainFeatures(ArrayList(terrainFeatures).apply { add(terrainFeature) })

    fun removeTerrainFeature(terrainFeature: String) =
        setTerrainFeatures(ArrayList(terrainFeatures).apply { remove(terrainFeature) })

    fun removeTerrainFeatures() =
        setTerrainFeatures(listOf())


    /** If the unit isn't in the ruleset we can't even know what type of unit this is! So check each place
     * This works with no transients so can be called from gameInfo.setTransients with no fear
     */
    fun removeUnit(mapUnit: MapUnit) {
        when {
            airUnits.contains(mapUnit) -> airUnits.remove(mapUnit)
            civilianUnit == mapUnit -> civilianUnit = null
            militaryUnit == mapUnit -> militaryUnit = null
        }
    }

    fun startWorkingOnImprovement(improvement: TileImprovement, civInfo: Civilization, unit: MapUnit) {
        improvementInProgress = improvement.name
        turnsToImprovement = if (civInfo.gameInfo.gameParameters.godMode) 1
            else improvement.getTurnsToBuild(civInfo, unit)
    }

    /** Clears [improvementInProgress] and [turnsToImprovement] */
    fun stopWorkingOnImprovement() {
        improvementInProgress = null
        turnsToImprovement = 0
    }

    /** Sets tile improvement to pillaged (without prior checks for validity)
     *  and ensures that matching [UniqueType.CreatesOneImprovement] queued buildings are removed. */
    fun setPillaged() {
        if (!canPillageTile())
            return
        // http://well-of-souls.com/civ/civ5_improvements.html says that naval improvements are destroyed upon pillage
        //    and I can't find any other sources so I'll go with that
        if (!isLand) { changeImprovement(null); return }

        // Setting turnsToImprovement might interfere with UniqueType.CreatesOneImprovement
        improvementFunctions.removeCreatesOneImprovementMarker()
        improvementInProgress = null  // remove any in progress work as well
        turnsToImprovement = 0
        // if no Repair action, destroy improvements instead
        if (ruleset.tileImprovements[Constants.repair] == null) {
            if (canPillageTileImprovement())
                changeImprovement(null)
            else
                removeRoad()
        } else {
            // otherwise use pillage/repair systems
            if (canPillageTileImprovement()) {
                improvementIsPillaged = true
            } else {
                roadIsPillaged = true
            }
        }
    }

    fun isPillaged(): Boolean {
        return improvementIsPillaged || roadIsPillaged
    }

    fun setRepaired() {
        improvementInProgress = null
        turnsToImprovement = 0
        if (improvementIsPillaged)
            improvementIsPillaged = false
        else
            roadIsPillaged = false
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
