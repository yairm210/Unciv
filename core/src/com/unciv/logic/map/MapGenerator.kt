package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.HexMath
import com.unciv.models.Counter
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.River
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.*
import kotlin.random.Random


class MapGenerator(val ruleset: Ruleset) {

    private val MAX_RIVER_LENGTH = 10

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
        generateLand(map)
        divideIntoBiomes(map)
        spawnLakesAndCoasts(map)
        createRivers(map)
        randomizeTiles(map)
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

    /**
     * generates a single river at the center of the map.
     * this is a proof of concept to show that the spawning of rivers works
     * and other aspects of the game can be adjusted to fully incorporate them.
     */
    private fun createRivers(map: TileMap) {
        map.rivers = ArrayList(Collections.singletonList(createRiver(Vector2(0F, 0F), map)))
    }

    private fun createRiver(source: Vector2, map: TileMap): River {
        val river = River()
        var currentRiverTiles = generateFirstRiverPair(source)
        var length = 0
        while (areLegalRiverTiles(currentRiverTiles, map) && length < MAX_RIVER_LENGTH) {
            length++
            river.course.add(currentRiverTiles)
            currentRiverTiles = generateNextRiverPair(currentRiverTiles)
        }
        return river
    }

    private fun generateFirstRiverPair(source: Vector2): Pair<Vector2, Vector2> {
        return Pair(source, Vector2(source).add(1F, 0F))
    }

    private fun generateNextRiverPair(oldPair: Pair<Vector2, Vector2>): Pair<Vector2, Vector2> {
        val newSecond: Vector2 = oldPair.first
        val newFirst = if (oldPair.first.x + 1 == oldPair.second.x) {
            Vector2(newSecond).sub(0F, 1F)
        } else {
            Vector2(newSecond).sub(1F, 0F)
        }
        return Pair(newFirst, newSecond)
    }

    private fun areLegalRiverTiles(currentRiverTiles: Pair<Vector2, Vector2>, map: TileMap): Boolean {
        return (!(map[currentRiverTiles.first].isWater
                || map[currentRiverTiles.second].isWater)
                && map.contains(currentRiverTiles.first)
                && map.contains(currentRiverTiles.second))
    }


    private fun randomizeTiles(tileMap: TileMap) {

        for (tile in tileMap.values) {
            if (tile.getBaseTerrain().type == TerrainType.Land && RNG.nextDouble() < tileMap.mapParameters.mountainProbability) {
                tile.baseTerrain = Constants.mountain
                tile.setTransients()
            }
            addRandomTerrainFeature(tile, tileMap.mapParameters)
        }
    }

    private fun divideIntoBiomes(tileMap: TileMap) {
        val averageTilesPerArea = tileMap.mapParameters.tilesPerBiomeArea
        val waterPercent = tileMap.mapParameters.waterProbability

        val maxLatitude = tileMap.values.map { abs(HexMath.getLatitude(it.position)) }.max()!!

        val areas = ArrayList<Area>()

        val terrains = ruleset.terrains.values
                .filter { it.type === TerrainType.Land && it.name != Constants.lakes && it.name != Constants.mountain }

        // So we know it's not chosen
        for (tile in tileMap.values.filter { it.baseTerrain == Constants.grassland })
            tile.baseTerrain = ""

        while (tileMap.values.any { it.baseTerrain == "" }) // the world could be split into lots off tiny islands, and every island deserves land types
        {
            val emptyTiles = tileMap.values.filter { it.baseTerrain == "" }.toMutableList()
            val numberOfSeeds = ceil(emptyTiles.size / averageTilesPerArea.toFloat()).toInt()


            for (i in 0 until numberOfSeeds) {
                var terrain = if (RNG.nextDouble() < waterPercent) Constants.ocean
                else terrains.random(RNG).name

                val tile = emptyTiles.random(RNG)

                val desertBand = maxLatitude * 0.5 * tileMap.mapParameters.temperatureExtremeness
                val tundraBand = maxLatitude * (1 - 0.5 * tileMap.mapParameters.temperatureExtremeness)

                if (abs(HexMath.getLatitude(tile.position)) < desertBand) {

                    if (terrain in arrayOf(Constants.grassland, Constants.tundra))
                        terrain = Constants.desert

                } else if (abs(HexMath.getLatitude(tile.position)) > tundraBand) {

                    if (terrain in arrayOf(Constants.grassland, Constants.plains, Constants.desert, Constants.ocean))
                        terrain = Constants.tundra

                } else {
                    if (terrain == Constants.tundra) terrain = Constants.plains
                    else if (terrain == Constants.desert) terrain = Constants.grassland
                }

                val area = Area(terrain)
                emptyTiles -= tile
                area.addTile(tile)
                areas += area
            }

            expandAreas(areas)
            expandAreas(areas)
        }

        for (tile in tileMap.values)
            tile.setTransients()
    }

