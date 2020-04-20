package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.HexMath
import com.unciv.models.Counter
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import kotlin.math.*
import kotlin.random.Random


class MapGenerator(val ruleset: Ruleset) {

    fun generateMap(mapParameters: MapParameters, seed: Long = System.currentTimeMillis()): TileMap {
        val mapRadius = mapParameters.size.radius
        val mapType = mapParameters.type
        val map: TileMap

        if (mapParameters.shape == MapShape.rectangular) {
            val size = HexMath.getEquivalentRectangularSize(mapRadius)
            map = TileMap(size.x.toInt(), size.y.toInt(), ruleset)
        }
        else
            map = TileMap(mapRadius, ruleset)

        map.mapParameters = mapParameters
        map.mapParameters.seed = seed

        if (mapType == MapType.empty)
            return map

        seedRNG(seed)
        generateLand(map,ruleset)
        raiseMountainsAndHills(map)
        applyHumidityAndTemperature(map)
        spawnLakesAndCoasts(map)
        spawnVegetation(map)
        spawnRareFeatures(map)
        spawnIce(map)
        spreadResources(map)
        spreadAncientRuins(map)
        spawnNaturalWonders(map)
        return map
    }

    private fun seedRNG(seed: Long) {
        RNG = Random(seed)
        println("RNG seeded with $seed")
    }

    private fun spawnLakesAndCoasts(map: TileMap) {

        //define lakes
        var waterTiles = map.values.filter { it.isWater }

        val tilesInArea = ArrayList<TileInfo>()
        val tilesToCheck = ArrayList<TileInfo>()

        while (waterTiles.isNotEmpty()) {
            val initialWaterTile = waterTiles.random(RNG)
            tilesInArea += initialWaterTile
            tilesToCheck += initialWaterTile
            waterTiles -= initialWaterTile

            // Floodfill to cluster water tiles
            while (tilesToCheck.isNotEmpty()) {
                val tileWeAreChecking = tilesToCheck.random(RNG)
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
            val coastLength = max(1, RNG.nextInt(max(1, map.mapParameters.maxCoastExtension)))
            if (tile.getTilesInDistance(coastLength).any { it.isLand }) {
                tile.baseTerrain = Constants.coast
                tile.setTransients()
            }
        }
    }

    private fun spreadAncientRuins(map: TileMap) {
        if(map.mapParameters.noRuins)
            return
        val suitableTiles = map.values.filter { it.isLand && !it.getBaseTerrain().impassable }
        val locations = chooseSpreadOutLocations(suitableTiles.size/100,
                suitableTiles, 10)
        for(tile in locations)
            tile.improvement = Constants.ancientRuins
    }

    private fun spreadResources(tileMap: TileMap) {
        val distance = tileMap.mapParameters.size.radius
        for (tile in tileMap.values)
            if (tile.resource != null)
                tile.resource = null

        spreadStrategicResources(tileMap, distance)
        spreadResources(tileMap, distance, ResourceType.Luxury)
        spreadResources(tileMap, distance, ResourceType.Bonus)
    }

    //region natural-wonders

    /*
    https://gaming.stackexchange.com/questions/95095/do-natural-wonders-spawn-more-closely-to-city-states/96479
    https://www.reddit.com/r/civ/comments/1jae5j/information_on_the_occurrence_of_natural_wonders/
    */
    private fun spawnNaturalWonders(tileMap: TileMap) {
        if (tileMap.mapParameters.noNaturalWonders)
            return
        val mapRadius = tileMap.mapParameters.size.radius
        // number of Natural Wonders scales linearly with mapRadius as #wonders = mapRadius * 0.13133208 - 0.56128831
        val numberToSpawn = round(mapRadius * 0.13133208f - 0.56128831f).toInt()

        val toBeSpawned = ArrayList<Terrain>()
        val allNaturalWonders = ruleset.terrains.values
                .filter { it.type == TerrainType.NaturalWonder }.toMutableList()

        while (allNaturalWonders.isNotEmpty() && toBeSpawned.size < numberToSpawn) {
            val totalWeight = allNaturalWonders.map { it.weight }.sum().toFloat()
            val random = RNG.nextDouble()
            var sum = 0f
            for (wonder in allNaturalWonders) {
                sum += wonder.weight/totalWeight
                if (random <= sum) {
                    toBeSpawned.add(wonder)
                    allNaturalWonders.remove(wonder)
                    break
                }
            }
        }

        println("Natural Wonders for this game: $toBeSpawned")

        for (wonder in toBeSpawned) {
            when (wonder.name) {
                Constants.barringerCrater -> spawnBarringerCrater(tileMap)
                Constants.mountFuji -> spawnMountFuji(tileMap)
                Constants.grandMesa -> spawnGrandMesa(tileMap)
                Constants.greatBarrierReef -> spawnGreatBarrierReef(tileMap)
                Constants.krakatoa -> spawnKrakatoa(tileMap)
                Constants.rockOfGibraltar -> spawnRockOfGibraltar(tileMap)
                Constants.oldFaithful -> spawnOldFaithful(tileMap)
                Constants.cerroDePotosi -> spawnCerroDePotosi(tileMap)
                Constants.elDorado -> spawnElDorado(tileMap)
                Constants.fountainOfYouth -> spawnFountainOfYouth(tileMap)
            }
        }
    }

    private fun trySpawnOnSuitableLocation(suitableLocations: List<TileInfo>, wonder: Terrain): TileInfo? {
        if (suitableLocations.isNotEmpty()) {
            val location = suitableLocations.random()
            location.naturalWonder = wonder.name
            location.baseTerrain = wonder.turnsInto!!
            location.terrainFeature = null
            return location
        }

        println("No suitable location for ${wonder.name}")
        return null
    }


    /*
    Must be in tundra or desert; cannot be adjacent to grassland; can be adjacent to a maximum
    of 2 mountains and a maximum of 4 hills and mountains; avoids oceans; becomes mountain
    */
    private fun spawnBarringerCrater(tileMap: TileMap) {
        val wonder = ruleset.terrains[Constants.barringerCrater]!!
        val suitableLocations = tileMap.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.none { neighbor -> neighbor.getBaseTerrain().name == Constants.grassland }
                && it.neighbors.count{ neighbor -> neighbor.getBaseTerrain().name == Constants.mountain } <= 2
                && it.neighbors.count{ neighbor -> neighbor.getBaseTerrain().name == Constants.mountain || neighbor.getBaseTerrain().name == Constants.hill} <= 4
        }

        trySpawnOnSuitableLocation(suitableLocations, wonder)
    }

