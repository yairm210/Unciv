package com.unciv.logic.map.mapgenerator.mapregions

import com.badlogic.gdx.math.Rectangle
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.math.max
import kotlin.math.min

class Region (val tileMap: TileMap, val rect: Rectangle, val continentID: Int = -1) {
    val tiles = HashSet<Tile>()
    val terrainCounts = HashMap<String, Int>()
    var totalFertility = 0
    var type = "Hybrid" // being an undefined or indeterminate type
    var luxury: String? = null
    var startPosition: HexCoord? = null
    val assignedMinorCivs = ArrayList<Civilization>()

    var affectedByWorldWrap = false

    /** Recalculates tiles and fertility */
    fun updateTiles(trim: Boolean = true) {
        totalFertility = 0
        var minColumn = 99999f
        var maxColumn = -99999f
        var minRow = 99999f
        var maxRow = -99999f

        val columnHasTile = HashSet<Int>()

        tiles.clear()
        for (tile in tileMap.getTilesInRectangle(rect).filter {
                continentID == -1 || it.getContinent() == continentID } ) {
            val fertility = tile.getTileFertility(continentID != -1)
            tiles.add(tile)
            totalFertility += fertility

            if (affectedByWorldWrap)
                columnHasTile.add(tile.getColumn())

            if (trim) {
                val row = tile.getRow().toFloat()
                val column = tile.getColumn().toFloat()
                minColumn = min(minColumn, column)
                maxColumn = max(maxColumn, column)
                minRow = min(minRow, row)
                maxRow = max(maxRow, row)
            }
        }

        if (trim) {
            if (affectedByWorldWrap) // Need to be more thorough with origin longitude
                rect.x = columnHasTile.filter { !columnHasTile.contains(it - 1) }.maxOf { it }.toFloat()
            else
                rect.x = minColumn // ez way for non-wrapping regions
            rect.y = minRow
            rect.height = maxRow - minRow + 1
            if (affectedByWorldWrap && minColumn < rect.x) { // Thorough way
                rect.width = columnHasTile.size.toFloat()
            } else {
                rect.width = maxColumn - minColumn + 1 // ez way
                affectedByWorldWrap = false // also we're not wrapping anymore
            }
        }
    }

    /** Counts the terrains in the Region for type and start determination */
    fun countTerrains() {
        // Count terrains in the region
        terrainCounts.clear()
        for (tile in tiles) {
            val terrainsToCount = if (tile.terrainHasUnique(UniqueType.IgnoreBaseTerrainForRegion))
                tile.terrainFeatureObjects.map { it.name }.asSequence()
            else
                tile.allTerrains.map { it.name }
            for (terrain in terrainsToCount) {
                terrainCounts[terrain] = (terrainCounts[terrain] ?: 0) + 1
            }
            if (tile.isCoastalTile())
                terrainCounts["Coastal"] = (terrainCounts["Coastal"] ?: 0) + 1
        }
    }

    /** Returns number terrains with [name] */
    fun getTerrainAmount(name: String) = terrainCounts[name] ?: 0

    override fun toString() = "Region($type, ${tiles.size} tiles, ${terrainCounts.entries.joinToString { "${it.value} ${it.key}" }})"
}
