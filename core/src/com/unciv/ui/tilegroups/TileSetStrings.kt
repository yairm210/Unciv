package com.unciv.ui.tilegroups

import com.unciv.UncivGame

class TileSetStrings {
    // this is so that when we have 100s of TileGroups, they won't all individually come up with all these strings themselves,
    // it gets pretty memory-intensive (10s of MBs which is a lot for lower-end phones)
    val tileSetLocation = "TileSets/" + UncivGame.Current.settings.tileSet + "/"

    val hexagon = tileSetLocation + "Hexagon"
    val crosshatchHexagon = tileSetLocation + "CrosshatchHexagon"
    val cityOverlay = tileSetLocation + "CityOverlay"
    val railroad = tileSetLocation + "Railroad"
    val naturalWonderOverlay = tileSetLocation + "NaturalWonderOverlay"

    val tilesLocation = tileSetLocation + "Tiles/"
    val cityTile = tilesLocation + "City"
    val bottomRightRiver = tilesLocation + "River-BottomRight"
    val bottomRiver = tilesLocation + "River-Bottom"
    val bottomLeftRiver = tilesLocation + "River-BottomLeft"

    val unitsLocation = tileSetLocation + "Units/"
    val landUnit = unitsLocation + "LandUnit"
    val waterUnit = unitsLocation + "WaterUnit"

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
        if (baseTerrain != null && era != null) getString(tilesLocation, baseTerrain, city, tag, era)
        if (era != null) return getString(tilesLocation, city, tag, era)
        if (baseTerrain != null) return getString(tilesLocation, baseTerrain, "+", city)
        else return cityTile
    }
}
