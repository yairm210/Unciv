package com.unciv.logic.map.mapgenerator

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
import kotlin.math.*
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.random.Random


class MapGenerator(val ruleset: Ruleset) {
    companion object {
        // temporary instrumentation while tuning/debugging
        const val consoleOutput = false
        private const val consoleTimings = false
    }

    private var randomness = MapGenerationRandomness()
    private val firstLandTerrain = ruleset.terrains.values.first { it.type==TerrainType.Land }

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
        runAndMeasure("RiverGenerator") {
            RiverGenerator(map, randomness, ruleset).spawnRivers()
        }
        convertTerrains(map, ruleset)

        // Region based map generation - not used when generating maps in map editor
        if (civilizations.isNotEmpty()) {
            val regions = MapRegions(ruleset)
            runAndMeasure("generateRegions") {
                regions.generateRegions(map, civilizations.count { ruleset.nations[it.civName]!!.isMajorCiv() })
            }
            runAndMeasure("assignRegions") {
                regions.assignRegions(map, civilizations.filter { ruleset.nations[it.civName]!!.isMajorCiv() })
            }
            runAndMeasure("placeResourcesAndMinorCivs") {
                regions.placeResourcesAndMinorCivs(map, civilizations.filter { ruleset.nations[it.civName]!!.isCityState() })
            }
        } else {
            // Fallback spread resources function - used when generating maps in map editor
            runAndMeasure("spreadResources") {
                spreadResources(map)
            }
        }
        runAndMeasure("NaturalWonderGenerator") {
            NaturalWonderGenerator(ruleset, randomness).spawnNaturalWonders(map)
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

    private fun convertTerrains(map: TileMap, ruleset: Ruleset) {
        for (tile in map.values) {
            val conversionUnique =
                tile.getBaseTerrain().getMatchingUniques(UniqueType.ChangesTerrain)
                    .firstOrNull { tile.isAdjacentTo(it.params[1]) }
                    ?: continue
            val terrain = ruleset.terrains[conversionUnique.params[0]] ?: continue
            if (!terrain.occursOn.contains(tile.getLastTerrain().name)) continue

            if (terrain.type == TerrainType.TerrainFeature)
                tile.addTerrainFeature(terrain.name)
            else tile.baseTerrain = terrain.name
            tile.setTerrainTransients()
        }
    }

    private fun spawnLakesAndCoasts(map: TileMap) {

        if (ruleset.terrains.containsKey(Constants.lakes)) {
            //define lakes
            val waterTiles = map.values.filter { it.isWater }.toMutableList()

            val tilesInArea = ArrayList<TileInfo>()
            val tilesToCheck = ArrayList<TileInfo>()

            val maxLakeSize = ruleset.modOptions.constants.maxLakeSize

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

                if (tilesInArea.size <= maxLakeSize) {
                    for (tile in tilesInArea) {
                        tile.baseTerrain = Constants.lakes
                        tile.setTransients()
                    }
                }
                tilesInArea.clear()
            }
        }

        //Coasts
        if (ruleset.terrains.containsKey(Constants.coast)) {
            for (tile in map.values.filter { it.baseTerrain == Constants.ocean }) {
                val coastLength =
                    max(1, randomness.RNG.nextInt(max(1, map.mapParameters.maxCoastExtension)))
                if (tile.getTilesInDistance(coastLength).any { it.isLand }) {
                    tile.baseTerrain = Constants.coast
                    tile.setTransients()
                }
            }
        }
    }

