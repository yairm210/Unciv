package com.unciv.logic.map.mapgenerator

import com.unciv.Constants
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.utils.debug
import kotlin.math.abs
import kotlin.math.roundToInt

class NaturalWonderGenerator(val ruleset: Ruleset, val randomness: MapGenerationRandomness) {

    private val blockedTiles = HashSet<Tile>()

    /*
    https://gaming.stackexchange.com/questions/95095/do-natural-wonders-spawn-more-closely-to-city-states/96479
    https://www.reddit.com/r/civ/comments/1jae5j/information_on_the_occurrence_of_natural_wonders/
    Above all, look in assignstartingplots.lua! The wonders are always attempted to be placed in order of
    which has the least amount of candidate tiles. There is a minimum distance between wonders equal
    to the map height / 5.
    */
    fun spawnNaturalWonders(tileMap: TileMap) {
        if (tileMap.mapParameters.noNaturalWonders)
            return
        val mapRadius = tileMap.mapParameters.mapSize.radius
        // number of Natural Wonders scales linearly with mapRadius
        val numberToSpawn = ruleset.modOptions.constants.run {
            mapRadius * naturalWonderCountMultiplier + naturalWonderCountAddedConstant
        }.roundToInt()

        val chosenWonders = mutableListOf<Terrain>()
        val wonderCandidateTiles = mutableMapOf<Terrain, Collection<Tile>>()
        val allNaturalWonders = ruleset.terrains.values
                .filter { it.type == TerrainType.NaturalWonder }.toMutableList()
        val spawned = mutableListOf<Terrain>()

        while (allNaturalWonders.isNotEmpty() && chosenWonders.size < numberToSpawn) {
            val totalWeight = allNaturalWonders.sumOf { it.weight }.toFloat()
            val random = randomness.RNG.nextDouble()
            var sum = 0f
            for (wonder in allNaturalWonders) {
                sum += wonder.weight / totalWeight
                if (random <= sum) {
                    chosenWonders.add(wonder)
                    allNaturalWonders.remove(wonder)
                    break
                }
            }
        }

        val tilesTooCloseToSpawnLocations = tileMap.startingLocationsByNation.values.flatten().flatMap { it.getTilesInDistance(5) }.toSet()

        // First attempt to spawn the chosen wonders in order of least candidate tiles
        chosenWonders.forEach {
            wonderCandidateTiles[it] = getCandidateTilesForWonder(tileMap, it, tilesTooCloseToSpawnLocations)
        }
        chosenWonders.sortBy { wonderCandidateTiles[it]!!.size }
        for (wonder in chosenWonders) {
            if (trySpawnOnSuitableLocation(wonderCandidateTiles[wonder]!!.filter { it !in blockedTiles }, wonder))
                spawned.add(wonder)
        }


        // If some wonders were not able to be spawned we will pull a wonder from the fallback list
        if (spawned.size < numberToSpawn) {
            // Now we have to do some more calculations. Unfortunately we have to calculate candidate tiles for everyone.
            allNaturalWonders.forEach {
                wonderCandidateTiles[it] = getCandidateTilesForWonder(tileMap, it, tilesTooCloseToSpawnLocations)
            }
            allNaturalWonders.sortBy { wonderCandidateTiles[it]!!.size }
            for (wonder in allNaturalWonders) {
                if (trySpawnOnSuitableLocation(wonderCandidateTiles[wonder]!!.filter { it !in blockedTiles }
                            .toList(), wonder))
                    spawned.add(wonder)
                if (spawned.size >= numberToSpawn)
                    break
            }
        }

        debug("Natural Wonders for this game: %s", spawned)
    }

    private fun getCandidateTilesForWonder(tileMap: TileMap, naturalWonder: Terrain, tilesTooCloseToSpawnLocations: Set<Tile>): Collection<Tile> {
        
        val suitableLocations = tileMap.values.filter { tile ->
            tile.resource == null &&
            tile !in tilesTooCloseToSpawnLocations &&
            naturalWonder.occursOn.contains(tile.lastTerrain.name) && 
            fitsTerrainUniques(naturalWonder, tile)
        }

        return suitableLocations
    }


