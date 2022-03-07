package com.unciv.ui.tilegroups

import com.unciv.UncivGame
import com.unciv.logic.map.RoadStatus
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.tilesets.TileSetConfig
import com.unciv.ui.utils.ImageGetter

/**
 * @param tileSet Name of the tileset. Defaults to active at time of instantiation.
 * @param fallbackDepth Maximum number of fallback tilesets to try. Used to prevent infinite recursion.
 * */
class TileSetStrings(tileSet: String = UncivGame.Current.settings.tileSet, fallbackDepth: Int = 1) {

    // this is so that when we have 100s of TileGroups, they won't all individually come up with all these strings themselves,
    // it gets pretty memory-intensive (10s of MBs which is a lot for lower-end phones)
    val tileSetLocation = "TileSets/$tileSet/"
    val tileSetConfig = TileSetCache[tileSet] ?: TileSetConfig()

    // These need to be by lazy since the orFallback expects a tileset, which it may not get.
    val hexagon: String by lazy { orFallback {tileSetLocation + "Hexagon"} }
    val hexagonList by lazy { listOf(hexagon) }
    val crosshatchHexagon by lazy { orFallback { tileSetLocation + "CrosshatchHexagon" } }
    val crosshair by lazy { orFallback { getString(tileSetLocation, "Crosshair") } }
    val highlight by lazy { orFallback { getString(tileSetLocation, "Highlight") } }
    val cityOverlay = tileSetLocation + "CityOverlay"
    val roadsMap = RoadStatus.values()
        .filterNot { it == RoadStatus.None }
        .associateWith { tileSetLocation + it.name }
    val naturalWonderOverlay = tileSetLocation + "NaturalWonderOverlay"

    val tilesLocation = tileSetLocation + "Tiles/"
    val cityTile = tilesLocation + "City"
    val bottomRightRiver by lazy { orFallback { tilesLocation + "River-BottomRight"} }
    val bottomRiver by lazy { orFallback { tilesLocation + "River-Bottom"} }
    val bottomLeftRiver  by lazy { orFallback { tilesLocation + "River-BottomLeft"} }
    val unitsLocation = tileSetLocation + "Units/"
    val landUnit = unitsLocation + "LandUnit"
    val waterUnit = unitsLocation + "WaterUnit"
    val civilianLandUnit = unitsLocation + "CivilianLandUnit"

    val bordersLocation = tileSetLocation + "Borders/"

    // There aren't that many tile combinations, and so we end up joining the same strings over and over again.
    // On large maps, this can end up as quite a lot of space, some tens of MB!
    // In order to save on space, we have this function that gets several strings and returns their concat,
    //  but is able to retrieve the existing concat if it exists, letting us essentially save each string exactly once.
    private val hashmap = HashMap<Pair<String, String>, String>()
    fun getString(vararg strings: String): String {
        var currentString = ""
        for (str in strings) {
            if (currentString == "") {
                currentString = str
                continue
            }
            val pair = Pair(currentString, str)
            if (hashmap.containsKey(pair)) currentString = hashmap[pair]!!
            else {
                val newString = currentString + str
                hashmap[pair] = newString
                currentString = newString
            }
        }
        return currentString
    }

    val overlay = "Overlay"
    val city = "City"
    val tag = "-"
    fun getTile(baseTerrain: String) = getString(tilesLocation, baseTerrain)
    fun getBaseTerrainOverlay(baseTerrain: String) = getString(tileSetLocation, baseTerrain, overlay)
    fun getTerrainFeatureOverlay(terrainFeature: String) = getString(tileSetLocation, terrainFeature, overlay)

    fun getCityTile(baseTerrain: String?, era: String?): String {
        if (baseTerrain != null && era != null) return getString(tilesLocation, baseTerrain, city, tag, era)
        if (era != null) return getString(tilesLocation, city, tag, era)
        if (baseTerrain != null) return getString(tilesLocation, baseTerrain, "+", city)
        else return cityTile
    }

    fun getBorder(imageName: String) = getString(bordersLocation, imageName)

    /** Fallback [TileSetStrings] to use when the currently chosen tileset is missing an image. */
    val fallback by lazy {
        if (fallbackDepth <= 0 || tileSetConfig.fallbackTileSet == null)
            null
        else
            TileSetStrings(tileSetConfig.fallbackTileSet!!, fallbackDepth-1)
    }
    /**
     * @param image An image path string, such as returned from an instance of [TileSetStrings].
     * @param fallbackImage A lambda function that will be run with the [fallback] as its receiver if the original image does not exist according to [ImageGetter.imageExists].
     * @return The original image path string if its image exists, or the return result of the [fallbackImage] lambda if the original image does not exist.
     * */
    fun orFallback(image: String, fallbackImage: TileSetStrings.() -> String): String {
        return if (fallback == null || ImageGetter.imageExists(image))
            image
        else
            fallback!!.run(fallbackImage)
    }
    /** @see orFallback */
    fun orFallback(image: TileSetStrings.() -> String, fallbackImage: TileSetStrings.() -> String)
            = orFallback(this.run(image), fallbackImage)
    /** @see orFallback */
    fun orFallback(image: TileSetStrings.() -> String)
            = orFallback(image, image)

}
