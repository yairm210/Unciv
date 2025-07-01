package com.unciv.logic.map.tile

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.MultiFilter
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.UnitTurnManager
import com.unciv.logic.map.mapunit.movement.UnitMovement
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
import com.unciv.ui.components.fonts.Fonts
import com.unciv.utils.DebugUtils
import com.unciv.utils.Log
import com.unciv.utils.withItem
import com.unciv.utils.withoutItem
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class Tile : IsPartOfGameInfoSerialization, Json.Serializable {
    //region Serialized fields
    var militaryUnit: MapUnit? = null
    var civilianUnit: MapUnit? = null
    var airUnits = ArrayList<MapUnit>(0)

    var position: Vector2 = Vector2.Zero
    lateinit var baseTerrain: String
    var terrainFeatures: List<String> = listOf()
        private set

    /** Should be immutable - never be altered in-place, instead replaced */
    private var exploredBy = HashSet<String>(0)

    var naturalWonder: String? = null
    var resource: String? = null
        set(value) {
            tileResourceCache = null
            field = value
        }
    var resourceAmount: Int = 0

    var improvement: String? = null
    var improvementIsPillaged = false

    internal class ImprovementQueueEntry(
        val improvement: String, turnsToImprovement: Int
    ) : IsPartOfGameInfoSerialization {
        @Suppress("unused") // Gdx Json will find this constructor and use it
        private constructor() : this("", 0)
        var turnsToImprovement: Int = turnsToImprovement
            private set
        override fun toString() = "$improvement: $turnsToImprovement${Fonts.turn}"
        /** @return `true` if it's still counting and not finished */
        fun countDown(): Boolean {
            turnsToImprovement = (turnsToImprovement - 1).coerceAtLeast(0)
            return turnsToImprovement > 0
        }
    }
    private val improvementQueue = ArrayList<ImprovementQueueEntry>(1)

    var roadStatus = RoadStatus.None
    
    var roadIsPillaged = false
    private var roadOwner: String = "" // either who last built the road or last owner of tile

    var hasBottomRightRiver = false
    var hasBottomRiver = false
    var hasBottomLeftRiver = false

    var history: TileHistory = TileHistory()

    private var continent = -1
    //endregion

    //region Transient fields
    @Transient
    lateinit var tileMap: TileMap
    
    @Transient
    var zeroBasedIndex: Int = 0

    @Transient
    lateinit var ruleset: Ruleset  // a tile can be a tile with a ruleset, even without a map.

    @Transient
    val improvementFunctions = TileImprovementFunctions(this)

    @Transient
    val stats = TileStatFunctions(this)

    // This is for performance - since we access the neighbors of a tile ALL THE TIME,
    // and the neighbors of a tile never change, it's much more efficient to save the list once and for all!
    @delegate:Transient
    val neighbors: Sequence<Tile> by lazy { getTilesAtDistance(1).toList().asSequence() }
    // We have to .toList() so that the values are stored together once for caching,
    // and the toSequence so that aggregations (like neighbors.flatMap{it.units} don't take up their own space

    @Transient
    private var isCityCenterInternal = false

    @Transient
    var owningCity: City? = null
        private set

    @Transient
    private lateinit var baseTerrainObject: Terrain

    // These are for performance - checked with every tile movement and "canEnter" check, which makes them performance-critical
    @Transient
    var isLand = false

    @Transient
    var isWater = false

    @Transient
    var isOcean = false

    @delegate:Transient
    private val _isCoastalTile: Boolean by lazy { neighbors.any { it.baseTerrain == Constants.coast } }

    @Transient
    var unitHeight = 0

    @Transient
    var tileHeight = 0

    @Transient
    var terrainFeatureObjects: List<Terrain> = listOf()
        private set

    @Transient
    /** Saves a sequence of a list */
    var allTerrains: Sequence<Terrain> = emptySequence()
        private set

    @Transient
    lateinit var lastTerrain: Terrain
        private set

    @Transient
    var cachedTerrainData = TileMap.TerrainListData.EMPTY
        private set

    @Transient
    /** Between 0.0 and 1.0 - For map generation use only */
    var humidity: Double? = null

    @Transient
    /** Between -1.0 and 1.0 - For map generation use only */
    var temperature: Double? = null

    val latitude: Float
        get() = HexMath.getLatitude(position)
    val longitude: Float
        get() = HexMath.getLongitude(position)

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

    @Transient
    private var isAdjacentToRiver = false
    @Transient
    private var isAdjacentToRiverKnown = false

    val improvementInProgress get() = improvementQueue.firstOrNull()?.improvement
    val turnsToImprovement get() = improvementQueue.firstOrNull()?.turnsToImprovement ?: 0

    //endregion

    fun clone(/** For stat diff checks, units are meaningless */ addUnits:Boolean = true): Tile {
        val toReturn = Tile()
        toReturn.tileMap = tileMap
        toReturn.ruleset = ruleset
        toReturn.isCityCenterInternal = isCityCenterInternal
        toReturn.owningCity = owningCity
        toReturn.baseTerrainObject = baseTerrainObject
        toReturn.isLand = isLand
        toReturn.isWater = isWater
        toReturn.isOcean = isOcean
        if (addUnits) {
            if (militaryUnit != null) toReturn.militaryUnit = militaryUnit!!.clone()
            if (civilianUnit != null) toReturn.civilianUnit = civilianUnit!!.clone()
            for (airUnit in airUnits) toReturn.airUnits.add(airUnit.clone())
        }
        toReturn.position = position.cpy()
        toReturn.baseTerrain = baseTerrain
        toReturn.terrainFeatures = terrainFeatures // immutable lists can be directly passed around
        toReturn.terrainFeatureObjects = terrainFeatureObjects
        toReturn.naturalWonder = naturalWonder
        toReturn.resource = resource
        toReturn.resourceAmount = resourceAmount
        toReturn.improvement = improvement
        toReturn.improvementQueue.addAll(improvementQueue)
        toReturn.improvementIsPillaged = improvementIsPillaged
        toReturn.roadStatus = roadStatus
        toReturn.roadIsPillaged = roadIsPillaged
        toReturn.roadOwner = roadOwner
        toReturn.hasBottomLeftRiver = hasBottomLeftRiver
        toReturn.hasBottomRightRiver = hasBottomRightRiver
        toReturn.hasBottomRiver = hasBottomRiver
        toReturn.continent = continent
        toReturn.exploredBy = exploredBy
        toReturn.history = history.clone()
        // Setting even though it's transient - where it's needed, it's a real performance saver
        toReturn.tileResourceCache = tileResourceCache
        return toReturn
    }

    //region pure functions

    fun isHill() = baseTerrain == Constants.hill || terrainFeatures.contains(Constants.hill)

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

    fun getCity(): City? = owningCity

    internal fun getNaturalWonder(): Terrain =
            if (naturalWonder == null) throw Exception("No natural wonder exists for this tile!")
            else ruleset.terrains[naturalWonder!!]!!

    fun isVisible(player: Civilization): Boolean {
        if (DebugUtils.VISIBLE_MAP)
            return true
        return player.viewableTiles.contains(this)
    }

    fun isExplored(player: Civilization): Boolean {
        if (DebugUtils.VISIBLE_MAP || player.civName == Constants.spectator)
            return true
        return exploredBy.contains(player.civName)
    }

    fun isCityCenter(): Boolean = isCityCenterInternal
    fun isNaturalWonder(): Boolean = naturalWonder != null
    fun isImpassible() = lastTerrain.impassable

    fun hasImprovementInProgress() = improvementQueue.isNotEmpty()

    fun getTileImprovement(): TileImprovement? = if (improvement == null) null else ruleset.tileImprovements[improvement!!]
    fun isPillaged(): Boolean = improvementIsPillaged || roadIsPillaged
    fun getUnpillagedTileImprovement(): TileImprovement? = if (getUnpillagedImprovement() == null) null else ruleset.tileImprovements[improvement!!]
    fun getTileImprovementInProgress(): TileImprovement? = improvementQueue.firstOrNull()?.let { ruleset.tileImprovements[it.improvement] }
    fun containsGreatImprovement() = getTileImprovement()?.isGreatImprovement() == true

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
    
    /** @return [RoadStatus] on this [Tile], pillaged road counts as [RoadStatus.None] */
    fun getUnpillagedRoad(): RoadStatus = if (roadIsPillaged) RoadStatus.None else roadStatus

    fun getUnpillagedRoadImprovement(): TileImprovement? {
        return if (getUnpillagedRoad() == RoadStatus.None) null
        else ruleset.tileImprovements[getUnpillagedRoad().name]
    }

    /**
     *  Improvement to display, accounting for knowledge about a Tile possibly getting stale when a human player is no longer actively watching it.
     *  Relies on a Civilization's lastSeenImprovement always being up to date while the civ can see the Tile.
     *  @param viewingCiv `null` means civ-agnostic and thus always showing the actual improvement
     *  @return The improvement name, or `null` if no improvement should be shown
     */
    fun getShownImprovement(viewingCiv: Civilization?): String? =
        if (viewingCiv == null || viewingCiv.playerType == PlayerType.AI || viewingCiv.isSpectator()) improvement
        else viewingCiv.getLastSeenImprovement(position)

    /** Returns true if this tile has fallout or an equivalent terrain feature */
    fun hasFalloutEquivalent(): Boolean = terrainFeatures.any { ruleset.terrains[it]!!.hasUnique(UniqueType.NullifyYields)}


    fun getRow() = HexMath.getRow(position)
    fun getColumn() = HexMath.getColumn(position)

    fun getBaseTerrain(): Terrain = baseTerrainObject

    fun getOwner(): Civilization? = getCity()?.civ

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
            else -> tileOwner.getDiplomacyManager(civInfo)!!.isConsideredFriendlyTerritory()
        }
    }

    fun isEnemyTerritory(civInfo: Civilization): Boolean {
        val tileOwner = getOwner() ?: return false
        return civInfo.isAtWarWith(tileOwner)
    }

    fun isRoughTerrain() = allTerrains.any { it.isRough() }

    @Transient
    internal var stateThisTile: StateForConditionals = StateForConditionals.EmptyState
    /** Checks whether any of the TERRAINS of this tile has a certain unique */
    fun terrainHasUnique(uniqueType: UniqueType, state: StateForConditionals = stateThisTile) =
        cachedTerrainData.uniques.hasMatchingUnique(uniqueType, state)
    /** Get all uniques of this type that any TERRAIN on this tile has */
    fun getTerrainMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals = stateThisTile ): Sequence<Unique> {
        return cachedTerrainData.uniques.getMatchingUniques(uniqueType, stateForConditionals)
    }

    /** Get all uniques of this type that any part of this tile has: terrains, improvement, resource */
    fun getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals = stateThisTile): Sequence<Unique> {
        var uniques = getTerrainMatchingUniques(uniqueType, stateForConditionals)
        if (getUnpillagedImprovement() != null) {
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
        if (owningCity?.isWorked(this) == true) return owningCity // common case
        return civInfo.cities.firstOrNull { it != owningCity && it.isWorked(this) }
    }

    fun isBlockaded(): Boolean {
        val owner = getOwner() ?: return false
        val unit = militaryUnit

        if (unit != null) {
            return when {
                unit.civ == owner -> false              // Own - unblocks tile;
                unit.civ.isAtWarWith(owner) -> true     // Enemy - blocks tile;
                else -> false                           // Neutral - unblocks tile;
            }
        }
        if (isLand) // Only water tiles are blocked if empty
            return false

        // For water tiles need also to check neighbors:
        // enemy military naval units blockade all adjacent water tiles.
        for (neighbor in neighbors.filter { it.isWater }) {
            val neighborUnit = neighbor.militaryUnit ?: continue

            // Embarked units do not blockade adjacent tiles
            if (neighborUnit.civ.isAtWarWith(owner) && !neighborUnit.isEmbarked())
                return true
        }
        return false
    }

    fun isWorked(): Boolean = getWorkingCity() != null
    fun providesYield(): Boolean {
        if (getCity() == null) return false
        return isCityCenter()
                || isWorked()
                || getUnpillagedTileImprovement()?.hasUnique(UniqueType.TileProvidesYieldWithoutPopulation, stateThisTile) == true
                || terrainHasUnique(UniqueType.TileProvidesYieldWithoutPopulation)
    }

    fun isLocked(): Boolean {
        val workingCity = getWorkingCity()
        return workingCity != null && workingCity.lockedTiles.contains(position)
    }

    fun providesResources(civInfo: Civilization): Boolean {
        if (!hasViewableResource(civInfo)) return false
        if (isCityCenter()) {
            val possibleImprovements = tileResource.getImprovements()
            if (possibleImprovements.isEmpty()) return true
            // Per Civ V, resources under city tiles require the *possibility of extraction* -
            //  that is, there needs to be a tile improvement you have the tech for.
            // Does NOT take all GetImprovementBuildingProblems into account.
            return possibleImprovements.any { improvement ->
                ruleset.tileImprovements[improvement]?.let {
                    it.turnsToBuild != -1 && // Buildable by workers (not just 'placeable')
                        (it.techRequired == null || civInfo.tech.isResearched(it.techRequired!!))
                } == true
            }
        }
        val improvement = getUnpillagedTileImprovement()
        if (improvement != null && improvement.name in tileResource.getImprovements()
                && (improvement.techRequired == null || civInfo.tech.isResearched(improvement.techRequired!!))
            ) return true
        // TODO: Generic-ify to unique
        return (tileResource.resourceType == ResourceType.Strategic
            && improvement != null
            && improvement.isGreatImprovement())
    }

    // This should be the only adjacency function
    fun isAdjacentTo(terrainFilter: String, observingCiv: Civilization? = null): Boolean {
        // Rivers are odd, as they aren't technically part of any specific tile but still count towards adjacency
        if (terrainFilter == Constants.river) return isAdjacentToRiver()
        if (terrainFilter == Constants.freshWater && isAdjacentToRiver()) return true
        return neighbors.any { neighbor -> neighbor.matchesFilter(terrainFilter, observingCiv) }
    }

    /** Implements [UniqueParameterType.TileFilter][com.unciv.models.ruleset.unique.UniqueParameterType.TileFilter] */
    fun matchesFilter(filter: String, civInfo: Civilization? = null): Boolean {
        return MultiFilter.multiFilter(filter, { matchesSingleFilter(it, civInfo) })
    }

    private fun matchesSingleFilter(filter: String, civInfo: Civilization? = null): Boolean {
        if (matchesSingleTerrainFilter(filter, civInfo)) return true
        if ((improvement == null || improvementIsPillaged) && filter == "unimproved") return true
        if (improvement != null && !improvementIsPillaged && filter == "improved") return true
        if (isPillaged() && filter == "pillaged") return true
        if (filter == "worked" && isWorked()) return true
        if (getUnpillagedTileImprovement()?.matchesFilter(filter, stateThisTile, false) == true) return true
        return getUnpillagedRoadImprovement()?.matchesFilter(filter, stateThisTile, false) == true
    }

    /** Implements [UniqueParameterType.TerrainFilter][com.unciv.models.ruleset.unique.UniqueParameterType.TerrainFilter] */
    fun matchesTerrainFilter(filter: String, observingCiv: Civilization?, multiFilter: Boolean = true): Boolean {
        return if (multiFilter) MultiFilter.multiFilter(filter, { matchesSingleTerrainFilter(it, observingCiv) })
        else matchesSingleTerrainFilter(filter, observingCiv)
    }
    

    private fun matchesSingleTerrainFilter(filter: String, observingCiv: Civilization?): Boolean {
        // Constant strings get their own 'when' for performance - 
        //  see https://yairm210.medium.com/kotlin-when-string-optimization-e15c6eea2734
        when (filter) {
            "Terrain" -> return true
            "All", "all" -> return true
            "Water" -> return isWater
            "Land" -> return isLand
            Constants.coastal -> return isCoastalTile()
            Constants.river -> return isAdjacentToRiver()
            "Unowned" -> return getOwner() == null
            "your" -> return observingCiv != null && getOwner() == observingCiv
            "Foreign Land", "Foreign" -> return observingCiv != null && !isFriendlyTerritory(observingCiv)
            "Friendly Land", "Friendly" -> return observingCiv != null && isFriendlyTerritory(observingCiv)
            "Enemy Land", "Enemy" -> return observingCiv != null && isEnemyTerritory(observingCiv)
            "resource" -> return observingCiv != null && hasViewableResource(observingCiv)
            "Water resource" -> return isWater && observingCiv != null && hasViewableResource(observingCiv)
            "Featureless" -> return terrainFeatures.isEmpty()
            "Open terrain" -> return allTerrains.all { !it.isRough() } // special case - if *one* terrain is open, we don't care, we need *all*
            Constants.freshWaterFilter -> 
                return isAdjacentTo(Constants.freshWater, observingCiv)
        }
        
        return when (filter) {
            baseTerrain -> true
            resource -> observingCiv == null || hasViewableResource(observingCiv)

            else -> {
                val owner = getOwner()
                if (allTerrains.any { it.matchesFilter(filter, stateThisTile, false) }) return true
                if (owner != null && owner.matchesFilter(filter, stateThisTile, false)) return true

                // Resource type check is last - cannot succeed if no resource here
                if (resource == null) return false

                // Checks 'luxury resource', 'strategic resource' and 'bonus resource' - only those that are visible of course
                // not using hasViewableResource as observingCiv is often not passed in,
                // and we want to be able to at least test for non-strategic in that case.
                val resourceObject = tileResource
                val hasResourceWithFilter =
                        tileResource.name == filter
                                || tileResource.hasUnique(filter, stateThisTile)
                                || filter.removeSuffix(" resource") == tileResource.resourceType.name
                if (!hasResourceWithFilter) return false

                // Now that we know that this resource matches the filter - can the observer see that there's a resource here?
                if (resourceObject.revealedBy == null) return true  // no need for tech
                if (observingCiv == null) return false  // can't check tech
                return observingCiv.tech.isResearched(resourceObject.revealedBy!!)
            }
        }
    }

    fun isCoastalTile() = _isCoastalTile

    fun hasViewableResource(civInfo: Civilization): Boolean =
            resource != null && civInfo.tech.isRevealed(tileResource)

    fun getViewableTilesList(distance: Int): List<Tile> = tileMap.getViewableTiles(position, distance)
    fun getTilesInDistance(distance: Int): Sequence<Tile> = tileMap.getTilesInDistance(position, distance)
    fun getTilesInDistanceRange(range: IntRange): Sequence<Tile> = tileMap.getTilesInDistanceRange(position, range)
    fun getTilesAtDistance(distance: Int): Sequence<Tile> = tileMap.getTilesAtDistance(position, distance)

    fun getDefensiveBonus(includeImprovementBonus: Boolean = true, unit: MapUnit? = null): Float {
        var bonus = baseTerrainObject.defenceBonus
        if (terrainFeatureObjects.isNotEmpty()) {
            val otherTerrainBonus = terrainFeatureObjects.maxOf { it.defenceBonus }
            if (otherTerrainBonus != 0f) bonus = otherTerrainBonus  // replaces baseTerrainObject
        }
        if (naturalWonder != null) bonus += getNaturalWonder().defenceBonus
        val tileImprovement = getUnpillagedTileImprovement()
        if (tileImprovement != null && includeImprovementBonus) {
            for (unique in tileImprovement.getMatchingUniques(UniqueType.DefensiveBonus, unit?.cache?.state ?: stateThisTile))
                bonus += unique.params[0].toFloat() / 100
        }
        return bonus
    }

    /**
     * See [TileMap] secondary constructor's comment for visual illustration about coordinate system
     * 
     * @param otherTile Destination tile
     * @return Shortest distance from this [Tile] to [otherTile] in count of tiles including impassable tiles but not including origin tile
     */
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
        return when {
            isWater || isImpassible() -> false
            getTilesInDistance(modConstants.minimalCityDistanceOnDifferentContinents)
                .any { it.isCityCenter() && it.getContinent() != getContinent() } -> false
            getTilesInDistance(modConstants.minimalCityDistance)
                .any { it.isCityCenter() && it.getContinent() == getContinent() } -> false
            else -> true
        }
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

    fun isAdjacentToRiver(): Boolean {
        if (!isAdjacentToRiverKnown) {
            isAdjacentToRiver =
            // These are so if you add a river at the bottom of the map (no neighboring tile to be connected to)
                //   that tile is still considered adjacent to river
                hasBottomLeftRiver || hasBottomRiver || hasBottomRightRiver
                    || neighbors.any { isConnectedByRiver(it) }
            isAdjacentToRiverKnown = true
        }
        return isAdjacentToRiver
    }

    /**
     * @returns whether units of [civInfo] can pass through this tile, considering only civ-wide filters.
     * Use [UnitMovement.canPassThrough] to check whether a specific unit can pass through a tile.
     */
    fun canCivPassThrough(civInfo: Civilization): Boolean {
        val tileOwner = getOwner()
        // comparing the CivInfo objects is cheaper than comparing strings
        if (tileOwner == null || tileOwner == civInfo) return true
        if (isCityCenter() && civInfo.isAtWarWith(tileOwner)
                && !getCity()!!.hasJustBeenConquered) return false
        return civInfo.diplomacyFunctions.canPassThroughTiles(tileOwner)
    }

    fun hasEnemyInvisibleUnit(viewingCiv: Civilization): Boolean {
        val unitsInTile = getUnits()
        return when {
            unitsInTile.none() -> false
            unitsInTile.first().civ == viewingCiv -> false
            unitsInTile.none { it.isInvisible(viewingCiv) } -> false
            else -> true
        }
    }

    fun hasConnection(civInfo: Civilization) =
        getUnpillagedRoad() != RoadStatus.None || forestOrJungleAreRoads(civInfo)

    fun hasRoadConnection(civInfo: Civilization, mustBeUnpillaged: Boolean) =
        if (mustBeUnpillaged)
            (getUnpillagedRoad() == RoadStatus.Road) || forestOrJungleAreRoads(civInfo)
        else
            roadStatus == RoadStatus.Road || forestOrJungleAreRoads(civInfo)

    fun hasRailroadConnection(mustBeUnpillaged: Boolean) =
        if (mustBeUnpillaged)
            getUnpillagedRoad() == RoadStatus.Railroad
        else
            roadStatus == RoadStatus.Railroad


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
    
    fun isTilemapInitialized() = ::tileMap.isInitialized

    //endregion
    //region state-changing functions

    /** Do not run this on cloned tiles, since then the cloned *units* will be assigned to the civs
     * Instead run setTerrainTransients */
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
        if (isTilemapInitialized() && resource != null && tileResource.resourceType == ResourceType.Strategic && resourceAmount == 0) {
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
        // If it has an owning city, the state was already set in setOwningCity
        if (owningCity == null) stateThisTile = StateForConditionals(tile = this,
            // When generating maps we call this function but there's no gameinfo
            gameInfo = if (tileMap.hasGameInfo()) tileMap.gameInfo else null)
        if (owningCity == null && roadOwner != "")
            getRoadOwner()!!.neutralRoads.add(this.position)
    }

    fun setOwningCity(city: City?) {
        if (city != null) {
            if (roadStatus != RoadStatus.None && roadOwner != "") {
                // remove previous neutral tile owner
                getRoadOwner()!!.neutralRoads.remove(this.position)
            }
            roadOwner = city.civ.civName // only when taking control, otherwise last owner
        } else if (roadStatus != RoadStatus.None && owningCity != null) {
            // Razing City! Remove owner
            roadOwner = ""
        }
        owningCity = city
        stateThisTile = StateForConditionals(tile = this, city = city, gameInfo = tileMap.gameInfo)
        isCityCenterInternal = getCity()?.location == position
    }

    /**
     * Sets this tile's [resource] and, if [newResource] is a Strategic resource, [resourceAmount] fields.
     *
     * [resourceAmount] is determined by [MapParameters.mapResources] and [majorDeposit], and
     * if the latter is `null` a random choice between major and minor deposit is made, approximating
     * the frequency [MapRegions][com.unciv.logic.map.mapgenerator.mapregions.MapRegions] would use.
     * A randomness source ([rng]) can optionally be provided for that step (not used otherwise).
     */
    fun setTileResource(newResource: TileResource, majorDeposit: Boolean? = null, rng: Random = Random.Default) {
        resource = newResource.name

        if (newResource.resourceType != ResourceType.Strategic) {
            resourceAmount = 0
            return
        }

        for (unique in newResource.getMatchingUniques(UniqueType.ResourceAmountOnTiles, stateThisTile)) {
            if (matchesTerrainFilter(unique.params[0], null)) {
                resourceAmount = unique.params[1].toInt()
                return
            }
        }

        val majorDepositFinal = majorDeposit ?: (rng.nextDouble() < approximateMajorDepositDistribution())
        val depositAmounts = if (majorDepositFinal) newResource.majorDepositAmount else newResource.minorDepositAmount
        resourceAmount = when (tileMap.mapParameters.mapResources) {
            MapResourceSetting.sparse.label -> depositAmounts.sparse
            MapResourceSetting.abundant.label -> depositAmounts.abundant
            else -> depositAmounts.default
        }
    }

    fun setTerrainFeatures(terrainFeatureList: List<String>) {
        terrainFeatures = terrainFeatureList
        terrainFeatureObjects = terrainFeatureList.mapNotNull { ruleset.terrains[it] }
        allTerrains = sequence {
            yield(baseTerrainObject) // There is an assumption here that base terrains do not change
            if (naturalWonder != null) yield(getNaturalWonder())
            yieldAll(terrainFeatureObjects)
        }.toList().asSequence() //Save in memory, and return as sequence

        updateUniqueMap()

        lastTerrain = when {
            terrainFeatures.isNotEmpty() -> ruleset.terrains[terrainFeatures.last()]
                ?: getBaseTerrain()  // defense against rare edge cases involving baseTerrain Hill deprecation
            naturalWonder != null -> getNaturalWonder()
            else -> getBaseTerrain()
        }

        unitHeight = allTerrains.flatMap { it.getMatchingUniques(UniqueType.VisibilityElevation) }
            .map { it.params[0].toInt() }.sum()
        tileHeight = if (terrainHasUnique(UniqueType.BlocksLineOfSightAtSameElevation)) unitHeight + 1
        else unitHeight
    }

    fun setBaseTerrain(baseTerrainObject: Terrain){
        baseTerrain = baseTerrainObject.name
        this.baseTerrainObject = baseTerrainObject
        TileNormalizer.normalizeToRuleset(this, ruleset)
        setTerrainFeatures(terrainFeatures)
        setTerrainTransients()
    }

    private fun updateUniqueMap() {
        if (!isTilemapInitialized()) return // This tile is a fake tile, for visual display only (e.g. map editor, civilopedia)
        val terrainNameList = allTerrains.map { it.name }.toList()

        // List hash is function of all its items, so the same items in the same order will always give the same hash
        cachedTerrainData = tileMap.tileUniqueMapCache.getOrPut(terrainNameList) {
            TileMap.TerrainListData(
                UniqueMap(allTerrains.flatMap { it.uniqueObjects }),
                terrainNameList.toSet()
                )
        }
    }

    fun addTerrainFeature(terrainFeature: String) {
        if (!terrainFeatures.contains(terrainFeature))
            setTerrainFeatures(ArrayList(terrainFeatures).apply { add(terrainFeature) })
    }

    fun removeTerrainFeature(terrainFeature: String) {
        if (terrainFeature in terrainFeatures)
            setTerrainFeatures(ArrayList(terrainFeatures).apply { remove(terrainFeature) })
    }

    fun removeTerrainFeatures() =
        setTerrainFeatures(listOf())

    /** Clean stuff missing in [ruleset] - called from [TileMap.removeMissingTerrainModReferences]
     *  Must be able to run before [setTransients] - and does not need to fix transients.
     */
    fun removeMissingTerrainModReferences(ruleset: Ruleset) {
        terrainFeatures = terrainFeatures.filter { it in ruleset.terrains }
        if (resource != null && resource !in ruleset.tileResources)
            resource = null
        if (improvement != null && improvement !in ruleset.tileImprovements)
            improvement = null
        if (improvementQueue.any { it.improvement !in ruleset.tileImprovements })
            improvementQueue.clear() // Just get rid of everything, all bets are off
        if (naturalWonder != null && naturalWonder !in ruleset.terrains)
            naturalWonder = null
    }

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

    /** Does not remove roads */
    fun removeImprovement() =
        improvementFunctions.setImprovement(null)

    fun setImprovement(improvementStr: String, civToHandleCompletion: Civilization? = null, unit: MapUnit? = null) =
        improvementFunctions.setImprovement(improvementStr, civToHandleCompletion, unit)

    // function handling when removing a road from the tile
    fun removeRoad() = setRoadStatus(RoadStatus.None, null)
    
    fun setRoadStatus(newRoadStatus: RoadStatus, creatingCivInfo: Civilization?) {
        roadStatus = newRoadStatus
        roadIsPillaged = false
        
        if (newRoadStatus == RoadStatus.None && owningCity == null)
            getRoadOwner()?.neutralRoads?.remove(this.position)
        else if (getOwner() != null) {
            roadOwner = getOwner()!!.civName
        } else if (creatingCivInfo != null) {
            roadOwner = creatingCivInfo.civName // neutral tile, use building unit
            creatingCivInfo.neutralRoads.add(this.position)
        }
    }

    fun startWorkingOnImprovement(improvement: TileImprovement, civInfo: Civilization, unit: MapUnit) {
        improvementQueue.clear()
        queueImprovement(improvement, civInfo, unit)
    }

    /** Clears [improvementQueue] */
    fun stopWorkingOnImprovement() {
        improvementQueue.clear()
    }

    /** Adds an entry to the [improvementQueue], by looking up the time it takes using [civInfo] and [unit] */
    fun queueImprovement(improvement: TileImprovement, civInfo: Civilization, unit: MapUnit) {
        val turns = if (civInfo.gameInfo.gameParameters.godMode) 1
            else improvement.getTurnsToBuild(civInfo, unit)
        queueImprovement(improvement.name, turns)
    }

    /** Adds an entry to the [improvementQueue] with explicit [turnsToImprovement] */
    fun queueImprovement(improvementName: String, turnsToImprovement: Int) {
        improvementQueue.add(ImprovementQueueEntry(improvementName, turnsToImprovement))
    }

    /** Called from [UnitTurnManager.endTurn] when a Worker "spends time" here
     *  @return `true` if any work got finished and upstream moght want to update things */
    fun doWorkerTurn(worker: MapUnit): Boolean {
        if (isMarkedForCreatesOneImprovement()) return false
        if (improvementQueue.isEmpty()) return false

        if (improvementQueue.first().countDown()) return false
        val queueEntry = improvementQueue.removeAt(0)

        if (worker.civ.isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Construct an improvement")

        setImprovement(queueEntry.improvement, worker.civ, worker)
        return true
    }

    /** Sets tile improvement to pillaged (without prior checks for validity)
     *  and ensures that matching [UniqueType.CreatesOneImprovement] queued buildings are removed. */
    fun setPillaged() {
        if (!canPillageTile())
            return
        // http://well-of-souls.com/civ/civ5_improvements.html says that naval improvements are destroyed upon pillage
        //    and I can't find any other sources so I'll go with that
        if (!isLand) {
            removeImprovement()
            owningCity?.reassignPopulationDeferred()
            return
        }

        // Setting turnsToImprovement might interfere with UniqueType.CreatesOneImprovement
        improvementFunctions.removeCreatesOneImprovementMarker()
        improvementQueue.clear()  // remove any in progress work as well
        // if no Repair action, destroy improvements instead
        if (ruleset.tileImprovements[Constants.repair] == null) {
            if (canPillageTileImprovement())
                removeImprovement()
            else
                removeRoad()
        } else {
            // otherwise use pillage/repair systems
            if (canPillageTileImprovement())
                improvementIsPillaged = true
            else {
                roadIsPillaged = true
                clearAllPathfindingCaches()
            }
        }

        owningCity?.reassignPopulationDeferred()
        if (owningCity != null)
            owningCity!!.civ.cache.updateCivResources()
    }

    fun setRepaired() {
        improvementQueue.clear()
        if (improvementIsPillaged)
            improvementIsPillaged = false
        else
            roadIsPillaged = false

        owningCity?.reassignPopulationDeferred()
    }


    private fun clearAllPathfindingCaches() {
        val units = tileMap.gameInfo.civilizations.asSequence()
            .filter { it.isAlive() }
            .flatMap { it.units.getCivUnits() }
        Log.debug("%s: road pillaged, clearing cache for %d units", this, { units.count() })
        for (otherUnit in units) {
            otherUnit.movement.clearPathfindingCache()
        }
    }

    fun setExplored(player: Civilization, isExplored: Boolean, explorerPosition: Vector2? = null) {
        if (isExplored) {
            // Disable the undo button if a new tile has been explored
            if (!exploredBy.contains(player.civName)) {
                GUI.clearUndoCheckpoints()
                exploredBy = exploredBy.withItem(player.civName)
            }

            if (player.playerType == PlayerType.Human)
                player.exploredRegion.checkTilePosition(position, explorerPosition)
        } else {
            exploredBy = exploredBy.withoutItem(player.civName)
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

    /** Allows resetting the cached value [isAdjacentToRiver] will return
     *  @param isKnownTrue Set this to indicate you need to update the cache due to **adding** a river edge
     *         (removing would need to look at other edges, and that is what isAdjacentToRiver will do)
     */
    private fun resetAdjacentToRiverTransient(isKnownTrue: Boolean = false) {
        isAdjacentToRiver = isKnownTrue
        isAdjacentToRiverKnown = isKnownTrue
    }

    /**
     *  Sets the "has river" state of one edge of this Tile. Works for all six directions.
     *  @param  otherTile The neighbor tile in the direction the river we wish to change is (If it's not a neighbor, this does nothing).
     *  @param  newValue The new river edge state: `true` to create a river, `false` to remove one.
     *  @param  convertTerrains If true, calls MapGenerator's convertTerrains to apply UniqueType.ChangesTerrain effects.
     *  @return The state did change (`false`: the edge already had the `newValue`)
     */
    fun setConnectedByRiver(otherTile: Tile, newValue: Boolean, convertTerrains: Boolean = false): Boolean {
        //todo synergy potential with [MapEditorEditRiversTab]?
        val field = when (tileMap.getNeighborTileClockPosition(this, otherTile)) {
            2 -> otherTile::hasBottomLeftRiver // we're to the bottom-left of it
            4 -> ::hasBottomRightRiver // we're to the top-left of it
            6 -> ::hasBottomRiver // we're directly above it
            8 -> ::hasBottomLeftRiver // we're to the top-right of it
            10 -> otherTile::hasBottomRightRiver // we're to the bottom-right of it
            12 -> otherTile::hasBottomRiver // we're directly below it
            else -> return false
        }
        if (field.get() == newValue) return false
        field.set(newValue)
        val affectedTiles = listOf(this, otherTile)
        for (tile in affectedTiles)
            tile.resetAdjacentToRiverTransient(newValue)
        if (convertTerrains)
            MapGenerator.Helpers.convertTerrains(ruleset, affectedTiles)
        return true
    }

    //endregion
    //region Overrides

    /** Shows important properties of this tile for debugging _only_, it helps to see what you're doing */
    override fun toString(): String {
        val lineList = arrayListOf("Tile @$position")
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

    override fun write(json: Json) {
        json.writeFields(this)
        // Compatibility code for the case an improvementQueue-using game is loaded by an older version: Write fake fields
        if (improvementInProgress != null) json.writeValue("improvementInProgress", improvementInProgress, String::class.java)
        if (turnsToImprovement != 0) json.writeValue("turnsToImprovement", turnsToImprovement, Int::class.java)
    }

    override fun read(json: Json, jsonData: JsonValue) {
        json.readFields(this, jsonData)
        // Compatibility code for the case an pre-improvementQueue game is loaded by this version: Read legacy fields
        if (improvementQueue.isEmpty() && jsonData.get("improvementQueue") == null) {
            val improvementInProgress = jsonData.getString("improvementInProgress", "")
            val turnsToImprovement = jsonData.getInt("turnsToImprovement", 0)
            if (improvementInProgress.isNotEmpty() && turnsToImprovement != 0)
                improvementQueue.add(ImprovementQueueEntry(improvementInProgress, turnsToImprovement))
        }
    }

    //endregion
}
