package com.unciv.logic.map.mapgenerator

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.HexMath
import com.unciv.logic.map.*
import com.unciv.models.Counter
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TerrainType
import kotlin.math.*
import kotlin.random.Random


class MapGenerator(val ruleset: Ruleset) {
    private var randomness = MapGenerationRandomness()

    fun generateMap(mapParameters: MapParameters): TileMap {
        val mapSize = mapParameters.mapSize
        val mapType = mapParameters.type

        if (mapParameters.seed == 0L)
            mapParameters.seed = System.currentTimeMillis()

        randomness.seedRNG(mapParameters.seed)

        val map: TileMap = if (mapParameters.shape == MapShape.rectangular)
            TileMap(mapSize.width, mapSize.height, ruleset, mapParameters.worldWrap)
        else
            TileMap(mapSize.radius, ruleset, mapParameters.worldWrap)

        map.mapParameters = mapParameters

        if (mapType == MapType.empty) {
            for (tile in map.values) {
                tile.baseTerrain = Constants.ocean
                tile.setTerrainTransients()
            }

            return map
        }

        MapLandmassGenerator(ruleset, randomness).generateLand(map)
        raiseMountainsAndHills(map)
        applyHumidityAndTemperature(map)
        spawnLakesAndCoasts(map)
        spawnVegetation(map)
        spawnRareFeatures(map)
        spawnIce(map)
        NaturalWonderGenerator(ruleset, randomness).spawnNaturalWonders(map)
        RiverGenerator(randomness).spawnRivers(map)
        spreadResources(map)
        spreadAncientRuins(map)
        return map
    }

    private fun seedRNG(seed: Long, verbose: Boolean = false) {
        randomness.RNG = Random(seed)
        if (verbose) println("RNG seeded with $seed")
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
        if (map.mapParameters.noRuins || !ruleset.tileImprovements.containsKey(Constants.ancientRuins))
            return
        val suitableTiles = map.values.filter { it.isLand && !it.isImpassible() }
        val locations = randomness.chooseSpreadOutLocations(suitableTiles.size / 50,
                suitableTiles, 10)
        for (tile in locations)
            tile.improvement = Constants.ancientRuins
    }

    private fun spreadResources(tileMap: TileMap) {
        val distance = tileMap.mapParameters.mapSize.radius
        for (tile in tileMap.values)
            tile.resource = null

        spreadStrategicResources(tileMap, distance)
        spreadResources(tileMap, distance, ResourceType.Luxury)
        spreadResources(tileMap, distance, ResourceType.Bonus)
    }

