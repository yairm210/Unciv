package com.unciv.logic.map.mapgenerator

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapResources
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.ui.utils.extensions.randomWeighted
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

class MapRegions (val ruleset: Ruleset){
    companion object {
        val minimumFoodForRing = mapOf(1 to 1, 2 to 4, 3 to 4)
        val minimumProdForRing = mapOf(1 to 0, 2 to 0, 3 to 2)
        val minimumGoodForRing = mapOf(1 to 3, 2 to 6, 3 to 8)
        const val maximumJunk = 9

        val firstRingFoodScores = listOf(0, 8, 14, 19, 22, 24, 25)
        val firstRingProdScores = listOf(0, 10, 16, 20, 20, 12, 0)
        val secondRingFoodScores = listOf(0, 2, 5, 10, 20, 25, 28, 30, 32, 34, 35)
        val secondRingProdScores = listOf(0, 10, 20, 25, 30, 35)

        val closeStartPenaltyForRing = mapOf(
                0 to 99, 1 to 97, 2 to 95,
                3 to 92, 4 to 89, 5 to 69,
                6 to 57, 7 to 24, 8 to 15 )

        val randomLuxuryRatios = mapOf(
                1 to listOf(1f),
                2 to listOf(0.55f, 0.44f),
                3 to listOf(0.40f, 0.33f, 0.27f),
                4 to listOf(0.35f, 0.25f, 0.25f, 0.15f),
                5 to listOf(0.25f, 0.25f, 0.20f, 0.15f, 0.15f),
                6 to listOf(0.20f, 0.20f, 0.20f, 0.15f, 0.15f, 0.10f),
                7 to listOf(0.20f, 0.20f, 0.15f, 0.15f, 0.10f, 0.10f, 0.10f),
                8 to listOf(0.20f, 0.15f, 0.15f, 0.10f, 0.10f, 0.10f, 0.10f, 0.10f)
        )

        // This number is 23 in G&K, but there's a bug where hills are exempt so this number brings
        // the result closer to the density and distribution that was probably intended.
        const val baseMinorDepositFrequency = 30

    }

    private val regions = ArrayList<Region>()
    private var usingArchipelagoRegions = false
    private val tileData = HashMap<Vector2, MapGenTileData>()
    private val cityStateLuxuries = ArrayList<String>()
    private val randomLuxuries = ArrayList<String>()

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
        // A huge box including the entire map.
        val mapRect = Rectangle(-radius, -radius, radius * 2 + 1, radius * 2 + 1)

        // Lots of small islands - just split ut the map in rectangles while ignoring Continents
        // 25% is chosen as limit so Four Corners maps don't fall in this category
        if (largestContinent / totalLand < 0.25f) {
            usingArchipelagoRegions = true
            // Make a huge rectangle covering the entire map
            val hugeRect = Region(tileMap, mapRect, -1) // -1 meaning ignore continent data
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
            val continentRegion = Region(tileMap, Rectangle(mapRect), continent)
            val cols = continentIsAtCol[continent]!!
            // Set origin at the rightmost column which does not have a neighbor on the left
            continentRegion.rect.x = cols.filter { !cols.contains(it - 1) }.maxOf { it }.toFloat()
            continentRegion.rect.width = cols.size.toFloat()
            if (tileMap.mapParameters.worldWrap) {
                // Check if the continent is wrapping - if the leftmost col is not the one we set origin by
                if (cols.minOf { it } < continentRegion.rect.x)
                    continentRegion.affectedByWorldWrap = true
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

        val splitOffRegion = Region(regionToSplit.tileMap, Rectangle(regionToSplit.rect), regionToSplit.continentID)

        val widerThanTall = regionToSplit.rect.width > regionToSplit.rect.height

        var bestSplitPoint = 1 // will be the size of the split-off region
        var closestFertility = 0
        var cumulativeFertility = 0

        val highestPointToTry = if (widerThanTall) regionToSplit.rect.width.toInt()
        else regionToSplit.rect.height.toInt()
        val pointsToTry = 1..highestPointToTry
        val halfwayPoint = highestPointToTry/2

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
            val bestSplitPointFertilityDeltaFromTarget = abs(closestFertility - targetFertility)
            val currentSplitPointFertilityDeltaFromTarget = abs(cumulativeFertility - targetFertility)
            if (currentSplitPointFertilityDeltaFromTarget < bestSplitPointFertilityDeltaFromTarget
                || (currentSplitPointFertilityDeltaFromTarget == bestSplitPointFertilityDeltaFromTarget // same fertility split but better 'amount of tiles' split
                        && abs(halfwayPoint- splitPoint) < abs(halfwayPoint- bestSplitPoint) )) { // current split point is closer to the halfway point
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

    fun assignRegions(tileMap: TileMap, civilizations: List<CivilizationInfo>, gameParameters: GameParameters) {
        if (civilizations.isEmpty()) return

        // first assign region types
        val regionTypes = ruleset.terrains.values.filter { getRegionPriority(it) != null }
                .sortedBy { getRegionPriority(it) }

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
                            region.getTerrainAmount(it.params[1]) >= (it.params[0].toInt() * region.tiles.size) / 100 }
                        || type.getMatchingUniques(UniqueType.RegionRequirePercentTwoTypes).any {
                            region.getTerrainAmount(it.params[1]) + region.getTerrainAmount(it.params[2]) >= (it.params[0].toInt() * region.tiles.size) / 100 }
                ) {
                    region.type = type.name
                    break
                }
            }
        }

        // Generate tile data for all tiles
        for (tile in tileMap.values) {
            val newData = MapGenTileData(tile, regions.firstOrNull { it.tiles.contains(tile) })
            newData.evaluate(ruleset)
            tileData[tile.position] = newData
        }

        // Sort regions by fertility so the worse regions get to pick first
        val sortedRegions = regions.sortedBy { it.totalFertility }
        // Find a start for each region
        for (region in sortedRegions) {
            findStart(region)
        }
        // Normalize starts
        for (region in regions) {
            normalizeStart(tileMap[region.startPosition!!], tileMap, minorCiv = false)
        }

        val coastBiasCivs = civilizations.filter { ruleset.nations[it.civName]!!.startBias.contains("Coast") }
        val negativeBiasCivs = civilizations.filter { ruleset.nations[it.civName]!!.startBias.any { bias -> bias.equalsPlaceholderText("Avoid []") } }
                .sortedByDescending { ruleset.nations[it.civName]!!.startBias.size } // Civs with more complex avoids go first
        val randomCivs = civilizations.filter { ruleset.nations[it.civName]!!.startBias.isEmpty() }.toMutableList() // We might fill this up as we go
        // The rest are positive bias
        val positiveBiasCivs = civilizations.filterNot { it in coastBiasCivs || it in negativeBiasCivs || it in randomCivs }
                .sortedBy { ruleset.nations[it.civName]!!.startBias.size } // civs with only one desired region go first
        val positiveBiasFallbackCivs = ArrayList<CivilizationInfo>() // Civs who couln't get their desired region at first pass
        val unpickedRegions = regions.toMutableList()

