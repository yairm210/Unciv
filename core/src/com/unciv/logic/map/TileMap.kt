package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.NewGameScreen

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


    constructor(distance: Int, mapType: NewGameScreen.NewGameParameters.MapType) {
        val map:HashMap<String,TileInfo>

        if(mapType==NewGameScreen.NewGameParameters.MapType.WithWater)
            map = PerlinNoiseRandomMapGenerator().generateMap(distance)

        else map = SeedRandomMapGenerator().generateMap(distance,0f)

        tileList.addAll(map.values)
//        tileList.addAll(AlexanderRandomMapGenerator().generateMap(distance,0.8f).values)

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
        return HexMath().getVectorsInDistance(origin, distance).asSequence()
                .filter {contains(it)}.map { get(it) }.toList()
    }

    fun getTilesAtDistance(origin: Vector2, distance: Int): List<TileInfo> {
        return HexMath().getVectorsAtDistance(origin, distance).asSequence()
                .filter {contains(it)}.map { get(it) }.toList()

    }

    fun placeUnitNearTile(position: Vector2, unitName: String, civInfo: CivilizationInfo): MapUnit {
        val unit = GameBasics.Units[unitName]!!.getMapUnit()
        val tilesInDistance = getTilesInDistance(position, 2)
        unit.assignOwner(civInfo)  // both the civ name and actual civ need to be in here in order to calculate the canMoveTo...Darn
        var unitToPlaceTile = tilesInDistance.firstOrNull { unit.canMoveTo(it) && (unit.type.isWaterUnit() || it.isLand()) }
        if (unitToPlaceTile==null)
            unitToPlaceTile = tilesInDistance.firstOrNull { unit.canMoveTo(it) }

        if(unitToPlaceTile!=null) { //see if a land unit can be placed on land. if impossible, put it on water.
            // only once we know the unit can be placed do we add it to the civ's unit list
            unit.putInTile(unitToPlaceTile)
            unit.currentMovement = unit.getMaxMovement().toFloat()
        }
        else civInfo.removeUnit(unit) // since we added it to the civ units in the previous assignOwner

        return unit
    }

    fun getViewableTiles(position: Vector2, sightDistance: Int): MutableList<TileInfo> {
        val viewableTiles = getTilesInDistance(position, 1).toMutableList()
        for (i in 1..sightDistance) { // in each layer,
            // This is so we don't use tiles in the same distance to "see over",
            // that is to say, the "viewableTiles.contains(it) check will return false for neighbors from the same distance
            val tilesToAddInDistanceI = ArrayList<TileInfo>()

            for (tile in getTilesAtDistance(position, i)) { // for each tile in that layer,
                val tileHeight = tile.getHeight()
                val containsViewableNeighborThatCanSeeOver = tile.neighbors.any {
                    val neighborHeight = it.getHeight()
                    viewableTiles.contains(it) && (neighborHeight == 0 || neighborHeight < tileHeight)
                }
                if (containsViewableNeighborThatCanSeeOver) tilesToAddInDistanceI.add(tile)
            }
            viewableTiles.addAll(tilesToAddInDistanceI)
        }

        return viewableTiles
    }

    fun setTransients() {
        if(tiles.any()) //
            tileList.addAll(tiles.values)

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
            tileInfo.setTransients()
        }
    }

}