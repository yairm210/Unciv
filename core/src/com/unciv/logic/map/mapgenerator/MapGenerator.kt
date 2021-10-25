package com.unciv.logic.map.mapgenerator

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.*
import com.unciv.models.Counter
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.ui.utils.toPercent
import kotlin.math.*
import kotlin.random.Random


class MapGenerator(val ruleset: Ruleset) {
    companion object {
        // temporary instrumentation while tuning/debugging
        const val consoleOutput = false
        private const val consoleTimings = false
    }

    private var randomness = MapGenerationRandomness()

    private val regions = ArrayList<Region>()
    private val tileData = HashMap<Vector2, MapGenTileData>()

    fun generateMap(mapParameters: MapParameters, civilizations: List<CivilizationInfo> = emptyList()): TileMap {
        val mapSize = mapParameters.mapSize
        val mapType = mapParameters.type

        if (mapParameters.seed == 0L)
            mapParameters.seed = System.currentTimeMillis()

        randomness.seedRNG(mapParameters.seed)

        val map: TileMap = if (mapParameters.shape == MapShape.rectangular)
            TileMap(mapSize.width, mapSize.height, ruleset, mapParameters.worldWrap)
        else
            TileMap(mapSize.radius, ruleset, mapParameters.worldWrap)

        mapParameters.createdWithVersion = UncivGame.Current.version
        map.mapParameters = mapParameters

        if (mapType == MapType.empty) {
            for (tile in map.values) {
                tile.baseTerrain = Constants.ocean
                tile.setTerrainTransients()
            }

            return map
        }

        if (consoleOutput || consoleTimings) println("\nMapGenerator run with parameters $mapParameters")
        runAndMeasure("MapLandmassGenerator") {
            MapLandmassGenerator(ruleset, randomness).generateLand(map)
        }
        runAndMeasure("raiseMountainsAndHills") {
            raiseMountainsAndHills(map)
        }
        runAndMeasure("applyHumidityAndTemperature") {
            applyHumidityAndTemperature(map)
        }
        runAndMeasure("spawnLakesAndCoasts") {
            spawnLakesAndCoasts(map)
        }
        runAndMeasure("spawnVegetation") {
            spawnVegetation(map)
        }
        runAndMeasure("spawnRareFeatures") {
            spawnRareFeatures(map)
        }
        runAndMeasure("spawnIce") {
            spawnIce(map)
        }
        runAndMeasure("assignContinents") {
            map.assignContinents(TileMap.AssignContinentsMode.Assign)
        }
        runAndMeasure("NaturalWonderGenerator") {
            NaturalWonderGenerator(ruleset, randomness).spawnNaturalWonders(map)
        }
        runAndMeasure("RiverGenerator") {
            RiverGenerator(map, randomness).spawnRivers()
        }
        runAndMeasure("generateRegions") {
            generateRegions(map, civilizations.count { ruleset.nations[it.civName]!!.isMajorCiv() })
        }
        runAndMeasure("assignRegions") {
            assignRegions(map, civilizations.filter { ruleset.nations[it.civName]!!.isMajorCiv() })
        }
        runAndMeasure("spreadResources") {
            spreadResources(map)
        }
        runAndMeasure("spreadAncientRuins") {
            spreadAncientRuins(map)
        }
        return map
    }

    private fun runAndMeasure(text: String, action: ()->Unit) {
        if (!consoleTimings) return action()
        val startNanos = System.nanoTime()
        action()
        val delta = System.nanoTime() - startNanos
        println("MapGenerator.$text took ${delta/1000000L}.${(delta/10000L).rem(100)}ms")
    }

    //todo: Why is this unused?
    private fun seedRNG(seed: Long) {
        randomness.RNG = Random(seed)
        if (consoleOutput) println("RNG seeded with $seed")
    }

