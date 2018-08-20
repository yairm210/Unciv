package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics

class TileMap {

    @Transient lateinit var gameInfo: GameInfo
    @Transient var tileMatrix=ArrayList<ArrayList<TileInfo?>>() // this works several times faster than a hashmap, the performance difference is really astounding
    @Transient var leftX=0
    @Transient var bottomY=0

    @Deprecated("as of 2.7.10")
    private var tiles = HashMap<String, TileInfo>()

    private var tileList = ArrayList<TileInfo>()

    constructor()  // for json parsing, we need to have a default constructor

    fun clone(): TileMap {
        val toReturn = TileMap()
        toReturn.tileList.addAll(tileList.map { it.clone() })
        return toReturn
    }

    val values: Collection<TileInfo>
        get() = tileList


    constructor(distance: Int) {
        tileList.addAll(SeedRandomMapGenerator().generateMap(distance).values)
        setTransients()
    }

    operator fun contains(vector: Vector2): Boolean {
        val arrayXIndex = vector.x.toInt()-leftX
        if(arrayXIndex<0 || arrayXIndex>=tileMatrix.size) return false
        val arrayYIndex = vector.y.toInt()-bottomY
        if(arrayYIndex<0 || arrayYIndex>=tileMatrix[arrayXIndex].size) return false
        return tileMatrix[arrayXIndex][arrayYIndex] != null
    }

    operator fun get(vector: Vector2): TileInfo {
        val arrayXIndex = vector.x.toInt()-leftX
        val arrayYIndex = vector.y.toInt()-bottomY
        return tileMatrix[arrayXIndex][arrayYIndex]!!
    }

    fun getTilesInDistance(origin: Vector2, distance: Int): List<TileInfo> {
        return HexMath().GetVectorsInDistance(origin, distance).filter {contains(it)}.map { get(it) }
    }

    fun getTilesAtDistance(origin: Vector2, distance: Int): List<TileInfo> {
        return HexMath().GetVectorsAtDistance(origin, distance).filter {contains(it)}.map { get(it) }

    }

    fun placeUnitNearTile(position: Vector2, unitName: String, civInfo: CivilizationInfo): MapUnit {
        val unit = GameBasics.Units[unitName]!!.getMapUnit()
        unit.assignOwner(civInfo)
        val tilesInDistance = getTilesInDistance(position, 2)
        val unitToPlaceTile = tilesInDistance.firstOrNull { unit.canMoveTo(it) }
        if(unitToPlaceTile!=null) unit.putInTile(unitToPlaceTile)
        return unit
    }

    fun getViewableTiles(position: Vector2, sightDistance: Int): MutableList<TileInfo> {
        val viewableTiles = getTilesInDistance(position, 1).toMutableList()
        for (i in 1..sightDistance) { // in each layer,
            getTilesAtDistance(position, i).filterTo(viewableTiles) // take only tiles which have a visible neighbor, which is lower than the tile
                { tile -> tile.neighbors.any{viewableTiles.contains(it) && (it.height==0 || it.height < tile.height)}  }
        }

        return viewableTiles
    }

    fun setTransients() {
        if(tiles.any()) //
            tileList.addAll(tiles.values)

        val topY=tileList.map { it.position.y.toInt() }.max()!!
        bottomY= tileList.map { it.position.y.toInt() }.min()!!
        val rightX=tileList.map { it.position.x.toInt() }.max()!!
        leftX = tileList.map { it.position.x.toInt() }.min()!!

        for(x in leftX..rightX){
            val row = ArrayList<TileInfo?>()
            for(y in bottomY..topY) row.add(null)
            tileMatrix.add(row)
        }

        for (tileInfo in values){
            tileMatrix[tileInfo.position.x.toInt()-leftX][tileInfo.position.y.toInt()-bottomY] = tileInfo
            tileInfo.tileMap = this
            if(tileInfo.militaryUnit!=null) tileInfo.militaryUnit!!.currentTile = tileInfo
            if(tileInfo.civilianUnit!=null) tileInfo.civilianUnit!!.currentTile = tileInfo
        }
    }

    fun getShortestPathBetweenTwoTiles(from:TileInfo, to:TileInfo): ArrayList<TileInfo> {
        val path = ArrayList<TileInfo>()
        var currentTile = from
        while(currentTile!=to){
            path += currentTile
            currentTile = currentTile.neighbors.minBy { it.arialDistanceTo(to) }!!
        }
        path+=to
        return path
    }


}