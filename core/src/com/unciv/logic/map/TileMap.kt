package com.unciv.logic.map

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapgenerator.MapLandmassGenerator
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.utils.addToMapOfSets
import com.unciv.utils.contains
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

/** An Unciv map with all properties as produced by the [map editor][com.unciv.ui.screens.mapeditorscreen.MapEditorScreen]
 * or [MapGenerator][com.unciv.logic.map.mapgenerator.MapGenerator]; or as part of a running [game][GameInfo].
 *
 * Note: Will be Serialized -> Take special care with lateinit and lazy!
 *
 * @param initialCapacity Passed to constructor of [tileList]
 */
class TileMap(initialCapacity: Int = 10) : IsPartOfGameInfoSerialization {
    //region Fields, Serialized

    var mapParameters = MapParameters()

    var tileList = ArrayList<Tile>(initialCapacity)

    /** Structure geared for simple serialization by Gdx.Json (which is a little blind to kotlin collections, especially HashSet)
     * @param position [Vector2] of the location
     * @param nation Name of the nation
     */
    data class StartingLocation(
        val position: Vector2 = Vector2.Zero,
        val nation: String = "",
        val usage: Usage = Usage.default // default for maps saved pior to this feature
    ) : IsPartOfGameInfoSerialization {
        /** How a starting location may be used when the map is loaded for a new game */
        enum class Usage(val label: String) {
            /** Starting location only */
            Normal("None"),
            /** Use for "Select players from starting locations" */
            Player("Player"),
            /** Use as first Human player */
            Human("Human")
            ;
            companion object {
                val default get() = Player
            }
        }
    }
    val startingLocations = arrayListOf<StartingLocation>()

    /** Optional freeform text a mod map creator can set for their "customers" */
    var description = ""

    //endregion
    //region Fields, Transient

    /** Attention: lateinit will _stay uninitialized_ while in MapEditorScreen! */
    @Transient
    lateinit var gameInfo: GameInfo

    /** Keep a copy of the [Ruleset] object passed to setTransients, for now only to allow subsequent setTransients without. Copied on [clone]. */
    @Transient
    var ruleset: Ruleset? = null

    data class TerrainListData(
        val uniques: UniqueMap,
        val terrainNameSet: Set<String>
    ){
        companion object{
            val EMPTY = TerrainListData(UniqueMap.EMPTY, emptySet())
        }
    }
    
    @Transient
    var tileUniqueMapCache = ConcurrentHashMap<List<String>, TerrainListData>()

    @Transient
    var tileMatrix = ArrayList<ArrayList<Tile?>>() // this works several times faster than a hashmap, the performance difference is really astounding

    @Transient
    var leftX = 0

    @Transient
    var bottomY = 0

    @delegate:Transient
    val maxLatitude: Float by lazy { if (values.isEmpty()) 0f else values.maxOf { abs(it.latitude) } }

    @delegate:Transient
    val maxLongitude: Float by lazy { if (values.isEmpty()) 0f else values.maxOf { abs(it.longitude) } }

    @delegate:Transient
    val naturalWonders: Set<String> by lazy { tileList.asSequence().filter { it.isNaturalWonder() }.map { it.naturalWonder!! }.toSet() }

    @delegate:Transient
    val resources: Set<String> by lazy { tileList.asSequence().filter { it.resource != null }.map { it.resource!! }.toSet() }

    // Excluded from Serialization by having no own backing field
    val values: Collection<Tile>
        get() = tileList

    @Transient
    val startingLocationsByNation = HashMap<String, HashSet<Tile>>()

    @Transient
    /** Continent ID to Continent size */
    val continentSizes = HashMap<Int, Int>()

    //endregion
    //region Constructors