    /*
    Mt. Fuji: Must be in grass or plains; cannot be adjacent to tundra, desert, marsh, or mountains;
    can be adjacent to a maximum of 2 hills; becomes mountain
    */
    private fun spawnMountFuji(tileMap: TileMap) {
        val wonder = ruleset.terrains[Constants.mountFuji]!!
        val suitableLocations = tileMap.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.none { neighbor -> neighbor.getBaseTerrain().name == Constants.tundra }
                && it.neighbors.none { neighbor -> neighbor.getBaseTerrain().name == Constants.desert }
                && it.neighbors.none { neighbor -> neighbor.getBaseTerrain().name == Constants.mountain }
                && it.neighbors.none { neighbor -> neighbor.getLastTerrain().name == Constants.marsh }
                && it.neighbors.count{ neighbor -> neighbor.getBaseTerrain().name == Constants.hill } <= 2
        }

        trySpawnOnSuitableLocation(suitableLocations, wonder)
    }

    /*
    Grand Mesa: Must be in plains, desert, or tundra, and must be adjacent to at least 2 hills;
    cannot be adjacent to grass; can be adjacent to a maximum of 2 mountains; avoids oceans; becomes mountain
    */
    private fun spawnGrandMesa(tileMap: TileMap) {
        val wonder = ruleset.terrains[Constants.grandMesa]!!
        val suitableLocations = tileMap.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.count{ neighbor -> neighbor.getBaseTerrain().name == Constants.hill } >= 2
                && it.neighbors.none { neighbor -> neighbor.getBaseTerrain().name == Constants.grassland }
                && it.neighbors.count { neighbor -> neighbor.getBaseTerrain().name == Constants.mountain } <= 2
        }

        trySpawnOnSuitableLocation(suitableLocations, wonder)
    }

    /*
    Great Barrier Reef: Specifics currently unknown;
    Assumption: at least 1 neighbour not water; no tundra; at least 1 neighbour coast; becomes coast
    */
    private fun spawnGreatBarrierReef(tileMap: TileMap) {
        val wonder = ruleset.terrains[Constants.greatBarrierReef]!!
        val suitableLocations = tileMap.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && abs(it.latitude) > tileMap.maxLatitude * 0.1
                && abs(it.latitude) < tileMap.maxLatitude * 0.7
                && it.neighbors.all {neighbor -> neighbor.isWater}
                && it.neighbors.any {neighbor ->
                    neighbor.resource == null && neighbor.improvement == null
                            && wonder.occursOn!!.contains(neighbor.getLastTerrain().name)
                            && neighbor.neighbors.all{ it.isWater } }
        }

        val location = trySpawnOnSuitableLocation(suitableLocations, wonder)
        if (location != null) {
            val location2 = location.neighbors
                    .filter { it.resource == null && it.improvement == null
                            && wonder.occursOn!!.contains(it.getLastTerrain().name)
                            && it.neighbors.all{ it.isWater } }
                    .toList().random()

            location2.naturalWonder = wonder.name
            location2.baseTerrain = wonder.turnsInto!!
            location2.terrainFeature = null
        }
    }

    /*
    Krakatoa: Must spawn in the ocean next to at least 1 shallow water tile; cannot be adjacent
    to ice; changes tiles around it to shallow water; mountain
    */
    private fun spawnKrakatoa(tileMap: TileMap) {
        val wonder = ruleset.terrains[Constants.krakatoa]!!
        val suitableLocations = tileMap.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.any { neighbor -> neighbor.getBaseTerrain().name == Constants.coast }
                && it.neighbors.none { neighbor -> neighbor.getLastTerrain().name == Constants.ice}
        }

        val location = trySpawnOnSuitableLocation(suitableLocations, wonder)
        if (location != null) {
            for (tile in location.neighbors) {
                if (tile.baseTerrain == Constants.coast) continue
                tile.baseTerrain = Constants.coast
                tile.terrainFeature = null
                tile.resource = null
                tile.improvement = null
            }
        }
    }

    /*
    Rock of Gibraltar: Specifics currently unknown
    Assumption: spawn on grassland, at least 1 coast and 1 mountain adjacent;
    turn neighbours into coast)
    */
    private fun spawnRockOfGibraltar(tileMap: TileMap) {
        val wonder = ruleset.terrains[Constants.rockOfGibraltar]!!
        val suitableLocations = tileMap.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.any { neighbor -> neighbor.getBaseTerrain().name == Constants.coast }
                && it.neighbors.count { neighbor -> neighbor.getBaseTerrain().name == Constants.mountain } == 1
        }

        val location = trySpawnOnSuitableLocation(suitableLocations, wonder)
        if (location != null) {
            for (tile in location.neighbors) {
                if (tile.baseTerrain == Constants.coast) continue
                if (tile.baseTerrain == Constants.mountain) continue

                tile.baseTerrain = Constants.coast
                tile.terrainFeature = null
                tile.resource = null
                tile.improvement = null
            }
        }
    }

    /*
    Old Faithful: Must be adjacent to at least 3 hills and mountains; cannot be adjacent to
    more than 4 mountains, and cannot be adjacent to more than 3 desert or 3 tundra tiles;
    avoids oceans; becomes mountain
    */
    private fun spawnOldFaithful(tileMap: TileMap) {
        val wonder = ruleset.terrains[Constants.oldFaithful]!!
        val suitableLocations = tileMap.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.count { neighbor -> neighbor.getBaseTerrain().name == Constants.mountain } <= 4
                && it.neighbors.count { neighbor -> neighbor.getBaseTerrain().name == Constants.mountain ||
                                                    neighbor.getBaseTerrain().name == Constants.hill} >= 3
                && it.neighbors.count { neighbor -> neighbor.getBaseTerrain().name == Constants.desert } <= 3
                && it.neighbors.count { neighbor -> neighbor.getBaseTerrain().name == Constants.tundra } <= 3
        }

        trySpawnOnSuitableLocation(suitableLocations, wonder)
    }

    /*
    Cerro de Potosi: Must be adjacent to at least 1 hill; avoids oceans; becomes mountain
    */
    private fun spawnCerroDePotosi(tileMap: TileMap) {
        val wonder = ruleset.terrains[Constants.cerroDePotosi]!!
        val suitableLocations = tileMap.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.any { neighbor -> neighbor.getBaseTerrain().name == Constants.hill }
        }

        trySpawnOnSuitableLocation(suitableLocations, wonder)
    }

    /*
    El Dorado: Must be next to at least 1 jungle tile; avoids oceans; becomes flatland plains
    */
    private fun spawnElDorado(tileMap: TileMap) {
        val wonder = ruleset.terrains[Constants.elDorado]!!
        val suitableLocations = tileMap.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.any { neighbor -> neighbor.getLastTerrain().name == Constants.jungle }
        }

        trySpawnOnSuitableLocation(suitableLocations, wonder)
    }

    /*
    Fountain of Youth: Avoids oceans; becomes flatland plains
    */
    private fun spawnFountainOfYouth(tileMap: TileMap) {
        val wonder = ruleset.terrains[Constants.fountainOfYouth]!!
        val suitableLocations = tileMap.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name) }

        trySpawnOnSuitableLocation(suitableLocations, wonder)
    }
    //endregion

    // Here, we need each specific resource to be spread over the map - it matters less if specific resources are near each other
    private fun spreadStrategicResources(tileMap: TileMap, distance: Int) {
        val strategicResources = ruleset.tileResources.values.filter { it.resourceType == ResourceType.Strategic }
        // passable land tiles (no mountains, no wonders) without resources yet
        val candidateTiles = tileMap.values.filter { it.resource == null && it.isLand && !it.getLastTerrain().impassable }
        val totalNumberOfResources = candidateTiles.size * tileMap.mapParameters.resourceRichness
        val resourcesPerType = (totalNumberOfResources/strategicResources.size).toInt()
        for (resource in strategicResources) {
            // remove the tiles where previous resources have been placed
            val suitableTiles = candidateTiles
                    .filter { it.resource == null && resource.terrainsCanBeFoundOn.contains(it.getBaseTerrain().name)}

            val locations = chooseSpreadOutLocations(resourcesPerType, suitableTiles, distance)

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
                .filter { it.resource == null && resourcesOfType.any { r -> r.terrainsCanBeFoundOn.contains(it.getLastTerrain().name) } }
        val numberOfResources = tileMap.values.count { it.isLand && !it.getBaseTerrain().impassable } *
                tileMap.mapParameters.resourceRichness
        val locations = chooseSpreadOutLocations(numberOfResources.toInt(), suitableTiles, distance)

        val resourceToNumber = Counter<String>()

        for (tile in locations) {
            val possibleResources = resourcesOfType
                    .filter { it.terrainsCanBeFoundOn.contains(tile.getLastTerrain().name) }
                    .map { it.name }
            if (possibleResources.isEmpty()) continue
            val resourceWithLeastAssignments = possibleResources.minBy { resourceToNumber[it]!! }!!
            resourceToNumber.add(resourceWithLeastAssignments, 1)
            tile.resource = resourceWithLeastAssignments
        }
    }

    private fun chooseSpreadOutLocations(numberOfResources: Int, suitableTiles: List<TileInfo>, initialDistance: Int): ArrayList<TileInfo> {

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

            for (i in 1..numberOfResources) {
                if (availableTiles.isEmpty()) break
                val orderedKeys = baseTerrainsToChosenTiles.entries
                        .sortedBy { it.value }.map { it.key }
                val firstKeyWithTilesLeft = orderedKeys
                        .first { availableTiles.any { tile -> tile.baseTerrain== it} }
                val chosenTile = availableTiles.filter { it.baseTerrain==firstKeyWithTilesLeft }.random()
                availableTiles = availableTiles.filter { it.aerialDistanceTo(chosenTile) > distanceBetweenResources }
                chosenTiles.add(chosenTile)
                baseTerrainsToChosenTiles[firstKeyWithTilesLeft] = baseTerrainsToChosenTiles[firstKeyWithTilesLeft]!!+1
            }
            // Either we got them all, or we're not going to get anything better
            if (chosenTiles.size == numberOfResources || distanceBetweenResources == 1) return chosenTiles
        }
        throw Exception("Couldn't choose suitable tiles for $numberOfResources resources!")
    }

    /**
     * [MapParameters.elevationExponent] favors high elevation
     */
    private fun raiseMountainsAndHills(tileMap: TileMap) {
        val elevationSeed = RNG.nextInt().toDouble()
        tileMap.setTransients(ruleset)
        for (tile in tileMap.values.filter { !it.isWater }) {
            var elevation = getPerlinNoise(tile, elevationSeed, scale = 2.0)
                    elevation = abs(elevation).pow(1.0 - tileMap.mapParameters.elevationExponent.toDouble()) * elevation.sign

            if (elevation <= 0.5) tile.baseTerrain = Constants.plains
            else if (elevation <= 0.7) tile.baseTerrain = Constants.hill
            else if (elevation <= 1.0) tile.baseTerrain = Constants.mountain
        }
    }

    /**
     * [MapParameters.tilesPerBiomeArea] to set biomes size
     * [MapParameters.temperatureExtremeness] to favor very high and very low temperatures
     */
    private fun applyHumidityAndTemperature(tileMap: TileMap) {
        val humiditySeed = RNG.nextInt().toDouble()
        val temperatureSeed = RNG.nextInt().toDouble()

        tileMap.setTransients(ruleset)

        val scale = tileMap.mapParameters.tilesPerBiomeArea.toDouble()

        for (tile in tileMap.values) {
            if (tile.isWater || tile.baseTerrain in arrayOf(Constants.mountain, Constants.hill))
                continue

            val humidity = (getPerlinNoise(tile, humiditySeed, scale = scale, nOctaves = 1) + 1.0) / 2.0

            val randomTemperature = getPerlinNoise(tile, temperatureSeed, scale = scale, nOctaves = 1)
            val latitudeTemperature = 1.0 - 2.0 * abs(tile.latitude) / tileMap.maxLatitude
            var temperature = ((5.0 * latitudeTemperature + randomTemperature) / 6.0)
            temperature = abs(temperature).pow(1.0 - tileMap.mapParameters.temperatureExtremeness) * temperature.sign

            tile.baseTerrain = when {
                temperature < -0.4 -> {
                    when {
                        humidity < 0.5 -> Constants.snow
                        else -> Constants.tundra
                    }
                }
                temperature < 0.8 -> {
                    when {
                        humidity < 0.5 -> Constants.plains
                        else -> Constants.grassland
                    }
                }
                temperature <= 1.0 -> {
                    when {
                        humidity < 0.7 -> Constants.desert
                        else -> Constants.plains
                    }
                }
                else -> {
                    println(temperature)
                    Constants.lakes
                }

            }
        }
    }

    /**
     * [MapParameters.vegetationOccurrance] is the threshold for vegetation spawn
     */
    private fun spawnVegetation(tileMap: TileMap) {
        val vegetationSeed = RNG.nextInt().toDouble()
        val candidateTerrains = Constants.vegetation.flatMap{ ruleset.terrains[it]!!.occursOn!! }
        for (tile in tileMap.values.asSequence().filter { it.baseTerrain in candidateTerrains && it.terrainFeature == null}) {
            val vegetation = (getPerlinNoise(tile, vegetationSeed, scale = 3.0, nOctaves = 1) + 1.0) / 2.0

            if (vegetation <= tileMap.mapParameters.vegetationRichness)
                tile.terrainFeature = Constants.vegetation.filter { ruleset.terrains[it]!!.occursOn!!.contains(tile.baseTerrain) }.random(RNG)
        }
    }
    /**
     * [MapParameters.rareFeaturesProbability] is the probability of spawning a rare feature
     */
    private fun spawnRareFeatures(tileMap: TileMap) {
        val rareFeatures = ruleset.terrains.values.filter {
            it.type == TerrainType.TerrainFeature &&
            it.name !in Constants.vegetation &&
            it.name != Constants.ice
        }
        for (tile in tileMap.values.asSequence().filter { it.terrainFeature == null }) {
            if (RNG.nextDouble() <= tileMap.mapParameters.rareFeaturesRichness) {
                val possibleFeatures = rareFeatures.filter { it.occursOn != null && it.occursOn.contains(tile.baseTerrain) }
                if (possibleFeatures.any())
                    tile.terrainFeature = possibleFeatures.random(RNG).name
            }
        }
    }

    /**
     * [MapParameters.temperatureExtremeness] as in [applyHumidityAndTemperature]
     */
    private fun spawnIce(tileMap: TileMap) {
        tileMap.setTransients(ruleset)
        val temperatureSeed = RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            if (tile.baseTerrain !in Constants.sea || tile.terrainFeature != null)
                continue

            val randomTemperature = getPerlinNoise(tile, temperatureSeed, scale = tileMap.mapParameters.tilesPerBiomeArea.toDouble(), nOctaves = 1)
            val latitudeTemperature = 1.0 - 2.0 * abs(tile.latitude) / tileMap.maxLatitude
            var temperature = ((latitudeTemperature + randomTemperature) / 2.0)
            temperature = abs(temperature).pow(1.0 - tileMap.mapParameters.temperatureExtremeness) * temperature.sign
            if (temperature < -0.8)
                tile.terrainFeature = Constants.ice
        }
    }

    companion object MapLandmassGenerator {
        var RNG = Random(42)

        fun generateLand(tileMap: TileMap, ruleset: Ruleset) {
            if(ruleset.terrains.values.none { it.type==TerrainType.Water }) {
                for (tile in tileMap.values)
                    tile.baseTerrain = Constants.grassland
                return
            }
            when (tileMap.mapParameters.type) {
                MapType.pangaea -> createPangea(tileMap)
                MapType.continents -> createTwoContinents(tileMap)
                MapType.perlin -> createPerlin(tileMap)
                MapType.archipelago -> createArchipelago(tileMap)
                MapType.default -> generateLandCellularAutomata(tileMap)
            }
        }
        private fun spawnLandOrWater(tile: TileInfo, elevation: Double, threshold: Double) {
            when {
                elevation < threshold -> tile.baseTerrain = Constants.ocean
                else -> tile.baseTerrain = Constants.grassland
            }
        }

        private fun smooth(tileMap: TileMap) {
            for (tileInfo in tileMap.values) {
                val numberOfLandNeighbors = tileInfo.neighbors.count { it.baseTerrain == Constants.grassland }
                if (RNG.nextFloat() < 0.5f)
                    continue

                if (numberOfLandNeighbors > 3)
                    tileInfo.baseTerrain = Constants.grassland
                else if (numberOfLandNeighbors < 3)
                    tileInfo.baseTerrain = Constants.ocean
            }
        }

        private fun createPerlin(tileMap: TileMap) {
            val elevationSeed = RNG.nextInt().toDouble()
            for (tile in tileMap.values) {
                var elevation = getPerlinNoise(tile, elevationSeed)
                spawnLandOrWater(tile, elevation, tileMap.mapParameters.waterThreshold.toDouble())
            }
        }

        private fun createArchipelago(tileMap: TileMap) {
            val elevationSeed = RNG.nextInt().toDouble()
            for (tile in tileMap.values) {
                var elevation = getRidgedPerlinNoise(tile, elevationSeed)
                spawnLandOrWater(tile, elevation, 0.25 + tileMap.mapParameters.waterThreshold.toDouble())
            }
        }

        private fun createPangea(tileMap: TileMap) {
            val elevationSeed = RNG.nextInt().toDouble()
            for (tile in tileMap.values) {
                var elevation = getPerlinNoise(tile, elevationSeed)
                elevation = (elevation + getCircularNoise(tile, tileMap) ) / 2.0
                spawnLandOrWater(tile, elevation, tileMap.mapParameters.waterThreshold.toDouble())
            }
        }

        private fun createTwoContinents(tileMap: TileMap) {
            val elevationSeed = RNG.nextInt().toDouble()
            for (tile in tileMap.values) {
                var elevation = getPerlinNoise(tile, elevationSeed)
                elevation = (elevation + getTwoContinentsTransform(tile, tileMap)) / 2.0
                spawnLandOrWater(tile, elevation, tileMap.mapParameters.waterThreshold.toDouble())
            }
        }

        private fun getCircularNoise(tileInfo: TileInfo, tileMap: TileMap): Double {
            val randomScale = RNG.nextDouble()
            val distanceFactor =  percentualDistanceToCenter(tileInfo, tileMap)

            return min(0.3, 1.0 - (5.0 * distanceFactor * distanceFactor + randomScale) / 3.0)
        }

        private fun getTwoContinentsTransform(tileInfo: TileInfo, tileMap: TileMap): Double {
            val randomScale = RNG.nextDouble()
            val longitudeFactor = abs(tileInfo.longitude) / tileMap.maxLongitude

            return min(0.2,-1.0 + (5.0 * longitudeFactor.pow(0.6f) + randomScale) / 3.0)
        }

        private fun percentualDistanceToCenter(tileInfo: TileInfo, tileMap: TileMap): Double {
            val mapRadius = tileMap.mapParameters.size.radius
            if (tileMap.mapParameters.shape == MapShape.hexagonal)
                return HexMath.getDistance(Vector2.Zero, tileInfo.position).toDouble()/mapRadius
            else {
                val size = HexMath.getEquivalentRectangularSize(mapRadius)
                return HexMath.getDistance(Vector2.Zero, tileInfo.position).toDouble() / HexMath.getDistance(Vector2.Zero, Vector2(size.x/2, size.y/2))
            }
        }

        /**
         * Generates a perlin noise channel combining multiple octaves
         *
         * [nOctaves] is the number of octaves
         * [persistence] is the scaling factor of octave amplitudes
         * [lacunarity] is the scaling factor of octave frequencies
         * [scale] is the distance the noise is observed from
         */
        private fun getPerlinNoise(tile: TileInfo, seed: Double,
                                   nOctaves: Int = 6,
                                   persistence: Double = 0.5,
                                   lacunarity: Double = 2.0,
                                   scale: Double = 10.0): Double {
            val worldCoords = HexMath.hex2WorldCoords(tile.position)
            return Perlin.noise3d(worldCoords.x.toDouble(), worldCoords.y.toDouble(), seed, nOctaves, persistence, lacunarity, scale)
        }

        /**
         * Generates ridged perlin noise. As for parameters see [getPerlinNoise]
         */
        private fun getRidgedPerlinNoise(tile: TileInfo, seed: Double,
                                         nOctaves: Int = 10,
                                         persistence: Double = 0.5,
                                         lacunarity: Double = 2.0,
                                         scale: Double = 15.0): Double {
            val worldCoords = HexMath.hex2WorldCoords(tile.position)
            return Perlin.ridgedNoise3d(worldCoords.x.toDouble(), worldCoords.y.toDouble(), seed, nOctaves, persistence, lacunarity, scale)
        }

        // region Cellular automata
        private fun generateLandCellularAutomata(tileMap: TileMap) {
            val mapRadius = tileMap.mapParameters.size.radius
            val mapType = tileMap.mapParameters.type
            val numSmooth = 4

            //init
            for (tile in tileMap.values) {
                val terrainType = getInitialTerrainCellularAutomata(tile, tileMap.mapParameters)
                if (terrainType == TerrainType.Land) tile.baseTerrain = Constants.grassland
                else tile.baseTerrain = Constants.ocean
                tile.setTransients()
            }

            //smooth
            val grassland = Constants.grassland
            val ocean = Constants.ocean

            for (loop in 0..numSmooth) {
                for (tileInfo in tileMap.values) {
                    //if (HexMath.getDistance(Vector2.Zero, tileInfo.position) < mapRadius) {
                    val numberOfLandNeighbors = tileInfo.neighbors.count { it.baseTerrain == grassland }
                    if (tileInfo.baseTerrain == grassland) { // land tile
                        if (numberOfLandNeighbors < 3)
                            tileInfo.baseTerrain = ocean
                    } else { // water tile
                        if (numberOfLandNeighbors > 3)
                            tileInfo.baseTerrain = grassland
                    }
                    /*} else {
                        tileInfo.baseTerrain = ocean
                    }*/
                }
            }
        }


        private fun getInitialTerrainCellularAutomata(tileInfo: TileInfo, mapParameters: MapParameters): TerrainType {
            val mapRadius = mapParameters.size.radius

            // default
            if (HexMath.getDistance(Vector2.Zero, tileInfo.position) > 0.9f * mapRadius) {
                if (RNG.nextDouble() < 0.1) return TerrainType.Land else return TerrainType.Water
            }
            if (HexMath.getDistance(Vector2.Zero, tileInfo.position) > 0.85f * mapRadius) {
                if (RNG.nextDouble() < 0.2) return TerrainType.Land else return TerrainType.Water
            }
            if (RNG.nextDouble() < 0.55) return TerrainType.Land else return TerrainType.Water
        }

        // endregion
    }
}