    private fun expandAreas(areas: ArrayList<Area>) {
        val expandableAreas = ArrayList<Area>(areas)

        while (expandableAreas.isNotEmpty()) {
            val areaToExpand = expandableAreas.random(RNG)
            if (areaToExpand.tiles.size >= 20) {
                expandableAreas -= areaToExpand
                continue
            }

            val availableExpansionTiles = areaToExpand.tiles
                    .flatMap { it.neighbors }.distinct()
                    .filter { it.baseTerrain == "" }

            if (availableExpansionTiles.isEmpty()) expandableAreas -= areaToExpand
            else {
                val expansionTile = availableExpansionTiles.random(RNG)
                areaToExpand.addTile(expansionTile)

                val areasToJoin = areas.filter {
                    it.terrain == areaToExpand.terrain
                            && it != areaToExpand
                            && it.tiles.any { tile -> tile in expansionTile.neighbors }
                }
                for (area in areasToJoin) {
                    areaToExpand.tiles += area.tiles
                    areas.remove(area)
                    expandableAreas.remove(area)
                }
            }
        }
    }

    private fun addRandomTerrainFeature(tileInfo: TileInfo, mapParameters: MapParameters) {
        if (tileInfo.getBaseTerrain().canHaveOverlay && RNG.nextDouble() < mapParameters.terrainFeatureRichness) {
            val secondaryTerrains = ruleset.terrains.values
                    .filter { it.type === TerrainType.TerrainFeature &&
                            it.occursOn != null &&
                            it.occursOn.contains(tileInfo.baseTerrain) }
            if (secondaryTerrains.any())
                tileInfo.terrainFeature = secondaryTerrains.random(RNG).name
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

    private fun spreadResources(mapToReturn: TileMap) {
        val distance = mapToReturn.mapParameters.size.radius
        for (tile in mapToReturn.values)
            if (tile.resource != null)
                tile.resource = null

        spreadStrategicResources(mapToReturn, distance)
        spreadResource(mapToReturn, distance, ResourceType.Luxury)
        spreadResource(mapToReturn, distance, ResourceType.Bonus)
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
                Constants.barringerCrater -> spawnBarringerCrater(tileMap, ruleset)
                Constants.mountFuji -> spawnMountFuji(tileMap, ruleset)
                Constants.grandMesa -> spawnGrandMesa(tileMap, ruleset)
                Constants.greatBarrierReef -> spawnGreatBarrierReef(tileMap, ruleset, mapRadius)
                Constants.krakatoa -> spawnKrakatoa(tileMap, ruleset)
                Constants.rockOfGibraltar -> spawnRockOfGibraltar(tileMap, ruleset)
                Constants.oldFaithful -> spawnOldFaithful(tileMap, ruleset)
                Constants.cerroDePotosi -> spawnCerroDePotosi(tileMap, ruleset)
                Constants.elDorado -> spawnElDorado(tileMap, ruleset)
                Constants.fountainOfYouth -> spawnFountainOfYouth(tileMap, ruleset)
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
    private fun spawnBarringerCrater(mapToReturn: TileMap, ruleset: Ruleset) {
        val wonder = ruleset.terrains[Constants.barringerCrater]!!
        val suitableLocations = mapToReturn.values.filter { it.resource == null && it.improvement == null
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
    private fun spawnMountFuji(mapToReturn: TileMap, ruleset: Ruleset) {
        val wonder = ruleset.terrains[Constants.mountFuji]!!
        val suitableLocations = mapToReturn.values.filter { it.resource == null && it.improvement == null
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
    private fun spawnGrandMesa(mapToReturn: TileMap, ruleset: Ruleset) {
        val wonder = ruleset.terrains[Constants.grandMesa]!!
        val suitableLocations = mapToReturn.values.filter { it.resource == null && it.improvement == null
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
    TODO: investigate Great Barrier Reef placement requirements
    */
    private fun spawnGreatBarrierReef(mapToReturn: TileMap, ruleset: Ruleset, mapRadius: Int) {
        val wonder = ruleset.terrains[Constants.greatBarrierReef]!!
        val maxLatitude = abs(HexMath.getLatitude(Vector2(mapRadius.toFloat(), mapRadius.toFloat())))
        val suitableLocations = mapToReturn.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && abs(HexMath.getLatitude(it.position)) > maxLatitude * 0.1
                && abs(HexMath.getLatitude(it.position)) < maxLatitude * 0.7
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
                    .random()

            location2.naturalWonder = wonder.name
            location2.baseTerrain = wonder.turnsInto!!
            location2.terrainFeature = null
        }
    }

    /*
    Krakatoa: Must spawn in the ocean next to at least 1 shallow water tile; cannot be adjacent
    to ice; changes tiles around it to shallow water; mountain
    TODO: cannot be adjacent to ice
    */
    private fun spawnKrakatoa(mapToReturn: TileMap, ruleset: Ruleset) {
        val wonder = ruleset.terrains[Constants.krakatoa]!!
        val suitableLocations = mapToReturn.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.any { neighbor -> neighbor.getBaseTerrain().name == Constants.coast }
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
    TODO: investigate Rock of Gibraltar placement requirements
    */
    private fun spawnRockOfGibraltar(mapToReturn: TileMap, ruleset: Ruleset) {
        val wonder = ruleset.terrains[Constants.rockOfGibraltar]!!
        val suitableLocations = mapToReturn.values.filter { it.resource == null && it.improvement == null
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
    private fun spawnOldFaithful(mapToReturn: TileMap, ruleset: Ruleset) {
        val wonder = ruleset.terrains[Constants.oldFaithful]!!
        val suitableLocations = mapToReturn.values.filter { it.resource == null && it.improvement == null
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
    private fun spawnCerroDePotosi(mapToReturn: TileMap, ruleset: Ruleset) {
        val wonder = ruleset.terrains[Constants.cerroDePotosi]!!
        val suitableLocations = mapToReturn.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.any { neighbor -> neighbor.getBaseTerrain().name == Constants.hill }
        }

        trySpawnOnSuitableLocation(suitableLocations, wonder)
    }

    /*
    El Dorado: Must be next to at least 1 jungle tile; avoids oceans; becomes flatland plains
    */
    private fun spawnElDorado(mapToReturn: TileMap, ruleset: Ruleset) {
        val wonder = ruleset.terrains[Constants.elDorado]!!
        val suitableLocations = mapToReturn.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name)
                && it.neighbors.any { neighbor -> neighbor.getLastTerrain().name == Constants.jungle }
        }

        trySpawnOnSuitableLocation(suitableLocations, wonder)
    }

    /*
    Fountain of Youth: Avoids oceans; becomes flatland plains
    */
    private fun spawnFountainOfYouth(mapToReturn: TileMap, ruleset: Ruleset) {
        val wonder = ruleset.terrains[Constants.fountainOfYouth]!!
        val suitableLocations = mapToReturn.values.filter { it.resource == null && it.improvement == null
                && wonder.occursOn!!.contains(it.getLastTerrain().name) }

        trySpawnOnSuitableLocation(suitableLocations, wonder)
    }
    //endregion

    // Here, we need each specific resource to be spread over the map - it matters less if specific resources are near each other
    private fun spreadStrategicResources(mapToReturn: TileMap, distance: Int) {
        val resourcesOfType = ruleset.tileResources.values.filter { it.resourceType == ResourceType.Strategic }
        for (resource in resourcesOfType) {
            val suitableTiles = mapToReturn.values
                    .filter { it.resource == null && resource.terrainsCanBeFoundOn.contains(it.getLastTerrain().name) }

            val averageTilesPerResource = 15 * resourcesOfType.count()
            val numberOfResources = mapToReturn.values.count { it.isLand && !it.getBaseTerrain().impassable } / averageTilesPerResource

            val locations = chooseSpreadOutLocations(numberOfResources, suitableTiles, distance)

            for (location in locations) location.resource = resource.name
        }
    }

    // Here, we need there to be some luxury/bonus resource - it matters less what
    private fun spreadResource(mapToReturn: TileMap, distance: Int, resourceType: ResourceType) {
        val resourcesOfType = ruleset.tileResources.values.filter { it.resourceType == resourceType }

        val suitableTiles = mapToReturn.values
                .filter { it.resource == null && resourcesOfType.any { r -> r.terrainsCanBeFoundOn.contains(it.getLastTerrain().name) } }
        val numberOfResources = (mapToReturn.values.count { it.isLand && !it.getBaseTerrain().impassable } * mapToReturn.mapParameters.resourceRichness).toInt()
        val locations = chooseSpreadOutLocations(numberOfResources, suitableTiles, distance)

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
            val baseTerrainsToChosenTiles = HashMap<String,Int>()
            for(tileInfo in availableTiles){
                if(tileInfo.baseTerrain !in baseTerrainsToChosenTiles)
                    baseTerrainsToChosenTiles.put(tileInfo.baseTerrain,0)
            }

            for (i in 1..numberOfResources) {
                if (availableTiles.isEmpty()) break
                val orderedKeys = baseTerrainsToChosenTiles.entries
                        .sortedBy { it.value }.map { it.key }
                val firstKeyWithTilesLeft = orderedKeys
                        .first { availableTiles.any { tile -> tile.baseTerrain== it} }
                val chosenTile = availableTiles.filter { it.baseTerrain==firstKeyWithTilesLeft }.random()
                availableTiles = availableTiles.filter { it.arialDistanceTo(chosenTile) > distanceBetweenResources }
                chosenTiles.add(chosenTile)
                baseTerrainsToChosenTiles[firstKeyWithTilesLeft] = baseTerrainsToChosenTiles[firstKeyWithTilesLeft]!!+1
            }
            // Either we got them all, or we're not going to get anything better
            if (chosenTiles.size == numberOfResources || distanceBetweenResources == 1) return chosenTiles
        }
        throw Exception("Couldn't choose suitable tiles for $numberOfResources resources!")
    }

    companion object MapLandmassGenerator {
        var RNG = Random(42)

        fun generateLand(tileMap: TileMap) {
            when (tileMap.mapParameters.type) {
                MapType.pangaea -> createPangea(tileMap)
                MapType.continents -> createTwoContinents(tileMap)
                MapType.perlin -> createPerlin(tileMap)
                MapType.default -> generateLandCellularAutomata(tileMap)
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

                when {
                    elevation < 0 -> tile.baseTerrain = Constants.ocean
                    else -> tile.baseTerrain = Constants.grassland
                }
            }
        }

        private fun createPangea(tileMap: TileMap) {
            val elevationSeed = RNG.nextInt().toDouble()
            for (tile in tileMap.values) {
                var elevation = getPerlinNoise(tile, elevationSeed)
                elevation = (elevation + getCircularNoise(tile, tileMap) ) / 2.0

                when {
                    elevation < 0 -> tile.baseTerrain = Constants.ocean
                    else -> tile.baseTerrain = Constants.grassland
                }
            }
        }

        private fun createTwoContinents(tileMap: TileMap) {
            val elevationSeed = RNG.nextInt().toDouble()
            for (tile in tileMap.values) {
                var elevation = getPerlinNoise(tile, elevationSeed)
                elevation = (elevation + getTwoContinentsTransform(tile, tileMap)) / 2.0

                when {
                    elevation < 0 -> tile.baseTerrain = Constants.ocean
                    else -> tile.baseTerrain = Constants.grassland
                }
            }
        }

        private fun getCircularNoise(tileInfo: TileInfo, tileMap: TileMap): Double {
            val randomScale = RNG.nextDouble()
            val distanceFactor =  percentualDistanceToCenter(tileInfo, tileMap)

            return min(0.3, 1.0 - (5.0 * distanceFactor * distanceFactor + randomScale) / 3.0)
        }

        private fun getTwoContinentsTransform(tileInfo: TileInfo, tileMap: TileMap): Double {
            val randomScale = RNG.nextDouble()
            val maxLongitude = abs(tileMap.values.map { abs(HexMath.getLongitude(it.position)) }.max()!!)
            val longitudeFactor = abs(HexMath.getLongitude(tileInfo.position))/maxLongitude

            return min(0.0,-1.0 + (5.0 * longitudeFactor.pow(0.7f) + randomScale) / 3.0)
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

        private fun getPerlinNoise(tile: TileInfo, seed: Double,
                                   nOctaves: Int = 6,
                                   persistence: Double = 0.5,
                                   lacunarity: Double = 2.0,
                                   scale: Double = 10.0): Double {
            val worldCoords = HexMath.hex2WorldCoords(tile.position)
            return Perlin.noise3d(worldCoords.x.toDouble(), worldCoords.y.toDouble(), seed, nOctaves, persistence, lacunarity, scale)
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
            val landProbability = mapParameters.landProbability
            val mapRadius = mapParameters.size.radius

            // default
            if (HexMath.getDistance(Vector2.Zero, tileInfo.position) > 0.9f * mapRadius) {
                if (RNG.nextDouble() < 0.1) return TerrainType.Land else return TerrainType.Water
            }
            if (HexMath.getDistance(Vector2.Zero, tileInfo.position) > 0.85f * mapRadius) {
                if (RNG.nextDouble() < 0.2) return TerrainType.Land else return TerrainType.Water
            }
            if (RNG.nextDouble() < landProbability) return TerrainType.Land else return TerrainType.Water
        }

        // endregion
    }
}

class Area(var terrain: String) {
    val tiles = ArrayList<TileInfo>()
    fun addTile(tileInfo: TileInfo) {
        tiles += tileInfo
        tileInfo.baseTerrain = terrain
    }
}

