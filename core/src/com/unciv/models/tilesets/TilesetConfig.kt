package com.unciv.models.tilesets

class TilesetConfig {
    var useColorAsBaseTerrain = true
    var ruleVariants: HashMap<String, Array<String>> = HashMap()

    fun updateConfig(other: TilesetConfig){
        useColorAsBaseTerrain = other.useColorAsBaseTerrain
        for ((tileSetString, renderOrder) in other.ruleVariants){
            ruleVariants[tileSetString] = renderOrder
        }
    }
}