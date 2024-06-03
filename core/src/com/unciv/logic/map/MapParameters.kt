package com.unciv.logic.map

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexMath.getEquivalentHexagonalRadius
import com.unciv.logic.map.HexMath.getEquivalentRectangularSize
import com.unciv.logic.map.HexMath.getNumberOfTilesInHexagon
import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.models.metadata.BaseRuleset


/* Predefined Map Sizes - ours are a little lighter than the original values. For reference those are:
    Civ5Duel(40,24,17),
    Civ5Tiny(56,36,25),
    Civ5Small(66,42,30),
    Civ5Medium(80,52,37),
    Civ5Large(104,64,47),
    Civ5Huge(128,80,58),
 */
enum class MapSize(val radius: Int, val width: Int, val height: Int) {
    Tiny(10, 23, 15),
    Small(15, 33, 21),
    Medium(20, 44, 29),
    Large(30, 66, 43),
    Huge(40, 87, 57);

    companion object {
        /** Not a predefined [MapSize] enum value, but a String
         * used in [MapParameters.mapSize] to indicate user-defined dimensions.
         * Do not mistake for [MapGeneratedMainType.custom]. */
        const val custom = "Custom"
    }
}

class MapSizeNew : IsPartOfGameInfoSerialization {
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
        name = MapSize.custom
        setNewRadius(radius)
    }

    constructor(width: Int, height: Int) {
        name = MapSize.custom
        this.width = width
        this.height = height
        this.radius = getEquivalentHexagonalRadius(width, height)
    }

    fun clone() = MapSizeNew().also {
        it.name = name
        it.radius = radius
        it.width = width
        it.height = height
    }

    /** Check custom dimensions, fix if too extreme
     * @param worldWrap whether world wrap is on
     * @return null if size was acceptable, otherwise untranslated reason message
     */
    fun fixUndesiredSizes(worldWrap: Boolean): String? {
        if (name != MapSize.custom) return null  // predefined sizes are OK
        // world-wrap mas must always have an even width, so round down silently
        if (worldWrap && width % 2 != 0 ) width--
        // check for any bad condition and bail if none of them
        val message = when {
            worldWrap && width < 32 ->    // otherwise horizontal scrolling will show edges, empirical
                "World wrap requires a minimum width of 32 tiles"
            width < 3 || height < 3 || radius < 2 ->
                "The provided map dimensions were too small"
            radius > 500 ->
                "The provided map dimensions were too big"
            height * 16 < width || width * 16 < height ->    // aspect ratio > 16:1
                "The provided map dimensions had an unacceptable aspect ratio"
            else -> null
        } ?: return null

        // fix the size - not knowing whether hexagonal or rectangular is used
        setNewRadius(when {
            radius < 2 -> 2
            radius > 500 -> 500
            worldWrap && radius < 15 -> 15    // minimum for hexagonal but more than required for rectangular
            else -> radius
        })

        // tell the caller that map dimensions have changed and why
        return message
    }

    private fun setNewRadius(radius: Int) {
        this.radius = radius
        val size = getEquivalentRectangularSize(radius)
        width = size.x.toInt()
        height = size.y.toInt()
    }

    // For debugging and MapGenerator console output
    override fun toString() = if (name == MapSize.custom) "${width}x${height}" else name
}

object MapShape {
    const val hexagonal = "Hexagonal"
    const val flatEarth = "Flat Earth Hexagonal"
    const val rectangular = "Rectangular"
}

object MapGeneratedMainType {
    const val generated = "Generated"
    // Randomly choose a generated map type
    const val randomGenerated = "Random Generated"
    // Non-generated maps
    const val custom = "Custom"

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

class MapParameters : IsPartOfGameInfoSerialization {
    var name = ""
    var type = MapType.pangaea
    var shape = MapShape.hexagonal
    var mapSize = MapSizeNew(MapSize.Medium)
    var mapResources = MapResources.default.label
    var mapResources = MapResourceSetting.default.label
    var noRuins = false
    var noNaturalWonders = false
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
    fun displayMapDimensions() = mapSize.run {
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
