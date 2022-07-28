package com.unciv.logic.map

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.HexMath
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.mapgenerator.MapLandmassGenerator
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.math.abs

/** An Unciv map with all properties as produced by the [map editor][com.unciv.ui.mapeditor.MapEditorScreen]
 * or [MapGenerator][com.unciv.logic.map.mapgenerator.MapGenerator]; or as part of a running [game][GameInfo].
 *
 * Note: Will be Serialized -> Take special care with lateinit and lazy!
 */
class TileMap : IsPartOfGameInfoSerialization {
    companion object {
        /** Legacy way to store starting locations - now this is used only in [translateStartingLocationsFromMap] */
        const val startingLocationPrefix = "StartingLocation "

        /**
         * To be backwards compatible, a json without a startingLocations element will be recognized by an entry with this marker
         * New saved maps will never have this marker and will always have a serialized startingLocations list even if empty.
         * New saved maps will also never have "StartingLocation" improvements, these are converted on load in [setTransients].
         */
        private const val legacyMarker = " Legacy "
    }

    //region Fields, Serialized

    var mapParameters = MapParameters()

    private var tileList = ArrayList<TileInfo>()

    /** Structure geared for simple serialization by Gdx.Json (which is a little blind to kotlin collections, especially HashSet)
     * @param position [Vector2] of the location
     * @param nation Name of the nation
     */
    private data class StartingLocation(val position: Vector2 = Vector2.Zero, val nation: String = "") : IsPartOfGameInfoSerialization
    private val startingLocations = arrayListOf(StartingLocation(Vector2.Zero, legacyMarker))

    //endregion
    //region Fields, Transient

    /** Attention: lateinit will _stay uninitialized_ while in MapEditorScreen! */
    @Transient
    lateinit var gameInfo: GameInfo

    /** Keep a copy of the [Ruleset] object passed to setTransients, for now only to allow subsequent setTransients without. Copied on [clone]. */
    @Transient
    var ruleset: Ruleset? = null

    @Transient
    var tileMatrix = ArrayList<ArrayList<TileInfo?>>() // this works several times faster than a hashmap, the performance difference is really astounding

    @Transient
    var leftX = 0

    @Transient
    var bottomY = 0

    @delegate:Transient
    val maxLatitude: Float by lazy { if (values.isEmpty()) 0f else values.maxOf { abs(it.latitude) } }

    @delegate:Transient
    val maxLongitude: Float by lazy { if (values.isEmpty()) 0f else values.maxOf { abs(it.longitude) } }

    @delegate:Transient
    val naturalWonders: List<String> by lazy { tileList.asSequence().filter { it.isNaturalWonder() }.map { it.naturalWonder!! }.distinct().toList() }

    @delegate:Transient
    val resources: List<String> by lazy { tileList.asSequence().filter { it.resource != null }.map { it.resource!! }.distinct().toList() }

    // Excluded from Serialization by having no own backing field
    val values: Collection<TileInfo>
        get() = tileList

    @Transient
    val startingLocationsByNation = HashMap<String,HashSet<TileInfo>>()

    @Transient
    val continentSizes = HashMap<Int, Int>()    // Continent ID, Continent size

    //endregion
    //region Constructors

    /** for json parsing, we need to have a default constructor */
    constructor()

    /** creates a hexagonal map of given radius (filled with grassland) */
    constructor(radius: Int, ruleset: Ruleset, worldWrap: Boolean = false) {
        startingLocations.clear()
        val firstAvailableLandTerrain = MapLandmassGenerator.getInitializationTerrain(ruleset, TerrainType.Land)
        for (vector in HexMath.getVectorsInDistance(Vector2.Zero, radius, worldWrap))
            tileList.add(TileInfo().apply { position = vector; baseTerrain = firstAvailableLandTerrain })
        setTransients(ruleset)
    }

