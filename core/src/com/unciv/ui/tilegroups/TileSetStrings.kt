package com.unciv.ui.tilegroups

import com.unciv.UnCivGame

class TileSetStrings {
    // this is so that when we have 100s of TileGroups, they won't all individually come up with all these strings themselves,
    // it gets pretty memory-intensive (10s of MBs which is a lot for lower-end phones)
    val tileSetLocation = "TileSets/"+ UnCivGame.Current.settings.tileSet +"/"
    val hexagon = tileSetLocation+"Hexagon"
    val crosshatchHexagon = tileSetLocation+"CrosshatchHexagon"
    val cityTile = tileSetLocation+"City"
    val cityOverlay = tileSetLocation+"CityOverlay"
    val railroad = tileSetLocation+"Railroad"

    private val baseTerrainToTile = HashMap<String,String>()
    fun getBaseTerrainTile(baseTerrain:String): String {
        if(!baseTerrainToTile.containsKey(baseTerrain))
            baseTerrainToTile[baseTerrain] = "$tileSetLocation$baseTerrain"
        return baseTerrainToTile[baseTerrain]!!
    }


    private val baseTerrainToOverlay = HashMap<String,String>()
    fun getBaseTerrainOverlay(baseTerrain:String): String {
        if(!baseTerrainToOverlay.containsKey(baseTerrain))
            baseTerrainToOverlay[baseTerrain] = "$tileSetLocation$baseTerrain"+"Overlay"
        return baseTerrainToOverlay[baseTerrain]!!
    }

    private val baseTerrainToCityTile = HashMap<String,String>()
    fun getCityTile(baseTerrain:String): String {
        if(!baseTerrainToCityTile.containsKey(baseTerrain))
            baseTerrainToCityTile[baseTerrain] = "$tileSetLocation$baseTerrain+City"
        return baseTerrainToCityTile[baseTerrain]!!
    }

    private val terrainFeatureToOverlay = HashMap<String,String>()
    fun getTerrainFeatureOverlay(terrainFeature:String): String {
        if(!terrainFeatureToOverlay.containsKey(terrainFeature))
            terrainFeatureToOverlay[terrainFeature] = tileSetLocation + terrainFeature +"Overlay"
        return terrainFeatureToOverlay[terrainFeature]!!
    }


}
