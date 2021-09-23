package com.unciv.models.ruleset.tile

enum class TerrainType(val isBaseTerrain: Boolean) {
    Land(true),
    Water(true),
    TerrainFeature(false),
    NaturalWonder(false)
}
