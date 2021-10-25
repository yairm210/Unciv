package com.unciv.logic.map.mapgenerator

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MapRegions (val ruleset: Ruleset){
    private val regions = ArrayList<Region>()
    private val tileData = HashMap<Vector2, MapGenTileData>()

    /** Creates [numRegions] number of balanced regions for civ starting locations. */
    fun generateRegions(tileMap: TileMap, numRegions: Int) {
        if (numRegions <= 0) return // Don't bother about regions, probably map editor
        if (tileMap.continentSizes.isEmpty()) throw Exception("No Continents on this map!")
        val totalLand = tileMap.continentSizes.values.sum().toFloat()
        val largestContinent = tileMap.continentSizes.values.maxOf { it }.toFloat()

        val radius = if (tileMap.mapParameters.shape == MapShape.hexagonal)
            tileMap.mapParameters.mapSize.radius.toFloat()
        else
            (max(tileMap.mapParameters.mapSize.width / 2, tileMap.mapParameters.mapSize.height / 2)).toFloat()
        // A hueg box including the entire map.
        val mapRect = Rectangle(-radius, -radius, radius * 2 + 1, radius * 2 + 1)

        // Lots of small islands - just split ut the map in rectangles while ignoring Continents
        // 25% is chosen as limit so Four Corners maps don't fall in this category
        if (largestContinent / totalLand < 0.25f) {
            // Make a huge rectangle covering the entire map
            val hugeRect = Region()
            hugeRect.continentID = -1 // Don't care about continents
            hugeRect.rect = mapRect
            hugeRect.tileMap = tileMap
            hugeRect.affectedByWorldWrap = false // Might as well start at the seam
            hugeRect.updateTiles()
            divideRegion(hugeRect, numRegions)
            return
        }
        // Continents type - distribute civs according to total fertility, then split as needed
        val continents = tileMap.continentSizes.keys.toMutableList()
        val civsAddedToContinent = HashMap<Int, Int>() // Continent ID, civs added
        val continentFertility = HashMap<Int, Int>() // Continent ID, total fertility
        // Keep track of the even-q columns each continent is at, to figure out if they wrap
        val continentIsAtCol = HashMap<Int, HashSet<Int>>()

        // Calculate continent fertilities and columns
        for (tile in tileMap.values) {
            val continent = tile.getContinent()
            if (continent != -1) {
                continentFertility[continent] = tile.getTileFertility(true) +
                        (continentFertility[continent] ?: 0)

                if (continentIsAtCol[continent] == null)
                    continentIsAtCol[continent] = HashSet()
                continentIsAtCol[continent]!!.add(HexMath.hex2EvenQCoords(tile.position).x.toInt())
            }
        }

        // Assign regions to the best continents, giving half value for region #2 etc
        for (regionToAssign in 1..numRegions) {
            val bestContinent = continents
                    .maxByOrNull { continentFertility[it]!! / (1 + (civsAddedToContinent[it] ?: 0)) }!!
            civsAddedToContinent[bestContinent] = (civsAddedToContinent[bestContinent] ?: 0) + 1
        }

        // Split up the continents
        for (continent in civsAddedToContinent.keys) {
            val continentRegion = Region()
            val cols = continentIsAtCol[continent]!!
            continentRegion.continentID = continent
            continentRegion.rect = Rectangle(mapRect)
            // The rightmost column which does not have a neighbor on the left
            continentRegion.rect.x = cols.filter { !cols.contains(it - 1) }.maxOf { it }.toFloat()
            continentRegion.rect.width = cols.count().toFloat()
            continentRegion.tileMap = tileMap
            if (tileMap.mapParameters.worldWrap) {
                continentRegion.affectedByWorldWrap = false
                // Check if the continent is possibly wrapping - there needs to be a gap when going through the cols
                for (i in cols.minOf { it }..cols.maxOf { it }) {
                    if (!cols.contains(i)) {
                        continentRegion.affectedByWorldWrap = true
                        break
                    }
                }
            }
            continentRegion.updateTiles()
            divideRegion(continentRegion, civsAddedToContinent[continent]!!)
        }
    }

    /** Recursive function, divides a region into [numDivisions] pars of equal-ish fertility */
    private fun divideRegion(region: Region, numDivisions: Int) {
        if (numDivisions <= 1) {
            // We're all set, save the region and return
            regions.add(region)
            return
        }

        val firstDivisions = numDivisions / 2 // Since int division rounds down, works for all numbers
        val splitRegions = splitRegion(region, (100 * firstDivisions) / numDivisions)
        divideRegion(splitRegions.first, firstDivisions)
        divideRegion(splitRegions.second, numDivisions - firstDivisions)
    }

    /** Splits a region in 2, with the first having [firstPercent] of total fertility */
    private fun splitRegion(regionToSplit: Region, firstPercent: Int): Pair<Region, Region> {
        val targetFertility = (regionToSplit.totalFertility * firstPercent) / 100

        val splitOffRegion = Region()
        splitOffRegion.tileMap = regionToSplit.tileMap
        splitOffRegion.continentID = regionToSplit.continentID
        splitOffRegion.rect = Rectangle(regionToSplit.rect)

        val widerThanTall = regionToSplit.rect.width > regionToSplit.rect.height

        var bestSplitPoint = 1 // will be the size of the split-off region
        var closestFertility = 0
        var cumulativeFertility = 0
        val pointsToTry = if (widerThanTall) 1..regionToSplit.rect.width.toInt()
        else 1..regionToSplit.rect.height.toInt()

        for (splitPoint in pointsToTry) {
            val nextRect = if (widerThanTall)
                splitOffRegion.tileMap.getTilesInRectangle(Rectangle(
                        splitOffRegion.rect.x + splitPoint - 1, splitOffRegion.rect.y,
                        1f, splitOffRegion.rect.height),
                        evenQ = true)
            else
                splitOffRegion.tileMap.getTilesInRectangle(Rectangle(
                        splitOffRegion.rect.x, splitOffRegion.rect.y + splitPoint - 1,
                        splitOffRegion.rect.width, 1f),
                        evenQ = true)

            cumulativeFertility += if (splitOffRegion.continentID == -1)
                nextRect.sumOf { it.getTileFertility(false) }
            else
                nextRect.sumOf { if (it.getContinent() == splitOffRegion.continentID) it.getTileFertility(true) else 0 }

            // Better than last try?
            if (abs(cumulativeFertility - targetFertility) <= abs(closestFertility - targetFertility)) {
                bestSplitPoint = splitPoint
                closestFertility = cumulativeFertility
            }
        }

        if (widerThanTall) {
            splitOffRegion.rect.width = bestSplitPoint.toFloat()
            regionToSplit.rect.x = splitOffRegion.rect.x + splitOffRegion.rect.width
            regionToSplit.rect.width = regionToSplit.rect.width- bestSplitPoint
        } else {
            splitOffRegion.rect.height = bestSplitPoint.toFloat()
            regionToSplit.rect.y = splitOffRegion.rect.y + splitOffRegion.rect.height
            regionToSplit.rect.height = regionToSplit.rect.height - bestSplitPoint
        }
        splitOffRegion.updateTiles()
        regionToSplit.updateTiles()

        return Pair(splitOffRegion, regionToSplit)
    }

    fun assignRegions(tileMap: TileMap, civilizations: List<CivilizationInfo>) {
        if (civilizations.isEmpty()) return

        // first assign region types
        val regionTypes = ruleset.terrains.values.filter { it.hasUnique(UniqueType.IsRegion) }
                .sortedBy { it.getMatchingUniques(UniqueType.IsRegion).first().params[0].toInt() }

        for (region in regions) {
            region.countTerrains()

            for (type in regionTypes) {
                // Test exclusion criteria first
                if (type.getMatchingUniques(UniqueType.RegionRequireFirstLessThanSecond).any {
                            region.getTerrainAmount(it.params[0]) >= region.getTerrainAmount(it.params[1]) } ) {
                    continue
                }
                // Test inclusion criteria
                if (type.getMatchingUniques(UniqueType.RegionRequirePercentSingleType).any {
                            region.getTerrainAmount(it.params[1]) >= (it.params[0].toInt() * region.tiles.count()) / 100 }
                        || type.getMatchingUniques(UniqueType.RegionRequirePercentTwoTypes).any {
                            region.getTerrainAmount(it.params[1]) + region.getTerrainAmount(it.params[2]) >= (it.params[0].toInt() * region.tiles.count()) / 100 }
                ) {
                    region.type = type.name
                    break
                }
            }
        }

        // Generate tile data for all tiles
        for (tile in tileMap.values) {
            val newData = MapGenTileData(tile, regions.firstOrNull { it.tiles.contains(tile) })
            newData.evaluate()
            tileData[tile.position] = newData
        }

        // Sort regions by fertility so the worse regions get to pick first
        val sortedRegions = regions.sortedBy { it.totalFertility }
        // Find a start for each region
        for (region in sortedRegions) {
            findStart(region)
        }

        val coastBiasCivs = civilizations.filter { ruleset.nations[it.civName]!!.startBias.contains("Coast") }
        val negativeBiasCivs = civilizations.filter { ruleset.nations[it.civName]!!.startBias.any { bias -> bias.equalsPlaceholderText("Avoid []") } }
                .sortedByDescending { ruleset.nations[it.civName]!!.startBias.count() } // Civs with more complex avoids go first
        val randomCivs = civilizations.filter { ruleset.nations[it.civName]!!.startBias.isEmpty() }.toMutableList() // We might fill this up as we go
        // The rest are positive bias
        val positiveBiasCivs = civilizations.filterNot { it in coastBiasCivs || it in negativeBiasCivs || it in randomCivs }
                .sortedBy { ruleset.nations[it.civName]!!.startBias.count() } // civs with only one desired region go first
        val positiveBiasFallbackCivs = ArrayList<CivilizationInfo>() // Civs who couln't get their desired region at first pass

        // First assign coast bias civs
        for (civ in coastBiasCivs) {
            // Try to find a coastal start
            var startRegion = regions.filter { tileMap[it.startPosition!!].isCoastalTile() }.randomOrNull()
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                continue
            }
            // Else adjacent to a lake
            startRegion = regions.filter { tileMap[it.startPosition!!].neighbors.any { neighbor -> neighbor.getBaseTerrain().hasUnique(UniqueType.FreshWater) } }.randomOrNull()
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                continue
            }
            // Else adjacent to a river
            startRegion = regions.filter { tileMap[it.startPosition!!].isAdjacentToRiver() }.randomOrNull()
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                continue
            }
            // Else at least close to a river ????
            startRegion = regions.filter { tileMap[it.startPosition!!].neighbors.any { neighbor -> neighbor.isAdjacentToRiver() } }.randomOrNull()
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                continue
            }
            // Else pick a random region at the end
            randomCivs.add(civ)
        }

        // Next do positive bias civs
        for (civ in positiveBiasCivs) {
            // Try to find a start that matches any of the desired regions
            val startRegion = regions.filter { it.type in ruleset.nations[civ.civName]!!.startBias }.randomOrNull()
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                continue
            } else if (ruleset.nations[civ.civName]!!.startBias.count() == 1) { // Civs with a single bias (only) get to look for a fallback region
                positiveBiasFallbackCivs.add(civ)
            } else { // Others get random starts
                randomCivs.add(civ)
            }
        }

        // Do a second pass for fallback civs, choosing the region most similar to the desired type
        for (civ in positiveBiasFallbackCivs) {
            assignCivToRegion(civ, getFallbackRegion(ruleset.nations[civ.civName]!!.startBias.first()))
        }

        // Next do negative bias ones (ie "Avoid []")
        for (civ in negativeBiasCivs) {
            // Try to find a region not of the avoided types
            val startRegion = regions.filterNot { it.type in ruleset.nations[civ.civName]!!.startBias.map {
                bias -> bias.getPlaceholderParameters()[0] } }.randomOrNull()
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                continue
            } else
                randomCivs.add(civ) // else pick a random region at the end
        }

        // Finally assign the remaining civs randomly
        for (civ in randomCivs) {
            val startRegion = regions.random()
            assignCivToRegion(civ, startRegion)
        }
    }

    private fun assignCivToRegion(civInfo: CivilizationInfo, region: Region) {
        region.tileMap.addStartingLocation(civInfo.civName, region.tileMap[region.startPosition!!])
        regions.remove(region) // This region can no longer be picked
    }

    /** Attempts to find a good start close to the center of [region]. Calls setRegionStart with the position*/
    private fun findStart(region: Region) {
        // Establish center bias rects
        val centerRect = getCentralRectangle(region.rect, 0.33f)
        val middleRect = getCentralRectangle(region.rect, 0.67f)

        // Priority: 1. Adjacent to river, 2. Adjacent to coast or fresh water, 3. Other.
        // First check center rect, then middle. Only check the outer area if no good sites found
        val riverTiles = HashSet<Vector2>()
        val wetTiles = HashSet<Vector2>()
        val dryTiles = HashSet<Vector2>()
        val fallbackTiles = HashSet<Vector2>()

        // First check center
        val centerTiles = region.tileMap.getTilesInRectangle(centerRect, evenQ = true)
        for (tile in centerTiles) {
            if (tileData[tile.position]!!.isTwoFromCoast)
                continue // Don't even consider tiles two from coast
            if (region.continentID != -1 && region.continentID != tile.getContinent())
                continue // Wrong continent
            if (tile.isLand && !tile.isImpassible()) {
                evaluateTileForStart(tile)
                if (tile.isAdjacentToRiver())
                    riverTiles.add(tile.position)
                else if (tile.isCoastalTile() || tile.isAdjacentToFreshwater)
                    wetTiles.add(tile.position)
                else
                    dryTiles.add(tile.position)
            }
        }
        // Did we find a good start position?
        for (list in sequenceOf(riverTiles, wetTiles, dryTiles)) {
            if (list.any { tileData[it]!!.isGoodStart }) {
                setRegionStart(region, list
                        .filter { tileData[it]!!.isGoodStart }.maxByOrNull { tileData[it]!!.startScore }!!)
                return
            }
            if (list.isNotEmpty()) // Save the best not-good-enough spots for later fallback
                fallbackTiles.add(list.maxByOrNull { tileData[it]!!.startScore }!!)
        }

        // Now check middle donut
        val middleDonut = region.tileMap.getTilesInRectangle(middleRect, evenQ = true).filterNot { it in centerTiles }
        riverTiles.clear()
        wetTiles.clear()
        dryTiles.clear()
        for (tile in middleDonut) {
            if (tileData[tile.position]!!.isTwoFromCoast)
                continue // Don't even consider tiles two from coast
            if (region.continentID != -1 && region.continentID != tile.getContinent())
                continue // Wrong continent
            if (tile.isLand && !tile.isImpassible()) {
                evaluateTileForStart(tile)
                if (tile.isAdjacentToRiver())
                    riverTiles.add(tile.position)
                else if (tile.isCoastalTile() || tile.isAdjacentToFreshwater)
                    wetTiles.add(tile.position)
                else
                    dryTiles.add(tile.position)
            }
        }
        // Did we find a good start position?
        for (list in sequenceOf(riverTiles, wetTiles, dryTiles)) {
            if (list.any { tileData[it]!!.isGoodStart }) {
                setRegionStart(region, list
                        .filter { tileData[it]!!.isGoodStart }.maxByOrNull { tileData[it]!!.startScore }!!)
                return
            }
            if (list.isNotEmpty()) // Save the best not-good-enough spots for later fallback
                fallbackTiles.add(list.maxByOrNull { tileData[it]!!.startScore }!!)
        }

        // Now check the outer tiles. For these we don't care about rivers, coasts etc
        val outerDonut = region.tileMap.getTilesInRectangle(region.rect, evenQ = true).filterNot { it in centerTiles || it in middleDonut}
        dryTiles.clear()
        for (tile in outerDonut) {
            if (region.continentID != -1 && region.continentID != tile.getContinent())
                continue // Wrong continent
            if (tile.isLand && !tile.isImpassible()) {
                evaluateTileForStart(tile)
                dryTiles.add(tile.position)
            }
        }
        // Were any of them good?
        if (dryTiles.any { tileData[it]!!.isGoodStart }) {
            // Find the one closest to the center
            val center = region.rect.getCenter(Vector2())
            setRegionStart(region,
                    dryTiles.filter { tileData[it]!!.isGoodStart }.minByOrNull {
                        region.tileMap.getIfTileExistsOrNull(center.x.roundToInt(), center.y.roundToInt())!!
                                .aerialDistanceTo(region.tileMap.getIfTileExistsOrNull(it.x.toInt(), it.y.toInt())!!) }!!)
            return
        }
        if (dryTiles.isNotEmpty())
            fallbackTiles.add(dryTiles.maxByOrNull { tileData[it]!!.startScore }!!)

        // Fallback time. Just pick the one with best score
        val fallbackPosition = fallbackTiles.maxByOrNull { tileData[it]!!.startScore }
        if (fallbackPosition != null) {
            setRegionStart(region, fallbackPosition)
            return
        }

        // Something went extremely wrong and there is somehow no place to start. Spawn some land and start there
        val panicPosition = region.rect.getPosition(Vector2())
        val panicTerrain = ruleset.terrains.values.first { it.type == TerrainType.Land }.name
        region.tileMap[panicPosition].baseTerrain = panicTerrain
        setRegionStart(region, panicPosition)
    }

    /** @returns the region most similar to a region of [type] */
    private fun getFallbackRegion(type: String): Region {
        return regions.maxByOrNull { it.terrainCounts[type] ?: 0 }!!
    }

    private fun setRegionStart(region: Region, position: Vector2) {
        region.startPosition = position
        setCloseStartPenalty(region.tileMap[position])
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

    private fun setCloseStartPenalty(tile: TileInfo) {
        val penaltyForRing =
                mapOf(  0 to 99, 1 to 97, 2 to 95,
                        3 to 92, 4 to 89, 5 to 69,
                        6 to 57, 7 to 24, 8 to 15 )

        for ((ring, penalty) in penaltyForRing) {
            for (outerTile in tile.getTilesAtDistance(ring).map { it.position })
                tileData[outerTile]!!.addCloseStartPenalty(penalty)
        }
    }

    /** Evaluates a tile for starting position, setting isGoodStart and startScore in
     *  MapGenTileData. Assumes that all tiles have corresponding MapGenTileData. */
    private fun evaluateTileForStart(tile: TileInfo) {
        val minimumFoodForRing = mapOf(1 to 1, 2 to 4, 3 to 4)
        val minimumProdForRing = mapOf(1 to 0, 2 to 0, 3 to 2)
        val minimumGoodForRing = mapOf(1 to 3, 2 to 6, 3 to 8)
        val maximumJunk = 9

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
                    val foodScore = listOf(0, 8, 14, 19, 22, 24, 25)[totalFood]
                    val prodScore = listOf(0, 10, 16, 20, 20, 12, 0)[totalProd]
                    totalScore += foodScore + prodScore + totalRivers
                    + (totalGood * 2) - (totalJunk * 3)
                }
                2 -> {
                    val foodScore = if (totalFood > 10) 35
                    else listOf(0, 2, 5, 10, 20, 25, 28, 30, 32, 34, 35)[totalFood]
                    val effectiveTotalProd = if (totalProd >= totalFood * 2) totalProd
                    else (totalFood + 1) / 2 // Can't use all that production without food
                    val prodScore = if (effectiveTotalProd > 5) 35
                    else listOf(0, 10, 20, 25, 30, 35)[effectiveTotalProd]
                    totalScore += foodScore + prodScore + totalRivers
                    + (totalGood * 2) - (totalJunk * 3)
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

    class Region {
        val tiles = HashSet<TileInfo>()
        val terrainCounts = HashMap<String, Int>()
        var totalFertility = 0
        var continentID = -1 // -1 meaning no particular continent
        var type = "Hybrid" // being an undefined or inderminate type
        var startPosition: Vector2? = null
        lateinit var rect: Rectangle
        lateinit var tileMap: TileMap

        var affectedByWorldWrap = false

        /** Recalculates tiles and fertility */
        fun updateTiles(trim: Boolean = true) {
            totalFertility = 0
            var minX = 99999f
            var maxX = -99999f
            var minY = 99999f
            var maxY = -99999f

            val columnHasTile = HashSet<Int>()

            tiles.clear()
            for (tile in tileMap.getTilesInRectangle(rect, evenQ = true).filter {
                continentID == -1 || it.getContinent() == continentID } ) {
                val fertility = tile.getTileFertility(continentID != -1)
                if (fertility != 0) { // If fertility is 0 this is candidate for trimming
                    tiles.add(tile)
                    totalFertility += fertility
                }

                if (affectedByWorldWrap)
                    columnHasTile.add(HexMath.hex2EvenQCoords(tile.position).x.toInt())

                if (trim) {
                    val evenQCoords = HexMath.hex2EvenQCoords(tile.position)
                    minX = min(minX, evenQCoords.x)
                    maxX = max(maxX, evenQCoords.x)
                    minY = min(minY, evenQCoords.y)
                    maxY = max(maxY, evenQCoords.y)
                }
            }

            if (trim) {
                if (affectedByWorldWrap) // Need to be more thorough with origin longitude
                    rect.x = columnHasTile.filter { !columnHasTile.contains(it - 1) }.maxOf { it }.toFloat()
                else
                    rect.x = minX // ez way for non-wrapping regions
                rect.y = minY
                rect.height = maxY - minY + 1
                if (affectedByWorldWrap && minX < rect.x) { // Thorough way
                    rect.width = columnHasTile.count().toFloat()
                } else {
                    rect.width = maxX - minX + 1 // ez way
                    affectedByWorldWrap = false // also we're not wrapping anymore
                }
            }
        }

        /** Counts the terrains in the Region for type and start determination */
        fun countTerrains() {
            // Count terrains in the region
            terrainCounts.clear()
            for (tile in tiles) {
                val terrainsToCount = if (tile.getAllTerrains().any { it.hasUnique(UniqueType.IgnoreBaseTerrainForRegion) })
                    tile.getTerrainFeatures().map { it.name }.asSequence()
                else
                    tile.getAllTerrains().map { it.name }
                for (terrain in terrainsToCount) {
                    terrainCounts[terrain] = (terrainCounts[terrain] ?: 0) + 1
                }
                if (tile.isCoastalTile())
                    terrainCounts["Coastal"] = (terrainCounts["Coastal"] ?: 0) + 1
            }
        }

        /** Returns number terrains with [name] */
        fun getTerrainAmount(name: String) = terrainCounts[name] ?: 0
    }

    // Holds a bunch of tile info that is only interesting during map gen
    class MapGenTileData(val tile: TileInfo, val region: Region?) {
        var closeStartPenalty = 0
        var isFood = false
        var isProd = false
        var isGood = false
        var isJunk = false
        var isTwoFromCoast = false

        var isGoodStart = true
        var startScore = 0

        fun addCloseStartPenalty(penalty: Int) {
            if (closeStartPenalty == 0)
                closeStartPenalty = penalty
            else {
                // Multiple overlapping values - take the higher one and add 20 %
                closeStartPenalty = max(closeStartPenalty, penalty)
                closeStartPenalty = min(97, (closeStartPenalty * 1.2f).toInt())
            }
        }

        fun evaluate() {
            // First check for tiles that are always junk
            if (tile.getAllTerrains().any { it.getMatchingUniques(UniqueType.HasQualityInRegionType)
                            .any { unique -> unique.params[0] == "Junk" && unique.params[1] == "All" } } ) {
                isJunk = true
            }
            // For the rest of qualities we need to be in a region, and not auto junk
            if (region != null && !isJunk) {
                // Check first available out of unbuildable features, then other features, then base terrain
                val terrainToCheck = if (tile.terrainFeatures.isEmpty()) tile.getBaseTerrain()
                else tile.getTerrainFeatures().firstOrNull { it.unbuildable }
                        ?: tile.getTerrainFeatures().first()

                // Add all applicable qualities
                val qualities = HashSet<String>()
                for (unique in terrainToCheck.getMatchingUniques(UniqueType.HasQualityInRegionType)) {
                    if (unique.params[1] == "All" || unique.params[1] == region.type)
                        qualities.add(unique.params[0])
                }
                for (unique in terrainToCheck.getMatchingUniques(UniqueType.HasQualityExceptInRegionType)) {
                    if (unique.params[1] != region.type)
                        qualities.add(unique.params[0])
                }
                for (quality in qualities) {
                    when (quality) {
                        "Food" -> isFood = true
                        "Good" -> isGood = true
                        "Production" -> isProd = true
                        "Junk" -> isJunk = true
                    }
                }
            }
            // Check if we are two tiles from coast (a bad starting site)
            if (!tile.isCoastalTile() && tile.neighbors.any { it.isCoastalTile() })
                isTwoFromCoast = true
        }
    }
}