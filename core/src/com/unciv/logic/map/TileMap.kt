package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset
import kotlin.math.abs

class TileMap {

    @Transient lateinit var gameInfo: GameInfo
    @Transient var tileMatrix = ArrayList<ArrayList<TileInfo?>>() // this works several times faster than a hashmap, the performance difference is really astounding
    @Transient var leftX = 0
    @Transient var bottomY = 0
    @delegate:Transient val maxLatitude: Float by lazy { if (values.isEmpty()) 0f else values.map { abs(it.latitude) }.max()!! }
    @delegate:Transient val maxLongitude: Float by lazy { if (values.isEmpty()) 0f else values.map { abs(it.longitude) }.max()!! }

    var mapParameters = MapParameters()

    private var tileList = ArrayList<TileInfo>()

    val values: Collection<TileInfo>
        get() = tileList

    /** for json parsing, we need to have a default constructor */
    constructor()

    /** generates an hexagonal map of given radius */
    constructor(radius:Int, ruleset: Ruleset){
        for(vector in HexMath.getVectorsInDistance(Vector2.Zero, radius))
            tileList.add(TileInfo().apply { position = vector; baseTerrain = Constants.grassland })
        setTransients(ruleset)
    }

    /** generates a rectangular map of given width and height*/
    constructor(width: Int, height: Int, ruleset: Ruleset) {
        for(x in -width/2..width/2)
            for (y in -height/2..height/2)
                tileList.add(TileInfo().apply {
                    position = HexMath.evenQ2HexCoords(Vector2(x.toFloat(),y.toFloat()))
                    baseTerrain = Constants.grassland })
        setTransients(ruleset)
    }

    fun clone(): TileMap {
        val toReturn = TileMap()
        toReturn.tileList.addAll(tileList.map { it.clone() })
        toReturn.mapParameters = mapParameters
        return toReturn
    }

    operator fun contains(vector: Vector2): Boolean {
        return contains(vector.x.toInt(), vector.y.toInt())
    }

    fun contains(x:Int, y:Int): Boolean {
        val arrayXIndex = x-leftX
        if(arrayXIndex<0 || arrayXIndex>=tileMatrix.size) return false
        val arrayYIndex = y-bottomY
        if(arrayYIndex<0 || arrayYIndex>=tileMatrix[arrayXIndex].size) return false
        return tileMatrix[arrayXIndex][arrayYIndex] != null
    }

    operator fun get(x:Int, y:Int):TileInfo{
        val arrayXIndex = x-leftX
        val arrayYIndex = y-bottomY
        return tileMatrix[arrayXIndex][arrayYIndex]!!
    }

    operator fun get(vector: Vector2): TileInfo {
        return get(vector.x.toInt(), vector.y.toInt())
    }

    fun getTilesInDistance(origin: Vector2, distance: Int): Sequence<TileInfo> =
            getTilesInDistanceRange(origin, 0..distance)

    fun getTilesInDistanceRange(origin: Vector2, range: IntRange): Sequence<TileInfo> =
            range.asSequence().flatMap { getTilesAtDistance(origin, it) }

