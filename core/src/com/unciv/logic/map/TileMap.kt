package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.HexMath

class TileMap {

    @Transient
    lateinit var gameInfo: GameInfo

    private var tiles = HashMap<String, TileInfo>()

    constructor()  // for json parsing, we need to have a default constructor

    val values: MutableCollection<TileInfo>
        get() = tiles.values


    constructor(distance: Int) {
        tiles = SeedRandomMapGenerator().generateMap(distance)
        setTransients()
    }

    operator fun contains(vector: Vector2): Boolean {
        return tiles.containsKey(vector.toString())
    }

    operator fun get(vector: Vector2): TileInfo {
        return tiles[vector.toString()]!!
    }

    fun getTilesInDistance(origin: Vector2, distance: Int): List<TileInfo> {
        return HexMath.GetVectorsInDistance(origin, distance).filter {contains(it)}.map { get(it) }
    }

    fun getTilesAtDistance(origin: Vector2, distance: Int): List<TileInfo> {
        return HexMath.GetVectorsAtDistance(origin, distance).filter {contains(it)}.map { get(it) }

    }


    fun placeUnitNearTile(position: Vector2, unitName: String, civInfo: CivilizationInfo) {
        val unit = GameBasics.Units[unitName]!!.getMapUnit()
        unit.owner = civInfo.civName
        unit.civInfo = civInfo
        val tilesInDistance = getTilesInDistance(position, 2)
        tilesInDistance.first { it.unit == null }.unit = unit // And if there's none, then kill me.
    }

    fun getViewableTiles(position: Vector2, sightDistance: Int): MutableList<TileInfo> {
        var sightDistance = sightDistance
        val viewableTiles = getTilesInDistance(position, 1).toMutableList()
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