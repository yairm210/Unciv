package com.unciv.logic.map.mapgenerator.mapregions

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.BiasTypes.PositiveFallback
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.utils.randomWeighted
import com.unciv.utils.Log
import com.unciv.utils.Tag
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class TileDataMap : HashMap<Vector2, MapGenTileData>() {

    /** Adds numbers to tileData in a similar way to closeStartPenalty, but for different types */
    fun placeImpact(type: MapRegions.ImpactType, tile: Tile, radius: Int) {
        // Epicenter
        this[tile.position]!!.impacts[type] = 99
        if (radius <= 0) return

        for (ring in 1..radius) {
            val ringValue = radius - ring + 1
            for (outerTile in tile.getTilesAtDistance(ring)) {
                val data = this[outerTile.position]!!
                if (data.impacts.containsKey(type))
                    data.impacts[type] = min(50, max(ringValue, data.impacts[type]!!) + 2)
                else
                    data.impacts[type] = ringValue
            }
        }
    }
}

class MapRegions (val ruleset: Ruleset) {
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
    private val tileData = TileDataMap()

    /** Creates [numRegions] number of balanced regions for civ starting locations. */
    fun generateRegions(tileMap: TileMap, numRegions: Int) {
        if (numRegions <= 0) return // Don't bother about regions, probably map editor
        if (tileMap.continentSizes.isEmpty()) throw Exception("No Continents on this map!")
        val totalLand = tileMap.continentSizes.values.sum().toFloat()
        val largestContinent = tileMap.continentSizes.values.maxOf { it }.toFloat()

        val radius = if (tileMap.mapParameters.shape == MapShape.hexagonal || tileMap.mapParameters.shape == MapShape.flatEarth)
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
        val continentToColumnsItsIn = HashMap<Int, HashSet<Int>>()

        // Calculate continent fertilities and columns
        for (tile in tileMap.values) {
            val continent = tile.getContinent()
            if (continent != -1) {
                continentFertility[continent] = tile.getTileFertility(true) +
                        (continentFertility[continent] ?: 0)

                if (continentToColumnsItsIn[continent] == null)
                    continentToColumnsItsIn[continent] = HashSet()

                continentToColumnsItsIn[continent]!!.add(tile.getColumn())
            }
        }

        // Assign regions to the best continents, giving half value for region #2 etc
        repeat(numRegions) {
            val bestContinent = continents
                    .maxByOrNull { continentFertility[it]!! / (1 + (civsAddedToContinent[it] ?: 0)) }!!
            civsAddedToContinent[bestContinent] = (civsAddedToContinent[bestContinent] ?: 0) + 1
        }

        // Split up the continents
        for (continent in civsAddedToContinent.keys) {
            val continentRegion = Region(tileMap, Rectangle(mapRect), continent)
            val cols = continentToColumnsItsIn[continent]!!
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
                        1f, splitOffRegion.rect.height))
            else
                splitOffRegion.tileMap.getTilesInRectangle(Rectangle(
                        splitOffRegion.rect.x, splitOffRegion.rect.y + splitPoint - 1,
                        splitOffRegion.rect.width, 1f))

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
            regionToSplit.rect.width = regionToSplit.rect.width - bestSplitPoint
        } else {
            splitOffRegion.rect.height = bestSplitPoint.toFloat()
            regionToSplit.rect.y = splitOffRegion.rect.y + splitOffRegion.rect.height
            regionToSplit.rect.height = regionToSplit.rect.height - bestSplitPoint
        }
        splitOffRegion.updateTiles()
        regionToSplit.updateTiles()

        return Pair(splitOffRegion, regionToSplit)
    }

    /** Buckets for startBias to region assignments, used only in [assignRegions]. [PositiveFallback] is only for logging. */
    private enum class BiasTypes { Coastal, Positive, Negative, Random, PositiveFallback }

    fun assignRegions(tileMap: TileMap, civilizations: List<Civilization>, gameParameters: GameParameters) {
        if (civilizations.isEmpty()) return

        assignRegionTypes()

        // Generate tile data for all tiles
        for (tile in tileMap.values) {
            val newData = MapGenTileData(tile, regions.firstOrNull { it.tiles.contains(tile) }, ruleset)
            tileData[tile.position] = newData
        }

        // Sort regions by fertility so the worse regions get to pick first
        val sortedRegions = regions.sortedBy { it.totalFertility }
        for (region in sortedRegions) findStart(region)
        for (region in regions) {
            StartNormalizer.normalizeStart(tileMap[region.startPosition!!], tileMap, tileData, ruleset, isMinorCiv = false)
        }

        val civBiases = civilizations.associateWith { ruleset.nations[it.civName]!!.startBias }
        // This ensures each civ can only be in one of the buckets
        val civsByBiasType = civBiases.entries.groupBy(
            keySelector = {
                (_, startBias) ->
                when {
                    gameParameters.noStartBias -> BiasTypes.Random
                    startBias.any { bias -> bias.equalsPlaceholderText("Avoid []") } -> BiasTypes.Negative
                    "Coast" in startBias -> BiasTypes.Coastal
                    startBias.isNotEmpty() -> BiasTypes.Positive
                    else -> BiasTypes.Random
                }
            },
            valueTransform = { (civ, _) -> civ }
        )

        val coastBiasCivs = civsByBiasType[BiasTypes.Coastal]
                ?: emptyList()
        val positiveBiasCivs = civsByBiasType[BiasTypes.Positive]
                ?.sortedBy { civBiases[it]?.size } // civs with only one desired region go first
                ?: emptyList()
        val negativeBiasCivs = civsByBiasType[BiasTypes.Negative]
                ?.sortedByDescending { civBiases[it]?.size } // Civs with more complex avoids go first
                ?: emptyList()
        val randomCivs = civsByBiasType[BiasTypes.Random]
                ?.toMutableList() // We might fill this up as we go
                ?: mutableListOf()
        val positiveBiasFallbackCivs = mutableListOf<Civilization>() // Civs who couldn't get their desired region at first pass
        val unpickedRegions = regions.toMutableList()

        // First assign coast bias civs
        for (civ in coastBiasCivs) {
            // Try to find a coastal start, preferably a really coastal one
            var startRegion = unpickedRegions.filter { tileMap[it.startPosition!!].isCoastalTile() }
                    .maxByOrNull { it.terrainCounts["Coastal"] ?: 0 }
            if (startRegion != null) {
                logAssignRegion(true, BiasTypes.Coastal, civ, startRegion)
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            }
            // Else adjacent to a lake
            startRegion = unpickedRegions.filter { tileMap[it.startPosition!!].neighbors.any { neighbor -> neighbor.getBaseTerrain().hasUnique(UniqueType.FreshWater) } }
                    .maxByOrNull { it.terrainCounts["Coastal"] ?: 0 }
            if (startRegion != null) {
                logAssignRegion(true, BiasTypes.Coastal, civ, startRegion)
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            }
            // Else adjacent to a river
            startRegion = unpickedRegions.filter { tileMap[it.startPosition!!].isAdjacentToRiver() }
                    .maxByOrNull { it.terrainCounts["Coastal"] ?: 0 }
            if (startRegion != null) {
                logAssignRegion(true, BiasTypes.Coastal, civ, startRegion)
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            }
            // Else at least close to a river ????
            startRegion = unpickedRegions.filter { tileMap[it.startPosition!!].neighbors.any { neighbor -> neighbor.isAdjacentToRiver() } }
                    .maxByOrNull { it.terrainCounts["Coastal"] ?: 0 }
            if (startRegion != null) {
                logAssignRegion(true, BiasTypes.Coastal, civ, startRegion)
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            }
            // Else pick a random region at the end
            logAssignRegion(false, BiasTypes.Coastal, civ)
            randomCivs.add(civ)
        }

        // Next do positive bias civs
        for (civ in positiveBiasCivs) {
            // Try to find a start that matches any of the desired regions, ideally with lots of desired terrain
            val preferred = civBiases[civ]!!
            val startRegion = unpickedRegions.filter { it.type in preferred }
                    .maxByOrNull { it.terrainCounts.filterKeys { terrain -> terrain in preferred }.values.sum() }
            if (startRegion != null) {
                logAssignRegion(true, BiasTypes.Positive, civ, startRegion)
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            } else if (preferred.size == 1) { // Civs with a single bias (only) get to look for a fallback region
                positiveBiasFallbackCivs.add(civ)
            } else { // Others get random starts
                logAssignRegion(false, BiasTypes.Positive, civ)
                randomCivs.add(civ)
            }
        }

        // Do a second pass for fallback civs, choosing the region most similar to the desired type
        for (civ in positiveBiasFallbackCivs) {
            val startRegion = getFallbackRegion(civBiases[civ]!!.first(), unpickedRegions)
            logAssignRegion(true, PositiveFallback, civ, startRegion)
            assignCivToRegion(civ, startRegion)
            unpickedRegions.remove(startRegion)
        }

        // Next do negative bias ones (ie "Avoid []")
        for (civ in negativeBiasCivs) {
            val (avoidBias, preferred) = civBiases[civ]!!
                .partition { bias -> bias.equalsPlaceholderText("Avoid []") }
            val avoided = avoidBias.map { it.getPlaceholderParameters()[0] }
            // Try to find a region not of the avoided types, secondary sort by
            // least number of undesired terrains (weighed double) / most number of desired terrains
            val startRegion = unpickedRegions.filterNot { it.type in avoided }
                    .minByOrNull {
                        2 * it.terrainCounts.filterKeys { terrain -> terrain in avoided }.values.sum()
                        - it.terrainCounts.filterKeys { terrain -> terrain in preferred }.values.sum()
                    }
            if (startRegion != null) {
                logAssignRegion(true, BiasTypes.Negative, civ, startRegion)
                assignCivToRegion(civ, startRegion)
                unpickedRegions.remove(startRegion)
                continue
            } else {
                logAssignRegion(false, BiasTypes.Negative, civ)
                randomCivs.add(civ) // else pick a random region at the end
            }
        }

        // Finally assign the remaining civs randomly
        for (civ in randomCivs) {
            // throws if regions.size < civilizations.size or if the assigning mismatched - leads to popup on newgame screen
            val startRegion = unpickedRegions.random()
            logAssignRegion(true, BiasTypes.Random, civ, startRegion)
            assignCivToRegion(civ, startRegion)
            unpickedRegions.remove(startRegion)
        }
    }

    /** Sets region.type */
    private fun assignRegionTypes() {
        val regionTypes = ruleset.terrains.values.filter { getRegionPriority(it) != null }
            .sortedBy { getRegionPriority(it) }

        for (region in regions) {
            region.countTerrains()

            for (type in regionTypes) {
                // Test exclusion criteria first
                if (type.getMatchingUniques(UniqueType.RegionRequireFirstLessThanSecond).any {
                        region.getTerrainAmount(it.params[0]) >= region.getTerrainAmount(it.params[1])
                    }) {
                    continue
                }
                // Test inclusion criteria
                if (type.getMatchingUniques(UniqueType.RegionRequirePercentSingleType).any {
                        region.getTerrainAmount(it.params[1]) >= (it.params[0].toInt() * region.tiles.size) / 100
                    }
                    || type.getMatchingUniques(UniqueType.RegionRequirePercentTwoTypes).any {
                        region.getTerrainAmount(it.params[1]) + region.getTerrainAmount(it.params[2]) >= (it.params[0].toInt() * region.tiles.size) / 100
                    }
                ) {
                    region.type = type.name
                    break
                }
            }
        }
    }

    private fun logAssignRegion(success: Boolean, startBiasType: BiasTypes, civ: Civilization, region: Region? = null) {
        if (Log.backend.isRelease()) return

        val logCiv = { civ.civName + " " + ruleset.nations[civ.civName]!!.startBias.joinToString(",", "(", ")") }
        val msg = if (success) "(%s): %s to %s"
            else "no region (%s) found for %s"
        Log.debug(Tag("assignRegions"), msg, startBiasType, logCiv, region)
    }


    private fun assignCivToRegion(civ: Civilization, region: Region) {
        val tile = region.tileMap[region.startPosition!!]
        region.tileMap.addStartingLocation(civ.civName, tile)

        // Place impacts to keep city states etc at appropriate distance
        tileData.placeImpact(ImpactType.MinorCiv,tile, 6)
        tileData.placeImpact(ImpactType.Luxury,  tile, 3)
        tileData.placeImpact(ImpactType.Strategic,tile, 0)
        tileData.placeImpact(ImpactType.Bonus,   tile, 3)
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
        val centerTiles = region.tileMap.getTilesInRectangle(centerRect)
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
        val middleDonut = region.tileMap.getTilesInRectangle(middleRect).filterNot { it in centerTiles }
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
        val outerDonut = region.tileMap.getTilesInRectangle(region.rect).filterNot { it in centerTiles || it in middleDonut}
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

    private fun setCloseStartPenalty(tile: Tile) {
        for ((ring, penalty) in closeStartPenaltyForRing) {
            for (outerTile in tile.getTilesAtDistance(ring).map { it.position })
                tileData[outerTile]!!.addCloseStartPenalty(penalty)
        }
    }

    /** Evaluates a tile for starting position, setting isGoodStart and startScore in
     *  MapGenTileData. Assumes that all tiles have corresponding MapGenTileData. */
    private fun evaluateTileForStart(tile: Tile) {
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

    fun placeResourcesAndMinorCivs(tileMap: TileMap, minorCivs: List<Civilization>) {
        placeNaturalWonderImpacts(tileMap)

        val (cityStateLuxuries, randomLuxuries) = LuxuryResourcePlacementLogic.assignLuxuries(regions, tileData, ruleset)
        MinorCivPlacer.placeMinorCivs(regions, tileMap, minorCivs, usingArchipelagoRegions, tileData, ruleset)
        LuxuryResourcePlacementLogic.placeLuxuries(regions, tileMap, tileData, ruleset, cityStateLuxuries, randomLuxuries)
        placeStrategicAndBonuses(tileMap)
    }

    /** Places impacts from NWs that have been generated just prior to this step. */
    private fun placeNaturalWonderImpacts(tileMap: TileMap) {
        for (tile in tileMap.values.filter { it.isNaturalWonder() }) {
            tileData.placeImpact(ImpactType.Bonus, tile, 1)
            tileData.placeImpact(ImpactType.Strategic, tile, 1)
            tileData.placeImpact(ImpactType.Luxury, tile, 1)
            tileData.placeImpact(ImpactType.MinorCiv, tile, 1)
        }
    }


    private fun placeStrategicAndBonuses(tileMap: TileMap) {
        val strategicResources = ruleset.tileResources.values.filter { it.resourceType == ResourceType.Strategic }
        // As usual, if there are any relevant json definitions, assume they are complete
        val fallbackStrategic = ruleset.tileResources.values.none {
            it.resourceType == ResourceType.Strategic &&
                    it.hasUnique(UniqueType.ResourceWeighting) ||
                    it.hasUnique(UniqueType.MinorDepositWeighting)
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
        val bonusMultiplier = tileMap.mapParameters.getMapResources().bonusFrequencyMultiplier
        val landList = ArrayList<Tile>() // For minor deposits
        val ruleLists = HashMap<Unique, MutableList<Tile>>() // For rule-based generation

        // Figure out which rules (sets of conditionals) need lists built
        for (resource in ruleset.tileResources.values.filter {
            it.resourceType == ResourceType.Strategic ||
            it.resourceType == ResourceType.Bonus }) {

            for (rule in resource.uniqueObjects.filter { unique ->
                unique.type == UniqueType.ResourceFrequency ||
                unique.type == UniqueType.ResourceWeighting ||
                unique.type == UniqueType.MinorDepositWeighting }) {
                // Weed out some clearly impossible rules straight away to save time later
                if (rule.modifiers.any { conditional ->
                        (conditional.type == UniqueType.ConditionalOnWaterMaps && !usingArchipelagoRegions) ||
                        (conditional.type == UniqueType.ConditionalInRegionOfType && regions.none { region -> region.type == conditional.params[0] }) ||
                        (conditional.type == UniqueType.ConditionalInRegionExceptOfType && regions.all { region -> region.type == conditional.params[0] })
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
            val terrainRule = getTerrainRule(terrain, ruleset)
            val list = ruleLists.filterKeys { it.text == terrainRule.text }.values.firstOrNull()
                ?: continue // If not the terrain can be safely skipped
            totalPlaced += MapRegionResources.placeMajorDeposits(tileData, ruleset, list, terrain, fallbackStrategic, 2, 2)
        }

        // Second add some small deposits of modern strategic resources to city states
        val lastEra = ruleset.eras.values.maxOf { it.eraNumber }
        val modernOptions = strategicResources.filter {
            it.revealedBy != null &&
                    ruleset.eras[ruleset.technologies[it.revealedBy]!!.era()]!!.eraNumber >= lastEra / 2
        }

        if (modernOptions.any())
            for (cityStateLocation in tileMap.startingLocationsByNation
                    .filterKeys { ruleset.nations[it]!!.isCityState }.values.map { it.first() }) {
                val resourceToPlace = modernOptions.random()
                totalPlaced[resourceToPlace] =
                        totalPlaced[resourceToPlace]!! + MapRegionResources.tryAddingResourceToTiles(tileData, resourceToPlace, 1, cityStateLocation.getTilesInDistanceRange(1..3))
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
                    if (it.generatesNaturallyOn(tile)) 1f else 0f
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
            tileData.placeImpact(ImpactType.Strategic, tile, Random.nextInt(2) + Random.nextInt(2))
            totalPlaced[resourceToPlace] = totalPlaced[resourceToPlace]!! + 1
            minorDepositsAdded++
            if (minorDepositsAdded >= minorDepositsToAdd)
                break
        }

        // Fourth add water-based major deposits. Extra impact because we don't want them too clustered and there is usually lots to go around
        for (terrain in ruleset.terrains.values.filter { it.type == TerrainType.Water }) {
            // Figure out if we generated a list for this terrain
            val list = ruleLists.filterKeys { it.text == getTerrainRule(terrain, ruleset).text }.values.firstOrNull()
                ?: continue // If not the terrain can be safely skipped
            totalPlaced += MapRegionResources.placeMajorDeposits(tileData, ruleset, list, terrain, fallbackStrategic, 4, 3)
        }

        // Fifth place up to 2 extra deposits of each resource type if there is < 1 per civ
        for (resource in strategicResources) {
            val extraNeeded = min(2, regions.size - totalPlaced[resource]!!)
            if (extraNeeded > 0) {
                val tilesToAddTo = if (!isWaterOnlyResource(resource, ruleset)) landList.asSequence()
                else tileMap.values.asSequence().filter { it.isWater }.shuffled()

                MapRegionResources.tryAddingResourceToTiles(tileData, resource, extraNeeded, tilesToAddTo, respectImpacts = true)
            }
        }

        // Figure out if bonus generation rates are defined in json. Assume that if there are any, the definitions are complete.
        val fallbackBonuses = ruleset.tileResources.values.none { it.uniqueObjects.any { unique -> unique.type == UniqueType.ResourceFrequency } }

        // Sixth place bonus resources (and other resources that might have been assigned frequency-based generation).
        // Water-based bonuses go last and have extra impact, because coasts are very common and we don't want too much clustering
        val sortedResourceList = ruleset.tileResources.values.sortedBy { isWaterOnlyResource(it, ruleset) }
        for (resource in sortedResourceList) {
            val extraImpact = if (isWaterOnlyResource(resource, ruleset)) 1 else 0
            for (rule in resource.uniqueObjects.filter { it.type == UniqueType.ResourceFrequency }) {
                // Figure out which list applies, if any
                val simpleRule = anonymizeUnique(rule)
                val list = ruleLists.filterKeys { it.text == simpleRule.text }.values.firstOrNull()
                // If there is no matching list, it is because the rule was determined to be impossible and so can be safely skipped
                    ?: continue
                // Place the resources
                MapRegionResources.placeResourcesInTiles(tileData, (rule.params[0].toFloat() * bonusMultiplier).toInt(), list, listOf(resource), 0 + extraImpact, 2 + extraImpact, false)
            }
            if(fallbackBonuses && resource.resourceType == ResourceType.Bonus) {
                // Since we haven't been able to generate any rule-based lists, just generate new ones on the fly
                // Increase impact to avoid clustering since there is no terrain type stratification.
                val fallbackList = tileMap.values.filter { it.lastTerrain.name in resource.terrainsCanBeFoundOn }.shuffled()
                MapRegionResources.placeResourcesInTiles(tileData, (20 * bonusMultiplier).toInt(), fallbackList, listOf(resource), 2 + extraImpact, 2 + extraImpact, false)
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
            if (MapRegionResources.tryAddingResourceToTiles(tileData, resource, amount, candidateTiles) == 0) {
                // We couldn't place any, try adding a fish instead
                val fishyBonus = ruleset.tileResources.values.filter { it.resourceType == ResourceType.Bonus &&
                    it.terrainsCanBeFoundOn.any { terrainName -> ruleset.terrains[terrainName]!!.type == TerrainType.Water } }
                        .randomOrNull()
                if (fishyBonus != null)
                    MapRegionResources.tryAddingResourceToTiles(tileData, fishyBonus, 1, candidateTiles)
            }
        }
    }

    enum class ImpactType {
        Strategic,
        Luxury,
        Bonus,
        MinorCiv,
    }

}


// For dividing the map into Regions to determine start locations
fun Tile.getTileFertility(checkCoasts: Boolean): Int {
    var fertility = 0
    for (terrain in allTerrains) {
        if (terrain.hasUnique(UniqueType.OverrideFertility))
            return terrain.getMatchingUniques(UniqueType.OverrideFertility).first().params[0].toInt()
        else
            fertility += terrain.getMatchingUniques(UniqueType.AddFertility)
                .sumOf { it.params[0].toInt() }
    }
    if (isAdjacentToRiver()) fertility += 1
    if (isAdjacentTo(Constants.freshWater)) fertility += 1 // meaning total +2 for river
    if (checkCoasts && isCoastalTile()) fertility += 2
    return fertility
}

fun getRegionPriority(terrain: Terrain?): Int? {
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

/** @return a fake unique with the same conditionals, but sorted alphabetically.
 *  Used to save some memory and time when building resource lists. */
internal fun anonymizeUnique(unique: Unique) = Unique(
    "RULE" + unique.modifiers.sortedBy { it.text }.joinToString(prefix = " ", separator = " ") { "<" + it.text + ">" })

internal fun isWaterOnlyResource(resource: TileResource, ruleset: Ruleset) = resource.terrainsCanBeFoundOn
    .all { terrainName -> ruleset.terrains[terrainName]!!.type == TerrainType.Water }


/** @return a fake unique with conditionals that will satisfy the same conditions as terrainsCanBeFoundOn */
internal fun getTerrainRule(terrain: Terrain, ruleset: Ruleset): Unique {
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
