package com.unciv.json

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.Json.Serializer
import com.badlogic.gdx.utils.JsonValue
import com.unciv.Constants
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile

/**
 * Web/TeaVM can miss reflective field writes in some serializer paths.
 * Keep TileMap serialization explicit so tile payloads are always loaded.
 */
class TileMapSerializer : Serializer<TileMap> {
    override fun write(json: Json, tileMap: TileMap, knownType: Class<*>?) {
        json.writeObjectStart()
        json.writeValue("mapParameters", tileMap.mapParameters, MapParameters::class.java)

        json.writeArrayStart("tileList")
        for (tile in tileMap.tileList) json.writeValue(tile, Tile::class.java)
        json.writeArrayEnd()

        json.writeArrayStart("startingLocations")
        for (startingLocation in tileMap.startingLocations) {
            json.writeValue(startingLocation, TileMap.StartingLocation::class.java)
        }
        json.writeArrayEnd()

        if (tileMap.description.isNotEmpty()) {
            json.writeValue("description", tileMap.description)
        }
        json.writeObjectEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): TileMap {
        val tileMap = TileMap()
        tileMap.mapParameters = json.readValue(
            "mapParameters",
            MapParameters::class.java,
            MapParameters(),
            jsonData
        )
        tileMap.description = json.readValue("description", String::class.java, "", jsonData)

        val tiles = ArrayList<Tile>()
        val tileListNode = jsonData.get("tileList") ?: jsonData.get("tiles")
        var tileNode = tileListNode?.child
        while (tileNode != null) {
            val tile = Tile()
            runCatching { json.readFields(tile, tileNode) }
            val positionNode = tileNode.get("position")
            val x = positionNode?.getInt("x", 0) ?: 0
            val y = positionNode?.getInt("y", 0) ?: 0
            tile.position = HexCoord(x, y)
            tile.baseTerrain = tileNode.getString("baseTerrain", tile.baseTerrain.ifBlank { Constants.grassland })
            if (tileNode.get("terrainFeatures") != null && tile.terrainFeatures.isEmpty()) {
                val features = ArrayList<String>()
                var featureNode = tileNode.get("terrainFeatures")?.child
                while (featureNode != null) {
                    val feature = featureNode.asString()
                    if (feature.isNotBlank()) features += feature
                    featureNode = featureNode.next
                }
                tile.setTerrainFeaturesSerialized(features)
            }
            tile.hasBottomRiver = tileNode.getBoolean("hasBottomRiver", tile.hasBottomRiver)
            tile.hasBottomLeftRiver = tileNode.getBoolean("hasBottomLeftRiver", tile.hasBottomLeftRiver)
            tile.hasBottomRightRiver = tileNode.getBoolean("hasBottomRightRiver", tile.hasBottomRightRiver)
            tile.improvement = tileNode.getString("improvement", tile.improvement)
            tile.improvementIsPillaged = tileNode.getBoolean("improvementIsPillaged", tile.improvementIsPillaged)
            val resourceName = tileNode.getString("resource", "")
            tile.setTileResource(resourceName.ifBlank { null }, updateCache = false)
            tile.resourceAmount = tileNode.getInt("resourceAmount", tile.resourceAmount)
            tile.naturalWonder = tileNode.getString("naturalWonder", tile.naturalWonder)
            tiles.add(tile)
            tileNode = tileNode.next
        }
        tileMap.tileList = tiles

        tileMap.startingLocations.clear()
        val startingLocationsNode = jsonData.get("startingLocations")
        var startingLocationNode = startingLocationsNode?.child
        while (startingLocationNode != null) {
            tileMap.startingLocations.add(
                json.readValue(TileMap.StartingLocation::class.java, startingLocationNode)
            )
            startingLocationNode = startingLocationNode.next
        }
        return tileMap
    }
}