    private fun trySpawnOnSuitableLocation(suitableLocations: List<Tile>, wonder: Terrain): Boolean {
        val minGroupSize: Int
        val maxGroupSize: Int
        val groupUnique = wonder.getMatchingUniques(UniqueType.NaturalWonderGroups).firstOrNull()
        if (groupUnique == null) {
            minGroupSize = 1
            maxGroupSize = 1
        } else {
            minGroupSize = groupUnique.getIntParam(0)
            maxGroupSize = groupUnique.getIntParam(1)
        }
        val targetGroupSize = if (minGroupSize == maxGroupSize) maxGroupSize
            else (minGroupSize..maxGroupSize).random(randomness.RNG)

        if (suitableLocations.size >= minGroupSize) {
            val location = suitableLocations.random(randomness.RNG)
            val list = mutableListOf(location)

            while (list.size < targetGroupSize) {
                val allNeighbors = list.flatMap { it.neighbors }.minus(list).toHashSet()
                val candidates = suitableLocations.filter { it in allNeighbors }
                if (candidates.isEmpty()) break
                list.add(candidates.random(randomness.RNG))
            }
            if (list.size >= minGroupSize) {
                for (tileToConvert in list) {
                    placeNaturalWonder(wonder, tileToConvert)
                    // Add all tiles within a certain distance to a blacklist so NW:s don't cluster
                    blockedTiles.addAll(tileToConvert.getTilesInDistance(tileToConvert.tileMap.mapParameters.mapSize.height / 5))
                }

                debug("Natural Wonder %s @%s", wonder.name, location.position)

                return true
            }
        }

        debug("No suitable location for %s", wonder.name)
        return false
    }

    companion object {
        fun placeNaturalWonder(wonder: Terrain, location: Tile) {
            location.naturalWonder = wonder.name
            val turnsIntoObject = location.ruleset.terrains[wonder.turnsInto]
            if (turnsIntoObject != null) {
                clearTile(location)
                location.setBaseTerrain(turnsIntoObject)
            } else {
                clearTile(location, wonder.occursOn)
            }

            val conversionUniques = wonder.getMatchingUniques(UniqueType.NaturalWonderConvertNeighbors, GameContext.IgnoreConditionals)
            if (conversionUniques.none()) return

            for (tile in location.neighbors) {
                val state = GameContext(tile = tile)
                for (unique in conversionUniques) {
                    if (!unique.conditionalsApply(state)) continue
                    val convertTo = unique.params[0]
                    if (tile.baseTerrain == convertTo || convertTo in tile.terrainFeatures) continue
                    if (convertTo == Constants.lakes && tile.isCoastalTile()) continue
                    val terrainObject = location.ruleset.terrains[convertTo] ?: continue
                    if (terrainObject.type == TerrainType.TerrainFeature && tile.baseTerrain !in terrainObject.occursOn) continue
                    if (convertTo == Constants.coast)
                        removeLakesNextToFutureCoast(location, tile)
                    if (terrainObject.type.isBaseTerrain) {
                        clearTile(tile)
                        tile.setBaseTerrain(terrainObject)
                    }
                    if (terrainObject.type == TerrainType.TerrainFeature) {
                        clearTile(tile, tile.terrainFeatures)
                        tile.addTerrainFeature(convertTo)
                    }
                }
            }
        }


        fun fitsTerrainUniques(
            naturalWonderOrTerrainFeature: Terrain,
            tile: Tile
        ): Boolean {
            val continentsRelevant = naturalWonderOrTerrainFeature.hasUnique(UniqueType.NaturalWonderLargerLandmass) ||
                    naturalWonderOrTerrainFeature.hasUnique(UniqueType.NaturalWonderSmallerLandmass)
            val sortedContinents = if (continentsRelevant)
                tile.tileMap.continentSizes.asSequence()
                    .sortedByDescending { it.value }
                    .map { it.key }
                    .toList()
            else listOf()

            return naturalWonderOrTerrainFeature.uniqueObjects.all { unique ->
                when (unique.type) {
                    UniqueType.NaturalWonderNeighborCount -> {
                        val count = tile.neighbors.count {
                            it.matchesWonderFilter(unique.params[1])
                        }
                        count == unique.getIntParam(0)
                    }

                    UniqueType.NaturalWonderNeighborsRange -> {
                        val count = tile.neighbors.count {
                            it.matchesWonderFilter(unique.params[2])
                        }
                        count in unique.getIntParam(0)..unique.getIntParam(1)
                    }

                    UniqueType.NaturalWonderSmallerLandmass -> {
                        tile.getContinent() !in sortedContinents.take(unique.getIntParam(0))
                    }

                    UniqueType.NaturalWonderLargerLandmass -> {
                        tile.getContinent() in sortedContinents.take(unique.getIntParam(0))
                    }

                    UniqueType.NaturalWonderLatitude -> {
                        val lower = tile.tileMap.maxLatitude * unique.getIntParam(0) * 0.01f
                        val upper = tile.tileMap.maxLatitude * unique.getIntParam(1) * 0.01f
                        abs(tile.latitude) in lower..upper
                    }

                    else -> true
                }
            }
        }
        
        private fun Unique.getIntParam(index: Int) = params[index].toInt()

        // location is being converted to a NW, tile is a neighbor to be converted to coast: Ensure that coast won't show invalid rivers or coast touching lakes
        private fun removeLakesNextToFutureCoast(location: Tile, tile: Tile) {
            for (neighbor in tile.neighbors) {
                // This is so we don't have this tile turn into Coast, and then it's touching a Lake tile.
                // We just turn the lake tiles into this kind of tile.
                if (neighbor.baseTerrain == Constants.lakes) {
                    clearTile(neighbor)
                    neighbor.baseTerrain = tile.baseTerrain
                    neighbor.setTerrainTransients()
                }
            }
            location.setConnectedByRiver(tile, false)
        }

        /** Implements [UniqueParameterType.SimpleTerrain][com.unciv.models.ruleset.unique.UniqueParameterType.SimpleTerrain] */
        private fun Tile.matchesWonderFilter(filter: String) = when (filter) {
            "Elevated" -> baseTerrain == Constants.mountain || isHill()
            "Water" -> isWater
            "Land" -> isLand
            Constants.hill -> isHill()
            naturalWonder -> true
            lastTerrain.name -> true
            else -> baseTerrain == filter
        }

        private fun clearTile(tile: Tile, exceptFeatures: List<String> = listOf()) {
            if (tile.terrainFeatures.isNotEmpty() && exceptFeatures != tile.terrainFeatures)
                tile.setTerrainFeatures(tile.terrainFeatures.filter { it in exceptFeatures })
            tile.resource = null
            tile.removeImprovement()
            tile.setTerrainTransients()
        }
    }


