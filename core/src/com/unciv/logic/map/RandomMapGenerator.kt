package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.HexMath
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.ui.utils.getRandom
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.ceil

class PerlinNoiseRandomMapGenerator:SeedRandomMapGenerator(){
    override fun generateMap(distance: Int): HashMap<String, TileInfo> {
        val map = HashMap<Vector2, TileInfo>()
        val mapRandomSeed = Random().nextDouble() // without this, all the "random" maps would look the same
        for (vector in HexMath().getVectorsInDistance(Vector2.Zero, distance))
            map[vector] = generateTile(vector,mapRandomSeed)

        divideIntoAreas(6, 0f, map)

        val mapToReturn = HashMap<String, TileInfo>()
        for(tile in map)
            mapToReturn[tile.key.toString()] = tile.value

        setWaterTiles(mapToReturn)
        for(tile in mapToReturn.values) randomizeTile(tile)

        return mapToReturn
    }

    private fun generateTile(vector: Vector2, mapRandomSeed: Double): TileInfo {
        val tile=TileInfo()
        tile.position=vector
        val ratio = 1/10.0
        val height = Perlin.noise(vector.x*ratio,vector.y*ratio,mapRandomSeed)
        + Perlin.noise(vector.x*ratio*2,vector.y*ratio*2,mapRandomSeed)/2
        + Perlin.noise(vector.x*ratio*4,vector.y*ratio*4,mapRandomSeed)/4
        when {
//            height>0.4 -> tile.baseTerrain = "Hill"
            height>0   -> tile.baseTerrain = "" // we'll leave this to the area division
            else       -> tile.baseTerrain = "Ocean"
        }
        return tile
    }
}

class AlexanderRandomMapGenerator:RandomMapGenerator(){
    fun generateMap(distance: Int, landExpansionChance:Float): HashMap<String, TileInfo> {
        val map = HashMap<Vector2, TileInfo?>()

        for (vector in HexMath().getVectorsInDistance(Vector2.Zero, distance))
            map[vector] = null

        val sparkList = ArrayList<Vector2>()
        val grassland = "Grassland"
        val ocean = "Ocean"
        for(i in 0..distance*distance/6){
            val location = map.filter { it.value==null }.map { it.key }.getRandom()
            map[location] = TileInfo().apply { baseTerrain= grassland}
            sparkList.add(location)
        }

        while(sparkList.any()){
            val currentSpark = sparkList.getRandom()
            val emptyTilesAroundSpark = HexMath().getAdjacentVectors(currentSpark)
                    .filter { map.containsKey(it) && map[it]==null }
            if(map[currentSpark]!!.baseTerrain==grassland){
                for(tile in emptyTilesAroundSpark){
                    if(Math.random()<landExpansionChance) map[tile]=TileInfo().apply { baseTerrain=grassland }
                    else  map[tile]=TileInfo().apply { baseTerrain=ocean }
                }
            }
            else{
                for(tile in emptyTilesAroundSpark)
                    map[tile]=TileInfo().apply { baseTerrain=ocean }
            }
            sparkList.remove(currentSpark)
            sparkList.addAll(emptyTilesAroundSpark)
        }

        val newmap = HashMap<String,TileInfo>()
        for(entry in map){
            entry.value!!.position = entry.key
            if(entry.value!!.baseTerrain==ocean
                    && HexMath().getAdjacentVectors(entry.key).all { !map.containsKey(it) || map[it]!!.baseTerrain==grassland })
                entry.value!!.baseTerrain=grassland

            newmap[entry.key.toString()] = entry.value!!
        }

        setWaterTiles(newmap)

        return newmap
        // now that we've divided them into land and not-land, stage 2 - seeding areas the way we did with the seed generator!

    }
}

class Area(var terrain: String) {
    val locations = ArrayList<Vector2>()
    fun addTile(tileInfo: TileInfo) {
        locations+=tileInfo.position
        tileInfo.baseTerrain = terrain
    }
}

/**
 * This generator works by creating a number of seeds of different terrain types in random places,
 * and choosing a random one each time to expand in a random direction, until the map is filled.
 * With water, this creates canal-like structures.
 */
open class SeedRandomMapGenerator : RandomMapGenerator() {

    fun generateMap(distance: Int, waterPercent:Float): HashMap<String, TileInfo> {

        val map = HashMap<Vector2, TileInfo>()

        for (vector in HexMath().getVectorsInDistance(Vector2.Zero, distance))
            map[vector] = TileInfo().apply { position=vector; baseTerrain="" }


        divideIntoAreas(6, waterPercent, map)

        val mapToReturn = HashMap<String,TileInfo>()
        for (entry in map){
            mapToReturn[entry.key.toString()] = entry.value
        }

        setWaterTiles(mapToReturn)
        return mapToReturn
    }

    fun divideIntoAreas(averageTilesPerArea: Int, waterPercent: Float, map: HashMap<Vector2, TileInfo>) {
        val areas = ArrayList<Area>()

        val terrains = GameBasics.Terrains.values.filter { it.type === TerrainType.Land && it.name != "Lakes" }

        while(map.values.any { it.baseTerrain=="" }) // the world could be split into lots off tiny islands, and every island deserves land types
        {
            val emptyTiles = map.values.filter { it.baseTerrain == "" }.toMutableList()
            val numberOfSeeds = ceil(emptyTiles.size / averageTilesPerArea.toFloat()).toInt()

            for (i in 0 until numberOfSeeds) {
                val terrain = if (Math.random() > waterPercent) terrains.getRandom().name
                else "Ocean"
                val area = Area(terrain)
                val tile = emptyTiles.getRandom()
                emptyTiles -= tile
                area.addTile(tile)
                areas += area
            }

            expandAreas(areas, map)

//             After we've assigned all the tiles, there will be some areas that contain only 1 or 2 tiles.
//             So, we kill those areas, and have the world expand on and cover them too
//            for (area in areas.toList()) {
//                if (area.locations.size < 3) {
//                    areas -= area
//                    for (location in area.locations) map[location]!!.baseTerrain = ""
//                }
//            }
            expandAreas(areas, map)
        }


        for (area in areas.filter { it.terrain == "Ocean" && it.locations.size <= 10 }) {
            // areas with 10 or less tiles are lakes.
            for (location in area.locations)
                map[location]!!.baseTerrain = "Lakes"
        }
    }

