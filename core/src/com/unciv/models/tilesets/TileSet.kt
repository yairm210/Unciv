package com.unciv.models.tilesets

class TileSet(val name: String) {

    var config = TileSetConfig()
    var fallback: TileSet? = null

    private val configs = HashMap<String, TileSetConfig>()

    fun addConfig(id: String, config: TileSetConfig) {
        configs[id] = config
    }

    fun mergeConfig(id: String) {
        val configToMerge = configs[id] ?: return
        config.updateConfig(configToMerge)
    }

    fun resetConfig() {
        config = TileSetConfig()
    }

    companion object {
        const val DEFAULT = "INTERNAL"
    }

}