    fun getTilesAtDistance(origin: Vector2, distance: Int): Sequence<TileInfo> =
            if (distance <= 0) // silently take negatives.
                sequenceOf(get(origin))
            else
                sequence {
                    fun getIfTileExistsOrNull(x: Int, y: Int) = if (contains(x, y)) get(x, y) else null

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

    /** Tries to place the [unitName] into the [TileInfo] closest to the given the [position]
     * @param civInfo civilization to assign unit to
     * @return created [MapUnit] or null if no suitable location was found
     * */
    fun placeUnitNearTile(
            position: Vector2,
            unitName: String,
            civInfo: CivilizationInfo
    ): MapUnit? {
        val unit = gameInfo.ruleSet.units[unitName]!!.getMapUnit(gameInfo.ruleSet)

        fun getPassableNeighbours(tileInfo: TileInfo): Set<TileInfo> =
                getTilesAtDistance(tileInfo.position, 1).filter { unit.movement.canPassThrough(it) }.toSet()

        // both the civ name and actual civ need to be in here in order to calculate the canMoveTo...Darn
        unit.assignOwner(civInfo, false)

        var unitToPlaceTile : TileInfo? = null
        // try to place at the original point (this is the most probable scenario)
        val currentTile = get(position)
        if (unit.movement.canMoveTo(currentTile)) unitToPlaceTile = currentTile

        // if it's not suitable, try to find another tile nearby
        if (unitToPlaceTile == null) {
            var tryCount = 0
            var potentialCandidates = getPassableNeighbours(currentTile)
            while (unitToPlaceTile == null && tryCount++ < 10) {
                unitToPlaceTile = potentialCandidates
                        .sortedByDescending { if(unit.type.isLandUnit()) it.isLand else true } // Land units should prefer to go into land tiles
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

        // And update civ stats, since the new unit changes both unit upkeep and resource consumption
        civInfo.updateStatsForNextTurn()
        civInfo.updateDetailedCivResources()

        return unit
    }


    fun getViewableTiles(position: Vector2, sightDistance: Int): List<TileInfo> {
        val viewableTiles = getTilesInDistance(position, 1).toMutableList()
        val currentTileHeight = get(position).getHeight()

        for (i in 1..sightDistance) { // in each layer,
            // This is so we don't use tiles in the same distance to "see over",
            // that is to say, the "viewableTiles.contains(it) check will return false for neighbors from the same distance
            val tilesToAddInDistanceI = ArrayList<TileInfo>()

            for (tile in getTilesAtDistance(position, i)) { // for each tile in that layer,
                val targetTileHeight = tile.getHeight()

                /*
            Okay so, if we're looking at a tile from a to c with b in the middle,
            we have several scenarios:
            1. a>b -  - I can see everything, b does not hide c
            2. a==b
                2.1 a==b==0, all flat ground, no hiding
                2.2 a>0, b>=c - b hides c from view (say I am in a forest/jungle and b is a forest/jungle, or hill)
                2.3 a>0, c>b - c is tall enough I can see it over b!
            3. a<b
                3.1 b>=c - b hides c
                3.2 b<c - c is tall enough I can see it over b!

            This can all be summed up as "I can see c if a>b || c>b || b==0 "
            */

                val containsViewableNeighborThatCanSeeOver = tile.neighbors.any {
                    val neighborHeight = it.getHeight()
                    viewableTiles.contains(it) && (
                            currentTileHeight > neighborHeight // a>b
                                    || targetTileHeight > neighborHeight // c>b
                                    || neighborHeight == 0) // b==0
                }
                if (containsViewableNeighborThatCanSeeOver) tilesToAddInDistanceI.add(tile)
            }
            viewableTiles.addAll(tilesToAddInDistanceI)
        }

        return viewableTiles
    }

    fun setTransients(ruleset: Ruleset, setUnitCivTransients:Boolean=true) { // In the map editor, no Civs or Game exist, so we won't set the unit transients
        val topY= tileList.asSequence().map { it.position.y.toInt() }.max()!!
        bottomY= tileList.asSequence().map { it.position.y.toInt() }.min()!!
        val rightX= tileList.asSequence().map { it.position.x.toInt() }.max()!!
        leftX = tileList.asSequence().map { it.position.x.toInt() }.min()!!

        for(x in leftX..rightX){
            val row = ArrayList<TileInfo?>()
            for(y in bottomY..topY) row.add(null)
            tileMatrix.add(row)
        }

        for (tileInfo in values){
            tileMatrix[tileInfo.position.x.toInt()-leftX][tileInfo.position.y.toInt()-bottomY] = tileInfo
            tileInfo.tileMap = this
            tileInfo.ruleset = ruleset
            tileInfo.setTerrainTransients()
            tileInfo.setUnitTransients(setUnitCivTransients)
        }
    }
}