    private fun spawnLakesAndCoasts(map: TileMap) {

        //define lakes
        val waterTiles = map.values.filter { it.isWater }.toMutableList()

        val tilesInArea = ArrayList<TileInfo>()
        val tilesToCheck = ArrayList<TileInfo>()

        while (waterTiles.isNotEmpty()) {
            val initialWaterTile = waterTiles.random(randomness.RNG)
            tilesInArea += initialWaterTile
            tilesToCheck += initialWaterTile
            waterTiles -= initialWaterTile

            // Floodfill to cluster water tiles
            while (tilesToCheck.isNotEmpty()) {
                val tileWeAreChecking = tilesToCheck.random(randomness.RNG)
                for (vector in tileWeAreChecking.neighbors
                        .filter { !tilesInArea.contains(it) and waterTiles.contains(it) }) {
                    tilesInArea += vector
                    tilesToCheck += vector
                    waterTiles -= vector
                }
                tilesToCheck -= tileWeAreChecking
            }

            if (tilesInArea.size <= 10) {
                for (tile in tilesInArea) {
                    tile.baseTerrain = Constants.lakes
                    tile.setTransients()
                }
            }
            tilesInArea.clear()
        }

        //Coasts
        for (tile in map.values.filter { it.baseTerrain == Constants.ocean }) {
            val coastLength = max(1, randomness.RNG.nextInt(max(1, map.mapParameters.maxCoastExtension)))
            if (tile.getTilesInDistance(coastLength).any { it.isLand }) {
                tile.baseTerrain = Constants.coast
                tile.setTransients()
            }
        }
    }

    private fun spreadAncientRuins(map: TileMap) {
        val ruinsEquivalents = ruleset.tileImprovements.filter { it.value.isAncientRuinsEquivalent() }
        if (map.mapParameters.noRuins || ruinsEquivalents.isEmpty() )
            return
        val suitableTiles = map.values.filter { it.isLand && !it.isImpassible() }
        val locations = randomness.chooseSpreadOutLocations(suitableTiles.size / 50,
                suitableTiles, map.mapParameters.mapSize.radius)
        for (tile in locations)
            tile.improvement = ruinsEquivalents.keys.random()
    }