    fun expandAreas(areas: ArrayList<Area>, map: HashMap<Vector2, TileInfo>) {
        val expandableAreas = ArrayList<Area>(areas)
        while (expandableAreas.isNotEmpty()) {
            val areaToExpand = expandableAreas.getRandom()
            if(areaToExpand.locations.size>=20){
                expandableAreas -= areaToExpand
                continue
            }
            val availableExpansionVectors = areaToExpand.locations
                    .flatMap { HexMath().getAdjacentVectors(it) }.asSequence().distinct()
                    .filter { map.containsKey(it) && map[it]!!.baseTerrain=="" }.toList()
            if (availableExpansionVectors.isEmpty()) expandableAreas -= areaToExpand
            else {
                val expansionVector = availableExpansionVectors.getRandom()
                areaToExpand.addTile(map[expansionVector]!!)

                val neighbors = HexMath().getAdjacentVectors(expansionVector)
                val areasToJoin = areas.filter {
                    it.terrain == areaToExpand.terrain
                            && it != areaToExpand
                            && it.locations.any { location -> location in neighbors }
                }
                for (area in areasToJoin) {
                    areaToExpand.locations += area.locations
                    areas.remove(area)
                    expandableAreas.remove(area)
                }
            }
        }
    }

}


/**
 * This contains the basic randomizing tasks (add random terrain feature/resource)
 * and a basic map generator where every single tile is individually randomized.
 * Doesn't look very good TBH.
 */
open class RandomMapGenerator {

    private fun addRandomTile(position: Vector2): TileInfo {
        val tileInfo = TileInfo()
        tileInfo.position = position
        val terrains = GameBasics.Terrains.values

        val baseTerrain = terrains.filter { it.type === TerrainType.Land && it.name != "Lakes" }.getRandom()
        tileInfo.baseTerrain = baseTerrain.name

        addRandomTerrainFeature(tileInfo)
        addRandomResourceToTile(tileInfo)

        return tileInfo
    }

    fun addRandomTerrainFeature(tileInfo: TileInfo) {
        if (tileInfo.getBaseTerrain().canHaveOverlay && Math.random() > 0.7f) {
            val secondaryTerrains = GameBasics.Terrains.values
                    .filter { it.type === TerrainType.TerrainFeature && it.occursOn!!.contains(tileInfo.baseTerrain) }
            if (secondaryTerrains.any()) tileInfo.terrainFeature = secondaryTerrains.getRandom().name
        }
    }


    internal fun addRandomResourceToTile(tileInfo: TileInfo) {

        var tileResources = GameBasics.TileResources.values.toList()

        // Resources are placed according to TerrainFeature, if exists, otherwise according to BaseLayer.
        tileResources = tileResources.filter { it.terrainsCanBeFoundOn.contains(tileInfo.lastTerrain.name) }

        var resource: TileResource? = null
        when {
            Math.random() < 1 / 5f  -> resource = getRandomResource(tileResources, ResourceType.Bonus)
            Math.random() < 1 / 7f  -> resource = getRandomResource(tileResources, ResourceType.Strategic)
            Math.random() < 1 / 15f -> resource = getRandomResource(tileResources, ResourceType.Luxury)
        }
        if (resource != null) tileInfo.resource = resource.name
    }

    private fun getRandomResource(resources: List<TileResource>, resourceType: ResourceType): TileResource? {
        val filtered = resources.filter { it.resourceType == resourceType }
        if (filtered.isEmpty()) return null
        else return filtered.getRandom()
    }

    open fun generateMap(distance: Int): HashMap<String, TileInfo> {
        val map = HashMap<String, TileInfo>()
        for (vector in HexMath().getVectorsInDistance(Vector2.Zero, distance))
            map[vector.toString()] = addRandomTile(vector)
        return map
    }

    fun maybeAddAncientRuins(tile: TileInfo) {
        if(tile.getBaseTerrain().type!=TerrainType.Water && Random().nextDouble() < 1f/100)
            tile.improvement = "Ancient ruins"
    }


    private fun hasWaterTile(map: HashMap<String, TileInfo>, vector: Vector2): Boolean {
        return map.containsKey(vector.toString()) && map[vector.toString()]!!.getBaseTerrain().type == TerrainType.Land
    }

    fun setWaterTiles(map: HashMap<String, TileInfo>) {
        for (tile in map.values.filter { it.baseTerrain == "Ocean" }) {
            if (HexMath().getVectorsInDistance(tile.position,2).any { hasWaterTile(map,it) })
                tile.baseTerrain = "Coast"
        }
    }

    fun randomizeTile(tileInfo: TileInfo){
        RandomMapGenerator().addRandomTerrainFeature(tileInfo)
        RandomMapGenerator().addRandomResourceToTile(tileInfo)
        RandomMapGenerator().maybeAddAncientRuins(tileInfo)
    }
}