    /**
     * creates a hexagonal map of given radius (filled with grassland)
     *
     * To help you visualize how Unciv hexagonal coordinate system works, here's a small example:
     * 
     * ```
     *          _____         _____         _____
     *         /     \       /     \       /     \
     *   _____/ 2, 0  \_____/  1, 1 \_____/  0,2  \_____
     *  /     \       /     \       /     \       /     \
     * / 2,-1  \_____/  1,0  \_____/  0, 1 \_____/  -1,2 \
     * \       /     \       /     \       /     \       /
     *  \_____/ 1,-1  \_____/  0,0  \_____/  -1,1 \_____/
     *  /     \       /     \       /     \       /     \
     * / 1 ,-2 \_____/ 0,-1  \_____/ -1,0  \_____/ -2,1  \
     * \       /     \       /     \       /     \       /
     *  \_____/ 0,-2  \_____/ -1,-1 \_____/ -2,0  \_____/
     *  /     \       /     \       /     \       /     \
     * / 0,-3  \_____/ -1,-2 \_____/ -2,-1 \_____/ -3,0  \
     * \       /     \       /     \       /     \       /
     *  \_____/       \_____/       \_____/       \_____/
     * ```
     *
     * The rules are simple if you think about your X and Y axis as diagonal w.r.t. a standard carthesian plane. As such:
     *
     * moving "up": increase both X and Y by one
     * moving "down": decrease both X and Y by one
     * moving "up-right" and "down-left": moving along Y axis
     * moving "up-left" and "down-right": moving along X axis
     *
     * Tip: you can always use the in-game map editor if you have any doubt,
     * and the "secret" options can turn on coordinate display on the main map.
     */
    constructor(radius: Int, ruleset: Ruleset, worldWrap: Boolean = false)
            : this (HexMath.getNumberOfTilesInHexagon(radius)) {
        startingLocations.clear()
        val firstAvailableLandTerrain = MapLandmassGenerator.getInitializationTerrain(ruleset, TerrainType.Land)
        for (vector in HexMath.getVectorsInDistance(Vector2.Zero, radius, worldWrap))
            tileList.add(Tile().apply { position = vector; baseTerrain = firstAvailableLandTerrain })
        setTransients(ruleset)
    }

    /** creates a rectangular map of given width and height (filled with grassland) */
    constructor(width: Int, height: Int, ruleset: Ruleset, worldWrap: Boolean = false)
            : this(width * height) {
        startingLocations.clear()
        val firstAvailableLandTerrain = MapLandmassGenerator.getInitializationTerrain(ruleset, TerrainType.Land)

        // world-wrap maps must always have an even width, so round down
        val wrapAdjustedWidth = if (worldWrap && width % 2 != 0) width -1 else width

        // Even widths will have coordinates ranging -x..(x-1), not -x..x, which is always an odd-sized range
        // e.g. w=4 -> -2..1, w=5 -> -2..2, w=6 -> -3..2, w=7 -> -3..3
        for (column in -wrapAdjustedWidth / 2 .. (wrapAdjustedWidth-1) / 2)
            for (row in -height / 2 .. (height-1) / 2)
                tileList.add(Tile().apply {
                    position = HexMath.getTileCoordsFromColumnRow(column, row)
                    baseTerrain = firstAvailableLandTerrain
                })

        setTransients(ruleset)
    }

    //endregion
    //region Operators and Standards

    /** @return a deep-copy clone of the serializable fields, no transients initialized */
    fun clone(): TileMap {
        val toReturn = TileMap(tileList.size)
        toReturn.tileList.addAll(tileList.asSequence().map { it.clone() })
        toReturn.mapParameters = mapParameters
        toReturn.ruleset = ruleset

        // Note during normal play this is empty. Supported for MapEditorScreen.getMapCloneForSave.
        toReturn.startingLocations.clear()
        toReturn.startingLocations.ensureCapacity(startingLocations.size)
        toReturn.startingLocations.addAll(startingLocations)

        toReturn.description = description
        toReturn.tileUniqueMapCache = tileUniqueMapCache

        return toReturn
    }

    @Readonly
    operator fun contains(vector: Vector2) =
        contains(vector.x.toInt(), vector.y.toInt())

    @Readonly
    operator fun get(vector: Vector2) =
        get(vector.x.toInt(), vector.y.toInt())

    @Readonly
    fun contains(x: Int, y: Int) =
        getOrNull(x, y) != null

    @Readonly
    operator fun get(x: Int, y: Int) =
        tileMatrix[x - leftX][y - bottomY]!!

    /** @return tile at hex coordinates ([x],[y]) or null if they are outside the map. Does *not* respect world wrap, use [getIfTileExistsOrNull] for that. */
    @Readonly
    private fun getOrNull (x: Int, y: Int): Tile? =
            tileMatrix.getOrNull(x - leftX)?.getOrNull(y - bottomY)