    // Here, we need each specific resource to be spread over the map - it matters less if specific resources are near each other
    private fun spreadStrategicResources(tileMap: TileMap, distance: Int) {
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

            val locations = randomness.chooseSpreadOutLocations(resourcesPerType, suitableTiles, distance)

            for (location in locations) location.resource = resource.name
        }
    }

    /**
     * Spreads resources of type [resourceType] picking locations at [distance] from each other.
     * [MapParameters.resourceRichness] used to control how many resources to spawn.
     */
    private fun spreadResources(tileMap: TileMap, distance: Int, resourceType: ResourceType) {
        val resourcesOfType = ruleset.tileResources.values.filter { it.resourceType == resourceType }

        val suitableTiles = tileMap.values
                .filterNot { it.baseTerrain == Constants.snow && it.isHill() }
                .filter { it.resource == null && resourcesOfType.any { r -> r.terrainsCanBeFoundOn.contains(it.getLastTerrain().name) } }
        val numberOfResources = tileMap.values.count { it.isLand && !it.isImpassible() } *
                tileMap.mapParameters.resourceRichness
        val locations = randomness.chooseSpreadOutLocations(numberOfResources.toInt(), suitableTiles, distance)

        val resourceToNumber = Counter<String>()

        for (tile in locations) {
            val possibleResources = resourcesOfType
                    .filter { it.terrainsCanBeFoundOn.contains(tile.getLastTerrain().name) }
                    .map { it.name }
            if (possibleResources.isEmpty()) continue
            val resourceWithLeastAssignments = possibleResources.minByOrNull { resourceToNumber[it]!! }!!
            resourceToNumber.add(resourceWithLeastAssignments, 1)
            tile.resource = resourceWithLeastAssignments
        }
    }


    /**
     * [MapParameters.elevationExponent] favors high elevation
     */
    private fun raiseMountainsAndHills(tileMap: TileMap) {
        val mountain = ruleset.terrains.values.filter { it.uniques.contains("Occurs in chains at high elevations") }.firstOrNull()?.name
        val hill = ruleset.terrains.values.filter { it.uniques.contains("Occurs in groups around high elevations") }.firstOrNull()?.name
        val flat = ruleset.terrains.values.filter { !it.impassable && it.type == TerrainType.Land && !it.uniques.contains("Rough Terrain") }.firstOrNull()?.name

        if (flat == null) {
            println("Ruleset seems to contain no flat terrain - can't generate heightmap")
            return
        }

        if (mountain != null)
            println("Mountainlike generation for " + mountain)
        if (hill != null)
            println("Hill-like generation for " + hill)

        val elevationSeed = randomness.RNG.nextInt().toDouble()
        tileMap.setTransients(ruleset)
        for (tile in tileMap.values.filter { !it.isWater }) {
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

            for (tile in tileMap.values.filter { !it.isWater && (mountain == null || it.baseTerrain != mountain) }) {
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

            for (tile in tileMap.values.filter { !it.isWater && (mountain == null || it.baseTerrain != mountain) }) {
                if (tile.terrainFeatures.remove(Constants.rising) && (totalHills <= targetHills || i == 1) ) {
                    if (!tile.terrainFeatures.contains(hill)) {
                        tile.terrainFeatures.add(hill)
                        totalHills++
                    }
                }
                if (tile.terrainFeatures.remove(Constants.lowering) && (totalHills >= targetHills * 0.9f || i == 1)) {
                    if (tile.terrainFeatures.contains(hill)) {
                        tile.terrainFeatures.remove(hill)
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

        for (tile in tileMap.values) {
            if (tile.isWater || tile.baseTerrain in arrayOf(Constants.mountain, Constants.hill))
                continue

            val humidity = (randomness.getPerlinNoise(tile, humiditySeed, scale = scale, nOctaves = 1) + 1.0) / 2.0

            val randomTemperature = randomness.getPerlinNoise(tile, temperatureSeed, scale = scale, nOctaves = 1)
            val latitudeTemperature = 1.0 - 2.0 * abs(tile.latitude) / tileMap.maxLatitude
            var temperature = (5.0 * latitudeTemperature + randomTemperature) / 6.0
            temperature = abs(temperature).pow(1.0 - tileMap.mapParameters.temperatureExtremeness) * temperature.sign

            // Old, static map generation rules - necessary for existing base ruleset mods to continue to function
            if (ruleset.terrains.values.asSequence().flatMap { it.uniqueObjects }
                            .none { it.placeholderText == "Occurs at temperature between [] and [] and humidity between [] and []" }) {
                tile.baseTerrain = when {
                    temperature < -0.4 -> if (humidity < 0.5) Constants.snow   else Constants.tundra
                    temperature < 0.8  -> if (humidity < 0.5) Constants.plains else Constants.grassland
                    temperature <= 1.0 -> if (humidity < 0.7) Constants.desert else Constants.plains
                    else -> {
                        println(temperature)
                        Constants.lakes
                    }
                }
                tile.setTerrainTransients()
                continue
            }

            val matchingTerrain = ruleset.terrains.values.firstOrNull { terrain ->
                terrain.uniqueObjects.any {
                    it.placeholderText == "Occurs at temperature between [] and [] and humidity between [] and []"
                            && it.params[0].toFloat() < temperature && temperature <= it.params[1].toFloat()
                            && it.params[2].toFloat() < humidity && humidity <= it.params[3].toFloat()
                }
            }

            if (matchingTerrain != null) tile.baseTerrain = matchingTerrain.name
            else {
                tile.baseTerrain = ruleset.terrains.keys.first()
                println("Temperature: $temperature, humidity: $humidity")
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
            if (tile.baseTerrain !in Constants.sea || tile.terrainFeatures.any())
                continue

            val randomTemperature = randomness.getPerlinNoise(tile, temperatureSeed, scale = tileMap.mapParameters.tilesPerBiomeArea.toDouble(), nOctaves = 1)
            val latitudeTemperature = 1.0 - 2.0 * abs(tile.latitude) / tileMap.maxLatitude
            var temperature = ((latitudeTemperature + randomTemperature) / 2.0)
            temperature = abs(temperature).pow(1.0 - tileMap.mapParameters.temperatureExtremeness) * temperature.sign
            if (temperature < -0.8)
                tile.terrainFeatures.add(Constants.ice)
        }
    }

}

class MapGenerationRandomness{
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


    fun chooseSpreadOutLocations(number: Int, suitableTiles: List<TileInfo>, initialDistance: Int): ArrayList<TileInfo> {
        for (distanceBetweenResources in initialDistance downTo 1) {
            var availableTiles = suitableTiles.toList()
            val chosenTiles = ArrayList<TileInfo>()

            // If possible, we want to equalize the base terrains upon which
            //  the resources are found, so we save how many have been
            //  found for each base terrain and try to get one from the lowerst
            val baseTerrainsToChosenTiles = HashMap<String, Int>()
            for(tileInfo in availableTiles){
                if(tileInfo.baseTerrain !in baseTerrainsToChosenTiles)
                    baseTerrainsToChosenTiles[tileInfo.baseTerrain] = 0
            }

            for (i in 1..number) {
                if (availableTiles.isEmpty()) break
                val orderedKeys = baseTerrainsToChosenTiles.entries
                        .sortedBy { it.value }.map { it.key }
                val firstKeyWithTilesLeft = orderedKeys
                        .first { availableTiles.any { tile -> tile.baseTerrain== it} }
                val chosenTile = availableTiles.filter { it.baseTerrain==firstKeyWithTilesLeft }.random(RNG)
                availableTiles = availableTiles.filter { it.aerialDistanceTo(chosenTile) > distanceBetweenResources }
                chosenTiles.add(chosenTile)
                baseTerrainsToChosenTiles[firstKeyWithTilesLeft] = baseTerrainsToChosenTiles[firstKeyWithTilesLeft]!!+1
            }
            // Either we got them all, or we're not going to get anything better
            if (chosenTiles.size == number || distanceBetweenResources == 1) return chosenTiles
        }
        throw Exception("Couldn't choose suitable tiles for $number resources!")
    }
}


class RiverCoordinate(val position: Vector2, val bottomRightOrLeft: BottomRightOrLeft) {
    enum class BottomRightOrLeft {
        /** 7 O'Clock of the tile */
        BottomLeft,

        /** 5 O'Clock of the tile */
        BottomRight
    }

    fun getAdjacentPositions(): Sequence<RiverCoordinate> {
        // What's nice is that adjacents are always the OPPOSITE in terms of right-left - rights are adjacent to only lefts, and vice-versa
        // This means that a lot of obviously-wrong assignments are simple to spot
        if (bottomRightOrLeft == BottomRightOrLeft.BottomLeft) {
            return sequenceOf(RiverCoordinate(position, BottomRightOrLeft.BottomRight), // same tile, other side
                    RiverCoordinate(position.cpy().add(1f, 0f), BottomRightOrLeft.BottomRight), // tile to MY top-left, take its bottom right corner
                    RiverCoordinate(position.cpy().add(0f, -1f), BottomRightOrLeft.BottomRight) // Tile to MY bottom-left, take its bottom right
            )
        } else {
            return sequenceOf(RiverCoordinate(position, BottomRightOrLeft.BottomLeft), // same tile, other side
                    RiverCoordinate(position.cpy().add(0f, 1f), BottomRightOrLeft.BottomLeft), // tile to MY top-right, take its bottom left
                    RiverCoordinate(position.cpy().add(-1f, 0f), BottomRightOrLeft.BottomLeft)  // tile to MY bottom-right, take its bottom left
            )
        }
    }
}