    private fun spreadAncientRuins(map: TileMap) {
        val ruinsEquivalents = ruleset.tileImprovements.filter { it.value.isAncientRuinsEquivalent() }
        if (map.mapParameters.noRuins || ruinsEquivalents.isEmpty() )
            return
        val suitableTiles = map.values.filter { it.isLand && !it.isImpassible() }
        val locations = randomness.chooseSpreadOutLocations(
                (suitableTiles.size * ruleset.modOptions.constants.ancientRuinCountMultiplier).roundToInt(),
                suitableTiles,
                map.mapParameters.mapSize.radius)
        for (tile in locations)
            tile.improvement = ruinsEquivalents.keys.random()
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
        val mountain = ruleset.terrains.values.firstOrNull { it.hasUnique(UniqueType.OccursInChains) }?.name
        val hill = ruleset.terrains.values.firstOrNull { it.hasUnique(UniqueType.OccursInGroups) }?.name
        val flat = ruleset.terrains.values.firstOrNull { 
            !it.impassable && it.type == TerrainType.Land && !it.hasUnique(UniqueType.RoughTerrain) 
        }?.name

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
                elevation <= 0.7 && hill != null -> tile.addTerrainFeature(hill)
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
                        tile.addTerrainFeature(Constants.lowering)
                } else if (adjacentMountains == 1) {
                    if (randomness.RNG.nextInt(until = 10) == 0)
                        tile.addTerrainFeature(Constants.rising)
                } else if (adjacentImpassible == 3) {
                    if (randomness.RNG.nextInt(until = 2) == 0)
                        tile.addTerrainFeature(Constants.lowering)
                } else if (adjacentImpassible > 3) {
                    tile.addTerrainFeature(Constants.lowering)
                }
            }

            for (tile in tileMap.values.filter { !it.isWater }) {
                if (tile.terrainFeatures.contains(Constants.rising)) {
                    tile.removeTerrainFeature(Constants.rising)
                    if (totalMountains >= targetMountains) continue
                    if (hill != null)
                        tile.removeTerrainFeature(hill)
                    tile.baseTerrain = mountain
                    totalMountains++
                }
                if (tile.terrainFeatures.contains(Constants.lowering)) {
                    tile.removeTerrainFeature(Constants.lowering)
                    if (totalMountains <= targetMountains * 0.5f) continue
                    if (tile.baseTerrain == mountain) {
                        if (hill != null && !tile.terrainFeatures.contains(hill))
                            tile.addTerrainFeature(hill)
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
                    tile.addTerrainFeature(Constants.lowering)
                } else if (adjacentHills > 3 && adjacentMountains == 0 && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.addTerrainFeature(Constants.lowering)
                } else if (adjacentHills + adjacentMountains in 2..3 && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.addTerrainFeature(Constants.rising)
                }

            }

            for (tile in tileMap.values.asSequence().filter { !it.isWater && (mountain == null || it.baseTerrain != mountain) }) {
                if (tile.terrainFeatures.contains(Constants.rising)) {
                    tile.removeTerrainFeature(Constants.rising)
                    if (totalHills > targetHills && i != 1) continue
                    if (!tile.terrainFeatures.contains(hill)) {
                        tile.addTerrainFeature(hill)
                        totalHills++
                    }
                }
                if (tile.terrainFeatures.contains(Constants.lowering)) {
                    tile.removeTerrainFeature(Constants.lowering)
                    if (totalHills >= targetHills * 0.9f || i == 1) {
                        if (tile.terrainFeatures.contains(hill))
                            tile.removeTerrainFeature(hill)
                        totalHills--
                    }
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
        // List is OK here as it's only sequentially scanned
        val limitsMap: List<TerrainOccursRange> =
            ruleset.terrains.values.flatMap { terrain -> 
                terrain.getMatchingUniques(UniqueType.TileGenerationConditions)
                .map { unique ->
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
                val autoTerrain = when {
                    temperature < -0.4 -> if (humidity < 0.5) Constants.snow else Constants.tundra
                    temperature < 0.8 -> if (humidity < 0.5) Constants.plains else Constants.grassland
                    temperature <= 1.0 -> if (humidity < 0.7) Constants.desert else Constants.plains
                    else -> {
                        println("applyHumidityAndTemperature: Invalid temperature $temperature")
                        firstLandTerrain.name
                    }
                }
                if (ruleset.terrains.containsKey(autoTerrain)) tile.baseTerrain = autoTerrain
                tile.setTerrainTransients()
                continue
            }

            val matchingTerrain = limitsMap.firstOrNull {
                it.tempFrom < temperature && temperature <= it.tempTo
                && it.humidFrom < humidity && humidity <= it.humidTo
            }

            if (matchingTerrain != null) tile.baseTerrain = matchingTerrain.terrain.name
            else {
                tile.baseTerrain = firstLandTerrain.name
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
        val candidateTerrains = Constants.vegetation.mapNotNull { ruleset.terrains[it] }.flatMap{ it.occursOn }
        //Checking it.baseTerrain in candidateTerrains to make sure forest does not spawn on desert hill
        for (tile in tileMap.values.asSequence().filter { it.baseTerrain in candidateTerrains
                && it.getLastTerrain().name in candidateTerrains }) {
            val vegetation = (randomness.getPerlinNoise(tile, vegetationSeed, scale = 3.0, nOctaves = 1) + 1.0) / 2.0

            if (vegetation <= tileMap.mapParameters.vegetationRichness) {
                val randomVegetation = Constants.vegetation.filter { ruleset.terrains[it]!!.occursOn.contains(tile.getLastTerrain().name) }.random(randomness.RNG)
                tile.addTerrainFeature(randomVegetation)
            }
        }
    }
    /**
     * [MapParameters.rareFeaturesRichness] is the probability of spawning a rare feature
     */
    private fun spawnRareFeatures(tileMap: TileMap) {
        val rareFeatures = ruleset.terrains.values.filter {
            it.type == TerrainType.TerrainFeature && it.hasUnique(UniqueType.RareFeature)
        }
        for (tile in tileMap.values.asSequence().filter { it.terrainFeatures.isEmpty() }) {
            if (randomness.RNG.nextDouble() <= tileMap.mapParameters.rareFeaturesRichness) {
                val possibleFeatures = rareFeatures.filter { it.occursOn.contains(tile.baseTerrain)
                        && (!tile.isHill() || it.occursOn.contains(Constants.hill)) }
                if (possibleFeatures.any())
                    tile.addTerrainFeature(possibleFeatures.random(randomness.RNG).name)
            }
        }
    }

    /**
     * [MapParameters.temperatureExtremeness] as in [applyHumidityAndTemperature]
     */
    private fun spawnIce(tileMap: TileMap) {
        if (!ruleset.terrains.containsKey(Constants.ice)) return // I can't think of how to make this nicely moddable
        tileMap.setTransients(ruleset)
        val spawnIceBelowTemperature = ruleset.modOptions.constants.spawnIceBelowTemperature
        val temperatureSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            if (tile.baseTerrain !in Constants.sea || tile.terrainFeatures.isNotEmpty())
                continue

            val randomTemperature = randomness.getPerlinNoise(tile, temperatureSeed, scale = tileMap.mapParameters.tilesPerBiomeArea.toDouble(), nOctaves = 1)
            val latitudeTemperature = 1.0 - 2.0 * abs(tile.latitude) / tileMap.maxLatitude
            var temperature = ((latitudeTemperature + randomTemperature) / 2.0)
            temperature = abs(temperature).pow(1.0 - tileMap.mapParameters.temperatureExtremeness) * temperature.sign
            if (temperature < spawnIceBelowTemperature)
                tile.addTerrainFeature(Constants.ice)
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