    //endregion
    //region Pure Functions

    /** Can we access [gameInfo]? e.g. for MapEditor use where there is a map but no game */
    fun hasGameInfo() = ::gameInfo.isInitialized

    /** @return All tiles in a hexagon of radius [distance], including the tile at [origin] and all up to [distance] steps away.
     *  Respects map edges and world wrap. */
    @Readonly
    fun getTilesInDistance(origin: Vector2, distance: Int): Sequence<Tile> =
            getTilesInDistanceRange(origin, 0..distance)

    /** @return All tiles in a hexagonal ring around [origin] with the distances in [range]. Excludes the [origin] tile unless [range] starts at 0.
     *  Respects map edges and world wrap. */
    @Readonly
    fun getTilesInDistanceRange(origin: Vector2, range: IntRange): Sequence<Tile> =
            range.asSequence().flatMap { getTilesAtDistance(origin, it) }

    /** @return All tiles in a hexagonal ring 1 tile wide around [origin] with the [distance]. Contains the [origin] if and only if [distance] is <= 0.
     *  Respects map edges and world wrap. */
    @Readonly
    fun getTilesAtDistance(origin: Vector2, distance: Int): Sequence<Tile> =
            if (distance <= 0) // silently take negatives.
                sequenceOf(get(origin))
            else
                sequence {
                    val centerX = origin.x.toInt()
                    val centerY = origin.y.toInt()

                    // Start from 6 O'clock point which means (-distance, -distance) away from the center point
                    var currentX = centerX - distance
                    var currentY = centerY - distance

                    for (i in 0 until distance) { // From 6 to 8
                        yield(getIfTileExistsOrNull(currentX, currentY))
                        // We want to get the tile on the other side of the clock,
                        // so if we're at current = origin-delta we want to get to origin+delta.
                        // The simplest way to do this is 2*origin - current = 2*origin- (origin - delta) = origin+delta
                        yield(getIfTileExistsOrNull(2 * centerX - currentX, 2 * centerY - currentY))
                        currentX += 1 // we're going upwards to the left, towards 8 o'clock
                    }
                    for (i in 0 until distance) { // 8 to 10
                        yield(getIfTileExistsOrNull(currentX, currentY))
                        yield(getIfTileExistsOrNull(2 * centerX - currentX, 2 * centerY - currentY))
                        currentX += 1
                        currentY += 1 // we're going up the left side of the hexagon so we're going "up" - +1,+1
                    }
                    for (i in 0 until distance) { // 10 to 12
                        yield(getIfTileExistsOrNull(currentX, currentY))
                        yield(getIfTileExistsOrNull(2 * centerX - currentX, 2 * centerY - currentY))
                        currentY += 1 // we're going up the top left side of the hexagon so we're heading "up and to the right"
                    }
                }.filterNotNull()

    /** @return all tiles within [rectangle], respecting world edges and wrap.
     *  The rectangle will be "straight" ie parallel with rectangular map edges. */
    fun getTilesInRectangle(rectangle: Rectangle) = sequence {
            val x = rectangle.x.toInt()
            val y = rectangle.y.toInt()
            for (worldColumnNumber in x until x + rectangle.width.toInt()) {
                for (worldRowNumber in y until y + rectangle.height.toInt()) {
                    val hexCoords = HexMath.getTileCoordsFromColumnRow(worldColumnNumber, worldRowNumber)
                    yield(getIfTileExistsOrNull(hexCoords.x.toInt(), hexCoords.y.toInt()))
                }
            }
        }.filterNotNull()

    /** @return tile at hex coordinates ([x],[y]) or null if they are outside the map. Respects map edges and world wrap. */
    @Readonly
    fun getIfTileExistsOrNull(x: Int, y: Int): Tile? {
        val tile = getOrNull(x, y)
        if (tile != null) return tile

        if (!mapParameters.worldWrap)
            return null

        var radius = mapParameters.mapSize.radius
        if (mapParameters.shape == MapShape.rectangular)
            radius = mapParameters.mapSize.width / 2

        // Maybe tile is "outside of the map" in world wrap.

        // A. Get tile wrapped around from right to left
        val rightSideTile = getOrNull(x + radius, y - radius)
        if (rightSideTile != null) return rightSideTile

        // B. Get tile wrapped around from left to right
        val leftSideTile = getOrNull(x - radius, y + radius)
        if (leftSideTile != null) return leftSideTile

        return null
    }


