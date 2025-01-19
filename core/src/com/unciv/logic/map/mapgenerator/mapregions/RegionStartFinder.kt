package com.unciv.logic.map.mapgenerator.mapregions

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.Companion.closeStartPenaltyForRing
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.Companion.firstRingFoodScores
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.Companion.firstRingProdScores
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.Companion.maximumJunk
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.Companion.minimumFoodForRing
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.Companion.minimumGoodForRing
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.Companion.minimumProdForRing
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.Companion.secondRingFoodScores
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.Companion.secondRingProdScores
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.TerrainType
import kotlin.math.roundToInt

object RegionStartFinder {

    /** Attempts to find a good start close to the center of [region]. Calls setRegionStart with the position*/
    internal fun findStart(region: Region, tileData: TileDataMap) {
        val fallbackTiles = HashSet<Vector2>()
        // Priority: 1. Adjacent to river, 2. Adjacent to coast or fresh water, 3. Other.

        // First check center rect, then middle. Only check the outer area if no good sites found
        val centerTiles = region.tileMap.getTilesInRectangle(getCentralRectangle(region.rect, 0.33f))
        if (findGoodPosition(centerTiles, region, tileData, fallbackTiles)) return
        
        val middleDonut = region.tileMap.getTilesInRectangle(getCentralRectangle(region.rect, 0.67f)).filterNot { it in centerTiles }
        if (findGoodPosition(middleDonut, region, tileData, fallbackTiles)) return 

        // Now check the outer tiles. For these we don't care about rivers, coasts etc
        val outerDonut = region.tileMap.getTilesInRectangle(region.rect).filterNot { it in centerTiles || it in middleDonut}
        if (findEdgePosition(outerDonut, region, tileData, fallbackTiles)) return
        
        findFallbackPosition(fallbackTiles, tileData, region)
    }

    private fun findGoodPosition(centerTiles: Sequence<Tile>, region: Region, tileData: TileDataMap, fallbackTiles: HashSet<Vector2>): Boolean {
        val riverTiles = HashSet<Vector2>()
        val wetTiles = HashSet<Vector2>()
        val dryTiles = HashSet<Vector2>()
        for (tile in centerTiles) {
            if (tileData[tile.position]!!.isTwoFromCoast)
                continue // Don't even consider tiles two from coast
            if (region.continentID != -1 && region.continentID != tile.getContinent())
                continue // Wrong continent
            if (tile.isLand && !tile.isImpassible()) {
                evaluateTileForStart(tile, tileData)
                if (tile.isAdjacentToRiver())
                    riverTiles.add(tile.position)
                else if (tile.isCoastalTile() || tile.isAdjacentTo(Constants.freshWater))
                    wetTiles.add(tile.position)
                else
                    dryTiles.add(tile.position)
            }
        }
        // Did we find a good start position?
        for (list in sequenceOf(riverTiles, wetTiles, dryTiles)) {
            if (list.any { tileData[it]!!.isGoodStart }) {
                setRegionStart(region, list
                    .filter { tileData[it]!!.isGoodStart }.maxByOrNull { tileData[it]!!.startScore }!!, tileData)
                return true
            }
            if (list.isNotEmpty()) // Save the best not-good-enough spots for later fallback
                fallbackTiles.add(list.maxByOrNull { tileData[it]!!.startScore }!!)
        }
        return false
    }


    private fun findEdgePosition(
        outerDonut: Sequence<Tile>,
        region: Region,
        tileData: TileDataMap,
        fallbackTiles: HashSet<Vector2>
    ): Boolean {
        val dryTiles = HashSet<Vector2>()
        for (tile in outerDonut) {
            if (region.continentID != -1 && region.continentID != tile.getContinent())
                continue // Wrong continent
            if (tile.isLand && !tile.isImpassible()) {
                evaluateTileForStart(tile, tileData)
                dryTiles.add(tile.position)
            }
        }
        // Were any of them good?
        if (dryTiles.any { tileData[it]!!.isGoodStart }) {
            // Find the one closest to the center
            val center = region.rect.getCenter(Vector2())
            val closestToCenter = dryTiles.filter { tileData[it]!!.isGoodStart }
                .minByOrNull {
                (region.tileMap.getIfTileExistsOrNull(center.x.roundToInt(), center.y.roundToInt())
                    ?: region.tileMap.values.first())
                    .aerialDistanceTo(
                        region.tileMap.getIfTileExistsOrNull(it.x.toInt(), it.y.toInt())
                            ?: region.tileMap.values.first()
                    )
            }!!
            
            setRegionStart(
                region,
                closestToCenter,
                tileData
            )
            return true
        }
        if (dryTiles.isNotEmpty())
            fallbackTiles.add(dryTiles.maxByOrNull { tileData[it]!!.startScore }!!)
        return false
    }

