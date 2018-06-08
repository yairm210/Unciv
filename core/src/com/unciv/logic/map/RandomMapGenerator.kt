package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.HexMath
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.ui.utils.getRandom

class SeedRandomMapGenerator : RandomMapGenerator() {

    override fun generateMap(distance: Int): HashMap<String, TileInfo> {

        val map = HashMap<Vector2, TileInfo?>()

        for (vector in HexMath().GetVectorsInDistance(Vector2.Zero, distance))
            map[vector] = null

        class Area(val terrain: String) {
            val locations = ArrayList<Vector2>()
            fun addTile(position: Vector2) : TileInfo{
                locations+=position

                val tile = TileInfo()
                tile.position = position
                tile.baseTerrain = terrain
                addRandomOverlay(tile)
                addRandomResourceToTile(tile)
                return tile
            }
        }

        val areas = ArrayList<Area>()

        val terrains = GameBasics.Terrains.values.filter { it.type === TerrainType.BaseTerrain && it.name != "Lakes" }


        for (i in 0..(distance*distance/2)){
            val area = Area(terrains.getRandom().name)
            val location = map.filter { it.value==null }.map { it.key }.getRandom()
            map[location] = area.addTile(location)
            areas += area
        }



        fun expandAreas(){
            val expandableAreas = ArrayList<Area>(areas)
            while (expandableAreas.isNotEmpty()){
                val areaToExpand = expandableAreas.getRandom()
                val availableExpansionVectors = areaToExpand.locations.flatMap { HexMath().GetAdjacentVectors(it) }.distinct()
                        .filter { map.containsKey(it) && map[it] == null }
                if(availableExpansionVectors.isEmpty()) expandableAreas -= areaToExpand
                else {
                    val expansionVector = availableExpansionVectors.getRandom()
                    map[expansionVector] = areaToExpand.addTile(expansionVector)
                }
            }
        }
        expandAreas()

        // After we've assigned all the tiles, there will be some areas that contain only 1 or 2 tiles.
        // So, we kill those areas, and have the world expand on and cover them too.
        for(area in areas.toList()){
            if(area.locations.size<3){
                areas -= area
                for(location in area.locations) map[location] = null
            }
        }

        expandAreas()

        val mapToReturn = HashMap<String,TileInfo>()
        for (entry in map){
            mapToReturn[entry.key.toString()] = entry.value!!
        }

        return mapToReturn
    }
}

open class RandomMapGenerator {

    private fun addRandomTile(position: Vector2): TileInfo {
        val tileInfo = TileInfo()
        tileInfo.position = position
        val terrains = GameBasics.Terrains.values

        val baseTerrain = terrains.filter { it.type === TerrainType.BaseTerrain && it.name != "Lakes" }.getRandom()
        tileInfo.baseTerrain = baseTerrain.name

        addRandomOverlay(tileInfo)
        addRandomResourceToTile(tileInfo)

        return tileInfo
    }

    protected fun addRandomOverlay(tileInfo: TileInfo) {
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
}