    /**
     * Returns the clock position of [otherTile] seen from [tile]'s position
     * Returns -1 if not neighbors
     */
    @Readonly
    fun getNeighborTileClockPosition(tile: Tile, otherTile: Tile): Int {
        val radius = if (mapParameters.shape == MapShape.rectangular)
            mapParameters.mapSize.width / 2
        else mapParameters.mapSize.radius
        val x1 = tile.position.x.toInt()
        val y1 = tile.position.y.toInt()
        val x2 = otherTile.position.x.toInt()
        val y2 = otherTile.position.y.toInt()

        val xDifference = x1 - x2
        val yDifference = y1 - y2
        val xWrapDifferenceBottom = if (radius < 3) 0 else x1 - (x2 - radius)
        val yWrapDifferenceBottom = if (radius < 3) 0 else y1 - (y2 - radius)
        val xWrapDifferenceTop = if (radius < 3) 0 else x1 - (x2 + radius)
        val yWrapDifferenceTop = if (radius < 3) 0 else y1 - (y2 + radius)

        return when {
            xDifference == 1 && yDifference == 1 -> 6 // otherTile is below
            xDifference == -1 && yDifference == -1 -> 12 // otherTile is above
            xDifference == 1 || xWrapDifferenceBottom == 1 -> 4 // otherTile is bottom-right
            yDifference == 1 || yWrapDifferenceBottom == 1 -> 8 // otherTile is bottom-left
            xDifference == -1 || xWrapDifferenceTop == -1 -> 10 // otherTile is top-left
            yDifference == -1 || yWrapDifferenceTop == -1 -> 2 // otherTile is top-right
            else -> -1
        }
    }

    /**
     * Returns the neighbor tile of [tile] at [clockPosition], if it exists.
     * Takes world wrap into account
     * Returns null if there is no such neighbor tile or if [clockPosition] is not a valid clock position
     */
    @Readonly
    fun getClockPositionNeighborTile(tile: Tile, clockPosition: Int): Tile? {
        val difference = HexMath.getClockPositionToHexVector(clockPosition)
        if (difference == Vector2.Zero) return null
        @LocalState val possibleNeighborPosition = tile.position.cpy()
        possibleNeighborPosition.add(difference)
        return getIfTileExistsOrNull(possibleNeighborPosition.x.toInt(), possibleNeighborPosition.y.toInt())
    }

    /** Convert relative direction of [otherTile] seen from [tile]'s position into a vector
     * in world coordinates of length sqrt(3), so that it can be used to go from tile center to
     * the edge of the hex in that direction (meaning the center of the border between the hexes)
     */
    @Readonly
    fun getNeighborTilePositionAsWorldCoords(tile: Tile, otherTile: Tile): Vector2 =
        HexMath.getClockPositionToWorldVector(getNeighborTileClockPosition(tile, otherTile))

    /**
     * Returns the closest position to (0, 0) outside the map which can be wrapped
     * to the position of the given vector
     */
    @Readonly
    fun getUnWrappedPosition(position: Vector2): Vector2 {
        if (!contains(position))
            return position //The position is outside the map so its unwrapped already

        val radius = if (mapParameters.shape == MapShape.rectangular)
            mapParameters.mapSize.width / 2
        else mapParameters.mapSize.radius

        val vectorUnwrappedLeft = Vector2(position.x + radius, position.y - radius)
        val vectorUnwrappedRight = Vector2(position.x - radius, position.y + radius)

        return if (vectorUnwrappedRight.len() < vectorUnwrappedLeft.len())
            vectorUnwrappedRight
        else
            vectorUnwrappedLeft
    }

    data class ViewableTile(val tile: Tile, val maxHeightSeenToTile: Int, val isVisible: Boolean, val isAttackable: Boolean)

