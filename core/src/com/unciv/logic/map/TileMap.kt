package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.linq.Linq
import com.unciv.models.linq.LinqHashMap
import com.unciv.logic.GameInfo
import com.unciv.ui.utils.HexMath

class TileMap {

    @Transient
    @JvmField var gameInfo: GameInfo? = null

    private var tiles = LinqHashMap<String, TileInfo>()

    constructor()  // for json parsing, we need to have a default constructor

    val values: Linq<TileInfo>
        get() = tiles.linqValues()


    constructor(distance: Int) {
        tiles = RandomMapGenerator().generateMap(distance)
        setTransients()
    }

    operator fun contains(vector: Vector2): Boolean {
        return tiles.containsKey(vector.toString())
    }

    operator fun get(vector: Vector2): TileInfo {
        return tiles[vector.toString()]!!
    }

    fun getTilesInDistance(origin: Vector2, distance: Int): Linq<TileInfo> {
        return HexMath.GetVectorsInDistance(origin, distance).where{contains(it)}.select { get(it) }
    }

    fun getTilesAtDistance(origin: Vector2, distance: Int): Linq<TileInfo> {
        return HexMath.GetVectorsAtDistance(origin, distance).where{contains(it)}.select { get(it) }

    }

    fun getDistanceToTilesWithinTurn(origin: Vector2, currentUnitMovement: Float, machineryIsResearched: Boolean): LinqHashMap<TileInfo, Float> {
        val distanceToTiles = LinqHashMap<TileInfo, Float>()
        distanceToTiles[get(origin)] = 0f
        var tilesToCheck = Linq<TileInfo>(get(origin))
        while (!tilesToCheck.isEmpty()) {
            val updatedTiles = Linq<TileInfo>()
            for (tileToCheck in tilesToCheck)
                for (maybeUpdatedTile in getTilesInDistance(tileToCheck.position, 1)) {
                    var distanceBetweenTiles = maybeUpdatedTile.lastTerrain.movementCost.toFloat() // no road
                    if (tileToCheck.roadStatus !== RoadStatus.None && maybeUpdatedTile.roadStatus !== RoadStatus.None) //Road
                        distanceBetweenTiles = if (machineryIsResearched) 1 / 3f else 1 / 2f

                    if (tileToCheck.roadStatus === RoadStatus.Railroad && maybeUpdatedTile.roadStatus === RoadStatus.Railroad) // Railroad
                        distanceBetweenTiles = 1 / 10f

                    var totalDistanceToTile = distanceToTiles[tileToCheck]!! + distanceBetweenTiles
                    if (!distanceToTiles.containsKey(maybeUpdatedTile) || distanceToTiles[maybeUpdatedTile]!! > totalDistanceToTile) {
                        if (totalDistanceToTile < currentUnitMovement)
                            updatedTiles += maybeUpdatedTile
                        else
                            totalDistanceToTile = currentUnitMovement
                        distanceToTiles[maybeUpdatedTile] = totalDistanceToTile
                    }

                }

            tilesToCheck = updatedTiles
        }
        return distanceToTiles
    }

    fun getShortestPath(origin: Vector2, destination: Vector2, currentMovement: Float, maxMovement: Int, isMachineryResearched: Boolean): Linq<TileInfo> {
        var tilesToCheck: Linq<TileInfo> = Linq(get(origin))
        val movementTreeParents = LinqHashMap<TileInfo, TileInfo>() // contains a map of "you can get from X to Y in that turn"
        movementTreeParents[get(origin)] = null

        var distance = 1
        while (true) {
            val newTilesToCheck = Linq<TileInfo>()
            for (tileToCheck in tilesToCheck) {
                val movementThisTurn = if (distance == 1) currentMovement else maxMovement.toFloat()
                for (reachableTile in getDistanceToTilesWithinTurn(tileToCheck.position, movementThisTurn, isMachineryResearched).keys) {
                    if (movementTreeParents.containsKey(reachableTile)) continue // We cannot be faster than anything existing...
                    if (reachableTile.position != destination && reachableTile.unit != null) continue // This is an intermediary tile that contains a unit - we can't go there!
                    movementTreeParents[reachableTile] = tileToCheck
                    if (reachableTile.position == destination) {
                        val path = Linq<TileInfo>() // Traverse the tree upwards to get the list of tiles leading to the destination,
                        var current = reachableTile
                        while (movementTreeParents[current] != null) {
                            path.add(current)
                            current = movementTreeParents[current]
                        }
                        return path.reverse() // and reverse in order to get the list in chronological order
                    }
                    newTilesToCheck.add(reachableTile)
                }
            }
            tilesToCheck = newTilesToCheck
            distance++
        }
    }

    fun placeUnitNearTile(position: Vector2, unitName: String, civInfo: CivilizationInfo) {
        val unit = GameBasics.Units[unitName]!!.mapUnit
        unit.owner = civInfo.civName
        unit.civInfo = civInfo
        getTilesInDistance(position, 2).first { it.unit == null }!!.unit = unit // And if there's none, then kill me.
    }

    fun getViewableTiles(position: Vector2, sightDistance: Int): Linq<TileInfo> {
        var sightDistance = sightDistance
        val viewableTiles = getTilesInDistance(position, 1)
        if (get(position).baseTerrain == "Hill") sightDistance += 1
        for (i in 1..sightDistance) { // in each layer,
            getTilesAtDistance(position, i).filterTo(viewableTiles) // take only tiles which have a visible neighbor, which is lower than the tile
                { tile -> tile.neighbors.any{viewableTiles.contains(it) && (it.height==0 || it.height < tile.height)}  }
        }

        return viewableTiles
    }

    fun setTransients() {
        for (tileInfo in values) tileInfo.tileMap = this
    }

}