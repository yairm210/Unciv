package com.unciv.models.tilesets

class TileSetConfig {
    var useColorAsBaseTerrain = true
    var ruleVariants: HashMap<String, Array<String>> = HashMap()

    fun updateConfig(other: TileSetConfig){
        useColorAsBaseTerrain = other.useColorAsBaseTerrain
        for ((tileSetString, renderOrder) in other.ruleVariants){
            ruleVariants[tileSetString] = renderOrder
        }
    }
}