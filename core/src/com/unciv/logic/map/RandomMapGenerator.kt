package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ResourceType
import com.unciv.models.gamebasics.TerrainType
import com.unciv.models.gamebasics.TileResource
import com.unciv.models.linq.Linq
import com.unciv.models.linq.LinqHashMap
import com.unciv.ui.utils.HexMath

class RandomMapGenerator {

    private fun addRandomTile(position: Vector2): TileInfo {
        val tileInfo = TileInfo()
        tileInfo.position = position
        val terrains = GameBasics.Terrains.linqValues()

        val baseTerrain = terrains.where { it.type === TerrainType.BaseTerrain && it.name != "Lakes" }.random
        tileInfo.baseTerrain = baseTerrain!!.name

        if (baseTerrain.canHaveOverlay) {
            if (Math.random() > 0.7f) {
                val SecondaryTerrain = terrains.where { it.type === TerrainType.TerrainFeature && it.occursOn!!.contains(baseTerrain.name) }.random
                if (SecondaryTerrain != null) tileInfo.terrainFeature = SecondaryTerrain.name
            }
        }

        addRandomResourceToTile(tileInfo)

        return tileInfo
    }

    internal fun addRandomResourceToTile(tileInfo: TileInfo) {

        var TileResources = GameBasics.TileResources.linqValues()

        // Resources are placed according to TerrainFeature, if exists, otherwise according to BaseLayer.
        TileResources = TileResources.where { it.terrainsCanBeFoundOn.contains(tileInfo.lastTerrain!!.name) }

        var resource: TileResource? = null
        if (Math.random() < 1 / 5f) {
            resource = GetRandomResource(TileResources, ResourceType.Bonus)
        } else if (Math.random() < 1 / 7f) {
            resource = GetRandomResource(TileResources, ResourceType.Strategic)
        } else if (Math.random() < 1 / 15f) {
            resource = GetRandomResource(TileResources, ResourceType.Luxury)
        }
        if (resource != null) tileInfo.resource = resource.name
    }

    internal fun GetRandomResource(resources: Linq<TileResource>, resourceType: ResourceType): TileResource? {
        return resources.where { it.resourceType == resourceType }.random
    }


    fun generateMap(distance: Int): LinqHashMap<String, TileInfo> {
        val map = LinqHashMap<String, TileInfo>()
        for (vector in HexMath.GetVectorsInDistance(Vector2.Zero, distance))
            map[vector.toString()] = addRandomTile(vector)
        return map
    }
}