    private fun findFallbackPosition(
        fallbackTiles: HashSet<Vector2>,
        tileData: TileDataMap,
        region: Region
    ) {
        // Fallback time. Just pick the one with best score
        val fallbackPosition = fallbackTiles.maxByOrNull { tileData[it]!!.startScore }
        if (fallbackPosition != null) {
            setRegionStart(region, fallbackPosition, tileData)
            return
        }

        // Something went extremely wrong and there is somehow no place to start. Spawn some land and start there
        val panicPosition = region.rect.getPosition(Vector2())
        val panicTerrain = region.tileMap.ruleset!!.terrains.values.first { it.type == TerrainType.Land }.name
        region.tileMap[panicPosition].baseTerrain = panicTerrain
        region.tileMap[panicPosition].setTerrainFeatures(listOf())
        setRegionStart(region, panicPosition, tileData)
    }
    
    /** @returns a scaled according to [proportion] Rectangle centered over [originalRect] */
    private fun getCentralRectangle(originalRect: Rectangle, proportion: Float): Rectangle {
        val scaledRect = Rectangle(originalRect)

        scaledRect.width = (originalRect.width * proportion)
        scaledRect.height = (originalRect.height * proportion)
        scaledRect.x = originalRect.x + (originalRect.width - scaledRect.width) / 2
        scaledRect.y = originalRect.y + (originalRect.height - scaledRect.height) / 2

        // round values
        scaledRect.x = scaledRect.x.roundToInt().toFloat()
        scaledRect.y = scaledRect.y.roundToInt().toFloat()
        scaledRect.width = scaledRect.width.roundToInt().toFloat()
        scaledRect.height = scaledRect.height.roundToInt().toFloat()

        return scaledRect
    }


    /** Evaluates a tile for starting position, setting isGoodStart and startScore in
     *  MapGenTileData. Assumes that all tiles have corresponding MapGenTileData. */
    private fun evaluateTileForStart(tile: Tile, tileData: TileDataMap) {
        val localData = tileData[tile.position]!!

        var totalFood = 0
        var totalProd = 0
        var totalGood = 0
        var totalJunk = 0
        var totalRivers = 0
        var totalScore = 0

        if (tile.isCoastalTile()) totalScore += 40

        // Go through all rings
        for (ring in 1..3) {
            // Sum up the values for this ring
            for (outerTile in tile.getTilesAtDistance(ring)) {
                val outerTileData = tileData[outerTile.position]!!
                if (outerTileData.isJunk)
                    totalJunk++
                else {
                    if (outerTileData.isFood) totalFood++
                    if (outerTileData.isProd) totalProd++
                    if (outerTileData.isGood) totalGood++
                    if (outerTile.isAdjacentToRiver()) totalRivers++
                }
            }
            // Check for minimum levels. We still keep on calculating final score in case of failure
            if (totalFood < minimumFoodForRing[ring]!!
                || totalProd < minimumProdForRing[ring]!!
                || totalGood < minimumGoodForRing[ring]!!) {
                localData.isGoodStart = false
            }

            // Ring-specific scoring
            when (ring) {
                1 -> {
                    val foodScore = firstRingFoodScores[totalFood]
                    val prodScore = firstRingProdScores[totalProd]
                    totalScore += foodScore + prodScore + totalRivers + (totalGood * 2) - (totalJunk * 3)
                }
                2 -> {
                    val foodScore = if (totalFood > 10) secondRingFoodScores.last()
                    else secondRingFoodScores[totalFood]
                    val effectiveTotalProd = if (totalProd >= totalFood * 2) totalProd
                    else (totalFood + 1) / 2 // Can't use all that production without food
                    val prodScore = if (effectiveTotalProd > 5) secondRingProdScores.last()
                    else secondRingProdScores[effectiveTotalProd]
                    totalScore += foodScore + prodScore + totalRivers+ (totalGood * 2) - (totalJunk * 3)
                }
                else -> {
                    totalScore += totalFood + totalProd + totalGood + totalRivers - (totalJunk * 2)
                }
            }
        }
        // Too much junk?
        if (totalJunk > maximumJunk) {
            localData.isGoodStart = false
        }

        // Finally check if this is near another start
        if (localData.closeStartPenalty > 0) {
            localData.isGoodStart = false
            totalScore -= (totalScore * localData.closeStartPenalty) / 100
        }
        localData.startScore = totalScore
    }

    private fun setRegionStart(region: Region, position: Vector2, tileData: TileDataMap) {
        region.startPosition = position

        for ((ring, penalty) in closeStartPenaltyForRing) {
            for (outerTile in region.tileMap[position].getTilesAtDistance(ring).map { it.position })
                tileData[outerTile]!!.addCloseStartPenalty(penalty)
        }
    }
}