    /** Creates [numRegions] number of balanced regions for civ starting locations. */
    private fun generateRegions(tileMap: TileMap, numRegions: Int) {
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
                        println("continent $continent is wrapping")
                        break
                    }
                }
            }
            continentRegion.updateTiles()
            println("continent $continent is in rect ${continentRegion.rect}")
            println("It has total ${continentRegion.tiles.count()} tiles with total fertility ${continentRegion.totalFertility}")
            divideRegion(continentRegion, civsAddedToContinent[continent]!!)
        }
    }

    /** Recursive function, divides a region into [numDivisions] pars of equal-ish fertility */
    private fun divideRegion(region: Region, numDivisions: Int) {
        if (numDivisions <= 1) {
            // We're all set, save the region and return
            regions.add(region)
            println("Created region ${regions.indexOf(region)} on continent ${region.continentID}, with ${region.tiles.count()} tiles, total fertility ${region.totalFertility}.")
            return
        }
        println("Dividing a region on continent ${region.continentID} into $numDivisions..")

        val firstDivisions = numDivisions / 2 // Since int division rounds down, works for all numbers
        val splitRegions = splitRegion(region, (100 * firstDivisions) / numDivisions)
        divideRegion(splitRegions.first, firstDivisions)
        divideRegion(splitRegions.second, numDivisions - firstDivisions)
    }

    /** Splits a region in 2, with the first having [firstPercent] of total fertility */
    private fun splitRegion(regionToSplit: Region, firstPercent: Int): Pair<Region, Region> {
        val targetFertility = (regionToSplit.totalFertility * firstPercent) / 100
        //println("Splitting a region on continent ${regionToSplit.continentID}, from ${regionToSplit.origin}, size ${regionToSplit.size}, first percent $firstPercent, target fertility $targetFertility.")

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

        //println("Split off region from ${splitOffRegion.origin} to ${splitOffRegion.end}, fertility ${splitOffRegion.totalFertility}.")
        //println("Remainder ${regionToSplit.origin} to ${regionToSplit.end}, fertility ${regionToSplit.totalFertility}.")
        return Pair(splitOffRegion, regionToSplit)
    }

    private fun assignRegions(tileMap: TileMap, civilizations: List<CivilizationInfo>) {
        if (civilizations.isEmpty()) return

        // first assign region types
        val regionTypes = ruleset.terrains.values.filter { it.hasUnique(UniqueType.IsRegion) }
                .sortedBy { it.getMatchingUniques(UniqueType.IsRegion).first().params[0].toInt() }

        for (region in regions) {
            region.countTerrains()
            println(region.terrainCounts)

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
            println("Region ${regions.indexOf(region)} is a ${region.type} region.")
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

        println("BIASES - Coast: $coastBiasCivs, Positive: $positiveBiasCivs, Negative: $negativeBiasCivs, Random: $randomCivs")

        // First assign coast bias civs
        for (civ in coastBiasCivs) {
            println("Finding start region for ${civ.civName}")
            // Try to find a coastal start
            var startRegion = regions.filter { tileMap[it.startPosition!!].isCoastalTile() }.randomOrNull()
            if (startRegion != null) {
                println("Placed coastal bias ${civ.civName} at coast")
                assignCivToRegion(civ, startRegion)
                continue
            }
            // Else adjacent to a lake
            startRegion = regions.filter { tileMap[it.startPosition!!].neighbors.any { neighbor -> neighbor.getBaseTerrain().hasUnique(UniqueType.FreshWater) } }.randomOrNull()
            if (startRegion != null) {
                println("Placed coastal bias ${civ.civName} at lake")
                assignCivToRegion(civ, startRegion)
                continue
            }
            // Else adjacent to a river
            startRegion = regions.filter { tileMap[it.startPosition!!].isAdjacentToRiver() }.randomOrNull()
            if (startRegion != null) {
                println("Placed coastal bias ${civ.civName} at river")
                assignCivToRegion(civ, startRegion)
                continue
            }
            // Else at least close to a river ????
            startRegion = regions.filter { tileMap[it.startPosition!!].neighbors.any { neighbor -> neighbor.isAdjacentToRiver() } }.randomOrNull()
            if (startRegion != null) {
                println("Placed coastal bias ${civ.civName} at river... ish")
                assignCivToRegion(civ, startRegion)
                continue
            }
            // Else pick a random region at the end
            randomCivs.add(civ)
        }

        // Next do positive bias civs
        for (civ in positiveBiasCivs) {
            println("Finding start region for ${civ.civName}")
            // Try to find a start that matches any of the desired regions
            val startRegion = regions.filter { it.type in ruleset.nations[civ.civName]!!.startBias }.randomOrNull()
            if (startRegion != null) {
                println("Placed positive bias ${civ.civName} at desired type")
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
            println("Placed positive bias ${civ.civName} at fallback")
            assignCivToRegion(civ, getFallbackRegion(ruleset.nations[civ.civName]!!.startBias.first()))
        }

        // Next do negative bias ones (ie "Avoid []")
        for (civ in negativeBiasCivs) {
            println("Finding start region for ${civ.civName}")
            // Try to find a region not of the avoided types
            val startRegion = regions.filterNot { it.type in ruleset.nations[civ.civName]!!.startBias.map {
                bias -> bias.getPlaceholderParameters()[0] } }.randomOrNull()
            if (startRegion != null) {
                println("Placed negative bias ${civ.civName} at desired region")
                assignCivToRegion(civ, startRegion)
                continue
            } else
                randomCivs.add(civ) // else pick a random region at the end
        }

        // Finally assign the remaining civs randomly
        for (civ in randomCivs) {
            println("Finding random region for ${civ.civName}")
            val startRegion = regions.random()
            println("Placed random bias ${civ.civName}")
            assignCivToRegion(civ, startRegion)
        }
    }

    private fun assignCivToRegion(civInfo: CivilizationInfo, region: Region) {
        println("Placed ${civInfo.civName} at ${region.startPosition} on continent ${region.continentID}")
        region.tileMap.addStartingLocation(civInfo.civName, region.tileMap[region.startPosition!!])
        regions.remove(region) // This region can no longer be picked
    }

    /** Attempts to find a good start close to the center of [region]. Calls setRegionStart with the position*/
    private fun findStart(region: Region) {
        println("Finding a start for region ${regions.indexOf(region)}..")
        // Establish center bias rects
        val centerRect = getCentralRectangle(region.rect, 0.33f)
        val middleRect = getCentralRectangle(region.rect, 0.67f)
        println("Outer: ${region.rect}, Middle: $middleRect, Center: $centerRect")

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
            println("checking a list in center: $list")
            if (list.any { tileData[it]!!.isGoodStart }) {
                println("Found a good start in center")
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
                println("Found a good start in middle")
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
            println("Found a good start in outer")
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
            println("Using a fallback start")
            setRegionStart(region, fallbackPosition)
            return
        }

        // Something went extremely wrong and there is somehow no place to start. Spawn some land and start there
        val panicPosition = region.rect.getPosition(Vector2())
        val panicTerrain = ruleset.terrains.values.first { it.type == TerrainType.Land }.name
        region.tileMap[panicPosition].baseTerrain = panicTerrain
        println("PANIC!")
        setRegionStart(region, panicPosition)
    }

    /** @returns the region most similar to a region of [type] */
    private fun getFallbackRegion(type: String): Region {
        return regions.maxByOrNull { it.terrainCounts[type] ?: 0 }!!
    }

    private fun setRegionStart(region: Region, position: Vector2) {
        println("Set a start at $position")
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
                tile.mapGenLog += "Failed minimum at ring $ring, with f$totalFood p$totalProd g$totalGood\n"
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
            tile.mapGenLog += "Failed due to $totalJunk junk\n"
        }

        // Finally check if this is near another start
        if (localData.closeStartPenalty > 0) {
            localData.isGoodStart = false
            totalScore -= (totalScore * localData.closeStartPenalty) / 100
            tile.mapGenLog += "Failed due to nearby start\n"
        }
        localData.startScore = totalScore
        tile.mapGenLog += "Total start score $totalScore"
    }

    private fun spreadResources(tileMap: TileMap) {
        val mapRadius = tileMap.mapParameters.mapSize.radius
        for (tile in tileMap.values)
            tile.resource = null

        spreadStrategicResources(tileMap, mapRadius)
        spreadResources(tileMap, mapRadius, ResourceType.Luxury)
        spreadResources(tileMap, mapRadius, ResourceType.Bonus)
    }

    // Here, we need each specific resource to be spread over the map - it matters less if specific resources are near each other
    private fun spreadStrategicResources(tileMap: TileMap, mapRadius: Int) {
        val strategicResources = ruleset.tileResources.values.filter { it.resourceType == ResourceType.Strategic }
        // passable land tiles (no mountains, no wonders) without resources yet
        val candidateTiles = tileMap.values.filter { it.resource == null && !it.isImpassible() }
        val totalNumberOfResources = candidateTiles.count { it.isLand } * tileMap.mapParameters.resourceRichness
        val resourcesPerType = (totalNumberOfResources/strategicResources.size).toInt()
        for (resource in strategicResources) {
            // remove the tiles where previous resources have been placed
            val suitableTiles = candidateTiles
                    .filterNot { it.baseTerrain == Constants.snow && it.isHill() }
                    .filter { it.resource == null
                            && resource.terrainsCanBeFoundOn.contains(it.getLastTerrain().name) }

            val locations = randomness.chooseSpreadOutLocations(resourcesPerType, suitableTiles, mapRadius)

            for (location in locations) location.setTileResource(resource)
        }
    }

    /**
     * Spreads resources of type [resourceType] picking locations at a minimum distance from each other,
     * which is determined from [mapRadius] and then tuned down until the desired number fits.
     * [MapParameters.resourceRichness] used to control how many resources to spawn.
     */
    private fun spreadResources(tileMap: TileMap, mapRadius: Int, resourceType: ResourceType) {
        val resourcesOfType = ruleset.tileResources.values.filter { it.resourceType == resourceType }

        val suitableTiles = tileMap.values
                .filterNot { it.baseTerrain == Constants.snow && it.isHill() }
                .filter { it.resource == null && resourcesOfType.any { r -> r.terrainsCanBeFoundOn.contains(it.getLastTerrain().name) } }
        val numberOfResources = tileMap.values.count { it.isLand && !it.isImpassible() } *
                tileMap.mapParameters.resourceRichness
        val locations = randomness.chooseSpreadOutLocations(numberOfResources.toInt(), suitableTiles, mapRadius)

        val resourceToNumber = Counter<String>()

        for (tile in locations) {
            val possibleResources = resourcesOfType
                    .filter { it.terrainsCanBeFoundOn.contains(tile.getLastTerrain().name) }
            if (possibleResources.isEmpty()) continue
            val resourceWithLeastAssignments = possibleResources.minByOrNull { resourceToNumber[it.name]!! }!!
            resourceToNumber.add(resourceWithLeastAssignments.name, 1)
            tile.setTileResource(resourceWithLeastAssignments)
        }
    }


    /**
     * [MapParameters.elevationExponent] favors high elevation
     */
    private fun raiseMountainsAndHills(tileMap: TileMap) {
        val mountain = ruleset.terrains.values.firstOrNull { it.uniques.contains("Occurs in chains at high elevations") }?.name
        val hill = ruleset.terrains.values.firstOrNull { it.uniques.contains("Occurs in groups around high elevations") }?.name
        val flat = ruleset.terrains.values.firstOrNull { !it.impassable && it.type == TerrainType.Land && !it.uniques.contains("Rough Terrain") }?.name

        if (flat == null) {
            println("Ruleset seems to contain no flat terrain - can't generate heightmap")
            return
        }

        if (consoleOutput && mountain != null)
            println("Mountain-like generation for $mountain")
        if (consoleOutput && hill != null)
            println("Hill-like generation for $hill")

        val elevationSeed = randomness.RNG.nextInt().toDouble()
        tileMap.setTransients(ruleset)
        for (tile in tileMap.values.asSequence().filter { !it.isWater }) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed, scale = 2.0)
                    elevation = abs(elevation).pow(1.0 - tileMap.mapParameters.elevationExponent.toDouble()) * elevation.sign

            when {
                elevation <= 0.5 -> tile.baseTerrain = flat
                elevation <= 0.7 && hill != null -> tile.terrainFeatures.add(hill)
                elevation <= 0.7 && hill == null -> tile.baseTerrain = flat // otherwise would be hills become mountains
                elevation <= 1.0 && mountain != null -> tile.baseTerrain = mountain
            }
            tile.setTerrainTransients()
        }

        if (mountain != null)
            cellularMountainRanges(tileMap, mountain, hill, flat)
        if (hill != null)
            cellularHills(tileMap, mountain, hill)
    }

    private fun cellularMountainRanges(tileMap: TileMap, mountain: String, hill: String?, flat: String) {
        val targetMountains = tileMap.values.count { it.baseTerrain == mountain } * 2

        for (i in 1..5) {
            var totalMountains = tileMap.values.count { it.baseTerrain == mountain }

            for (tile in tileMap.values.filter { !it.isWater }) {
                val adjacentMountains =
                    tile.neighbors.count { it.baseTerrain == mountain }
                val adjacentImpassible =
                    tile.neighbors.count { ruleset.terrains[it.baseTerrain]?.impassable == true }

                if (adjacentMountains == 0 && tile.baseTerrain == mountain) {
                    if (randomness.RNG.nextInt(until = 4) == 0)
                        tile.terrainFeatures.add(Constants.lowering)
                } else if (adjacentMountains == 1) {
                    if (randomness.RNG.nextInt(until = 10) == 0)
                        tile.terrainFeatures.add(Constants.rising)
                } else if (adjacentImpassible == 3) {
                    if (randomness.RNG.nextInt(until = 2) == 0)
                        tile.terrainFeatures.add(Constants.lowering)
                } else if (adjacentImpassible > 3) {
                    tile.terrainFeatures.add(Constants.lowering)
                }
            }

            for (tile in tileMap.values.filter { !it.isWater }) {
                if (tile.terrainFeatures.remove(Constants.rising) && totalMountains < targetMountains) {
                    if (hill != null)
                        tile.terrainFeatures.remove(hill)
                    tile.baseTerrain = mountain
                    totalMountains++
                }
                if (tile.terrainFeatures.remove(Constants.lowering) && totalMountains > targetMountains * 0.5f) {
                    if (tile.baseTerrain == mountain) {
                        if (hill != null && !tile.terrainFeatures.contains(hill))
                            tile.terrainFeatures.add(hill)
                        totalMountains--
                    }
                    tile.baseTerrain = flat
                }
            }
        }
    }

    private fun cellularHills(tileMap: TileMap, mountain: String?, hill: String) {
        val targetHills = tileMap.values.count { it.terrainFeatures.contains(hill) }

        for (i in 1..5) {
            var totalHills = tileMap.values.count { it.terrainFeatures.contains(hill) }

            for (tile in tileMap.values.asSequence().filter { !it.isWater && (mountain == null || it.baseTerrain != mountain) }) {
                val adjacentMountains = if (mountain == null) 0 else
                    tile.neighbors.count { it.baseTerrain == mountain }
                val adjacentHills =
                    tile.neighbors.count { it.terrainFeatures.contains(hill) }

                if (adjacentHills <= 1 && adjacentMountains == 0 && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.terrainFeatures.add(Constants.lowering)
                } else if (adjacentHills > 3 && adjacentMountains == 0 && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.terrainFeatures.add(Constants.lowering)
                } else if (adjacentHills + adjacentMountains in 2..3 && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.terrainFeatures.add(Constants.rising)
                }

            }

            for (tile in tileMap.values.asSequence().filter { !it.isWater && (mountain == null || it.baseTerrain != mountain) }) {
                if (tile.terrainFeatures.remove(Constants.rising) && (totalHills <= targetHills || i == 1) ) {
                    if (!tile.terrainFeatures.contains(hill)) {
                        tile.terrainFeatures.add(hill)
                        totalHills++
                    }
                }
                if (tile.terrainFeatures.remove(Constants.lowering) && (totalHills >= targetHills * 0.9f || i == 1)) {
                    if (tile.terrainFeatures.remove(hill))
                        totalHills--
                }
            }
        }
    }

    /**
     * [MapParameters.tilesPerBiomeArea] to set biomes size
     * [MapParameters.temperatureExtremeness] to favor very high and very low temperatures
     */
    private fun applyHumidityAndTemperature(tileMap: TileMap) {
        val humiditySeed = randomness.RNG.nextInt().toDouble()
        val temperatureSeed = randomness.RNG.nextInt().toDouble()

        tileMap.setTransients(ruleset)

        val scale = tileMap.mapParameters.tilesPerBiomeArea.toDouble()
        val temperatureExtremeness = tileMap.mapParameters.temperatureExtremeness
        
        class TerrainOccursRange(
            val terrain: Terrain,
            val tempFrom: Float, val tempTo: Float,
            val humidFrom: Float, val humidTo: Float
        )
        val limitsMap: List<TerrainOccursRange> =
            // List is OK here as it's only sequentially scanned
            ruleset.terrains.values.flatMap { terrain ->
                terrain.uniqueObjects.filter {
                    it.placeholderText == "Occurs at temperature between [] and [] and humidity between [] and []"
                }.map { unique ->
                    TerrainOccursRange(terrain,
                        unique.params[0].toFloat(), unique.params[1].toFloat(),
                        unique.params[2].toFloat(), unique.params[3].toFloat())
                }
            }
        val noTerrainUniques = limitsMap.isEmpty()
        val elevationTerrains = arrayOf(Constants.mountain, Constants.hill)

        for (tile in tileMap.values.asSequence()) {
            if (tile.isWater || tile.baseTerrain in elevationTerrains)
                continue

            val humidity = (randomness.getPerlinNoise(tile, humiditySeed, scale = scale, nOctaves = 1) + 1.0) / 2.0

            val randomTemperature = randomness.getPerlinNoise(tile, temperatureSeed, scale = scale, nOctaves = 1)
            val latitudeTemperature = 1.0 - 2.0 * abs(tile.latitude) / tileMap.maxLatitude
            var temperature = (5.0 * latitudeTemperature + randomTemperature) / 6.0
            temperature = abs(temperature).pow(1.0 - temperatureExtremeness) * temperature.sign

            // Old, static map generation rules - necessary for existing base ruleset mods to continue to function
            if (noTerrainUniques) {
                tile.baseTerrain = when {
                    temperature < -0.4 -> if (humidity < 0.5) Constants.snow   else Constants.tundra
                    temperature < 0.8  -> if (humidity < 0.5) Constants.plains else Constants.grassland
                    temperature <= 1.0 -> if (humidity < 0.7) Constants.desert else Constants.plains
                    else -> {
                        println("applyHumidityAndTemperature: Invalid temperature $temperature")
                        Constants.grassland
                    }
                }
                tile.setTerrainTransients()
                continue
            }

            val matchingTerrain = limitsMap.firstOrNull {
                it.tempFrom < temperature && temperature <= it.tempTo
                && it.humidFrom < humidity && humidity <= it.humidTo
            }

            if (matchingTerrain != null) tile.baseTerrain = matchingTerrain.terrain.name
            else {
                tile.baseTerrain = ruleset.terrains.values.firstOrNull { it.type == TerrainType.Land }?.name ?: Constants.grassland
                println("applyHumidityAndTemperature: No terrain found for temperature: $temperature, humidity: $humidity")
            }
            tile.setTerrainTransients()
        }
    }

    /**
     * [MapParameters.vegetationRichness] is the threshold for vegetation spawn
     */
    private fun spawnVegetation(tileMap: TileMap) {
        val vegetationSeed = randomness.RNG.nextInt().toDouble()
        val candidateTerrains = Constants.vegetation.flatMap{ ruleset.terrains[it]!!.occursOn }
        //Checking it.baseTerrain in candidateTerrains to make sure forest does not spawn on desert hill
        for (tile in tileMap.values.asSequence().filter { it.baseTerrain in candidateTerrains
                && it.getLastTerrain().name in candidateTerrains }) {
            val vegetation = (randomness.getPerlinNoise(tile, vegetationSeed, scale = 3.0, nOctaves = 1) + 1.0) / 2.0

            if (vegetation <= tileMap.mapParameters.vegetationRichness)
                tile.terrainFeatures.add(Constants.vegetation.filter { ruleset.terrains[it]!!.occursOn.contains(tile.getLastTerrain().name) }.random(randomness.RNG))
        }
    }
    /**
     * [MapParameters.rareFeaturesRichness] is the probability of spawning a rare feature
     */
    private fun spawnRareFeatures(tileMap: TileMap) {
        val rareFeatures = ruleset.terrains.values.filter {
            it.type == TerrainType.TerrainFeature && it.uniques.contains("Rare feature")
        }
        for (tile in tileMap.values.asSequence().filter { it.terrainFeatures.isEmpty() }) {
            if (randomness.RNG.nextDouble() <= tileMap.mapParameters.rareFeaturesRichness) {
                val possibleFeatures = rareFeatures.filter { it.occursOn.contains(tile.baseTerrain)
                        && (!tile.isHill() || it.occursOn.contains(Constants.hill)) }
                if (possibleFeatures.any())
                    tile.terrainFeatures.add(possibleFeatures.random(randomness.RNG).name)
            }
        }
    }

    /**
     * [MapParameters.temperatureExtremeness] as in [applyHumidityAndTemperature]
     */
    private fun spawnIce(tileMap: TileMap) {
        tileMap.setTransients(ruleset)
        val temperatureSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            if (tile.baseTerrain !in Constants.sea || tile.terrainFeatures.isNotEmpty())
                continue

            val randomTemperature = randomness.getPerlinNoise(tile, temperatureSeed, scale = tileMap.mapParameters.tilesPerBiomeArea.toDouble(), nOctaves = 1)
            val latitudeTemperature = 1.0 - 2.0 * abs(tile.latitude) / tileMap.maxLatitude
            var temperature = ((latitudeTemperature + randomTemperature) / 2.0)
            temperature = abs(temperature).pow(1.0 - tileMap.mapParameters.temperatureExtremeness) * temperature.sign
            if (temperature < -0.8)
                tile.terrainFeatures.add(Constants.ice)
        }
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

