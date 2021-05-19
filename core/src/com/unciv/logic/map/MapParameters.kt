package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.logic.HexMath.getEquivalentHexagonalRadius
import com.unciv.logic.HexMath.getEquivalentRectangularSize


enum class MapSize(val radius: Int, val width: Int, val height: Int) {
    Tiny(10, 23, 15),
    Small(15, 33, 21),
    Medium(20, 44, 29),
    Large(30, 66, 43),
    Huge(40, 87, 57)
}

class MapSizeNew {
    var radius = 0
    var width = 0
    var height = 0
    var name = ""

    /** Needed for Json parsing */
    @Suppress("unused")
    constructor()

    private fun fromPredefined(predefined: MapSize) {
        name = predefined.name
        radius = predefined.radius
        width = predefined.width
        height = predefined.height
    }

    constructor(size: MapSize) {
        fromPredefined(size)
    }

    constructor(name: String) {
        try {
            fromPredefined(MapSize.valueOf(name))
        } catch (_: Exception) {
            fromPredefined(MapSize.Tiny)
        }
    }

    constructor(radius: Int) {
        name = Constants.custom
        this.radius = radius
        val size = getEquivalentRectangularSize(radius)
        this.width = size.x.toInt()
        this.height = size.y.toInt()
    }

    constructor(width: Int, height: Int) {
        name = Constants.custom
        this.width = width
        this.height = height
        this.radius = getEquivalentHexagonalRadius(width, height)

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

    // All ocean tiles
    const val empty = "Empty"
}

class MapParameters {
    var name = ""
    var type = MapType.pangaea
    var shape = MapShape.hexagonal
    @Deprecated("replaced by mapSize since 3.19.18")
    var size = MapSize.Medium
    var mapSize = MapSizeNew(MapSize.Medium)
    var noRuins = false
    var noNaturalWonders = false
    var worldWrap = false

    /** This is used mainly for the map editor, so you can continue editing a map under the ame ruleset you started with */
    var mods = LinkedHashSet<String>()

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
