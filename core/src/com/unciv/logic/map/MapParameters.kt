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
    var mountainProbability = 0.10f
    var temperatureExtremeness = 0.30f
    var terrainFeatureRichness = 0.30f
    var resourceRichness = 0.10f
    var strategicResourceRichness = 0.10f
    var ruinsRichness = 1f
    var waterProbability = 0.05f
    var landProbability = 0.55f

    fun resetAdvancedSettings() {
        tilesPerBiomeArea = 6
        maxCoastExtension = 2
        mountainProbability = 0.10f
        temperatureExtremeness = 0.30f
        terrainFeatureRichness = 0.30f
        resourceRichness = 0.10f
        strategicResourceRichness = 0.10f
        ruinsRichness = 1f
        waterProbability = 0.05f
        landProbability = 0.55f
    }
}