    /** creates a rectangular map of given width and height (filled with grassland) */
    constructor(width: Int, height: Int, ruleset: Ruleset, worldWrap: Boolean = false) {
        startingLocations.clear()
        val firstAvailableLandTerrain = MapLandmassGenerator.getInitializationTerrain(ruleset, TerrainType.Land)

        // world-wrap maps must always have an even width, so round down
        val wrapAdjustedWidth = if (worldWrap && width % 2 != 0) width -1 else width

        // Even widths will have coordinates ranging -x..(x-1), not -x..x, which is always an odd-sized range
        // e.g. w=4 -> -2..1, w=5 -> -2..2, w=6 -> -3..2, w=7 -> -3..3
        for (x in -wrapAdjustedWidth / 2 .. (wrapAdjustedWidth-1) / 2)
            for (y in -height / 2 .. (height-1) / 2)
                tileList.add(TileInfo().apply {
                    position = HexMath.evenQ2HexCoords(Vector2(x.toFloat(), y.toFloat()))
                    baseTerrain = firstAvailableLandTerrain
                })

        setTransients(ruleset)
    }

    //endregion
    //region Operators and Standards

    /** @return a deep-copy clone of the serializable fields, no transients initialized */
    fun clone(): TileMap {
        val toReturn = TileMap()
        toReturn.tileList.addAll(tileList.map { it.clone() })
        toReturn.mapParameters = mapParameters
        toReturn.ruleset = ruleset
        toReturn.startingLocations.clear()
        toReturn.startingLocations.ensureCapacity(startingLocations.size)
        toReturn.startingLocations.addAll(startingLocations)
        return toReturn
    }

    operator fun contains(vector: Vector2) =
        contains(vector.x.toInt(), vector.y.toInt())

    operator fun get(vector: Vector2) =
        get(vector.x.toInt(), vector.y.toInt())

    fun contains(x: Int, y: Int) =
        getOrNull(x, y) != null

    operator fun get(x: Int, y: Int) =
        tileMatrix[x - leftX][y - bottomY]!!

    /** @return tile at hex coordinates ([x],[y]) or null if they are outside the map. Does *not* respect world wrap, use [getIfTileExistsOrNull] for that. */
    private fun getOrNull (x: Int, y: Int): TileInfo? {
        val arrayXIndex = x - leftX
        if (arrayXIndex < 0 || arrayXIndex >= tileMatrix.size) return null
        val arrayYIndex = y - bottomY
        if (arrayYIndex < 0 || arrayYIndex >= tileMatrix[arrayXIndex].size) return null
        return tileMatrix[arrayXIndex][arrayYIndex]
    }

    //endregion
    //region Pure Functions

    /** @return All tiles in a hexagon of radius [distance], including the tile at [origin] and all up to [distance] steps away.
     *  Respects map edges and world wrap. */
    fun getTilesInDistance(origin: Vector2, distance: Int): Sequence<TileInfo> =
            getTilesInDistanceRange(origin, 0..distance)

    /** @return All tiles in a hexagonal ring around [origin] with the distances in [range]. Excludes the [origin] tile unless [range] starts at 0.
     *  Respects map edges and world wrap. */
    fun getTilesInDistanceRange(origin: Vector2, range: IntRange): Sequence<TileInfo> =
            range.asSequence().flatMap { getTilesAtDistance(origin, it) }

    /** @return All tiles in a hexagonal ring 1 tile wide around [origin] with the [distance]. Contains the [origin] if and only if [distance] is <= 0.
     *  Respects map edges and world wrap. */
    fun getTilesAtDistance(origin: Vector2, distance: Int): Sequence<TileInfo> =
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
     *  If using even Q coordinates the rectangle will be "straight" ie parallel with rectangular map edges. */
    fun getTilesInRectangle(rectangle: Rectangle, evenQ: Boolean = false): Sequence<TileInfo> =
            if (rectangle.width <= 0 || rectangle.height <= 0) {
                val tile = getIfTileExistsOrNull(rectangle.x.toInt(), rectangle.y.toInt())
                if (tile == null) sequenceOf()
                else sequenceOf(tile)
            }
            else
                sequence {
                    for (x in 0 until rectangle.width.toInt()) {
                        for (y in 0 until rectangle.height.toInt()) {
                            val currentX = rectangle.x + x
                            val currentY = rectangle.y + y
                            if (evenQ) {
                                val hexCoords = HexMath.evenQ2HexCoords(Vector2(currentX, currentY))
                                yield(getIfTileExistsOrNull(hexCoords.x.toInt(), hexCoords.y.toInt()))
                            }
                            else
                                yield(getIfTileExistsOrNull(currentX.toInt(), currentY.toInt()))
                        }
                    }
                }.filterNotNull()