    /** @return List of tiles visible from location [position] for a unit with sight range [sightDistance] */
    @Readonly
    fun getViewableTiles(position: Vector2, sightDistance: Int, forAttack: Boolean = false): List<Tile> {
        val aUnitHeight = get(position).unitHeight
        val viewableTiles = mutableListOf(ViewableTile(
            get(position),
            aUnitHeight,
            isVisible = true,
            isAttackable = false
        ))

        for (i in 1..sightDistance+1) { // in each layer,
            // This is so we don't use tiles in the same distance to "see over",
            // that is to say, the "viewableTiles.contains(it) check will return false for neighbors from the same distance
            val tilesToAddInDistanceI = ArrayList<ViewableTile>()

            for (cTile in getTilesAtDistance(position, i)) { // for each tile in that layer,
                val cTileHeight = cTile.tileHeight

                // For the sightdistance+1 layer - that's "one out of sight" - it's only visible if it's higher than the current tile
                if (i == sightDistance+1 && (cTileHeight <= aUnitHeight || forAttack))
                    continue

                /*
            Okay so, if we're looking at a tile from height a to one with height c with a MAXIMUM HEIGHT of b in the middle,
            we have several scenarios:
            1. a>=b - I can see everything, b does not hide c (equals is 'flat plain' or 'string of hills' or 'hill viewing over forests')
            3. a<b
                3.1 b>=c - b hides c (hills hide other hills, forests, etc)
                3.2 b<c - c is tall enough I can see it over b (hill+forest, mountain)

            This can all be summed up as "I can see c if a=>b || c>b"
            */
                val bMinimumHighestSeenTerrainSoFar = viewableTiles
                    .filter { it.tile.aerialDistanceTo(cTile) == 1 }
                    .minOf { it.maxHeightSeenToTile }

                tilesToAddInDistanceI.add(ViewableTile(
                    cTile,
                    max(cTileHeight, bMinimumHighestSeenTerrainSoFar),
                    aUnitHeight >= bMinimumHighestSeenTerrainSoFar || cTileHeight > bMinimumHighestSeenTerrainSoFar,
                    aUnitHeight >= bMinimumHighestSeenTerrainSoFar || cTile.unitHeight > bMinimumHighestSeenTerrainSoFar,
                ))
            }
            viewableTiles.addAll(tilesToAddInDistanceI)
        }

        if (forAttack) return viewableTiles.filter { it.isAttackable }.map { it.tile }

        return viewableTiles.filter { it.isVisible }.map { it.tile }
    }

    /** Build a list of incompatibilities of a map with a ruleset for the new game loader
     *
     *  Is run before setTransients, so make do without startingLocationsByNation
     */
    fun getRulesetIncompatibility(ruleset: Ruleset): HashSet<String> {
        val rulesetIncompatibilities = HashSet<String>()
        for (set in values.map { it.getRulesetIncompatibility(ruleset) })
            rulesetIncompatibilities.addAll(set)

        // All the rest is to find missing nations
        try { // This can fail if the map contains a resource that isn't in the ruleset, in Tile.tileResource
            setTransients(ruleset)
        } catch (_: Exception) {
            return rulesetIncompatibilities
        }
        setStartingLocationsTransients()
        for ((_, nationName) in startingLocations) {
            if (nationName !in ruleset.nations)
                rulesetIncompatibilities.add("Nation [$nationName] does not exist in ruleset!")
        }
        rulesetIncompatibilities.remove("")
        return rulesetIncompatibilities
    }

    fun isWaterMap(): Boolean {
        assignContinents(AssignContinentsMode.Ensure)
        val bigIslands = continentSizes.count { it.value > 20 }
        val players = gameInfo.gameParameters.players.size
        return bigIslands >= players
    }
    
    fun usingArchipelagoRegions(): Boolean {
        val totalLand = continentSizes.values.sum().toFloat()
        val largestContinent = continentSizes.values.maxOf { it }.toFloat()
        return largestContinent / totalLand < 0.25f
    }

    //endregion
    //region State-Changing Methods

