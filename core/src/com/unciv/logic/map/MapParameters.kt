package com.unciv.logic.map

import com.unciv.Constants



class MapSize {
    var radius = 0
    var width = 0
    var height = 0
    var name = ""

    constructor(name: String) {
        when (name) {
            Constants.tiny -> { radius = 10; width = 10; height = 10 }
            Constants.small -> { radius = 15; width = 10; height = 10 }
            Constants.medium -> { radius = 20; width = 10; height = 10 }
            Constants.large -> { radius = 30; width = 10; height = 10 }
            Constants.huge -> { radius = 40; width = 10; height = 10 }
        }
    }
    constructor(radius: Int) {
        name = Constants.custom
        this.radius = radius
    }

    constructor(height: Int, width: Int) {
        name = Constants.custom
        this.height = height
        this.width = width
    }


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

    // Loaded scenario
    const val scenario = "Scenario"

    // All ocean tiles
    const val empty = "Empty"
}

class MapParameters {
    var name = ""
    var type = MapType.pangaea
    var shape = MapShape.hexagonal
    var size = MapSize(Constants.medium)
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