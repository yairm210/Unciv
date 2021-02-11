package com.unciv.logic.map

enum class MapSize(val radius: Int) {
    Tiny(10),
    Small(15),
    Medium(20),
    Large(30),
    Huge(40)
}

object MapShape {
    const val hexagonal = "Hexagonal"
    const val rectangular = "Rectangular"
}

object MapType {
    const val pangaea = "Pangaea"
    const val continents = "Continents"
    const val perlin = "Perlin"
    const val archipelago = "Archipelago"

    // Cellular automata
    const val default = "Default"

    // Non-generated maps
    const val custom = "Custom"

    // All ocean tiles
    const val empty = "Empty"
}

class MapParameters {
    var name = ""
    var type = MapType.pangaea
    var shape = MapShape.hexagonal
    var size: MapSize = MapSize.Medium
    var noRuins = false
    var noNaturalWonders = false

    var seed: Long = 0
    var tilesPerBiomeArea = 6
    var maxCoastExtension = 2
    var elevationExponent = 0.7f
    var temperatureExtremeness = 0.6f
    var vegetationRichness = 0.4f
    var rareFeaturesRichness = 0.05f
    var resourceRichness = 0.1f
    var waterThreshold = 0f

    fun resetAdvancedSettings() {
        tilesPerBiomeArea = 6
        maxCoastExtension = 2
        elevationExponent = 0.7f
        temperatureExtremeness = 0.6f
        vegetationRichness = 0.4f
        rareFeaturesRichness = 0.05f
        resourceRichness = 0.1f
        waterThreshold = 0f
    }
}