    /** Initialize transients - without, most operations, like [get] from coordinates, will fail.
     * @param ruleset Required unless this is a clone of an initialized TileMap including one
     * @param setUnitCivTransients when false Civ-specific parts of unit initialization are skipped, for the map editor.
     */
    fun setTransients(ruleset: Ruleset? = null, setUnitCivTransients: Boolean = true) {
        if (ruleset != null) this.ruleset = ruleset
        check(this.ruleset != null) { "TileMap.setTransients called without ruleset" }
        check(tileList.isNotEmpty()) { "No tiles were found in the save?!" }

        if (tileMatrix.isEmpty()) {
            val topY = tileList.asSequence().map { it.position.y.toInt() }.max()
            bottomY = tileList.asSequence().map { it.position.y.toInt() }.min()
            val rightX = tileList.asSequence().map { it.position.x.toInt() }.max()
            leftX = tileList.asSequence().map { it.position.x.toInt() }.min()

            // Initialize arrays with enough capacity to avoid re-allocations (+Arrays.copyOf).
            // We have just calculated the dimensions above, so we know the final size.
            tileMatrix.ensureCapacity(rightX - leftX + 1)
            for (x in leftX..rightX) {
                val row = ArrayList<Tile?>(topY - bottomY + 1)
                for (y in bottomY..topY) row.add(null)
                tileMatrix.add(row)
            }
        } else {
            // Yes the map generator calls this repeatedly, and we don't want to end up with an oversized tileMatrix
            // rightX is between -leftX - 1 (e.g. 105x90 map thanks @ravignir) and -leftX + 2
            check(tileMatrix.size in (- 2 * leftX)..(3 - 2 * leftX)) {
                "TileMap.setTransients called on existing tileMatrix of different size"
            }
        }

        for (tileInfo in values) {
            tileMatrix[tileInfo.position.x.toInt() - leftX][tileInfo.position.y.toInt() - bottomY] = tileInfo
        }
        for (tileInfo in values) {
            // Do ***NOT*** call Tile.setTerrainTransients before the tileMatrix is complete -
            // setting transients might trigger the neighbors lazy (e.g. thanks to convertHillToTerrainFeature).
            // When that lazy runs, some directions might be omitted because getIfTileExistsOrNull
            // looks at tileMatrix. Thus filling Tiles into tileMatrix and setting their
            // transients in the same loop will leave incomplete cached `neighbors`.
            tileInfo.tileMap = this
            tileInfo.zeroBasedIndex = HexMath.getZeroBasedIndex(tileInfo.position.x.toInt(), tileInfo.position.y.toInt())
            tileInfo.ruleset = this.ruleset!!
            tileInfo.setTerrainTransients()
            tileInfo.setUnitTransients(setUnitCivTransients)
        }
    }

    /** Initialize Civilization.neutralRoads based on Tile.roadOwner
     *  - which Civ owns roads on which neutral tiles */
    fun setNeutralTransients() {
        for (tileInfo in values) {
            tileInfo.setOwnerTransients()
        }
    }

    fun removeMissingTerrainModReferences(ruleSet: Ruleset) {
        // This will run before setTransients, so do not rely e.g. on Tile.ruleset being available.
        // That rules out Tile.removeTerrainFeature, which refreshes object/unique caches
        for (tile in this.values) {
            tile.removeMissingTerrainModReferences(ruleSet)
        }
        for (startingLocation in startingLocations.toList())
            if (startingLocation.nation !in ruleSet.nations)
                startingLocations.remove(startingLocation)
    }

    /** Tries to place the [unitName] into the [Tile] closest to the given [position]
     * @param position where to try to place the unit (or close - max 10 tiles distance)
     * @param unitName name of the [BaseUnit][com.unciv.models.ruleset.unit.BaseUnit] to create and place
     * @param civInfo civilization to assign unit to
     * @return created [MapUnit] or null if no suitable location was found
     * */
    fun placeUnitNearTile(
        position: Vector2,
        unitName: String,
        civInfo: Civilization,
        unitId: Int? = null
    ): MapUnit? {
        val unit = gameInfo.ruleset.units[unitName]!!
        return placeUnitNearTile(position, unit, civInfo, unitId)
    }

