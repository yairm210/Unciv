package com.unciv.models.tilesets

import com.badlogic.gdx.graphics.Color

class TileSetConfig {
    var useColorAsBaseTerrain = true
    var unexploredTileColor: Color = Color.DARK_GRAY
    var fogOfWarColor: Color = Color.BLACK
    /** Name of the tileset to use when this one is missing images. Null to disable. */
    var fallbackTileSet: String? = "FantasyHex"
    /** Scale factor for hex images, with hex center as origin. */
    var tileScale: Float = 1f
    var ruleVariants: HashMap<String, Array<String>> = HashMap()

    fun clone(): TileSetConfig {
        val toReturn = TileSetConfig()
        toReturn.useColorAsBaseTerrain = useColorAsBaseTerrain
        toReturn.unexploredTileColor = unexploredTileColor
        toReturn.fogOfWarColor = fogOfWarColor
        toReturn.fallbackTileSet = fallbackTileSet
        toReturn.tileScale = tileScale
        toReturn.ruleVariants.putAll(ruleVariants.map { Pair(it.key, it.value.clone()) })
        return toReturn
    }

    fun updateConfig(other: TileSetConfig) {
        useColorAsBaseTerrain = other.useColorAsBaseTerrain
        unexploredTileColor = other.unexploredTileColor
        fogOfWarColor = other.fogOfWarColor
        fallbackTileSet = other.fallbackTileSet
        for ((tileSetString, renderOrder) in other.ruleVariants){
            ruleVariants[tileSetString] = renderOrder
        }
    }
}
