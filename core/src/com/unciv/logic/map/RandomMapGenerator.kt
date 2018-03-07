package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.getRandom
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ResourceType
import com.unciv.models.gamebasics.TerrainType
import com.unciv.models.gamebasics.TileResource
import com.unciv.ui.utils.HexMath

class RandomMapGenerator {

    private fun addRandomTile(position: Vector2): TileInfo {
        val tileInfo = TileInfo()
        tileInfo.position = position
        val terrains = GameBasics.Terrains.values

        val baseTerrain = terrains.filter { it.type === TerrainType.BaseTerrain && it.name != "Lakes" }.getRandom()
        tileInfo.baseTerrain = baseTerrain.name

        if (baseTerrain.canHaveOverlay) {
            if (Math.random() > 0.7f) {
                val secondaryTerrains = terrains.filter { it.type === TerrainType.TerrainFeature && it.occursOn!!.contains(baseTerrain.name) }
                if (secondaryTerrains.any()) tileInfo.terrainFeature = secondaryTerrains.getRandom().name
            }
        }

        addRandomResourceToTile(tileInfo)

        return tileInfo
    }

    private fun addRandomResourceToTile(tileInfo: TileInfo) {

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


    fun generateMap(distance: Int): HashMap<String, TileInfo> {
        val map = HashMap<String, TileInfo>()
        for (vector in HexMath.GetVectorsInDistance(Vector2.Zero, distance))
            map[vector.toString()] = addRandomTile(vector)
        return map
    }
}
