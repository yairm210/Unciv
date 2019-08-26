package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.models.metadata.GameParameters
import com.unciv.logic.GameInfo
import com.unciv.logic.HexMath
import com.unciv.logic.MapSaver
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


    constructor(newGameParameters: GameParameters) {
        val mapValues:Collection<TileInfo>

        if(newGameParameters.mapType == MapType.File)
            mapValues = MapSaver().loadMap(newGameParameters.mapFileName!!).values
        else if(newGameParameters.mapType==MapType.Perlin)
            mapValues = PerlinNoiseRandomMapGenerator().generateMap(newGameParameters.mapRadius).values
        else
            mapValues = CelluarAutomataRandomMapGenerator(newGameParameters.mapType).generateMap(newGameParameters.mapRadius).values

        tileList.addAll(mapValues)

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

    fun placeUnitNearTile(position: Vector2, unitName: String, civInfo: CivilizationInfo): MapUnit? {
        val unit = GameBasics.Units[unitName]!!.getMapUnit()
        val tilesInDistance = getTilesInDistance(position, 2)
        unit.assignOwner(civInfo,false)  // both the civ name and actual civ need to be in here in order to calculate the canMoveTo...Darn
        var unitToPlaceTile = tilesInDistance.firstOrNull { unit.movement.canMoveTo(it) && (unit.type.isWaterUnit() || it.isLand) }
        if (unitToPlaceTile==null)
            unitToPlaceTile = tilesInDistance.firstOrNull { unit.movement.canMoveTo(it) }

        if(unitToPlaceTile!=null) { //see if a land unit can be placed on land. if impossible, put it on water.
            // only once we know the unit can be placed do we add it to the civ's unit list
            unit.putInTile(unitToPlaceTile)
            unit.currentMovement = unit.getMaxMovement().toFloat()

            // Only once we add the unit to the civ we can activate addPromotion, because it will try to update civ viewable tiles
            for(promotion in unit.baseUnit.promotions)
                unit.promotions.addPromotion(promotion,true)

            // And update civ stats, since the new unit changes both unit upkeep and resource consumption
            civInfo.updateStatsForNextTurn()
            civInfo.updateDetailedCivResources()
        }
        else {
            civInfo.removeUnit(unit) // since we added it to the civ units in the previous assignOwner
            return null // we didn't actually create a unit...
        }

        return unit
    }


    fun getViewableTiles(position: Vector2, sightDistance: Int, ignoreCurrentTileHeight:Boolean=false): MutableList<TileInfo> {
        val viewableTiles = getTilesInDistance(position, 1).toMutableList()
        val currentTileHeight = if(ignoreCurrentTileHeight) 0 else get(position).getHeight()

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

    fun setTransients() {
        if(tiles.any())
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