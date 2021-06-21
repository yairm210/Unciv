package com.unciv.models.ruleset.tile

import com.unciv.models.stats.NamedStats

enum class TerrainType {
    Land,
    Water,
    TerrainFeature,
    NaturalWonder
}

class TerrainType2 : NamedStats() {
    lateinit var type: String
    var priority = 0
}