    /** @return tile at hex coordinates ([x],[y]) or null if they are outside the map. Respects map edges and world wrap. */
    fun getIfTileExistsOrNull(x: Int, y: Int): TileInfo? {
        if (contains(x, y))
            return get(x, y)

        if (!mapParameters.worldWrap)
            return null

        var radius = mapParameters.mapSize.radius
        if (mapParameters.shape == MapShape.rectangular)
            radius = mapParameters.mapSize.width / 2

        //tile is outside of the map
        if (contains(x + radius, y - radius)) { //tile is on right side
            //get tile wrapped around from right to left
            return get(x + radius, y - radius)
        } else if (contains(x - radius, y + radius)) { //tile is on left side
            //get tile wrapped around from left to right
            return get(x - radius, y + radius)
        }

        return null
    }

    /**
     * Returns the clock position of [otherTile] seen from [tile]'s position
     * Returns -1 if not neighbors
     */
    fun getNeighborTileClockPosition(tile: TileInfo, otherTile: TileInfo): Int {
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
    fun getClockPositionNeighborTile(tile: TileInfo, clockPosition: Int): TileInfo? {
        val difference = HexMath.getClockPositionToHexVector(clockPosition)
        if (difference == Vector2.Zero) return null
        val possibleNeighborPosition = tile.position.cpy().add(difference)
        return getIfTileExistsOrNull(possibleNeighborPosition.x.toInt(), possibleNeighborPosition.y.toInt())
    }

    /** Convert relative direction of [otherTile] seen from [tile]'s position into a vector
     * in world coordinates of length sqrt(3), so that it can be used to go from tile center to
     * the edge of the hex in that direction (meaning the center of the border between the hexes)
     */
    fun getNeighborTilePositionAsWorldCoords(tile: TileInfo, otherTile: TileInfo): Vector2 =
        HexMath.getClockPositionToWorldVector(getNeighborTileClockPosition(tile, otherTile))

    /**
     * Returns the closest position to (0, 0) outside the map which can be wrapped
     * to the position of the given vector
     */
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

    /** @return List of tiles visible from location [position] for a unit with sight range [sightDistance] */
    fun getViewableTiles(position: Vector2, sightDistance: Int): List<TileInfo> {
        val viewableTiles = getTilesInDistance(position, 1).toMutableList()
        val currentTileHeight = get(position).height

        for (i in 1..sightDistance) { // in each layer,
            // This is so we don't use tiles in the same distance to "see over",
            // that is to say, the "viewableTiles.contains(it) check will return false for neighbors from the same distance
            val tilesToAddInDistanceI = ArrayList<TileInfo>()

            for (cTile in getTilesAtDistance(position, i)) { // for each tile in that layer,
                val cTileHeight = cTile.height

                /*
            Okay so, if we're looking at a tile from a to c with b in the middle,
            we have several scenarios:
            1. a>b -  - I can see everything, b does not hide c
            2. a==b
                2.1 c>b - c is tall enough I can see it over b!
                2.2 b blocks view from same-elevation tiles - hides c
                2.3 none of the above - I can see c
            3. a<b
                3.1 b>=c - b hides c
                3.2 b<c - c is tall enough I can see it over b!

            This can all be summed up as "I can see c if a>b || c>b || (a==b && b !blocks same-elevation view)"
            */

                val containsViewableNeighborThatCanSeeOver = cTile.neighbors.any { bNeighbor: TileInfo ->
                    val bNeighborHeight = bNeighbor.height
                    viewableTiles.contains(bNeighbor)
                    && (
                        currentTileHeight > bNeighborHeight // a>b
                        || cTileHeight > bNeighborHeight // c>b
                        || (
                            currentTileHeight == bNeighborHeight // a==b
                            && !bNeighbor.terrainHasUnique(UniqueType.BlocksLineOfSightAtSameElevation)
                        )
                    )
                }
                if (containsViewableNeighborThatCanSeeOver) tilesToAddInDistanceI.add(cTile)
            }
            viewableTiles.addAll(tilesToAddInDistanceI)
        }

        return viewableTiles
    }

    /** Strips all units from [TileMap]
     * @return stripped [clone] of [TileMap]
     */
    fun stripAllUnits(): TileMap {
        return clone().apply { tileList.forEach { it.stripUnits() } }
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
        try { // This can fail if the map contains a resource that isn't in the ruleset, in TileInfo.tileResource
            setTransients(ruleset)
        } catch (ex: Exception) {
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

    //endregion
    //region State-Changing Methods

    /** Initialize transients - without, most operations, like [get] from coordinates, will fail.
     * @param ruleset Required unless this is a clone of an initialized TileMap including one
     * @param setUnitCivTransients when false Civ-specific parts of unit initialization are skipped, for the map editor.
     */
    fun setTransients(ruleset: Ruleset? = null, setUnitCivTransients: Boolean = true) {
        if (ruleset != null) this.ruleset = ruleset
        if (this.ruleset == null) throw(IllegalStateException("TileMap.setTransients called without ruleset"))

        if (tileMatrix.isEmpty()) {
            val topY = tileList.asSequence().map { it.position.y.toInt() }.maxOrNull()!!
            bottomY = tileList.asSequence().map { it.position.y.toInt() }.minOrNull()!!
            val rightX = tileList.asSequence().map { it.position.x.toInt() }.maxOrNull()!!
            leftX = tileList.asSequence().map { it.position.x.toInt() }.minOrNull()!!

            // Initialize arrays with enough capacity to avoid re-allocations (+Arrays.copyOf).
            // We have just calculated the dimensions above, so we know the final size.
            tileMatrix.ensureCapacity(rightX - leftX + 1)
            for (x in leftX..rightX) {
                val row = ArrayList<TileInfo?>(topY - bottomY + 1)
                for (y in bottomY..topY) row.add(null)
                tileMatrix.add(row)
            }
        } else {
            // Yes the map generator calls this repeatedly, and we don't want to end up with an oversized tileMatrix
            // rightX is -leftX or -leftX + 1 or -leftX + 2
            if (tileMatrix.size !in (1 - 2 * leftX)..(3 - 2 * leftX))
                throw(IllegalStateException("TileMap.setTransients called on existing tileMatrix of different size"))
        }

        for (tileInfo in values) {
            tileMatrix[tileInfo.position.x.toInt() - leftX][tileInfo.position.y.toInt() - bottomY] = tileInfo
        }
        for (tileInfo in values) {
            // Do ***NOT*** call TileInfo.setTerrainTransients before the tileMatrix is complete -
            // setting transients might trigger the neighbors lazy (e.g. thanks to convertHillToTerrainFeature).
            // When that lazy runs, some directions might be omitted because getIfTileExistsOrNull
            // looks at tileMatrix. Thus filling TileInfos into tileMatrix and setting their
            // transients in the same loop will leave incomplete cached `neighbors`.
            tileInfo.tileMap = this
            tileInfo.ruleset = this.ruleset!!
            tileInfo.setTerrainTransients()
            tileInfo.setUnitTransients(setUnitCivTransients)
        }
    }

    fun removeMissingTerrainModReferences(ruleSet: Ruleset) {
        for (tile in this.values) {
            for (terrainFeature in tile.terrainFeatures.filter { !ruleSet.terrains.containsKey(it) })
                tile.removeTerrainFeature(terrainFeature)
            if (tile.resource != null && !ruleSet.tileResources.containsKey(tile.resource!!))
                tile.resource = null
            if (tile.improvement != null && !ruleSet.tileImprovements.containsKey(tile.improvement!!))
                tile.improvement = null
        }
        for (startingLocation in startingLocations.toList())
            if (startingLocation.nation !in ruleSet.nations.keys)
                startingLocations.remove(startingLocation)
    }

    /** Tries to place the [unitName] into the [TileInfo] closest to the given [position]
     * @param position where to try to place the unit (or close - max 10 tiles distance)
     * @param unitName name of the [BaseUnit][com.unciv.models.ruleset.unit.BaseUnit] to create and place
     * @param civInfo civilization to assign unit to
     * @return created [MapUnit] or null if no suitable location was found
     * */
    fun placeUnitNearTile(
            position: Vector2,
            unitName: String,
            civInfo: CivilizationInfo
    ): MapUnit? {
        val unit = gameInfo.ruleSet.units[unitName]!!.getMapUnit(civInfo)

        fun getPassableNeighbours(tileInfo: TileInfo): Set<TileInfo> =
                tileInfo.neighbors.filter { unit.movement.canPassThrough(it) }.toSet()

        // both the civ name and actual civ need to be in here in order to calculate the canMoveTo...Darn
        unit.assignOwner(civInfo, false)
        // remember our first owner
        unit.originalOwner = civInfo.civName

        var unitToPlaceTile: TileInfo? = null
        // try to place at the original point (this is the most probable scenario)
        val currentTile = get(position)
        unit.currentTile = currentTile  // temporary
        if (unit.movement.canMoveTo(currentTile)) unitToPlaceTile = currentTile

        // if it's not suitable, try to find another tile nearby
        if (unitToPlaceTile == null) {
            var tryCount = 0
            var potentialCandidates = getPassableNeighbours(currentTile)
            while (unitToPlaceTile == null && tryCount++ < 10) {
                unitToPlaceTile = potentialCandidates
                        .sortedByDescending { if (unit.baseUnit.isLandUnit()) it.isLand else true } // Land units should prefer to go into land tiles
                        .firstOrNull { unit.movement.canMoveTo(it) }
                if (unitToPlaceTile != null) continue
                // if it's not found yet, let's check their neighbours
                val newPotentialCandidates = mutableSetOf<TileInfo>()
                potentialCandidates.forEach { newPotentialCandidates.addAll(getPassableNeighbours(it)) }
                potentialCandidates = newPotentialCandidates
            }
        }

        if (unitToPlaceTile == null) {
            civInfo.removeUnit(unit) // since we added it to the civ units in the previous assignOwner
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

        if (unit.baseUnit.getResourceRequirements().isNotEmpty())
            civInfo.updateDetailedCivResources()

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
        val newCiv = CivilizationInfo(newNation.name).apply { nation = newNation }
        tileList.forEach {
            for (unit in it.getUnits()) if (unit.owner == player.chosenCiv) {
                unit.owner = newNation.name
                unit.civInfo = newCiv
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
     *  Initialize startingLocations transients, including legacy support (maps saved with placeholder improvements)
     */
    fun setStartingLocationsTransients() {
        if (startingLocations.size == 1 && startingLocations[0].nation == legacyMarker)
            return translateStartingLocationsFromMap()
        startingLocationsByNation.clear()
        for ((position, nationName) in startingLocations) {
            val nationSet = startingLocationsByNation[nationName] ?: hashSetOf<TileInfo>().also { startingLocationsByNation[nationName] = it }
            nationSet.add(get(position))
        }
    }

    /**
     *  Scan and remove placeholder improvements from map and build startingLocations from them
     */
    private fun translateStartingLocationsFromMap() {
        startingLocations.clear()
        tileList.asSequence()
            .filter { it.improvement?.startsWith(startingLocationPrefix) == true }
            .map { it to StartingLocation(it.position, it.improvement!!.removePrefix(startingLocationPrefix)) }
            .sortedBy { it.second.nation }  // vanity, or to make diffs between un-gzipped map files easier
            .forEach { (tile, startingLocation) ->
                tile.improvement = null
                startingLocations.add(startingLocation)
            }
        setStartingLocationsTransients()
    }

    /** Adds a starting position, maintaining the transients
     * @return true if the starting position was not already stored as per [Collection]'s add */
    fun addStartingLocation(nationName: String, tile: TileInfo): Boolean {
        if (startingLocationsByNation[nationName]?.contains(tile) == true) return false
        startingLocations.add(StartingLocation(tile.position, nationName))
        val nationSet = startingLocationsByNation[nationName] ?: hashSetOf<TileInfo>().also { startingLocationsByNation[nationName] = it }
        return nationSet.add(tile)
    }

    /** Removes a starting position, maintaining the transients
     * @return true if the starting position was removed as per [Collection]'s remove */
    fun removeStartingLocation(nationName: String, tile: TileInfo): Boolean {
        if (startingLocationsByNation[nationName]?.contains(tile) != true) return false
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
}
