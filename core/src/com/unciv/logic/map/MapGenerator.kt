package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.HexMath
import com.unciv.models.Counter
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.metadata.GameParameters
import java.util.*
import kotlin.math.*

// This is no longer an Enum because there were map types that were disabled,
// and when parsing an existing map to an Enum you have to have all the options.
// So either we had to keep the old Enums forever, or change to strings.

class MapType {
    companion object{
        val default="Default" // Creates a cellular automata map
        val perlin="Perlin"
        val continents = "Continents"
        val pangaea = "Pangaea"
        val file = "File"
    }
}

class MapGenerator() {

    fun generateMap(gameParameters: GameParameters): TileMap {
        val mapRadius = gameParameters.mapRadius
        val mapType = gameParameters.mapType

        val map = TileMap(mapRadius)

        // Step one - separate land and water, in form of Grasslands and Oceans
        if(mapType == MapType.perlin)
            MapLandmassGenerator().generateLandPerlin(map)

        else MapLandmassGenerator().generateLandCellularAutomata(map,mapRadius, mapType)

        divideIntoBiomes(map,6, 0.05f, mapRadius)

        for(tile in map.values) tile.setTransients()

        setWaterTiles(map)

        for(tile in map.values) randomizeTile(tile,gameParameters)

        randomizeResources(map, mapRadius)

        return map
    }



    fun setWaterTiles(map: TileMap) {

        //define lakes
        var waterTiles = map.values.filter { it.isWater }
        val tilesInArea = ArrayList<TileInfo>()
        val tilesToCheck = ArrayList<TileInfo>()
        while (waterTiles.isNotEmpty()) {
            val initialWaterTile = waterTiles.random()
            tilesInArea += initialWaterTile
            tilesToCheck += initialWaterTile
            waterTiles -= initialWaterTile

            while (tilesToCheck.isNotEmpty()) {
                val tileWeAreChecking = tilesToCheck.random()
                for (vector in tileWeAreChecking.neighbors
                        .filter { !tilesInArea.contains(it) and waterTiles.contains(it) }) {
                    tilesInArea += vector
                    tilesToCheck += vector
                    waterTiles -= vector
                }
                tilesToCheck -= tileWeAreChecking
            }

            if (tilesInArea.size <= 10) {
                for (tile in tilesInArea) {
                    tile.baseTerrain = Constants.lakes
                    tile.setTransients()
                }
            }
            tilesInArea.clear()
        }

        //Coasts
        for (tile in map.values.filter { it.baseTerrain == Constants.ocean }) {
            if (tile.getTilesInDistance(2).any { it.isLand }) {
                tile.baseTerrain = Constants.coast
                tile.setTransients()
            }
        }
    }

    fun randomizeTile(tileInfo: TileInfo,gameParameters : GameParameters){
        if(tileInfo.getBaseTerrain().type==TerrainType.Land && Math.random()<0.05f){
            tileInfo.baseTerrain = Constants.mountain
            tileInfo.setTransients()
        }
        addRandomTerrainFeature(tileInfo)
        maybeAddAncientRuins(tileInfo,gameParameters)
    }

    fun getLatitude(vector: Vector2): Float {
        return (sin(3.1416/3) * vector.y).toFloat()
    }

    fun divideIntoBiomes(map: TileMap, averageTilesPerArea: Int, waterPercent: Float, distance: Int) {
        val areas = ArrayList<Area>()

        val terrains = GameBasics.Terrains.values
                .filter { it.type === TerrainType.Land && it.name != Constants.lakes && it.name != Constants.mountain}

        for(tile in map.values.filter { it.baseTerrain==Constants.grassland }) tile.baseTerrain="" // So we know it's not chosen

        while(map.values.any { it.baseTerrain=="" }) // the world could be split into lots off tiny islands, and every island deserves land types
        {
            val emptyTiles = map.values.filter { it.baseTerrain == "" }.toMutableList()
            val numberOfSeeds = ceil(emptyTiles.size / averageTilesPerArea.toFloat()).toInt()
            val maxLatitude = abs(getLatitude(Vector2(distance.toFloat(), distance.toFloat())))

            for (i in 0 until numberOfSeeds) {
                var terrain = if (Math.random() > waterPercent) terrains.random().name
                else Constants.ocean
                val tile = emptyTiles.random()

                //change grassland to desert or tundra based on y
                if (abs(getLatitude(tile.position)) < maxLatitude * 0.1) {
                    if (terrain == Constants.grassland || terrain == Constants.tundra)
                        terrain = Constants.desert
                } else if (abs(getLatitude(tile.position)) > maxLatitude * 0.7) {
                    if (terrain == Constants.grassland || terrain == Constants.plains || terrain == Constants.desert || terrain == Constants.ocean) {
                        terrain = Constants.tundra
                    }
                } else {
                    if (terrain == Constants.tundra) terrain = Constants.plains
                    else if (terrain == Constants.desert) terrain = Constants.grassland
                }

                val area = Area(terrain)
                emptyTiles -= tile
                area.addTile(tile)
                areas += area
            }

            expandAreas(areas)
            expandAreas(areas)
        }
    }