        // First assign coast bias civs
        for (civ in coastBiasCivs) {
            // If noStartBias is enabled consider these to be randomCivs
            if (gameParameters.noStartBias) {
                randomCivs.addAll(coastBiasCivs)
                break
            }

            // Try to find a coastal start, preferably a really coastal one
            var startRegion = unpickedRegions.filter { tileMap[it.startPosition!!].isCoastalTile() }
                    .maxByOrNull { it.terrainCounts["Coastal"] ?: 0 }
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            }
            // Else adjacent to a lake
            startRegion = unpickedRegions.filter { tileMap[it.startPosition!!].neighbors.any { neighbor -> neighbor.getBaseTerrain().hasUnique(UniqueType.FreshWater) } }
                    .maxByOrNull { it.terrainCounts["Coastal"] ?: 0 }
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            }
            // Else adjacent to a river
            startRegion = unpickedRegions.filter { tileMap[it.startPosition!!].isAdjacentToRiver() }
                    .maxByOrNull { it.terrainCounts["Coastal"] ?: 0 }
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            }
            // Else at least close to a river ????
            startRegion = unpickedRegions.filter { tileMap[it.startPosition!!].neighbors.any { neighbor -> neighbor.isAdjacentToRiver() } }
                    .maxByOrNull { it.terrainCounts["Coastal"] ?: 0 }
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            }
            // Else pick a random region at the end
            randomCivs.add(civ)
        }

        // Next do positive bias civs
        for (civ in positiveBiasCivs) {
            // If noStartBias is enabled consider these to be randomCivs
            if (gameParameters.noStartBias) {
                randomCivs.addAll(positiveBiasCivs)
                break
            }

            // Try to find a start that matches any of the desired regions, ideally with lots of desired terrain
            val preferred = ruleset.nations[civ.civName]!!.startBias
            val startRegion = unpickedRegions.filter { it.type in preferred }
                    .maxByOrNull { it.terrainCounts.filterKeys { terrain -> terrain in preferred }.values.sum() }
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            } else if (ruleset.nations[civ.civName]!!.startBias.size == 1) { // Civs with a single bias (only) get to look for a fallback region
                positiveBiasFallbackCivs.add(civ)
            } else { // Others get random starts
                randomCivs.add(civ)
            }
        }

        // Do a second pass for fallback civs, choosing the region most similar to the desired type
        for (civ in positiveBiasFallbackCivs) {
            val startRegion = getFallbackRegion(ruleset.nations[civ.civName]!!.startBias.first(), unpickedRegions)
            assignCivToRegion(civ, startRegion)
            unpickedRegions.remove(startRegion)
        }

        // Next do negative bias ones (ie "Avoid []")
        for (civ in negativeBiasCivs) {
            // If noStartBias is enabled consider these to be randomCivs
            if (gameParameters.noStartBias) {
                randomCivs.addAll(negativeBiasCivs)
                break
            }

            val avoided = ruleset.nations[civ.civName]!!.startBias.map { it.getPlaceholderParameters()[0] }
            // Try to find a region not of the avoided types, secondary sort by least number of undesired terrains
            val startRegion = unpickedRegions.filterNot { it.type in avoided }
                    .minByOrNull { it.terrainCounts.filterKeys { terrain -> terrain in avoided }.values.sum() }
            if (startRegion != null) {
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            } else
                randomCivs.add(civ) // else pick a random region at the end
        }

        // Finally assign the remaining civs randomly
        for (civ in randomCivs) {
            val startRegion = unpickedRegions.random()
            assignCivToRegion(civ, startRegion)
            unpickedRegions.remove(startRegion)
        }
    }

    private fun getRegionPriority(terrain: Terrain?): Int? {
        if (terrain == null) // ie "hybrid"
            return 99999 // a big number
        return if (!terrain.hasUnique(UniqueType.RegionRequirePercentSingleType)
                   && !terrain.hasUnique(UniqueType.RegionRequirePercentTwoTypes))
            null
        else
            if (terrain.hasUnique(UniqueType.RegionRequirePercentSingleType))
                terrain.getMatchingUniques(UniqueType.RegionRequirePercentSingleType).first().params[2].toInt()
            else
                terrain.getMatchingUniques(UniqueType.RegionRequirePercentTwoTypes).first().params[3].toInt()
    }

    private fun assignCivToRegion(civInfo: CivilizationInfo, region: Region) {
        val tile = region.tileMap[region.startPosition!!]
        region.tileMap.addStartingLocation(civInfo.civName, tile)

        // Place impacts to keep city states etc at appropriate distance
        placeImpact(ImpactType.MinorCiv,tile, 6)
        placeImpact(ImpactType.Luxury,  tile, 3)
        placeImpact(ImpactType.Strategic,tile, 0)
        placeImpact(ImpactType.Bonus,   tile, 3)
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
                        (region.tileMap.getIfTileExistsOrNull(center.x.roundToInt(), center.y.roundToInt()) ?: region.tileMap.values.first())
                                .aerialDistanceTo(
                                        region.tileMap.getIfTileExistsOrNull(it.x.toInt(), it.y.toInt()) ?: region.tileMap.values.first()
                                ) }!!)
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
        region.tileMap[panicPosition].setTerrainFeatures(listOf())
        setRegionStart(region, panicPosition)
    }

    /** Attempts to improve the start on [startTile] as needed to make it decent.
     *  Relies on startPosition having been set previously.
     *  Assumes unchanged baseline values ie citizens eat 2 food each, similar production costs
     *  If [minorCiv] is true, different weightings will be used. */
    private fun normalizeStart(startTile: TileInfo, tileMap: TileMap, minorCiv: Boolean) {
        // Remove ice-like features adjacent to start
        for (tile in startTile.neighbors) {
            val lastTerrain = tile.terrainFeatureObjects.lastOrNull { it.impassable }
            if (lastTerrain != null) {
                tile.removeTerrainFeature(lastTerrain.name)
            }
        }

        // evaluate production potential
        val innerProduction = startTile.neighbors.sumOf { getPotentialYield(it, Stat.Production).toInt() }
        val outerProduction = startTile.getTilesAtDistance(2).sumOf { getPotentialYield(it, Stat.Production).toInt() }
        // for very early production we ideally want tiles that also give food
        val earlyProduction = startTile.getTilesInDistanceRange(1..2).sumOf {
            if (getPotentialYield(it, Stat.Food, unimproved = true) > 0f) getPotentialYield(it, Stat.Production, unimproved = true).toInt()
                else 0 }

        // If terrible, try adding a hill to a dry flat tile
        if (innerProduction == 0 || (innerProduction < 2 && outerProduction < 8) || (minorCiv && innerProduction < 4)) {
            val hillSpot = startTile.neighbors
                    .filter { it.isLand && it.terrainFeatures.isEmpty() && !it.isAdjacentTo(Constants.freshWater) && !it.isImpassible() }
                    .toList().randomOrNull()
            val hillEquivalent = ruleset.terrains.values
                    .firstOrNull { it.type == TerrainType.TerrainFeature && it.production >= 2 && !it.hasUnique(UniqueType.RareFeature) }?.name
            if (hillSpot != null && hillEquivalent != null) {
                hillSpot.addTerrainFeature(hillEquivalent)
            }
        }

        // Place Strategic Balance Resources
        if (tileMap.mapParameters.mapResources == MapResources.strategicBalance) {
            val candidateTiles = startTile.getTilesInDistanceRange(1..2).shuffled() + startTile.getTilesAtDistance(3).shuffled()
            for (resource in ruleset.tileResources.values.filter { it.hasUnique(UniqueType.StrategicBalanceResource) }) {
                if (tryAddingResourceToTiles(resource, 1, candidateTiles, majorDeposit = true) == 0) {
                    // Fallback mode - force placement, even on an otherwise inappropriate terrain. Do still respect water and impassible tiles!
                    if (isWaterOnlyResource(resource))
                        placeResourcesInTiles(999, candidateTiles.filter { it.isWater && !it.isImpassible() }.toList(), listOf(resource), majorDeposit = true, forcePlacement = true)
                    else
                        placeResourcesInTiles(999, candidateTiles.filter { it.isLand && !it.isImpassible() }.toList(), listOf(resource), majorDeposit = true, forcePlacement = true)

                }
            }
        }

        // If bad early production, add a small strategic resource to SECOND ring (not for minors)
        if (!minorCiv && innerProduction < 3 && earlyProduction < 6) {
            val lastEraNumber = ruleset.eras.values.maxOf { it.eraNumber }
            val earlyEras = ruleset.eras.filterValues { it.eraNumber <= lastEraNumber / 3 }
            val validResources = ruleset.tileResources.values.filter {
                it.resourceType == ResourceType.Strategic &&
                        (it.revealedBy == null ||
                        ruleset.technologies[it.revealedBy]!!.era() in earlyEras)
            }.shuffled()
            val candidateTiles = startTile.getTilesAtDistance(2).shuffled()
            for (resource in validResources) {
                if (tryAddingResourceToTiles(resource, 1, candidateTiles, majorDeposit = false) > 0)
                    break
            }
        }

        // Now evaluate food situation
        // Food²/4 because excess food is really good and lets us work other tiles or run specialists!
        // 2F is worth 1, 3F is worth 2, 4F is worth 4, 5F is worth 6 and so on
        val innerFood = startTile.neighbors.sumOf { (getPotentialYield(it, Stat.Food).pow(2) / 4).toInt() }
        val outerFood = startTile.getTilesAtDistance(2).sumOf { (getPotentialYield(it, Stat.Food).pow(2) / 4).toInt() }
        val totalFood = innerFood + outerFood
        // we want at least some two-food tiles to keep growing
        val innerNativeTwoFood = startTile.neighbors.count { getPotentialYield(it, Stat.Food, unimproved = true) >= 2f }
        val outerNativeTwoFood = startTile.getTilesAtDistance(2).count { getPotentialYield(it, Stat.Food, unimproved = true) >= 2f }
        val totalNativeTwoFood = innerNativeTwoFood + outerNativeTwoFood

        // Determine number of needed bonuses. Different weightings for minor and major civs.
        var bonusesNeeded = if (minorCiv) {
            when { // From 2 to 0
                totalFood < 12 || innerFood < 4 -> 2
                totalFood < 16 || innerFood < 9 -> 1
                else -> 0
            }
        } else {
            when { // From 5 to 0
                innerFood == 0 && totalFood < 4 -> 5
                totalFood < 6 -> 4
                totalFood < 8 ||
                        (totalFood < 12 && innerFood < 5) -> 3
                (totalFood < 17 && innerFood < 9) ||
                        totalNativeTwoFood < 2 -> 2
                (totalFood < 24 && innerFood < 11) ||
                        totalNativeTwoFood == 2 ||
                        innerNativeTwoFood == 0 ||
                        totalFood < 20 -> 1
                else -> 0
            }
        }
        if (tileMap.mapParameters.mapResources == MapResources.legendaryStart)
            bonusesNeeded += 2

        // Attempt to place one grassland at a plains-only spot (nor for minors)
        if (!minorCiv && bonusesNeeded < 3 && totalNativeTwoFood == 0) {
            val twoFoodTerrain = ruleset.terrains.values.firstOrNull { it.type == TerrainType.Land && it.food >= 2 }?.name
            val candidateInnerSpots = startTile.neighbors
                    .filter { it.isLand && !it.isImpassible() && it.terrainFeatures.isEmpty() && it.resource == null }
            val candidateOuterSpots = startTile.getTilesAtDistance(2)
                    .filter { it.isLand && !it.isImpassible() && it.terrainFeatures.isEmpty() && it.resource == null }
            val spot = candidateInnerSpots.shuffled().firstOrNull() ?: candidateOuterSpots.shuffled().firstOrNull()
            if (twoFoodTerrain != null && spot != null) {
                spot.baseTerrain = twoFoodTerrain
            } else
                bonusesNeeded = 3 // Irredeemable plains situation
        }

        val oasisEquivalent = ruleset.terrains.values.firstOrNull {
                it.type == TerrainType.TerrainFeature &&
                it.hasUnique(UniqueType.RareFeature)  &&
                it.food >= 2 &&
                it.food + it.production + it.gold >= 3 &&
                it.occursOn.any { base -> ruleset.terrains[base]!!.type == TerrainType.Land }
        }
        var canPlaceOasis = oasisEquivalent != null // One oasis per start is enough. Don't bother finding a place if there is no good oasis equivalent
        var placedInFirst = 0 // Attempt to put first 2 in inner ring and next 3 in second ring
        var placedInSecond = 0
        val rangeForBonuses = if (minorCiv) 2 else 3

        // Start with list of candidate plots sorted in ring order 1,2,3
        val candidatePlots = startTile.getTilesInDistanceRange(1..rangeForBonuses)
                .filter { it.resource == null && oasisEquivalent !in it.terrainFeatureObjects }
                .shuffled().sortedBy { it.aerialDistanceTo(startTile) }.toMutableList()

        // Place food bonuses (and oases) as able
        while (bonusesNeeded > 0 && candidatePlots.isNotEmpty()) {
            val plot = candidatePlots.first()
            candidatePlots.remove(plot) // remove the plot as it has now been tried, whether successfully or not
            if (plot.getBaseTerrain().hasUnique(UniqueType.BlocksResources, StateForConditionals(attackedTile = plot)))
                continue // Don't put bonuses on snow hills

            val validBonuses = ruleset.tileResources.values.filter {
                it.resourceType == ResourceType.Bonus &&
                it.food >= 1 &&
                plot.getLastTerrain().name in it.terrainsCanBeFoundOn
            }
            val goodPlotForOasis = canPlaceOasis && plot.getLastTerrain().name in oasisEquivalent!!.occursOn

            if (validBonuses.isNotEmpty() || goodPlotForOasis) {
                if (goodPlotForOasis) {
                    plot.addTerrainFeature(oasisEquivalent!!.name)
                    canPlaceOasis = false
                } else {
                    plot.setTileResource(validBonuses.random())
                }

                if (plot.aerialDistanceTo(startTile) == 1) {
                    placedInFirst++
                    if (placedInFirst == 2) // Resort the list in ring order 2,3,1
                        candidatePlots.sortBy { abs(it.aerialDistanceTo(startTile) * 10 - 22 ) }
                } else if (plot.aerialDistanceTo(startTile) == 2) {
                    placedInSecond++
                    if (placedInSecond == 3) // Resort the list in ring order 3,1,2
                        candidatePlots.sortByDescending { abs(it.aerialDistanceTo(startTile) * 10 - 17) }
                }
                bonusesNeeded--
            }
        }

        // Minor civs are done, go on with grassiness checks for major civs
        if (minorCiv) return

        // Check for very grass-heavy starts that might still need some stone to help with production
        val grassTypePlots = startTile.getTilesInDistanceRange(1..2).filter {
            it.isLand &&
            getPotentialYield(it, Stat.Food, unimproved = true) >= 2f && // Food neutral natively
            getPotentialYield(it, Stat.Production) == 0f // Production can't even be improved
        }.toMutableList()
        val plainsTypePlots = startTile.getTilesInDistanceRange(1..2).filter {
            it.isLand &&
            getPotentialYield(it, Stat.Food) >= 2f && // Something that can be improved to food neutral
            getPotentialYield(it, Stat.Production, unimproved = true) >= 1f // Some production natively
        }.toList()
        var stoneNeeded = when {
            grassTypePlots.size >= 9 && plainsTypePlots.isEmpty() -> 2
            grassTypePlots.size >= 6 && plainsTypePlots.size <= 4 -> 1
            else -> 0
        }
        val stoneTypeBonuses = ruleset.tileResources.values.filter { it.resourceType == ResourceType.Bonus && it.production > 0 }

        if(stoneTypeBonuses.isNotEmpty()) {
            while (stoneNeeded > 0 && grassTypePlots.isNotEmpty()) {
                val plot = grassTypePlots.random()
                grassTypePlots.remove(plot)

                if (plot.resource != null) continue

                val bonusToPlace = stoneTypeBonuses.filter { plot.getLastTerrain().name in it.terrainsCanBeFoundOn }.randomOrNull()
                if (bonusToPlace != null) {
                    plot.resource = bonusToPlace.name
                    stoneNeeded--
                }
            }
        }
    }

    private fun getPotentialYield(tile: TileInfo, stat: Stat, unimproved: Boolean = false): Float {
        val baseYield = tile.getTileStats(null)[stat]
        if (unimproved) return baseYield

        val bestImprovementYield = tile.tileMap.ruleset!!.tileImprovements.values
                .filter { !it.hasUnique(UniqueType.GreatImprovement) &&
                        it.uniqueTo == null &&
                        tile.getLastTerrain().name in it.terrainsCanBeBuiltOn }
                .maxOfOrNull { it[stat] }
        return baseYield + (bestImprovementYield ?: 0f)
    }

    /** @returns the region most similar to a region of [type] */
    private fun getFallbackRegion(type: String, candidates: List<Region>): Region {
        return candidates.maxByOrNull { it.terrainCounts[type] ?: 0 }!!
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
        for ((ring, penalty) in closeStartPenaltyForRing) {
            for (outerTile in tile.getTilesAtDistance(ring).map { it.position })
                tileData[outerTile]!!.addCloseStartPenalty(penalty)
        }
    }

    /** Evaluates a tile for starting position, setting isGoodStart and startScore in
     *  MapGenTileData. Assumes that all tiles have corresponding MapGenTileData. */
    private fun evaluateTileForStart(tile: TileInfo) {
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
                    totalScore += foodScore + prodScore + totalRivers
                    + (totalGood * 2) - (totalJunk * 3)
                }
                2 -> {
                    val foodScore = if (totalFood > 10) secondRingFoodScores.last()
                    else secondRingFoodScores[totalFood]
                    val effectiveTotalProd = if (totalProd >= totalFood * 2) totalProd
                    else (totalFood + 1) / 2 // Can't use all that production without food
                    val prodScore = if (effectiveTotalProd > 5) secondRingProdScores.last()
                    else secondRingProdScores[effectiveTotalProd]
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

    fun placeResourcesAndMinorCivs(tileMap: TileMap, minorCivs: List<CivilizationInfo>) {
        placeNaturalWonderImpacts(tileMap)
        assignLuxuries()
        placeMinorCivs(tileMap, minorCivs)
        placeLuxuries(tileMap)
        placeStrategicAndBonuses(tileMap)
    }

    /** Places impacts from NWs that have been generated just prior to this step. */
    private fun placeNaturalWonderImpacts(tileMap: TileMap) {
        for (tile in tileMap.values.filter { it.isNaturalWonder() }) {
            placeImpact(ImpactType.Bonus, tile, 1)
            placeImpact(ImpactType.Strategic, tile, 1)
            placeImpact(ImpactType.Luxury, tile, 1)
            placeImpact(ImpactType.MinorCiv, tile, 1)
        }
    }

    /** Assigns a luxury to each region. No luxury can be assigned to too many regions.
     *  Some luxuries are earmarked for city states. The rest are randomly distributed or
     *  don't occur att all in the map */
    private fun assignLuxuries() {
        // If there are any weightings defined in json, assume they are complete. If there are none, use flat weightings instead
        val fallbackWeightings = ruleset.tileResources.values.none {
            it.resourceType == ResourceType.Luxury &&
                (it.uniqueObjects.any { unique -> unique.isOfType(UniqueType.ResourceWeighting) } || it.hasUnique(UniqueType.LuxuryWeightingForCityStates)) }

        val maxRegionsWithLuxury = when {
            regions.size > 12 -> 3
            regions.size > 8 -> 2
            else -> 1
        }
        val targetCityStateLuxuries = 3 // was probably intended to be "if (tileData.size > 5000) 4 else 3"
        val disabledPercent = 100 - min(tileData.size.toFloat().pow(0.2f) * 16, 100f).toInt() // Approximately
        val targetDisabledLuxuries = (ruleset.tileResources.values
                .count { it.resourceType == ResourceType.Luxury } * disabledPercent) / 100
        val assignableLuxuries = ruleset.tileResources.values.filter {
            it.resourceType == ResourceType.Luxury &&
            !it.hasUnique(UniqueType.LuxurySpecialPlacement) &&
            !it.hasUnique(UniqueType.CityStateOnlyResource) }
        val amountRegionsWithLuxury = HashMap<String, Int>()
        // Init map
        ruleset.tileResources.values
                .forEach { amountRegionsWithLuxury[it.name] = 0 }

        for (region in regions.sortedBy { getRegionPriority(ruleset.terrains[it.type]) } ) {
            val regionConditional = StateForConditionals(region = region)
            var candidateLuxuries = assignableLuxuries.filter {
                amountRegionsWithLuxury[it.name]!! < maxRegionsWithLuxury &&
                // Check that it has a weight for this region type
                (fallbackWeightings ||
                    it.hasUnique(UniqueType.ResourceWeighting, regionConditional)) &&
                // Check that there is enough coast if it is a water based resource
                ((region.terrainCounts["Coastal"] ?: 0) >= 12 ||
                    it.terrainsCanBeFoundOn.any { terrain -> ruleset.terrains[terrain]!!.type != TerrainType.Water } )
            }

            // If we couldn't find any options, pick from all luxuries. First try to not pick water luxuries on land regions
            if (candidateLuxuries.isEmpty()) {
                candidateLuxuries = assignableLuxuries.filter {
                    amountRegionsWithLuxury[it.name]!! < maxRegionsWithLuxury &&
                    // Ignore weightings for this pass
                    // Check that there is enough coast if it is a water based resource
                    ((region.terrainCounts["Coastal"] ?: 0) >= 12 ||
                            it.terrainsCanBeFoundOn.any { terrain -> ruleset.terrains[terrain]!!.type != TerrainType.Water })
                }
            }
            // If there are still no candidates, ignore water restrictions
            if (candidateLuxuries.isEmpty()) {
                candidateLuxuries = assignableLuxuries.filter {
                    amountRegionsWithLuxury[it.name]!! < maxRegionsWithLuxury
                    // Ignore weightings and water for this pass
                }
            }
            // If there are still no candidates (mad modders???) just skip this region
            if (candidateLuxuries.isEmpty()) continue

            // Pick a luxury at random. Weight is reduced if the luxury has been picked before
            val modifiedWeights = candidateLuxuries.map {
                val weightingUnique = it.getMatchingUniques(UniqueType.ResourceWeighting, regionConditional).firstOrNull()
                val relativeWeight = if (weightingUnique == null) 1f else weightingUnique.params[0].toFloat()
                relativeWeight / (1f + amountRegionsWithLuxury[it.name]!!)
            }.shuffled()
            region.luxury = candidateLuxuries.randomWeighted(modifiedWeights).name
            amountRegionsWithLuxury[region.luxury!!] = amountRegionsWithLuxury[region.luxury]!! + 1
        }

        // Assign luxuries to City States
        for (i in 1..targetCityStateLuxuries) {
            val candidateLuxuries = assignableLuxuries.filter {
                amountRegionsWithLuxury[it.name] == 0 &&
                (fallbackWeightings || it.hasUnique(UniqueType.LuxuryWeightingForCityStates))
            }
            if (candidateLuxuries.isEmpty()) continue

            val weights = candidateLuxuries.map {
                val weightingUnique = it.getMatchingUniques(UniqueType.LuxuryWeightingForCityStates).firstOrNull()
                if (weightingUnique == null)
                    1f
                else
                    weightingUnique.params[0].toFloat()
            }
            val luxury = candidateLuxuries.randomWeighted(weights).name
            cityStateLuxuries.add(luxury)
            amountRegionsWithLuxury[luxury] = 1
        }

        // Assign some resources as random placement.
        val remainingLuxuries = assignableLuxuries.filter {
            amountRegionsWithLuxury[it.name] == 0
        }.map { it.name }.shuffled()
        randomLuxuries.addAll(remainingLuxuries.drop(targetDisabledLuxuries))
    }

    /** Assigns [civs] to regions or "uninhabited" land and places them. Depends on
     *  assignLuxuries having been called previously.
     *  Note: can silently fail to place all city states if there is too little room.
     *  Currently our GameStarter fills out with random city states, Civ V behavior is to
     *  forget about the discarded city states entirely. */
    private fun placeMinorCivs(tileMap: TileMap, civs: List<CivilizationInfo>) {
        if (civs.isEmpty()) return

        // Some but not all city states are assigned to regions directly. Determine the CS density.
        val minorCivRatio = civs.size.toFloat() / regions.size
        val minorCivPerRegion = when {
            minorCivRatio > 14f     -> 10 // lol
            minorCivRatio > 11f     -> 8
            minorCivRatio > 8f      -> 6
            minorCivRatio > 5.7f    -> 4
            minorCivRatio > 4.35f   -> 3
            minorCivRatio > 2.7f    -> 2
            minorCivRatio > 1.35f   -> 1
            else                    -> 0
        }
        val unassignedCivs = civs.shuffled().toMutableList()
        if (minorCivPerRegion > 0) {
            regions.forEach {
                val civsToAssign = unassignedCivs.take(minorCivPerRegion)
                it.assignedMinorCivs.addAll(civsToAssign)
                unassignedCivs.removeAll(civsToAssign)
            }
        }
        // Some city states are assigned to "uninhabited" continents - unless it's an archipelago type map
        // (Because then every continent will have been assigned to a region anyway)
        val uninhabitedCoastal = ArrayList<TileInfo>()
        val uninhabitedHinterland = ArrayList<TileInfo>()
        val uninhabitedContinents = tileMap.continentSizes.filter {
            it.value >= 4 && // Don't bother with tiny islands
            regions.none { region -> region.continentID == it.key }
        }.keys
        val civAssignedToUninhabited = ArrayList<CivilizationInfo>()
        var numUninhabitedTiles = 0
        var numInhabitedTiles = 0
        if (!usingArchipelagoRegions) {
            // Go through the entire map to build the data
            for (tile in tileMap.values) {
                if (!canPlaceMinorCiv(tile)) continue
                val continent = tile.getContinent()
                if (continent in uninhabitedContinents) {
                    if(tile.isCoastalTile())
                        uninhabitedCoastal.add(tile)
                    else
                        uninhabitedHinterland.add(tile)
                    numUninhabitedTiles++
                } else
                    numInhabitedTiles++
            }
            // Determine how many minor civs to put on uninhabited continents.
            val maxByUninhabited = (3 * civs.size * numUninhabitedTiles) / (numInhabitedTiles + numUninhabitedTiles)
            val maxByRatio = (civs.size + 1) / 2
            val targetForUninhabited = min(maxByRatio, maxByUninhabited)
            val civsToAssign = unassignedCivs.take(targetForUninhabited)
            unassignedCivs.removeAll(civsToAssign)
            civAssignedToUninhabited.addAll(civsToAssign)
        }

        // If there are still unassigned minor civs, assign extra ones to regions that share their
        // luxury type with two others, as compensation. Because starting close to a city state is good??
        if (unassignedCivs.isNotEmpty()) {
            val regionsWithCommonLuxuries = regions.filter {
                regions.count { other -> other.luxury == it.luxury } >= 3
            }
            // assign one civ each to regions with common luxuries if there are enough to go around
            if (regionsWithCommonLuxuries.isNotEmpty() &&
                            regionsWithCommonLuxuries.size <= unassignedCivs.size
            ) {
                regionsWithCommonLuxuries.forEach {
                    val civToAssign = unassignedCivs.first()
                    unassignedCivs.remove(civToAssign)
                    it.assignedMinorCivs.add(civToAssign)
                }
            }
        }
        // Still unassigned civs??
        if (unassignedCivs.isNotEmpty()) {
            // Add one extra to each region as long as there are enough to go around
            while (unassignedCivs.size >= regions.size) {
                regions.forEach {
                    val civToAssign = unassignedCivs.first()
                    unassignedCivs.remove(civToAssign)
                    it.assignedMinorCivs.add(civToAssign)
                }
            }

            // STILL unassigned civs??
            if (unassignedCivs.isNotEmpty()) {
                // At this point there is at least for sure less remaining city states than regions
                // Sort regions by fertility and put extra city states in the worst ones.
                val worstRegions = regions.sortedBy { it.totalFertility }.take(unassignedCivs.size)
                worstRegions.forEach {
                    val civToAssign = unassignedCivs.first()
                    unassignedCivs.remove(civToAssign)
                    it.assignedMinorCivs.add(civToAssign)
                }
            }
        }

        // All minor civs are assigned - now place them
        // First place the "uninhabited continent" ones, preferring coastal starts
        tryPlaceMinorCivsInTiles(civAssignedToUninhabited, tileMap, uninhabitedCoastal)
        tryPlaceMinorCivsInTiles(civAssignedToUninhabited, tileMap, uninhabitedHinterland)
        // Fallback to a random region for civs that couldn't be placed in the wilderness
        for (unplacedCiv in civAssignedToUninhabited) {
            regions.random().assignedMinorCivs.add(unplacedCiv)
        }
        // Fallback lists for minor civs that can't be placed with any other method
        val fallbackTiles = ArrayList<TileInfo>()
        val fallbackMinors = ArrayList<CivilizationInfo>()

        // Now place the ones assigned to specific regions.
        for (region in regions) {
            // Check the outer edges of the region, working inwards
            val section = Rectangle(region.rect)
            val unprocessedTiles = ArrayList<TileInfo>()
            val regionCoastal = ArrayList<TileInfo>()
            val regionHinterland = ArrayList<TileInfo>()
            while (section.width >= 4 && section.height >= 4 && region.assignedMinorCivs.isNotEmpty()) {
                // Clear the tile lists
                unprocessedTiles.clear()
                regionCoastal.clear()
                regionHinterland.clear()
                if (section.height > section.width) {
                    // Check top and bottom
                    unprocessedTiles.addAll(
                            tileMap.getTilesInRectangle(
                                    Rectangle(section.x, section.y, section.width, 1f),
                                    evenQ = true)
                    )
                    unprocessedTiles.addAll(
                            tileMap.getTilesInRectangle(
                                    Rectangle(section.x, section.y + section.height - 1, section.width, 1f),
                                    evenQ = true)
                    )
                    // Narrow the remaining section
                    section.y += 1
                    section.height -= 2
                } else {
                    // Check left and right
                    unprocessedTiles.addAll(
                            tileMap.getTilesInRectangle(
                                    Rectangle(section.x, section.y, 1f, section.height),
                                    evenQ = true)
                    )
                    unprocessedTiles.addAll(
                            tileMap.getTilesInRectangle(
                                    Rectangle(section.x + section.width - 1, section.y, 1f, section.height),
                                    evenQ = true)
                    )
                    // Narrow the remaining section
                    section.x += 1
                    section.width -= 2
                }
                // Now process the tiles
                for (tile in unprocessedTiles) {
                    if (!canPlaceMinorCiv(tile)) continue
                    if (!usingArchipelagoRegions && tile.getContinent() != region.continentID) continue
                    if(tile.isCoastalTile())
                        regionCoastal.add(tile)
                    else
                        regionHinterland.add(tile)
                }
                // Now attempt to place as many minor civs as possible, trying coastal tiles first
                tryPlaceMinorCivsInTiles(region.assignedMinorCivs, tileMap, regionCoastal)
                tryPlaceMinorCivsInTiles(region.assignedMinorCivs, tileMap, regionHinterland)
            }
            // In case we went through the entire region without finding spots for all assigned civs
            if(region.assignedMinorCivs.isNotEmpty()) {
                fallbackMinors.addAll(region.assignedMinorCivs)
            } else {
                // If we did find spots for all civs, there might be more eligible tiles left in the region
                // Add them to the fallback list
                fallbackTiles.addAll(regionCoastal)
                fallbackTiles.addAll(regionHinterland)
                fallbackTiles.addAll(tileMap.getTilesInRectangle(section, evenQ = true)
                        .filter { canPlaceMinorCiv(it) }
                )
            }
        }

        // Finally attempt to place the fallback lists - the rest will be silently discarded
        if (fallbackMinors.isNotEmpty()) {
            // Throw in the uninhabited lists as well
            fallbackTiles.addAll(uninhabitedCoastal)
            fallbackTiles.addAll(uninhabitedHinterland)
            tryPlaceMinorCivsInTiles(fallbackMinors, tileMap, fallbackTiles)
        }
    }

    /** Attempts to randomly place civs from [civsToPlace] in tiles from [tileList]. Assumes that
     *  [tileList] is pre-vetted and only contains habitable land tiles.
     *  Will modify both [civsToPlace] and [tileList] as it goes! */
    private fun tryPlaceMinorCivsInTiles(civsToPlace: MutableList<CivilizationInfo>, tileMap: TileMap, tileList: MutableList<TileInfo>) {
        while (tileList.isNotEmpty() && civsToPlace.isNotEmpty()) {
            val chosenTile = tileList.random()
            tileList.remove(chosenTile)
            val data = tileData[chosenTile.position]!!
            // If the randomly chosen tile is too close to a player or a city state, discard it
            if (data.impacts.containsKey(ImpactType.MinorCiv))
                continue
            // Otherwise, go ahead and place the minor civ
            val civToAdd = civsToPlace.first()
            civsToPlace.remove(civToAdd)
            placeMinorCiv(civToAdd, tileMap, chosenTile)
        }
    }

    private fun canPlaceMinorCiv(tile: TileInfo) = !tile.isWater && !tile.isImpassible() &&
            !tileData[tile.position]!!.isJunk &&
            tile.getBaseTerrain().getMatchingUniques(UniqueType.HasQuality).none { it.params[0] == "Undesirable" } && // So we don't get snow hills
            tile.neighbors.count() == 6 // Avoid map edges

    private fun placeMinorCiv(civ: CivilizationInfo, tileMap: TileMap, tile: TileInfo) {
        tileMap.addStartingLocation(civ.civName, tile)
        placeImpact(ImpactType.MinorCiv,tile, 4)
        placeImpact(ImpactType.Luxury,  tile, 3)
        placeImpact(ImpactType.Strategic,tile, 0)
        placeImpact(ImpactType.Bonus,   tile, 3)

        normalizeStart(tile, tileMap, minorCiv = true)
    }

    /** Places all Luxuries onto [tileMap]. Assumes that assignLuxuries and placeMinorCivs have been called. */
    private fun placeLuxuries(tileMap: TileMap) {
        // First place luxuries at major civ start locations
        val averageFertilityDensity = regions.sumOf { it.totalFertility } / regions.sumOf { it.tiles.size }.toFloat()
        for (region in regions) {
            var targetLuxuries = 1
            if (tileMap.mapParameters.mapResources == MapResources.legendaryStart)
                targetLuxuries++
            if (region.totalFertility / region.tiles.size.toFloat() < averageFertilityDensity) {
                targetLuxuries++
            }

            val luxuryToPlace = ruleset.tileResources[region.luxury] ?: continue
            // First check 2 inner rings
            val firstPass = tileMap[region.startPosition!!].getTilesInDistanceRange(1..2)
                    .shuffled().sortedBy { it.getTileFertility(false) } // Check bad tiles first
            targetLuxuries -= tryAddingResourceToTiles(luxuryToPlace, targetLuxuries, firstPass, 0.5f) // Skip every 2nd tile on first pass

            if (targetLuxuries > 0) {
                val secondPass = firstPass + tileMap[region.startPosition!!].getTilesAtDistance(3)
                        .shuffled().sortedBy { it.getTileFertility(false) } // Check bad tiles first
                targetLuxuries -= tryAddingResourceToTiles(luxuryToPlace, targetLuxuries, secondPass)
            }
            if (targetLuxuries > 0) {
                // Try adding in 1 luxury from the random rotation as compensation
                for (luxury in randomLuxuries) {
                    if (tryAddingResourceToTiles(ruleset.tileResources[luxury]!!, 1, firstPass) > 0) break
                }
            }
        }
        // Second place one (1) luxury at minor civ start locations
        // Check only ones that got a start location
        for (startLocation in tileMap.startingLocationsByNation
                .filterKeys { ruleset.nations[it]!!.isCityState() }.map { it.value.first() }) {
            val region = regions.firstOrNull { startLocation in it.tiles }
            val tilesToCheck = startLocation.getTilesInDistanceRange(1..2)
            // 75% probability that we first attempt to place a "city state" luxury, then a random or regional one
            // 25% probability of going the other way around
            val globalLuxuries = if (region?.luxury != null) randomLuxuries + listOf(region.luxury) else randomLuxuries
            val candidateLuxuries = if (Random.nextInt(100) >= 25)
                cityStateLuxuries.shuffled() + globalLuxuries.shuffled()
            else
                globalLuxuries.shuffled() + cityStateLuxuries.shuffled()
            // Now try adding one until we are successful
            for (luxury in candidateLuxuries) {
                if (tryAddingResourceToTiles(ruleset.tileResources[luxury]!!, 1, tilesToCheck) > 0) break
            }
        }
        // Third add regional luxuries
        // The target number depends on map size and how close we are to an "ideal number" of civs for the map
        val idealCivs = max(2, tileData.size / 500)
        var regionTargetNumber = (tileData.size / 600) - (0.3f * abs(regions.size - idealCivs)).toInt()
        regionTargetNumber += when (tileMap.mapParameters.mapResources) {
            MapResources.abundant -> 1
            MapResources.sparse -> -1
            else -> 0
        }
        regionTargetNumber = max(1, regionTargetNumber)
        for (region in regions) {
            val resource = ruleset.tileResources[region.luxury] ?: continue
            if (isWaterOnlyResource(resource))
                tryAddingResourceToTiles(resource, regionTargetNumber,
                        tileMap.getTilesInRectangle(region.rect).filter { it.isWater && it.neighbors.any { neighbor -> neighbor.getContinent() == region.continentID } }.shuffled(),
                        0.4f, true, 4, 2)
            else
                tryAddingResourceToTiles(resource, regionTargetNumber, region.tiles.asSequence().shuffled(), 0.4f,
                    true, 4, 2)
        }
        // Fourth add random luxuries
        if (randomLuxuries.isNotEmpty()) {
            var targetRandomLuxuries = tileData.size.toFloat().pow(0.45f).toInt() // Approximately
            targetRandomLuxuries *= when (tileMap.mapParameters.mapResources) {
                MapResources.sparse -> 80
                MapResources.abundant -> 133
                else -> 100
            }
            targetRandomLuxuries /= 100
            targetRandomLuxuries += Random.nextInt(regions.size) // Add random number based on number of civs
            val minimumRandomLuxuries = tileData.size.toFloat().pow(0.2f).toInt() // Approximately
            val worldTiles = tileMap.values.asSequence().shuffled()
            for ((index, luxury) in randomLuxuries.shuffled().withIndex()) {
                val targetForThisLuxury = if (randomLuxuries.size > 8) targetRandomLuxuries / 10
                    else {
                    val minimum = max(3, minimumRandomLuxuries - index)
                    max(minimum, (targetRandomLuxuries * randomLuxuryRatios[randomLuxuries.size]!![index] + 0.5f).toInt())
                }
                tryAddingResourceToTiles(ruleset.tileResources[luxury]!!, targetForThisLuxury, worldTiles, 0.25f,
                        true, 4, 2)
            }
        }
        val specialLuxuries = ruleset.tileResources.values.filter {
            it.resourceType == ResourceType.Luxury &&
                    it.hasUnique(UniqueType.LuxurySpecialPlacement)
        }
        val placedSpecials = HashMap<String, Int>()
        specialLuxuries.forEach { placedSpecials[it.name] = 0 } // init map

        // Fifth, on resource settings other than sparse, add an extra luxury to starts
        if (tileMap.mapParameters.mapResources != MapResources.sparse) {
            for (region in regions) {
                val tilesToCheck = tileMap[region.startPosition!!].getTilesInDistanceRange(1..2)
                val candidateLuxuries = randomLuxuries.shuffled().toMutableList()
                if (tileMap.mapParameters.mapResources != MapResources.strategicBalance)
                    candidateLuxuries += specialLuxuries.shuffled().map { it.name } // Include marble!
                candidateLuxuries += cityStateLuxuries.shuffled()
                candidateLuxuries += regions.mapNotNull { it.luxury }.shuffled()
                for (luxury in candidateLuxuries) {
                    if (tryAddingResourceToTiles(ruleset.tileResources[luxury]!!, 1, tilesToCheck) > 0) {
                        if (placedSpecials.containsKey(luxury)) // Keep track of marble-type specials as they may be placed now.
                            placedSpecials[luxury] = placedSpecials[luxury]!! + 1
                        break
                    }
                }
            }
        }
        // Sixth, top up marble-type specials if needed
        for (special in specialLuxuries) {
            val targetNumber = when (tileMap.mapParameters.mapResources) {
                MapResources.sparse -> (regions.size * 0.5f).toInt()
                MapResources.abundant -> (regions.size * 0.9f).toInt()
                else -> (regions.size * 0.75f).toInt()
            }
            val numberToPlace = max(2, targetNumber - placedSpecials[special.name]!!)
            tryAddingResourceToTiles(special, numberToPlace, tileMap.values.asSequence().shuffled(), 1f,
                    true, 6, 0)
        }
    }

    private fun placeStrategicAndBonuses(tileMap: TileMap) {
        val strategicResources = ruleset.tileResources.values.filter { it.resourceType == ResourceType.Strategic }
        // As usual, if there are any relevant json definitions, assume they are complete
        val fallbackStrategic = ruleset.tileResources.values.none {
            it.resourceType == ResourceType.Strategic &&
                    it.uniqueObjects.any { unique -> unique.isOfType(UniqueType.ResourceWeighting) } ||
                    it.uniqueObjects.any { unique -> unique.isOfType(UniqueType.MinorDepositWeighting) }
        }
        /* There are a couple competing/complementary distribution systems at work here. First, major
           deposits are placed according to a frequency defined in the terrains themselves, for each
           tile that is eligible to get a major deposit, there is a weighted random choice between
           resource types.
           Minor deposits are placed by randomly picking a number of land tiles from anywhere on the
           map (so not stratified by terrain type) and assigning a weighted randomly picked resource.
           Bonuses are placed according to a frequency for a rule like "every 8 jungle hills", here
           implemented as a conditional.

           We need to build lists of all tiles following a given rule to place these, which is BY FAR
           the most expensive calculation in this entire class. To save some time we anonymize the
           uniques so we only have to make one list for each set of conditionals, so eg Wheat and
           Horses can share a list since they are both interested in Featureless Plains.
           We also save a list of all land tiles for minor deposit generation. */

        // Determines number tiles per resource
        val bonusMultiplier = when (tileMap.mapParameters.mapResources) {
            MapResources.sparse -> 1.5f
            MapResources.abundant -> 0.6667f
            else -> 1f
        }
        val landList = ArrayList<TileInfo>() // For minor deposits
        val ruleLists = HashMap<Unique, MutableList<TileInfo>>() // For rule-based generation

        // Figure out which rules (sets of conditionals) need lists built
        for (resource in ruleset.tileResources.values.filter {
            it.resourceType == ResourceType.Strategic ||
            it.resourceType == ResourceType.Bonus }) {
            for (rule in resource.uniqueObjects.filter { unique ->
                unique.isOfType(UniqueType.ResourceFrequency) ||
                unique.isOfType(UniqueType.ResourceWeighting) ||
                unique.isOfType(UniqueType.MinorDepositWeighting) }) {
                // Weed out some clearly impossible rules straight away to save time later
                if (rule.conditionals.any { conditional ->
                        (conditional.isOfType(UniqueType.ConditionalOnWaterMaps) && !usingArchipelagoRegions) ||
                        (conditional.isOfType(UniqueType.ConditionalInRegionOfType) && regions.none { region -> region.type == conditional.params[0] }) ||
                        (conditional.isOfType(UniqueType.ConditionalInRegionExceptOfType) && regions.all { region -> region.type == conditional.params[0] })
                    } )
                    continue
                val simpleRule = anonymizeUnique(rule)
                if (ruleLists.keys.none { it.text == simpleRule.text }) // Need to do text comparison since the uniques will not be equal otherwise
                    ruleLists[simpleRule] = ArrayList()
            }
        }
        // Make up some rules for placing strategics in a fallback situation
        if (fallbackStrategic) {
            val interestingTerrains = strategicResources.flatMap { it.terrainsCanBeFoundOn }.map { ruleset.terrains[it]!! }.toSet()
            for (terrain in interestingTerrains) {
                val fallbackRule = if (terrain.type == TerrainType.TerrainFeature)
                    Unique("RULE <in [${terrain.name}] tiles>")
                else
                    Unique("RULE <in [Featureless] [${terrain.name}] tiles>")
                if (ruleLists.keys.none { it.text == fallbackRule.text }) // Need to do text comparison since the uniques will not be equal otherwise
                    ruleLists[fallbackRule] = ArrayList()
            }
        }
        // Now go through the entire map to build lists
        for (tile in tileMap.values.asSequence().shuffled()) {
            val terrainCondition = StateForConditionals(attackedTile = tile, region = regions.firstOrNull { tile in it.tiles })
            if (tile.getBaseTerrain().hasUnique(UniqueType.BlocksResources, terrainCondition))
                continue // Don't count snow hills
            if (tile.isLand)
                landList.add(tile)
            for ((rule, list) in ruleLists) {
                if (rule.conditionalsApply(terrainCondition)) {
                    list.add(tile)
                }
            }
        }
        // Keep track of total placed strategic resources in case we need to top them up later
        val totalPlaced = HashMap<TileResource, Int>()
        strategicResources.forEach { totalPlaced[it] = 0 }

        // First place major deposits on land
        for (terrain in ruleset.terrains.values.filter { it.type != TerrainType.Water }) {
            // Figure out if we generated a list for this terrain
            val list = ruleLists.filterKeys { it.text == getTerrainRule(terrain).text }.values.firstOrNull()
                ?: continue // If not the terrain can be safely skipped
            totalPlaced += placeMajorDeposits(list, terrain, fallbackStrategic, 2, 2)
        }

        // Second add some small deposits of modern strategic resources to city states
        val lastEra = ruleset.eras.values.maxOf { it.eraNumber }
        val modernOptions = strategicResources.filter {
            it.revealedBy != null &&
                    ruleset.eras[ruleset.technologies[it.revealedBy]!!.era()]!!.eraNumber >= lastEra / 2
        }
        for (cityStateLocation in tileMap.startingLocationsByNation.filterKeys { ruleset.nations[it]!!.isCityState() }.values.map { it.first() }) {
            val resourceToPlace = modernOptions.random()
            totalPlaced[resourceToPlace] =
                    totalPlaced[resourceToPlace]!! + tryAddingResourceToTiles(resourceToPlace, 1, cityStateLocation.getTilesInDistanceRange(1..3))
        }

        // Third add some minor deposits to land tiles
        // Note: In G&K there is a bug where minor deposits are never placed on hills. We're not replicating that.
        val frequency = (baseMinorDepositFrequency * bonusMultiplier).toInt()
        val minorDepositsToAdd = (landList.size / frequency) + 1 // I sometimes have division by zero errors on this line
        var minorDepositsAdded = 0
        for (tile in landList) {
            if (tile.resource != null || tileData[tile.position]!!.impacts.containsKey(ImpactType.Strategic))
                continue
            val conditionalTerrain = StateForConditionals(attackedTile = tile)
            if (tile.getBaseTerrain().hasUnique(UniqueType.BlocksResources, conditionalTerrain))
                continue
            val weightings = strategicResources.map {
                if (fallbackStrategic) {
                    if (tile.getLastTerrain().name in it.terrainsCanBeFoundOn) 1f else 0f
                } else {
                    val uniques = it.getMatchingUniques(UniqueType.MinorDepositWeighting, conditionalTerrain).toList()
                    uniques.sumOf { unique -> unique.params[0].toInt() }.toFloat()
                }
            }
            if (weightings.sum() <= 0) {
                continue
            }
            val resourceToPlace = strategicResources.randomWeighted(weightings)
            tile.setTileResource(resourceToPlace, majorDeposit = false)
            placeImpact(ImpactType.Strategic, tile, Random.nextInt(2) + Random.nextInt(2))
            totalPlaced[resourceToPlace] = totalPlaced[resourceToPlace]!! + 1
            minorDepositsAdded++
            if (minorDepositsAdded >= minorDepositsToAdd)
                break
        }

        // Fourth add water-based major deposits. Extra impact because we don't want them too clustered and there is usually lots to go around
        for (terrain in ruleset.terrains.values.filter { it.type == TerrainType.Water }) {
            // Figure out if we generated a list for this terrain
            val list = ruleLists.filterKeys { it.text == getTerrainRule(terrain).text }.values.firstOrNull()
                ?: continue // If not the terrain can be safely skipped
            totalPlaced += placeMajorDeposits(list, terrain, fallbackStrategic, 4, 3)
        }

        // Fifth place up to 2 extra deposits of each resource type if there is < 1 per civ
        for (resource in strategicResources) {
            val extraNeeded = min(2, regions.size - totalPlaced[resource]!!)
            if (extraNeeded > 0) {
                if (isWaterOnlyResource(resource))
                    tryAddingResourceToTiles(resource, extraNeeded, tileMap.values.asSequence().filter { it.isWater }.shuffled(), respectImpacts = true)
                else
                    tryAddingResourceToTiles(resource, extraNeeded, landList.asSequence(), respectImpacts = true)
            }
        }

        // Figure out if bonus generation rates are defined in json. Assume that if there are any, the definitions are complete.
        val fallbackBonuses = ruleset.tileResources.values.none { it.uniqueObjects.any { unique -> unique.type == UniqueType.ResourceFrequency } }

        // Sixth place bonus resources (and other resources that might have been assigned frequency-based generation).
        // Water-based bonuses go last and have extra impact, because coasts are very common and we don't want too much clustering
        val sortedResourceList = ruleset.tileResources.values.sortedBy { isWaterOnlyResource(it) }
        for (resource in sortedResourceList) {
            val extraImpact = if (isWaterOnlyResource(resource)) 1 else 0
            for (rule in resource.uniqueObjects.filter { it.type == UniqueType.ResourceFrequency }) {
                // Figure out which list applies, if any
                val simpleRule = anonymizeUnique(rule)
                val list = ruleLists.filterKeys { it.text == simpleRule.text }.values.firstOrNull()
                // If there is no matching list, it is because the rule was determined to be impossible and so can be safely skipped
                    ?: continue
                // Place the resources
                placeResourcesInTiles((rule.params[0].toFloat() * bonusMultiplier).toInt(), list, listOf(resource), 0 + extraImpact, 2 + extraImpact, false)
            }
            if(fallbackBonuses && resource.resourceType == ResourceType.Bonus) {
                // Since we haven't been able to generate any rule-based lists, just generate new ones on the fly
                // Increase impact to avoid clustering since there is no terrain type stratification.
                val fallbackList = tileMap.values.filter { it.getLastTerrain().name in resource.terrainsCanBeFoundOn }.shuffled()
                placeResourcesInTiles((20 * bonusMultiplier).toInt(), fallbackList, listOf(resource), 2 + extraImpact, 2 + extraImpact, false)
            }
        }

        // Seventh (and finally!) place an extra bonus in the THIRD ring of each start to make it slightly more attractive
        for (region in regions) {
            val terrain = if (region.type == "Hybrid") region.terrainCounts.filterNot { it.key == "Coastal" }.maxByOrNull { it.value }!!.key
                else region.type
            val resourceUnique = ruleset.terrains[terrain]!!.getMatchingUniques(UniqueType.RegionExtraResource).firstOrNull()
            // If this region has an explicit "this is the bonus" unique go with that, else random appropriate
            val resource = if (resourceUnique != null) ruleset.tileResources[resourceUnique.params[0]]!!
                else {
                val possibleResources =
                    ruleset.tileResources.values.filter { it.resourceType == ResourceType.Bonus && terrain in it.terrainsCanBeFoundOn }
                if (possibleResources.isEmpty()) continue
                possibleResources.random()
            }
            val candidateTiles = tileMap[region.startPosition!!].getTilesAtDistance(3).shuffled()
            val amount = if (resourceUnique != null) 2 else 1 // Place an extra if the region type requests it
            if (tryAddingResourceToTiles(resource, amount, candidateTiles) == 0) {
                // We couldn't place any, try adding a fish instead
                val fishyBonus = ruleset.tileResources.values.filter { it.resourceType == ResourceType.Bonus &&
                    it.terrainsCanBeFoundOn.any { terrainName -> ruleset.terrains[terrainName]!!.type == TerrainType.Water } }
                        .randomOrNull()
                if (fishyBonus != null)
                    tryAddingResourceToTiles(fishyBonus, 1, candidateTiles)
            }
        }
    }

    /** Attempts to place [amount] [resource] on [tiles], checking tiles in order. A [ratio] below 1 means skipping
     *  some tiles, ie ratio = 0.25 will put a resource on every 4th eligible tile. Can optionally respect impact flags,
     *  and places impact if [baseImpact] >= 0. Returns number of placed resources. */
    private fun tryAddingResourceToTiles(resource: TileResource, amount: Int, tiles: Sequence<TileInfo>, ratio: Float = 1f,
                                         respectImpacts: Boolean = false, baseImpact: Int = -1, randomImpact: Int = 0,
                                         majorDeposit: Boolean = false): Int {
        if (amount <= 0) return 0
        var amountAdded = 0
        var ratioProgress = 1f
        val impactType = when (resource.resourceType) {
            ResourceType.Luxury -> ImpactType.Luxury
            ResourceType.Strategic -> ImpactType.Strategic
            ResourceType.Bonus -> ImpactType.Bonus
        }

        for (tile in tiles) {
            val conditionalTerrain = StateForConditionals(attackedTile = tile)
            if (tile.resource == null &&
                    tile.getLastTerrain().name in resource.terrainsCanBeFoundOn &&
                    !tile.getBaseTerrain().hasUnique(UniqueType.BlocksResources, conditionalTerrain) &&
                    !resource.hasUnique(UniqueType.NoNaturalGeneration, conditionalTerrain)) {
                if (ratioProgress >= 1f &&
                        !(respectImpacts && tileData[tile.position]!!.impacts.containsKey(impactType))) {
                    tile.setTileResource(resource, majorDeposit)
                    ratioProgress -= 1f
                    amountAdded++
                    if (baseImpact + randomImpact >= 0)
                        placeImpact(impactType, tile, baseImpact + Random.nextInt(randomImpact + 1))
                    if (amountAdded >= amount) break
                }
                ratioProgress += ratio
            }
        }
        return amountAdded
    }

    /** Attempts to place major deposits in a [tileList] consisting exclusively of [terrain] tiles.
     *  Lifted out of the main function to allow postponing water resources.
     *  @return a map of resource types to placed deposits. */
    private fun placeMajorDeposits(tileList: List<TileInfo>, terrain: Terrain, fallbackWeightings: Boolean, baseImpact: Int, randomImpact: Int): Map<TileResource, Int> {
        if (tileList.isEmpty())
            return mapOf()
        val frequency = if (terrain.hasUnique(UniqueType.MajorStrategicFrequency))
            terrain.getMatchingUniques(UniqueType.MajorStrategicFrequency).first().params[0].toInt()
        else 25
        val resourceOptions = ruleset.tileResources.values.filter {
            it.resourceType == ResourceType.Strategic &&
                    ((fallbackWeightings && terrain.name in it.terrainsCanBeFoundOn) ||
                    it.uniqueObjects.any { unique -> anonymizeUnique(unique).text == getTerrainRule(terrain).text })
        }
        return if (resourceOptions.isNotEmpty())
            placeResourcesInTiles(frequency, tileList, resourceOptions, baseImpact, randomImpact, true)
        else
            mapOf()
    }

    /** Given a [tileList] and possible [resourceOptions], will place a resource on every [frequency] tiles.
     *  Tries to avoid impacts, but falls back to lowest impact otherwise.
     *  Goes through the list in order, so pre-shuffle it!
     *  Assumes all tiles in the list are of the same terrain type when generating weightings, irrelevant if only one option.
     *  Respects terrainsCanBeFoundOn when there is only one option, unless [forcePlacement] is true.
     *  @return a map of the resources in the options list to number placed. */
    private fun placeResourcesInTiles(frequency: Int, tileList: List<TileInfo>, resourceOptions: List<TileResource>,
                                      baseImpact: Int = 0, randomImpact: Int = 0, majorDeposit: Boolean = false, forcePlacement: Boolean = false): Map<TileResource, Int> {
        if (tileList.isEmpty() || resourceOptions.isEmpty()) return mapOf()
        val impactType = when (resourceOptions.first().resourceType) {
            ResourceType.Strategic -> ImpactType.Strategic
            ResourceType.Bonus -> ImpactType.Bonus
            ResourceType.Luxury -> ImpactType.Luxury
        }
        val conditionalTerrain = StateForConditionals(attackedTile = tileList.firstOrNull())
        val weightings = resourceOptions.map {
            val unique = it.getMatchingUniques(UniqueType.ResourceWeighting, conditionalTerrain).firstOrNull()
            if (unique != null)
                unique.params[0].toFloat()
            else
                1f
        }
        val testTerrains = (resourceOptions.size == 1) && !forcePlacement
        val amountToPlace = (tileList.size / frequency) + 1
        var amountPlaced = 0
        val detailedPlaced = HashMap<TileResource, Int>()
        resourceOptions.forEach { detailedPlaced[it] = 0 }
        val fallbackTiles = ArrayList<TileInfo>()
        // First pass - avoid impacts entirely
        for (tile in tileList) {
            if (tile.resource != null ||
                    (testTerrains &&
                            (tile.getLastTerrain().name !in resourceOptions.first().terrainsCanBeFoundOn ||
                            resourceOptions.first().hasUnique(UniqueType.NoNaturalGeneration, conditionalTerrain)) ) ||
                    tile.getBaseTerrain().hasUnique(UniqueType.BlocksResources, conditionalTerrain))
                continue // Can't place here, can't be a fallback tile
            if (tileData[tile.position]!!.impacts.containsKey(impactType)) {
                fallbackTiles.add(tile) // Taken but might be a viable fallback tile
            } else {
                // Add a resource to the tile
                val resourceToPlace = resourceOptions.randomWeighted(weightings)
                tile.setTileResource(resourceToPlace, majorDeposit)
                placeImpact(impactType, tile, baseImpact + Random.nextInt(randomImpact + 1))
                amountPlaced++
                detailedPlaced[resourceToPlace] = detailedPlaced[resourceToPlace]!! + 1
                if (amountPlaced >= amountToPlace) {
                    return detailedPlaced
                }
            }
        }
        // Second pass - place on least impacted tiles
        while (amountPlaced < amountToPlace && fallbackTiles.isNotEmpty()) {
            // Sorry, we do need to re-sort the list for every pass since new impacts are made with every placement
            val bestTile = fallbackTiles.minByOrNull { tileData[it.position]!!.impacts[impactType]!! }!!
            fallbackTiles.remove(bestTile)
            val resourceToPlace = resourceOptions.randomWeighted(weightings)
            bestTile.setTileResource(resourceToPlace, majorDeposit)
            placeImpact(impactType, bestTile, baseImpact + Random.nextInt(randomImpact + 1))
            amountPlaced++
            detailedPlaced[resourceToPlace] = detailedPlaced[resourceToPlace]!! + 1
        }
        return detailedPlaced
    }

    /** Adds numbers to tileData in a similar way to closeStartPenalty, but for different types */
    private fun placeImpact(type: ImpactType, tile: TileInfo, radius: Int) {
        // Epicenter
        tileData[tile.position]!!.impacts[type] = 99
        if (radius <= 0) return

        for (ring in 1..radius) {
            val ringValue = radius - ring + 1
            for (outerTile in tile.getTilesAtDistance(ring)) {
                val data = tileData[outerTile.position]!!
                if (data.impacts.containsKey(type))
                    data.impacts[type] = min(50, max(ringValue, data.impacts[type]!!) + 2)
                else
                    data.impacts[type] = ringValue
            }
        }
    }

    /** @return a fake unique with the same conditionals, but sorted alphabetically.
     *  Used to save some memory and time when building resource lists. */
    private fun anonymizeUnique(unique: Unique) = Unique(
        "RULE" + unique.conditionals.sortedBy { it.text }.joinToString(prefix = " ", separator = " ") { "<" + it.text + ">" })

    /** @return a fake unique with conditionals that will satisfy the same conditions as terrainsCanBeFoundOn */
    private fun getTerrainRule(terrain: Terrain): Unique {
        return if (terrain.type == TerrainType.TerrainFeature) {
            if (terrain.hasUnique(UniqueType.VisibilityElevation))
                Unique("RULE <in [${terrain.name}] tiles>")
            else
                Unique("RULE <in [${terrain.name}] tiles> " + ruleset.terrains.values
                        .filter { it.type == TerrainType.TerrainFeature && it.hasUnique(UniqueType.VisibilityElevation) }
                        .joinToString(separator = " ") { "<in tiles without [${it.name}]>" })
        } else
            Unique("RULE <in [Featureless] [${terrain.name}] tiles>")
    }

    private fun isWaterOnlyResource(resource: TileResource) = resource.terrainsCanBeFoundOn
            .all { terrainName -> ruleset.terrains[terrainName]!!.type == TerrainType.Water }

    enum class ImpactType {
        Strategic,
        Luxury,
        Bonus,
        MinorCiv,
    }

    // Holds a bunch of tile info that is only interesting during map gen
    class MapGenTileData(val tile: TileInfo, val region: Region?) {
        var closeStartPenalty = 0
        val impacts = HashMap<ImpactType, Int>()
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

        fun evaluate(ruleset: Ruleset) {
            // Check if we are two tiles from coast (a bad starting site)
            if (!tile.isCoastalTile() && tile.neighbors.any { it.isCoastalTile() })
                isTwoFromCoast = true

            // Check first available out of unbuildable features, then other features, then base terrain
            val terrainToCheck = if (tile.terrainFeatures.isEmpty()) tile.getBaseTerrain()
            else tile.terrainFeatureObjects.firstOrNull { it.unbuildable }
                    ?: tile.terrainFeatureObjects.first()

            // Add all applicable qualities
            for (unique in terrainToCheck.getMatchingUniques(UniqueType.HasQuality, StateForConditionals(region = region))) {
                when (unique.params[0]) {
                    "Food" -> isFood = true
                    "Desirable" -> isGood = true
                    "Production" -> isProd = true
                    "Undesirable" -> isJunk = true
                }
            }

            // Were there in fact no explicit qualities defined for any region at all? If so let's guess at qualities to preserve mod compatibility.
            if (terrainToCheck.uniqueObjects.none { it.type == UniqueType.HasQuality }) {
                if (tile.isWater) return // Most water type tiles have no qualities

                // is it junk???
                if (terrainToCheck.impassable) {
                    isJunk = true
                    return // Don't bother checking the rest, junk is junk
                }

                // Take possible improvements into account
                val improvements = ruleset.tileImprovements.values.filter {
                    terrainToCheck.name in it.terrainsCanBeBuiltOn &&
                    it.uniqueTo == null &&
                    !it.hasUnique(UniqueType.GreatImprovement)
                }

                val maxFood = terrainToCheck.food + (improvements.maxOfOrNull { it.food } ?: 0f)
                val maxProd = terrainToCheck.production + (improvements.maxOfOrNull { it.production } ?: 0f)
                val bestImprovementValue = improvements.maxOfOrNull { it.food + it.production + it.gold + it.culture + it.science + it.faith } ?: 0f
                val maxOverall = terrainToCheck.food + terrainToCheck.production + terrainToCheck.gold +
                        terrainToCheck.culture + terrainToCheck.science + terrainToCheck.faith + bestImprovementValue

                if (maxFood >= 2) isFood = true
                if (maxProd >= 2) isProd = true
                if (maxOverall >= 3) isGood = true
            }
        }
    }
}

class Region (val tileMap: TileMap, val rect: Rectangle, val continentID: Int = -1) {
    val tiles = HashSet<TileInfo>()
    val terrainCounts = HashMap<String, Int>()
    var totalFertility = 0
    var type = "Hybrid" // being an undefined or indeterminate type
    var luxury: String? = null
    var startPosition: Vector2? = null
    val assignedMinorCivs = ArrayList<CivilizationInfo>()

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
            tiles.add(tile)
            totalFertility += fertility


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
                rect.width = columnHasTile.size.toFloat()
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
}
