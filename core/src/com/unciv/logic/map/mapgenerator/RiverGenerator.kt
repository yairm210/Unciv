package com.unciv.logic.map.mapgenerator

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.utils.debug
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Ruleset
import kotlin.math.roundToInt

class RiverGenerator(
    private val tileMap: TileMap,
    private val randomness: MapGenerationRandomness,
    ruleset: Ruleset
) {
    private val riverCountMultiplier = ruleset.modOptions.constants.riverCountMultiplier
    private val minRiverLength = ruleset.modOptions.constants.minRiverLength
    private val maxRiverLength = ruleset.modOptions.constants.maxRiverLength

    fun spawnRivers() {
        if (tileMap.values.none { it.isWater }) return
        val numberOfRivers = (tileMap.values.count { it.isLand } * riverCountMultiplier).roundToInt()

        var optionalTiles = tileMap.values.asSequence()
            .filter { it.baseTerrain == Constants.mountain && it.isFarEnoughFromWater() }
            .toMutableList()
        if (optionalTiles.size < numberOfRivers)
            optionalTiles.addAll(tileMap.values.filter { it.isHill() && it.isFarEnoughFromWater() })
        if (optionalTiles.size < numberOfRivers)
            optionalTiles =
                tileMap.values.filter { it.isLand && it.isFarEnoughFromWater() }.toMutableList()

        val mapRadius = tileMap.mapParameters.mapSize.radius
        val riverStarts =
            randomness.chooseSpreadOutLocations(numberOfRivers, optionalTiles, mapRadius)
        for (tile in riverStarts) spawnRiver(tile)
    }

    private fun TileInfo.isFarEnoughFromWater(): Boolean {
        for (distance in 1 until minRiverLength) {
            if (getTilesAtDistance(distance).any { it.isWater }) return false
        }
        return true
    }

    fun getClosestWaterTile(tile: TileInfo): TileInfo? {
        for (distance in 1..maxRiverLength) {
            val waterTiles = tile.getTilesAtDistance(distance).filter { it.isWater }
            if (waterTiles.any())
                return waterTiles.toList().random(randomness.RNG)
        }
        return null
    }

    private fun spawnRiver(initialPosition: TileInfo) {
        val endPosition = getClosestWaterTile(initialPosition)
            ?: throw IllegalStateException("No water found for river destination")
        spawnRiver(initialPosition, endPosition)
    }

    fun spawnRiver(initialPosition: TileInfo, endPosition: TileInfo, resultingTiles: MutableSet<TileInfo>? = null) {
        // Recommendation: Draw a bunch of hexagons on paper before trying to understand this, it's super helpful!

        var riverCoordinate = RiverCoordinate(initialPosition.position,
                RiverCoordinate.BottomRightOrLeft.values().random(randomness.RNG))

        for (step in 1..maxRiverLength) {     // Arbitrary max on river length, otherwise this will go in circles - rarely
            val riverCoordinateTile = tileMap[riverCoordinate.position]
            resultingTiles?.add(riverCoordinateTile)
            if (riverCoordinate.getAdjacentTiles(tileMap).any { it.isWater }) return
            val possibleCoordinates = riverCoordinate.getAdjacentPositions(tileMap)
            if (possibleCoordinates.none()) return // end of the line
            val newCoordinate = possibleCoordinates
                .groupBy { newCoordinate ->
                    newCoordinate.getAdjacentTiles(tileMap).map { it.aerialDistanceTo(endPosition) }
                        .minOrNull()!!
                }
                .minByOrNull { it.key }!!
                    .component2().random(randomness.RNG)

            // set new rivers in place
            if (newCoordinate.position == riverCoordinate.position) // same tile, switched right-to-left
                riverCoordinateTile.hasBottomRiver = true
            else if (riverCoordinate.bottomRightOrLeft == RiverCoordinate.BottomRightOrLeft.BottomRight) {
                if (newCoordinate.getAdjacentTiles(tileMap).contains(riverCoordinateTile)) // moved from our 5 O'Clock to our 3 O'Clock
                    riverCoordinateTile.hasBottomRightRiver = true
                else // moved from our 5 O'Clock down in the 5 O'Clock direction - this is the 8 O'Clock river of the tile to our 4 O'Clock!
                    tileMap[newCoordinate.position].hasBottomLeftRiver = true
            } else { // riverCoordinate.bottomRightOrLeft==RiverCoordinate.BottomRightOrLeft.Left
                if (newCoordinate.getAdjacentTiles(tileMap).contains(riverCoordinateTile)) // moved from our 7 O'Clock to our 9 O'Clock
                    riverCoordinateTile.hasBottomLeftRiver = true
                else // moved from our 7 O'Clock down in the 7 O'Clock direction
                    tileMap[newCoordinate.position].hasBottomRightRiver = true
            }
            riverCoordinate = newCoordinate
        }
        debug("River reached max length!")
    }

/*
    fun numberOfConnectedRivers(riverCoordinate: RiverCoordinate): Int {
        var sum = 0
        if (tileMap.contains(riverCoordinate.position) && tileMap[riverCoordinate.position].hasBottomRiver) sum += 1
        if (riverCoordinate.bottomRightOrLeft == RiverCoordinate.BottomRightOrLeft.BottomLeft) {
            if (tileMap.contains(riverCoordinate.position) && tileMap[riverCoordinate.position].hasBottomLeftRiver) sum += 1
            val bottomLeftTilePosition = riverCoordinate.position.cpy().add(0f, -1f)
            if (tileMap.contains(bottomLeftTilePosition) && tileMap[bottomLeftTilePosition].hasBottomRightRiver) sum += 1
        } else {
            if (tileMap.contains(riverCoordinate.position) && tileMap[riverCoordinate.position].hasBottomRightRiver) sum += 1
            val bottomLeftTilePosition = riverCoordinate.position.cpy().add(-1f, 0f)
            if (tileMap.contains(bottomLeftTilePosition) && tileMap[bottomLeftTilePosition].hasBottomLeftRiver) sum += 1
        }
        return sum
    }
*/

    /** Describes a _Vertex_ on our hexagonal grid via a neighboring hex and clock direction, normalized
     * such that always the north-most hex and one of the two clock directions 5 / 7 o'clock are used. */
    class RiverCoordinate(val position: Vector2, val bottomRightOrLeft: BottomRightOrLeft) {
        enum class BottomRightOrLeft {
            /** 7 O'Clock of the tile */
            BottomLeft,

            /** 5 O'Clock of the tile */
            BottomRight
        }

        /** Lists the three neighboring vertices which have their anchor hex on the map
         * (yes some positions on the map's outer border will be included, some not) */
        fun getAdjacentPositions(tileMap: TileMap): Sequence<RiverCoordinate> = sequence {
            // What's nice is that adjacents are always the OPPOSITE in terms of right-left - rights are adjacent to only lefts, and vice-versa
            // This means that a lot of obviously-wrong assignments are simple to spot
            val x = position.x.toInt()
            val y = position.y.toInt()
            if (bottomRightOrLeft == BottomRightOrLeft.BottomLeft) {
                yield(RiverCoordinate(position, BottomRightOrLeft.BottomRight)) // same tile, other side
                val myTopLeft = tileMap.getIfTileExistsOrNull(x + 1, y)
                if (myTopLeft != null)
                    yield(RiverCoordinate(myTopLeft.position, BottomRightOrLeft.BottomRight)) // tile to MY top-left, take its bottom right corner
                val myBottomLeft = tileMap.getIfTileExistsOrNull(x, y - 1)
                if (myBottomLeft != null)
                    yield(RiverCoordinate(myBottomLeft.position, BottomRightOrLeft.BottomRight)) // Tile to MY bottom-left, take its bottom right
            } else {
                yield(RiverCoordinate(position, BottomRightOrLeft.BottomLeft)) // same tile, other side
                val myTopRight = tileMap.getIfTileExistsOrNull(x, y + 1)
                if (myTopRight != null)
                    yield(RiverCoordinate(myTopRight.position, BottomRightOrLeft.BottomLeft)) // tile to MY top-right, take its bottom left
                val myBottomRight = tileMap.getIfTileExistsOrNull(x - 1, y)
                if (myBottomRight != null)
                    yield(RiverCoordinate(myBottomRight.position, BottomRightOrLeft.BottomLeft))  // tile to MY bottom-right, take its bottom left
            }
        }

        /** Lists the three neighboring hexes to this vertex which are on the map */
        fun getAdjacentTiles(tileMap: TileMap): Sequence<TileInfo> = sequence {
            val x = position.x.toInt()
            val y = position.y.toInt()
            yield(tileMap[x, y])
            val below = tileMap.getIfTileExistsOrNull(x - 1, y - 1)  // tile directly below us,
            if (below != null) yield(below)
            val leftOrRight = if (bottomRightOrLeft == BottomRightOrLeft.BottomLeft)
                tileMap.getIfTileExistsOrNull(x, y - 1)  // tile to our bottom-left
                else tileMap.getIfTileExistsOrNull(x - 1, y)  // tile to our bottom-right
            if (leftOrRight != null) yield(leftOrRight)
        }
    }
}