    /** Tries to place the [baseUnit] into the [Tile] closest to the given [position]
     * @param position where to try to place the unit (or close - max 10 tiles distance)
     * @param baseUnit [BaseUnit][com.unciv.models.ruleset.unit.BaseUnit] to create and place
     * @param civInfo civilization to assign unit to
     * @return created [MapUnit] or null if no suitable location was found
     * */
    fun placeUnitNearTile(
            position: Vector2,
            baseUnit: BaseUnit,
            civInfo: Civilization,
            unitId: Int? = null
    ): MapUnit? {
        val unit = baseUnit.newMapUnit(civInfo, unitId)

        fun getPassableNeighbours(tile: Tile): Set<Tile> =
                tile.neighbors.filter { unit.movement.canPassThrough(it) }.toSet()

        // both the civ name and actual civ need to be in here in order to calculate the canMoveTo...Darn
        unit.assignOwner(civInfo, false)
        // remember our first owner
        unit.originalOwner = civInfo.civName

        var unitToPlaceTile: Tile? = null
        // try to place at the original point (this is the most probable scenario)
        val currentTile = get(position)
        unit.currentTile = currentTile  // temporary
        unit.cache.state = GameContext(unit)
        if (unit.movement.canMoveTo(currentTile)) unitToPlaceTile = currentTile

        // if it's not suitable, try to find another tile nearby
        if (unitToPlaceTile == null) {
            var tryCount = 0
            var potentialCandidates = getPassableNeighbours(currentTile)
            while (unitToPlaceTile == null && tryCount++ < 10) {
                unitToPlaceTile = potentialCandidates
                        .sortedByDescending { if (unit.baseUnit.isLandUnit && !unit.cache.canMoveOnWater) it.isLand else true } // Land units should prefer to go into land tiles
                        .firstOrNull { unit.movement.canMoveTo(it) }
                if (unitToPlaceTile != null) continue
                // if it's not found yet, let's check their neighbours
                val newPotentialCandidates = mutableSetOf<Tile>()
                potentialCandidates.forEach { newPotentialCandidates.addAll(getPassableNeighbours(it)) }
                potentialCandidates = newPotentialCandidates
            }
        }

        if (unitToPlaceTile == null) {
            civInfo.units.removeUnit(unit) // since we added it to the civ units in the previous assignOwner
            return null // we didn't actually create a unit...
        }

        // only once we know the unit can be placed do we add it to the civ's unit list
        unit.putInTile(unitToPlaceTile)
        unit.currentMovement = unit.getMaxMovement().toFloat()
        unit.addMovementMemory()

        // Only once we add the unit to the civ we can activate addPromotion, because it will try to update civ viewable tiles
        for (promotion in unit.baseUnit.promotions)
            unit.promotions.addPromotion(promotion, true)

        for (unique in civInfo.getMatchingUniques(UniqueType.UnitsGainPromotion)) {
            if (unit.matchesFilter(unique.params[0])) {
                unit.promotions.addPromotion(unique.params[1], true)
            }
        }

        // And update civ stats, since the new unit changes both unit upkeep and resource consumption
        civInfo.updateStatsForNextTurn()

        if (unit.getResourceRequirementsPerTurn().isNotEmpty())
            civInfo.cache.updateCivResources()

        return unit
    }


    /** Strips all units and starting locations from [TileMap] for specified [Player]
     * Operation in place
     *
     * Currently unreachable code
     *
     * @param player units of this player will be removed
     */
    fun stripPlayer(player: Player) {
        tileList.forEach {
            for (unit in it.getUnits()) if (unit.owner == player.chosenCiv) unit.removeFromTile()
        }
        startingLocations.removeAll { it.nation == player.chosenCiv }
        startingLocationsByNation.remove(player.chosenCiv)
    }

    /** Finds all units and starting location of [Player] and changes their [Nation]
     * Operation in place
     *
     * Currently unreachable code
     *
     * @param player player whose all units will be changed
     * @param newNation new nation to be set up
     */
    fun switchPlayersNation(player: Player, newNation: Nation) {
        val newCiv = Civilization(newNation.name).apply { nation = newNation }
        tileList.forEach {
            for (unit in it.getUnits()) if (unit.owner == player.chosenCiv) {
                unit.owner = newNation.name
                unit.civ = newCiv
                unit.setTransients(newCiv.gameInfo.ruleset)
            }
        }
        for (element in startingLocations.filter { it.nation != player.chosenCiv }) {
            startingLocations.remove(element)
            if (startingLocations.none { it.nation == newNation.name && it.position == element.position })
                startingLocations.add(StartingLocation(element.position, newNation.name))
        }
        setStartingLocationsTransients()
    }

    /**
     *  Initialize startingLocations transients
     */
    fun setStartingLocationsTransients() {
        startingLocationsByNation.clear()
        for ((position, nationName) in startingLocations) {
            startingLocationsByNation.addToMapOfSets(nationName, get(position))
        }
    }

