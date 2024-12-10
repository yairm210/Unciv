package com.unciv.logic.map

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexMath.getNumberOfTilesInHexagon
import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.models.metadata.BaseRuleset


object MapShape {
    const val rectangular = "Rectangular"
    const val hexagonal = "Hexagonal"
    const val flatEarth = "Flat Earth Hexagonal"
}

object MapGeneratedMainType {
    const val generated = "Generated"
    // Randomly choose a generated map type
    const val randomGenerated = "Random Generated"
    // Non-generated maps
    const val custom = "Custom"
    const val scenario = "Scenario"

}

object MapType {
    const val perlin = "Perlin"
    const val pangaea = "Pangaea"
    const val continentAndIslands = "Continent and Islands"
    const val twoContinents = "Two Continents"
    const val threeContinents = "Three Continents"
    const val fourCorners = "Four Corners"
    const val archipelago = "Archipelago"
    const val fractal = "Fractal"
    const val innerSea = "Inner Sea"
    const val lakes = "Lakes"
    const val smallContinents = "Small Continents"

    // All ocean tiles
    const val empty = "Empty"
}

object MirroringType {
    const val none = "None"
    const val aroundCenterTile = "Around Center Tile"
    const val fourway = "4-way"
    const val topbottom = "Top-Bottom"
    const val leftright = "Bottom-Top"
}

class MapParameters : IsPartOfGameInfoSerialization {
    var name = ""
    var type = MapType.pangaea
    // DO NOT CHANGE DEFAULTS since that changes all existing games to new default!
    var shape = MapShape.hexagonal
    var mapSize = MapSize.Medium
    var mapResources = MapResourceSetting.default.label
    var mirroring: String = MirroringType.none
    var noRuins = false
    var noNaturalWonders = false
    // DO NOT CHANGE DEFAULTS since that changes all existing games to new default!
    var worldWrap = false
    var strategicBalance = false
    var legendaryStart = false

    /** This is used mainly for the map editor, so you can continue editing a map under the same ruleset you started with */
    var mods = LinkedHashSet<String>()
    var baseRuleset = BaseRuleset.Civ_V_GnK.fullName // Hardcoded as the RulesetCache is not yet initialized when starting up

    /** Unciv Version of creation for support cases */
    var createdWithVersion = ""

    var seed: Long = System.currentTimeMillis()
    var tilesPerBiomeArea = 6
    var maxCoastExtension = 2
    var elevationExponent = 0.7f
    var temperatureExtremeness = 0.6f
    var vegetationRichness = 0.4f
    var rareFeaturesRichness = 0.05f
    var resourceRichness = 0.1f
    var waterThreshold = 0.0f

    /** Shifts temperature (after random, latitude and temperatureExtremeness).*/
    var temperatureShift = 0f

    fun clone() = MapParameters().also {
        it.name = name
        it.type = type
        it.shape = shape
        it.mapSize = mapSize.clone()
        it.mapResources = mapResources
        it.noRuins = noRuins
        it.noNaturalWonders = noNaturalWonders
        it.worldWrap = worldWrap
        it.strategicBalance = strategicBalance
        it.legendaryStart = legendaryStart
        it.mods = LinkedHashSet(mods)
        it.baseRuleset = baseRuleset
        it.seed = seed
        it.tilesPerBiomeArea = tilesPerBiomeArea
        it.maxCoastExtension = maxCoastExtension
        it.elevationExponent = elevationExponent
        it.temperatureExtremeness = temperatureExtremeness
        it.temperatureShift = temperatureShift
        it.vegetationRichness = vegetationRichness
        it.rareFeaturesRichness = rareFeaturesRichness
        it.resourceRichness = resourceRichness
        it.waterThreshold = waterThreshold
        it.createdWithVersion = createdWithVersion
    }

    fun reseed() {
        seed = System.currentTimeMillis()
    }

    fun resetAdvancedSettings() {
        reseed()
        tilesPerBiomeArea = 6
        maxCoastExtension = 2
        elevationExponent = 0.7f
        temperatureExtremeness = 0.6f
        temperatureShift = 0.0f
        vegetationRichness = 0.4f
        rareFeaturesRichness = 0.05f
        resourceRichness = 0.1f
        waterThreshold = 0f
    }

    fun getMapResources() = MapResourceSetting.safeValueOf(mapResources)
    @Suppress("DEPRECATION") // This IS the legacy support
    @JvmName("strategicBalanceGetter")
    fun getStrategicBalance() = strategicBalance || mapResources == MapResourceSetting.strategicBalance.label
    @Suppress("DEPRECATION") // This IS the legacy support
    @JvmName("legendaryStartGetter")
    fun getLegendaryStart() = legendaryStart || mapResources == MapResourceSetting.legendaryStart.label

    fun getArea() = when {
        shape == MapShape.hexagonal || shape == MapShape.flatEarth -> getNumberOfTilesInHexagon(mapSize.radius)
        worldWrap && mapSize.width % 2 != 0 -> (mapSize.width - 1) * mapSize.height
        else -> mapSize.width * mapSize.height
    }
    private fun displayMapDimensions() = mapSize.run {
        (if (shape == MapShape.hexagonal || shape == MapShape.flatEarth) "R$radius" else "${width}x$height") +
        (if (worldWrap) "w" else "")
    }

    // Human readable float representation akin to .net "0.###" - round to N digits but without redundant trailing zeroes
    private fun Float.niceToString(maxPrecision: Int) =
        "%.${maxPrecision}f".format(this).trimEnd('0').trimEnd('.')

    // For debugging and MapGenerator console output
    override fun toString() = sequence {
        if (name.isNotEmpty()) yield("\"$name\" ")
        yield("(")
        if (mapSize.name != MapSize.custom) yield("{${mapSize.name}} ")
        if (worldWrap) yield("{World Wrap} ")
        yield("{$shape}")
        yield(" " + displayMapDimensions() + ")")
        if (mapResources != MapResourceSetting.default.label) yield(" {Resource Setting}: {$mapResources}")
        if (strategicBalance) yield(" {Strategic Balance}")
        if (legendaryStart) yield(" {Legendary Start}")
        if (name.isEmpty()) return@sequence
        yield("\n")
        if (type != MapGeneratedMainType.custom && type != MapType.empty) yield("{Map Generation Type}: {$type}, ")
        yield("{RNG Seed} $seed")
        yield(", {Map Elevation}=" + elevationExponent.niceToString(2))
        yield(", {Temperature extremeness}=" + temperatureExtremeness.niceToString(2))
        yield(", {Resource richness}=" + resourceRichness.niceToString(3))
        yield(", {Vegetation richness}=" + vegetationRichness.niceToString(2))
        yield(", {Rare features richness}=" + rareFeaturesRichness.niceToString(3))
        yield(", {Max Coast extension}=$maxCoastExtension")
        yield(", {Biome areas extension}=$tilesPerBiomeArea")
        yield(", {Water level}=" + waterThreshold.niceToString(2))
    }.joinToString("")

    fun numberOfTiles() =
        if (shape == MapShape.hexagonal || shape == MapShape.flatEarth) {
            1 + 3 * mapSize.radius * (mapSize.radius - 1)
        } else {
            mapSize.width * mapSize.height
        }
}
