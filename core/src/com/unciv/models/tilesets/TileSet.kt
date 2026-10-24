package com.unciv.models.tilesets

class TileSet(val name: String) {

    var config = TileSetConfig()
    var fallback: TileSet? = null

    private val modNameToConfig = HashMap<String, TileSetConfig>()

    fun cacheConfigFromMod(modName: String, config: TileSetConfig) {
        modNameToConfig[modName] = config
    }

    fun mergeModConfig(modName: String) {
        val configToMerge = modNameToConfig[modName] ?: return
        config.updateConfig(configToMerge)
    }

    fun resetConfig() {
        config = TileSetConfig()
    }

    companion object {
        const val DEFAULT = "INTERNAL"
    }

    override fun toString(): String = name

}