class MapGenerationRandomness {
    var RNG = Random(42)

    fun seedRNG(seed: Long = 42) {
        RNG = Random(seed)
    }

    /**
     * Generates a perlin noise channel combining multiple octaves
     *
     * [nOctaves] is the number of octaves
     * [persistence] is the scaling factor of octave amplitudes
     * [lacunarity] is the scaling factor of octave frequencies
     * [scale] is the distance the noise is observed from
     */
    fun getPerlinNoise(tile: TileInfo, seed: Double,
                       nOctaves: Int = 6,
                       persistence: Double = 0.5,
                       lacunarity: Double = 2.0,
                       scale: Double = 10.0): Double {
        val worldCoords = HexMath.hex2WorldCoords(tile.position)
        return Perlin.noise3d(worldCoords.x.toDouble(), worldCoords.y.toDouble(), seed, nOctaves, persistence, lacunarity, scale)
    }


    fun chooseSpreadOutLocations(number: Int, suitableTiles: List<TileInfo>, mapRadius: Int): ArrayList<TileInfo> {
        if (number <= 0) return ArrayList(0)

        // Determine sensible initial distance from number of desired placements and mapRadius
        // empiric formula comes very close to eliminating retries for distance.
        // The `if` means if we need to fill 60% or more of the available tiles, no sense starting with minimum distance 2.
        val sparsityFactor = (HexMath.getHexagonalRadiusForArea(suitableTiles.size) / mapRadius).pow(0.333f)
        val initialDistance = if (number == 1 || number * 5 >= suitableTiles.size * 3) 1
            else max(1, (mapRadius * 0.666f / HexMath.getHexagonalRadiusForArea(number).pow(0.9f) * sparsityFactor + 0.5).toInt())

        // If possible, we want to equalize the base terrains upon which
        //  the resources are found, so we save how many have been
        //  found for each base terrain and try to get one from the lowest
        val baseTerrainsToChosenTiles = HashMap<String, Int>()
        for (tileInfo in suitableTiles){
            if (tileInfo.baseTerrain !in baseTerrainsToChosenTiles)
                baseTerrainsToChosenTiles[tileInfo.baseTerrain] = 0
        }

        for (distanceBetweenResources in initialDistance downTo 1) {
            var availableTiles = suitableTiles
            val chosenTiles = ArrayList<TileInfo>(number)

            for (terrain in baseTerrainsToChosenTiles.keys)
                baseTerrainsToChosenTiles[terrain] = 0

            for (i in 1..number) {
                if (availableTiles.isEmpty()) break
                val orderedKeys = baseTerrainsToChosenTiles.entries
                        .sortedBy { it.value }.map { it.key }
                val firstKeyWithTilesLeft = orderedKeys
                        .first { availableTiles.any { tile -> tile.baseTerrain == it} }
                val chosenTile = availableTiles.filter { it.baseTerrain == firstKeyWithTilesLeft }.random(RNG)
                availableTiles = availableTiles.filter { it.aerialDistanceTo(chosenTile) > distanceBetweenResources }
                chosenTiles.add(chosenTile)
                baseTerrainsToChosenTiles[firstKeyWithTilesLeft] = baseTerrainsToChosenTiles[firstKeyWithTilesLeft]!! + 1
            }
            if (chosenTiles.size == number || distanceBetweenResources == 1) {
                // Either we got them all, or we're not going to get anything better
                if (MapGenerator.consoleOutput && distanceBetweenResources < initialDistance)
                    println("chooseSpreadOutLocations: distance $distanceBetweenResources < initial $initialDistance")
                return chosenTiles
            }
        }
        // unreachable due to last loop iteration always returning and initialDistance >= 1
        throw Exception()
    }
}
