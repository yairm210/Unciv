package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.HexMath
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.ui.utils.getRandom
import java.util.*

class AlexanderRandomMapGenerator:RandomMapGenerator(){
    fun generateMap(distance: Int, landExpansionChange:Float){
        val map = HashMap<Vector2, TileInfo?>()

        for (vector in HexMath().GetVectorsInDistance(Vector2.Zero, distance))
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
            val emptyTilesAroundSpark = HexMath().GetAdjacentVectors(currentSpark)
                    .filter { map.containsKey(it) && map[it]==null }
            if(map[currentSpark]!!.baseTerrain==grassland){
                for(tile in emptyTilesAroundSpark){
                    if(Math.random()<landExpansionChange) map[tile]=TileInfo().apply { baseTerrain=grassland }
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

        // now that we've divided them into land and not-land, stage 2 - seeding areas  the way we did with the seed generator!

    }
}

class Area(var terrain: String) {
    val locations = ArrayList<Vector2>()
    fun addTile(position: Vector2) : TileInfo{
        locations+=position

        val tile = TileInfo()
        tile.position = position
        tile.baseTerrain = terrain
        RandomMapGenerator().addRandomTerrainFeature(tile)
        RandomMapGenerator().addRandomResourceToTile(tile)
        RandomMapGenerator().maybeAddAncientRuins(tile)
        return tile
    }
}

/**
 * This generator works by creating a number of seeds of different terrain types in random places,
 * and choosing a random one each time to expand in a random direction, until the map is filled.
 * With water, this creates canal-like structures.
 */
class SeedRandomMapGenerator : RandomMapGenerator() {

    fun generateMap(distance: Int, waterPercent:Float): HashMap<String, TileInfo> {

        val map = HashMap<Vector2, TileInfo?>()

        for (vector in HexMath().GetVectorsInDistance(Vector2.Zero, distance))
            map[vector] = null


        divideIntoAreas(6, waterPercent, map)

        val mapToReturn = HashMap<String,TileInfo>()
        for (entry in map){
            mapToReturn[entry.key.toString()] = entry.value!!
        }

        return mapToReturn
    }

    private fun divideIntoAreas(averageTilesPerArea: Int, waterPercent: Float, map: HashMap<Vector2, TileInfo?>) {
        val areas = ArrayList<Area>()

        val terrains = GameBasics.Terrains.values.filter { it.type === TerrainType.Land && it.name != "Lakes" }

        for (i in 0..(map.count { it.value==null } / averageTilesPerArea)) {
            val terrain = if (Math.random() > waterPercent) terrains.getRandom().name
            else "Ocean"
            val area = Area(terrain)
            val location = map.filter { it.value == null }.map { it.key }.getRandom()
            map[location] = area.addTile(location)
            areas += area
        }



        fun expandAreas() {
            val expandableAreas = ArrayList<Area>(areas)
            while (expandableAreas.isNotEmpty()) {
                val areaToExpand = expandableAreas.getRandom()
                val availableExpansionVectors = areaToExpand.locations
                        .flatMap { HexMath().GetAdjacentVectors(it) }.distinct()
                        .filter { map.containsKey(it) && map[it] == null }
                if (availableExpansionVectors.isEmpty()) expandableAreas -= areaToExpand
                else {
                    val expansionVector = availableExpansionVectors.getRandom()
                    map[expansionVector] = areaToExpand.addTile(expansionVector)

                    val neighbors = HexMath().GetAdjacentVectors(expansionVector)
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
        expandAreas()

        // After we've assigned all the tiles, there will be some areas that contain only 1 or 2 tiles.
        // So, we kill those areas, and have the world expand on and cover them too
        for (area in areas.toList()) {
            if (area.locations.size < 3) {
                areas -= area
                for (location in area.locations) map[location] = null
            }
        }

        expandAreas()

        // Once our map has all its tiles, we'll want to change the water tiles to Coasts, Oceans and Lakes

        for (area in areas.filter { it.terrain == "Ocean" && it.locations.size <= 10 }) {
            // areas with 10 or less tiles are lakes.
            for (location in area.locations)
                map[location]!!.baseTerrain = "Lakes"
        }
        for (tile in map.values.filter { it != null && it.baseTerrain == "Ocean" }) {
            if (HexMath().GetAdjacentVectors(tile!!.position)
                            .any { map.containsKey(it) && map[it]!!.getBaseTerrain().type == TerrainType.Land })
                tile.baseTerrain = "Coast"
        }
    }
}


/**
 * This contains the basic randomizing tasks (add random terrain feature/resource)
 * and a basic map generator where every single tile is individually randomized.
 * DDoeesn't look very good TBH.
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
            Math.random() < 1 / 5f -> resource = getRandomResource(tileResources, ResourceType.Bonus)
            Math.random() < 1 / 7f -> resource = getRandomResource(tileResources, ResourceType.Strategic)
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
        for (vector in HexMath().GetVectorsInDistance(Vector2.Zero, distance))
            map[vector.toString()] = addRandomTile(vector)
        return map
    }

    fun maybeAddAncientRuins(tile: TileInfo) {
        if(Random().nextDouble() < 1f/100) tile.improvement = "Ancient ruins"
    }
}
