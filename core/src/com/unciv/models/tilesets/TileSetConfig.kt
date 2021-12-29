package com.unciv.models.tilesets

import com.badlogic.gdx.graphics.Color

class TileSetConfig {
    var useColorAsBaseTerrain = true
    var unexploredTileColor: Color = Color.DARK_GRAY
    var fogOfWarColor: Color = Color.BLACK
    /** Name of the tileset to use when this one is missing images. Null to disable. */
    var fallbackTileSet: String? = "FantasyHex"
    var tileScale: Float = 1f
    var ruleVariants: HashMap<String, Array<String>> = HashMap()

    fun updateConfig(other: TileSetConfig){
        useColorAsBaseTerrain = other.useColorAsBaseTerrain
        unexploredTileColor = other.unexploredTileColor
        fogOfWarColor = other.fogOfWarColor
        fallbackTileSet = other.fallbackTileSet
        for ((tileSetString, renderOrder) in other.ruleVariants){
            ruleVariants[tileSetString] = renderOrder
        }
    }
}