    fun expandAreas(areas: ArrayList<Area>) {
        val expandableAreas = ArrayList<Area>(areas)

        while (expandableAreas.isNotEmpty()) {
            val areaToExpand = expandableAreas.random()
            if(areaToExpand.tiles.size>=20){
                expandableAreas -= areaToExpand
                continue
            }

            val availableExpansionTiles = areaToExpand.tiles
                    .flatMap { it.neighbors }.distinct()
                    .filter { it.baseTerrain=="" }

            if (availableExpansionTiles.isEmpty()) expandableAreas -= areaToExpand
            else {
                val expansionTile = availableExpansionTiles.random()
                areaToExpand.addTile(expansionTile)

                val areasToJoin = areas.filter {
                    it.terrain == areaToExpand.terrain
                            && it != areaToExpand
                            && it.tiles.any { tile -> tile in expansionTile.neighbors }
                }
                for (area in areasToJoin) {
                    areaToExpand.tiles += area.tiles
                    areas.remove(area)
                    expandableAreas.remove(area)
                }
            }
        }
    }


    fun addRandomTerrainFeature(tileInfo: TileInfo) {
        if (tileInfo.getBaseTerrain().canHaveOverlay && Math.random() > 0.7f) {
            val secondaryTerrains = GameBasics.Terrains.values
                    .filter { it.type === TerrainType.TerrainFeature && it.occursOn != null && it.occursOn!!.contains(tileInfo.baseTerrain) }
            if (secondaryTerrains.any()) tileInfo.terrainFeature = secondaryTerrains.random().name
        }
    }


    fun maybeAddAncientRuins(tile: TileInfo, gameParameters: GameParameters) {
        val baseTerrain = tile.getBaseTerrain()
        if(!gameParameters.noRuins &&  baseTerrain.type!=TerrainType.Water && !baseTerrain.impassable && Random().nextDouble() < 1f/100)
            tile.improvement = Constants.ancientRuins
    }


    fun randomizeResources(mapToReturn: TileMap, distance: Int) {
        for(tile in mapToReturn.values)
            if(tile.resource!=null)
                tile.resource=null

        randomizeStrategicResources(mapToReturn, distance)
        randomizeResource(mapToReturn, distance, ResourceType.Luxury)
        randomizeResource(mapToReturn, distance, ResourceType.Bonus)
    }

    // Here, we need each specific resource to be spread over the map - it matters less if specific resources are near each other
    private fun randomizeStrategicResources(mapToReturn: TileMap, distance: Int) {
        val resourcesOfType = GameBasics.TileResources.values.filter { it.resourceType == ResourceType.Strategic }
        for (resource in resourcesOfType) {
            val suitableTiles = mapToReturn.values
                    .filter { it.resource == null && resource.terrainsCanBeFoundOn.contains(it.getLastTerrain().name) }

            val averageTilesPerResource = 15 * resourcesOfType.count()
            val numberOfResources = mapToReturn.values.count { it.isLand && !it.getBaseTerrain().impassable } / averageTilesPerResource

            val locations = chooseSpreadOutLocations(numberOfResources, suitableTiles, distance)

            for (location in locations) location.resource = resource.name
        }
    }

    // Here, we need there to be some luxury/bonus resource - it matters less what
    private fun randomizeResource(mapToReturn: TileMap, distance: Int, resourceType: ResourceType) {
        val resourcesOfType = GameBasics.TileResources.values.filter { it.resourceType == resourceType }

        val suitableTiles = mapToReturn.values
                .filter { it.resource == null && resourcesOfType.any { r->r.terrainsCanBeFoundOn.contains(it.getLastTerrain().name) } }
        val numberOfResources = mapToReturn.values.count { it.isLand && !it.getBaseTerrain().impassable } / 15
        val locations = chooseSpreadOutLocations(numberOfResources, suitableTiles, distance)

        val resourceToNumber = Counter<String>()

        for(tile in locations){
            val possibleResources = resourcesOfType
                    .filter { it.terrainsCanBeFoundOn.contains(tile.getLastTerrain().name) }
                    .map { it.name }
            if(possibleResources.isEmpty()) continue
            val resourceWithLeastAssignments = possibleResources.minBy { resourceToNumber[it]!! }!!
            resourceToNumber.add(resourceWithLeastAssignments, 1)
            tile.resource = resourceWithLeastAssignments
        }
    }

    fun chooseSpreadOutLocations(numberOfResources: Int, suitableTiles: List<TileInfo>, initialDistance:Int): ArrayList<TileInfo> {

        for(distanceBetweenResources in initialDistance downTo 1){
            var availableTiles = suitableTiles.toList()
            val chosenTiles = ArrayList<TileInfo>()

            for(i in 1..numberOfResources){
                if(availableTiles.isEmpty()) break
                val chosenTile = availableTiles.random()
                availableTiles = availableTiles.filter { it.arialDistanceTo(chosenTile)>distanceBetweenResources }
                chosenTiles.add(chosenTile)
            }
            // Either we got them all, or we're not going to get anything better
            if(chosenTiles.size == numberOfResources || distanceBetweenResources==1) return chosenTiles
        }
        throw Exception("Couldn't choose suitable tiles for $numberOfResources resources!")
    }
}