    /** Adds a starting position, maintaining the transients
     *
     * Note: Will not replace an existing StartingLocation to update its [usage]
     * @return true if the starting position was not already stored as per [Collection]'s add */
    fun addStartingLocation(
        nationName: String,
        tile: Tile,
        usage: StartingLocation.Usage = StartingLocation.Usage.Player
    ): Boolean {
        if (startingLocationsByNation.contains(nationName, tile)) return false
        startingLocations.add(StartingLocation(tile.position, nationName, usage))
        return startingLocationsByNation.addToMapOfSets(nationName, tile)
    }

    /** Removes a starting position, maintaining the transients
     * @return true if the starting position was removed as per [Collection]'s remove */
    fun removeStartingLocation(nationName: String, tile: Tile): Boolean {
        if (!startingLocationsByNation.contains(nationName, tile)) return false
        startingLocations.remove(StartingLocation(tile.position, nationName))
        return startingLocationsByNation[nationName]!!.remove(tile)
        // we do not clean up an empty startingLocationsByNation[nationName] set - not worth it
    }

    /** Removes all starting positions for [nationName], maintaining the transients */
    fun removeStartingLocations(nationName: String) {
        if (startingLocationsByNation[nationName] == null) return
        for (tile in startingLocationsByNation[nationName]!!) {
            startingLocations.remove(StartingLocation(tile.position, nationName))
        }
        startingLocationsByNation[nationName]!!.clear()
    }

    /** Removes all starting positions for [position], rebuilding the transients */
    fun removeStartingLocations(position: Vector2) {
        startingLocations.removeAll { it.position == position }
        setStartingLocationsTransients()
    }

    /** Clears starting positions, e.g. after GameStarter is done with them. Does not clear the pseudo-improvements. */
    fun clearStartingLocations() {
        startingLocations.clear()
        startingLocationsByNation.clear()
    }

    /** Behaviour of [assignContinents] */
    enum class AssignContinentsMode { Assign, Reassign, Ensure, Clear }

    /** Set a continent id for each tile, so we can quickly see which tiles are connected.
     *  Can also be called on saved maps.
     *  @param mode As follows:
     *  [Assign][AssignContinentsMode.Assign] = initial assign, throw if tiles have continents.
     *  [Reassign][AssignContinentsMode.Reassign] = clear continent data and redo for map editor.
     *  [Ensure][AssignContinentsMode.Ensure] = regenerate continent sizes from tile data, and if that is empty, Assign.
     *  @throws Exception when `mode==Assign` and any land tile already has a continent ID
     *  @return A map of continent sizes (continent ID to tile count)
     */
    fun assignContinents(mode: AssignContinentsMode) {
        if (mode == AssignContinentsMode.Clear) {
            values.forEach { it.clearContinent() }
            continentSizes.clear()
            return
        }

        if (mode == AssignContinentsMode.Ensure) {
            if (continentSizes.isNotEmpty()) return
            for (tile in values) {
                val continent = tile.getContinent()
                if (continent == -1) continue
                continentSizes[continent] = 1 + (continentSizes[continent] ?: 0)
            }
            if (continentSizes.isNotEmpty()) return
        }

        var landTiles = values.filter { it.isLand && !it.isImpassible() }
        var currentContinent = 0
        continentSizes.clear()

        if (mode == AssignContinentsMode.Reassign)
            values.forEach { it.clearContinent() }

        while (landTiles.any()) {
            val bfs = BFS(landTiles.random()) { it.isLand && !it.isImpassible() }
            bfs.stepToEnd()
            bfs.getReachedTiles().forEach {
                it.setContinent(currentContinent)
            }
            val continent = bfs.getReachedTiles()
            continentSizes[currentContinent] = continent.size

            currentContinent++
            landTiles = landTiles.filter { it !in continent }
        }
    }
    //endregion

    /** Class to parse only the parameters and starting locations out of a map file */
    class Preview {
        val mapParameters = MapParameters()
        private val startingLocations = arrayListOf<StartingLocation>()
        fun getDeclaredNations() = startingLocations.asSequence()
            .filter { it.usage != StartingLocation.Usage.Normal }
            .map { it.nation }
            .distinct()
        fun getNationsForHumanPlayer() = startingLocations.asSequence()
            .filter { it.usage == StartingLocation.Usage.Human }
            .map { it.nation }
            .distinct()
    }
}