    /*
    Barringer Crater: Must be in tundra or desert; cannot be adjacent to grassland; can be adjacent to a maximum
    of 2 mountains and a maximum of 4 hills and mountains; avoids oceans; becomes mountain

    Grand Mesa: Must be in plains, desert, or tundra, and must be adjacent to at least 2 hills;
    cannot be adjacent to grass; can be adjacent to a maximum of 2 mountains; avoids oceans; becomes mountain

    Mt. Fuji: Must be in grass or plains; avoids oceans and the biggest landmass; cannot be adjacent to tundra,
    desert, marsh, or mountains;can be adjacent to a maximum of 2 hills; becomes mountain

    Great Barrier Reef: Specifics currently unknown;
    Assumption: at least 1 neighbour coast; no tundra; at least 1 neighbour coast; becomes coast

    Krakatoa: Must spawn in the ocean next to at least 1 shallow water tile; cannot be adjacent
    to ice; changes tiles around it to shallow water; mountain

    Rock of Gibraltar: Specifics currently unknown
    Assumption: spawn on grassland, at least 1 coast and 1 mountain adjacent;
    turn neighbours into coast)

    Old Faithful: Must be adjacent to at least 3 hills and mountains; cannot be adjacent to
    more than 4 mountains, and cannot be adjacent to more than 3 desert or 3 tundra tiles;
    avoids oceans; becomes mountain

    Cerro de Potosi: Must be adjacent to at least 1 hill; avoids oceans; becomes mountain

    El Dorado: Must be next to at least 1 jungle tile; avoids oceans; becomes flatland plains

    Fountain of Youth: Avoids oceans; becomes flatland plains

    // G&K Natural Wonders

    Mount Kailash: Must be in plains or grassland, and must be adjacent to at least 4 hills and/or mountains;
    cannot be adjacent to marshes; can be adjacent to a maximum of 1 desert tile; avoids oceans; becomes mountain

    Mount Sinai: Must be in plains or desert, and must be adjacent to a minimum of 3 desert tiles;
    cannot be adjacent to tundra, marshes, or grassland; avoids oceans; becomes mountain

    Sri Pada: Must be in a grass or plains; cannot be adjacent to desert, tundra, or marshes;
    avoids the biggest landmass ; becomes mountain

    Uluru: Must be in plains or desert, and must be adjacent to a minimum of 3 plains tiles;
    cannot be adjacent to grassland, tundra, or marshes; avoids oceans; becomes mountain

    //BNW Natural Wonders

    King Solomon's Mines: Cannot be adjacent to more than 2 mountains; avoids oceans; becomes flatland plains

    Lake Victoria: Avoids oceans; becomes flatland plains

    Mount Kilimanjaro:  Must be in plains or grassland, and must be adjacent to at least 2 hills;
    cannot be adjacent to more than 2 mountains; avoids oceans; becomes mountain
    */

}