class MapLandmassGenerator(){

    fun generateLandCellularAutomata(tileMap: TileMap, mapRadius: Int, mapType: String) {

        val numSmooth = 4

        //init
        for (tile in tileMap.values) {
            val terrainType = getInitialTerrainCellularAutomata(tile, mapRadius, mapType)
            if(terrainType==TerrainType.Land) tile.baseTerrain = Constants.grassland
            else tile.baseTerrain = Constants.ocean
            tile.setTransients()
        }

        //smooth
        val grassland = Constants.grassland
        val ocean = Constants.ocean

        for (loop in 0..numSmooth) {
            for (tileInfo in tileMap.values) {
                if (HexMath().getDistance(Vector2.Zero, tileInfo.position) < mapRadius) {
                    val numberOfLandNeighbors = tileInfo.neighbors.count { it.baseTerrain==grassland }
                    if (tileInfo.baseTerrain == grassland) { // land tile
                        if (numberOfLandNeighbors < 3)
                            tileInfo.baseTerrain = ocean
                    } else { // water tile
                        if (numberOfLandNeighbors > 3)
                            tileInfo.baseTerrain = grassland
                    }
                } else {
                    tileInfo.baseTerrain = ocean
                }
            }

            if (mapType == MapType.continents) { //keep a ocean column in the middle
                for (y in -mapRadius..mapRadius) {
                    tileMap.get(Vector2((y / 2).toFloat(), y.toFloat())).baseTerrain=ocean
                    tileMap.get(Vector2((y / 2 +1).toFloat(), y.toFloat())).baseTerrain=ocean
                }
            }
        }
    }


    private fun getInitialTerrainCellularAutomata(tileInfo: TileInfo, mapRadius: Int, mapType: String):TerrainType {

        val landProbability = 0.55f

        if (mapType == MapType.pangaea) {
            val distanceFactor = (HexMath().getDistance(Vector2.Zero, tileInfo.position) * 1.8 / mapRadius).toFloat()
            if (Random().nextDouble() < landProbability.pow(distanceFactor)) return TerrainType.Land
            else return TerrainType.Water
        }

        if (mapType == MapType.continents) {
            val distanceWeight = min(getDistanceWeightForContinents(Vector2(mapRadius.toFloat() / 2, 0f), tileInfo.position),
                    getDistanceWeightForContinents(Vector2(-mapRadius.toFloat() / 2, 0f), tileInfo.position))
            val distanceFactor = (distanceWeight * 1.8 / mapRadius).toFloat()
            if (Random().nextDouble() < landProbability.pow(distanceFactor)) return TerrainType.Land
            else return TerrainType.Water
        }

        // default
        if (HexMath().getDistance(Vector2.Zero, tileInfo.position) > 0.9f * mapRadius) {
            if (Random().nextDouble() < 0.1) return TerrainType.Land else return TerrainType.Water
        }
        if (HexMath().getDistance(Vector2.Zero, tileInfo.position) > 0.85f * mapRadius) {
            if (Random().nextDouble() < 0.2) return TerrainType.Land else return TerrainType.Water
        }
        if (Random().nextDouble() < landProbability) return TerrainType.Land else return TerrainType.Water
    }


    private fun getDistanceWeightForContinents(origin: Vector2, destination: Vector2): Float {
        val relative_x = 2*(origin.x-destination.x)
        val relative_y = origin.y-destination.y
        if (relative_x * relative_y >= 0)
            return max(abs(relative_x),abs(relative_y))
        else
            return (abs(relative_x) + abs(relative_y))
    }



    /**
     * This generator simply generates Perlin noise,
     * "spreads" it out according to the ratio in generateTile,
     * and assigns it as the height of the various tiles.
     * Tiles below a certain height threshold (determined in generateTile, currently 50%)
     *   are considered water tiles, the rest are land tiles
     */
    fun generateLandPerlin(tileMap: TileMap){
        val mapRandomSeed = Random().nextDouble() // without this, all the "random" maps would look the same
        for(tile in tileMap.values){
            val ratio = 1/10.0
            val vector = tile.position
            val height = Perlin.noise(vector.x*ratio,vector.y*ratio,mapRandomSeed)
            + Perlin.noise(vector.x*ratio*2,vector.y*ratio*2,mapRandomSeed)/2
            + Perlin.noise(vector.x*ratio*4,vector.y*ratio*4,mapRandomSeed)/4
            when { // If we want to change water levels, we could raise or lower the >0
                height>0   -> tile.baseTerrain = Constants.grassland
                else       -> tile.baseTerrain = Constants.ocean
            }
        }
    }

}



class Area(var terrain: String) {
    val tiles = ArrayList<TileInfo>()
    fun addTile(tileInfo: TileInfo) {
        tiles+=tileInfo
        tileInfo.baseTerrain = terrain
    }
}

