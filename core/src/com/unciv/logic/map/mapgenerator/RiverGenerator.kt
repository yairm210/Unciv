package com.unciv.logic.map.mapgenerator

import com.unciv.Constants
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap

class RiverGenerator(val randomness: MapGenerationRandomness){

    fun spawnRivers(map: TileMap){
        val numberOfRivers = map.values.count { it.isLand } / 100

        var optionalTiles = map.values
                .filter { it.baseTerrain== Constants.mountain && it.aerialDistanceTo(getClosestWaterTile(it)) > 4 }.toMutableList()
        if(optionalTiles.size < numberOfRivers)
            optionalTiles.addAll(map.values.filter { it.baseTerrain== Constants.hill && it.aerialDistanceTo(getClosestWaterTile(it)) > 4 })
        if(optionalTiles.size < numberOfRivers)
            optionalTiles = map.values.filter { it.isLand && it.aerialDistanceTo(getClosestWaterTile(it)) > 4 }.toMutableList()


        val riverStarts = randomness.chooseSpreadOutLocations(numberOfRivers, optionalTiles, 10)
        for(tile in riverStarts) spawnRiver(tile, map)

        for(tile in map.values){
            if(tile.isAdjacentToRiver()){
                if(tile.baseTerrain== Constants.desert) tile.terrainFeature= Constants.floodPlains
                else if(tile.baseTerrain== Constants.snow) tile.baseTerrain = Constants.tundra
                else if(tile.baseTerrain== Constants.tundra) tile.baseTerrain = Constants.plains
                tile.setTerrainTransients()
            }
        }
    }

    private fun getClosestWaterTile(tile: TileInfo): TileInfo {
        var distance = 1
        while(true){
            val waterTiles = tile.getTilesAtDistance(distance).filter { it.isWater }
            if(waterTiles.none()) {
                distance++
                continue
            }
            return waterTiles.toList().random(randomness.RNG)
        }
    }

    private fun spawnRiver(initialPosition: TileInfo, map: TileMap) {
        // Recommendation: Draw a bunch of hexagons on paper before trying to understand this, it's super helpful!
        val endPosition = getClosestWaterTile(initialPosition)

        var riverCoordinate = RiverCoordinate(initialPosition.position,
                RiverCoordinate.BottomRightOrLeft.values().random(randomness.RNG))


        while(getAdjacentTiles(riverCoordinate, map).none { it.isWater }){
            val possibleCoordinates = riverCoordinate.getAdjacentPositions()
                    .filter { map.contains(it.position) }
            if(possibleCoordinates.none()) return // end of the line
            val newCoordinate = possibleCoordinates
                    .groupBy { getAdjacentTiles(it,map).map { it.aerialDistanceTo(endPosition) }.min()!! }
                    .minBy { it.key }!!
                    .component2().random(randomness.RNG)

            // set new rivers in place
            val riverCoordinateTile = map[riverCoordinate.position]
            if(newCoordinate.position == riverCoordinate.position) // same tile, switched right-to-left
                riverCoordinateTile.hasBottomRiver=true
            else if(riverCoordinate.bottomRightOrLeft== RiverCoordinate.BottomRightOrLeft.BottomRight){
                if(getAdjacentTiles(newCoordinate,map).contains(riverCoordinateTile)) // moved from our 5 O'Clock to our 3 O'Clock
                    riverCoordinateTile.hasBottomRightRiver = true
                else // moved from our 5 O'Clock down in the 5 O'Clock direction - this is the 8 O'Clock river of the tile to our 4 O'Clock!
                    map[newCoordinate.position].hasBottomLeftRiver = true
            }
            else { // riverCoordinate.bottomRightOrLeft==RiverCoordinate.BottomRightOrLeft.Left
                if(getAdjacentTiles(newCoordinate,map).contains(riverCoordinateTile)) // moved from our 7 O'Clock to our 9 O'Clock
                    riverCoordinateTile.hasBottomLeftRiver = true
                else // moved from our 7 O'Clock down in the 7 O'Clock direction
                    map[newCoordinate.position].hasBottomRightRiver = true
            }
            riverCoordinate = newCoordinate
        }

    }

    fun getAdjacentTiles(riverCoordinate: RiverCoordinate, map: TileMap): Sequence<TileInfo> {
        val potentialPositions = sequenceOf(
                riverCoordinate.position,
                riverCoordinate.position.cpy().add(-1f, -1f), // tile directly below us,
                if (riverCoordinate.bottomRightOrLeft == RiverCoordinate.BottomRightOrLeft.BottomLeft)
                    riverCoordinate.position.cpy().add(0f, -1f) // tile to our bottom-left
                else riverCoordinate.position.cpy().add(-1f, 0f) // tile to our bottom-right
        )
        return potentialPositions.map { if (map.contains(it)) map[it] else null }.filterNotNull()
    }

    fun numberOfConnectedRivers(riverCoordinate: RiverCoordinate, map: TileMap): Int {
        var sum = 0
        if (map.contains(riverCoordinate.position) && map[riverCoordinate.position].hasBottomRiver) sum += 1
        if (riverCoordinate.bottomRightOrLeft == RiverCoordinate.BottomRightOrLeft.BottomLeft) {
            if (map.contains(riverCoordinate.position) && map[riverCoordinate.position].hasBottomLeftRiver) sum += 1
            val bottomLeftTilePosition = riverCoordinate.position.cpy().add(0f, -1f)
            if (map.contains(bottomLeftTilePosition) && map[bottomLeftTilePosition].hasBottomRightRiver) sum += 1
        } else {
            if (map.contains(riverCoordinate.position) && map[riverCoordinate.position].hasBottomRightRiver) sum += 1
            val bottomLeftTilePosition = riverCoordinate.position.cpy().add(-1f, 0f)
            if (map.contains(bottomLeftTilePosition) && map[bottomLeftTilePosition].hasBottomLeftRiver) sum += 1
        }
        return sum
    }

}