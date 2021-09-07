package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import kotlin.math.abs

/** An Unciv map with all properties as produced by the [map editor][com.unciv.ui.mapeditor.MapEditorScreen]
 * or [MapGenerator][com.unciv.logic.map.mapgenerator.MapGenerator]; or as part of a running [game][GameInfo]. 
 * 
 * Note: Will be Serialized -> Take special care with lateinit and lazy! 
 */
class TileMap {
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
    private data class StartingLocation(val position: Vector2 = Vector2.Zero, val nation: String = "")
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
    val maxLatitude: Float by lazy { if (values.isEmpty()) 0f else values.map { abs(it.latitude) }.maxOrNull()!! }

    @delegate:Transient
    val maxLongitude: Float by lazy { if (values.isEmpty()) 0f else values.map { abs(it.longitude) }.maxOrNull()!! }

    @delegate:Transient
    val naturalWonders: List<String> by lazy { tileList.asSequence().filter { it.isNaturalWonder() }.map { it.naturalWonder!! }.distinct().toList() }

    // Excluded from Serialization by having no own backing field
    val values: Collection<TileInfo>
        get() = tileList

    @Transient
    val startingLocationsByNation = HashMap<String,HashSet<TileInfo>>()

    //endregion
    //region Constructors

    /** for json parsing, we need to have a default constructor */
    constructor()

    /** creates a hexagonal map of given radius (filled with grassland) */
    constructor(radius: Int, ruleset: Ruleset, worldWrap: Boolean = false) {
        startingLocations.clear()
        for (vector in HexMath.getVectorsInDistance(Vector2.Zero, radius, worldWrap))
            tileList.add(TileInfo().apply { position = vector; baseTerrain = Constants.grassland })
        setTransients(ruleset)
    }

    /** creates a rectangular map of given width and height (filled with grassland) */
    constructor(width: Int, height: Int, ruleset: Ruleset, worldWrap: Boolean = false) {
        startingLocations.clear()

        // world-wrap maps must always have an even width, so round down
        val wrapAdjustedWidth = if (worldWrap && width % 2 != 0 ) width -1 else width

        // Even widths will have coordinates ranging -x..(x-1), not -x..x, which is always an odd-sized range
        // e.g. w=4 -> -2..1, w=5 -> -2..2, w=6 -> -3..2, w=7 -> -3..3
        for (x in -wrapAdjustedWidth / 2 .. (wrapAdjustedWidth-1) / 2)
            for (y in -height / 2 .. (height-1) / 2)
                tileList.add(TileInfo().apply {
                    position = HexMath.evenQ2HexCoords(Vector2(x.toFloat(), y.toFloat()))
                    baseTerrain = Constants.grassland
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
     * Returns the clockPosition of [otherTile] seen from [tile]'s position
     * Returns -1 if not neighbors
     */
    fun getNeighborTileClockPosition(tile: TileInfo, otherTile: TileInfo): Int {
        val radius = if (mapParameters.shape == MapShape.rectangular)
            mapParameters.mapSize.width / 2
        else mapParameters.mapSize.radius

        val xDifference = tile.position.x - otherTile.position.x
        val yDifference = tile.position.y - otherTile.position.y
        val xWrapDifferenceBottom = tile.position.x - (otherTile.position.x - radius)
        val yWrapDifferenceBottom = tile.position.y - (otherTile.position.y - radius)
        val xWrapDifferenceTop = tile.position.x - (otherTile.position.x + radius)
        val yWrapDifferenceTop = tile.position.y - (otherTile.position.y + radius)

        return when {
            xDifference == 1f && yDifference == 1f -> 6 // otherTile is below
            xDifference == -1f && yDifference == -1f -> 12 // otherTile is above
            xDifference == 1f || xWrapDifferenceBottom == 1f -> 4 // otherTile is bottom-right
            yDifference == 1f || yWrapDifferenceBottom == 1f -> 8 // otherTile is bottom-left
            xDifference == -1f || xWrapDifferenceTop == -1f -> 10 // otherTile is top-left
            yDifference == -1f || yWrapDifferenceTop == -1f -> 2 // otherTile is top-right
            else -> -1
        }
    }

    /** Convert relative direction of [otherTile] seen from [tile]'s position into a vector
     * in world coordinates of length sqrt(3), so that it can be used to go from tile center to
     * the edge of the hex in that direction (meaning the center of the border between the hexes)
     */
    fun getNeighborTilePositionAsWorldCoords(tile: TileInfo, otherTile: TileInfo): Vector2 =
        HexMath.getClockDirectionToWorldVector(getNeighborTileClockPosition(tile, otherTile))

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
        val currentTileHeight = get(position).getHeight()

        for (i in 1..sightDistance) { // in each layer,
            // This is so we don't use tiles in the same distance to "see over",
            // that is to say, the "viewableTiles.contains(it) check will return false for neighbors from the same distance
            val tilesToAddInDistanceI = ArrayList<TileInfo>()

            for (cTile in getTilesAtDistance(position, i)) { // for each tile in that layer,
                val cTileHeight = cTile.getHeight()

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

                val containsViewableNeighborThatCanSeeOver = cTile.neighbors.any {
                        bNeighbor: TileInfo ->
                    val bNeighborHeight = bNeighbor.getHeight()
                    viewableTiles.contains(bNeighbor) && (
                            currentTileHeight > bNeighborHeight // a>b
                                    || cTileHeight > bNeighborHeight // c>b
                                    || currentTileHeight == bNeighborHeight // a==b
                                    && !bNeighbor.hasUnique("Blocks line-of-sight from tiles at same elevation"))
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
        setTransients(ruleset)
        setStartingLocationsTransients()
        val rulesetIncompatibilities = HashSet<String>()
        for (set in values.map { it.getRulesetIncompatibility(ruleset) })
            rulesetIncompatibilities.addAll(set)
        for ((_, nationName) in startingLocations) {
            if (nationName !in ruleset.nations)
                rulesetIncompatibilities.add("Nation [$nationName] does not exist in ruleset!")
        }
        rulesetIncompatibilities.remove("")
        return rulesetIncompatibilities
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

            for (x in leftX..rightX) {
                val row = ArrayList<TileInfo?>()
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
            tileInfo.tileMap = this
            tileInfo.ruleset = this.ruleset!!
            tileInfo.setTerrainTransients()
            tileInfo.setUnitTransients(setUnitCivTransients)
        }
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

        var unitToPlaceTile: TileInfo? = null
        // try to place at the original point (this is the most probable scenario)
        val currentTile = get(position)
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

        // Only once we add the unit to the civ we can activate addPromotion, because it will try to update civ viewable tiles
        for (promotion in unit.baseUnit.promotions)
            unit.promotions.addPromotion(promotion, true)

        for (unique in civInfo.getMatchingUniques("[] units gain the [] promotion")) {
            if (unit.matchesFilter(unique.params[0])) {
                unit.promotions.addPromotion(unique.params[1], true)
            }
        }
        
        // And update civ stats, since the new unit changes both unit upkeep and resource consumption
        civInfo.updateStatsForNextTurn()
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
        startingLocations.removeAll(startingLocations.filter { it.nation == player.chosenCiv }) // filter creates a copy, no concurrent modification
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

    /** Clears starting positions, e.g. after GameStarter is done with them. Does not clear the pseudo-improvements. */
    fun clearStartingLocations() {
        startingLocations.clear()
        startingLocationsByNation.clear()
    }

    //endregion
}
