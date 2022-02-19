package com.unciv.logic.map.mapgenerator

import com.unciv.Constants
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.math.abs
import kotlin.math.round

class NaturalWonderGenerator(val ruleset: Ruleset, val randomness: MapGenerationRandomness) {

    private val allTerrainFeatures = ruleset.terrains.values
        .filter { it.type == TerrainType.TerrainFeature }
        .map { it.name }.toSet()

    /*
    https://gaming.stackexchange.com/questions/95095/do-natural-wonders-spawn-more-closely-to-city-states/96479
    https://www.reddit.com/r/civ/comments/1jae5j/information_on_the_occurrence_of_natural_wonders/
    */
    fun spawnNaturalWonders(tileMap: TileMap) {
        if (tileMap.mapParameters.noNaturalWonders)
            return
        val mapRadius = tileMap.mapParameters.mapSize.radius
        // number of Natural Wonders scales linearly with mapRadius as #wonders = mapRadius * 0.13133208 - 0.56128831
        val numberToSpawn = round(mapRadius * 0.13133208f - 0.56128831f).toInt()

        val spawned = mutableListOf<Terrain>()
        val allNaturalWonders = ruleset.terrains.values
                .filter { it.type == TerrainType.NaturalWonder }.toMutableList()

        while (allNaturalWonders.isNotEmpty() && spawned.size < numberToSpawn) {
            val totalWeight = allNaturalWonders.sumOf { it.weight }.toFloat()
            val random = randomness.RNG.nextDouble()
            var sum = 0f
            for (wonder in allNaturalWonders) {
                sum += wonder.weight / totalWeight
                if (random <= sum) {
                    if (spawnSpecificWonder(tileMap, wonder))
                        spawned.add(wonder)
                    allNaturalWonders.remove(wonder)
                    break
                }
            }
        }

        if (MapGenerator.consoleOutput)
            println("Natural Wonders for this game: $spawned")
    }

    private fun Unique.getIntParam(index: Int) = params[index].toInt()

    private fun spawnSpecificWonder(tileMap: TileMap, naturalWonder: Terrain): Boolean {
        val continentsRelevant = naturalWonder.hasUnique(UniqueType.NaturalWonderLargerLandmass) ||
                naturalWonder.hasUnique(UniqueType.NaturalWonderSmallerLandmass)
        val sortedContinents = if (continentsRelevant)
                tileMap.continentSizes.asSequence()
                .sortedByDescending { it.value }
                .map { it.key }
                .toList()
            else listOf()

        val suitableLocations = tileMap.values.filter { tile->
            tile.resource == null &&
            naturalWonder.occursOn.contains(tile.getLastTerrain().name) &&
            naturalWonder.uniqueObjects.all { unique ->
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
                        val lower = tileMap.maxLatitude * unique.getIntParam(0) * 0.01f
                        val upper = tileMap.maxLatitude * unique.getIntParam(1) * 0.01f
                        abs(tile.latitude) in lower..upper
                    }
                    else -> true
                }
            }
        }

        return trySpawnOnSuitableLocation(suitableLocations, naturalWonder)
    }

    private fun trySpawnOnSuitableLocation(suitableLocations: List<TileInfo>, wonder: Terrain): Boolean {
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

        var convertNeighborsExcept: String? = null
        var convertUnique = wonder.getMatchingUniques(UniqueType.NaturalWonderConvertNeighbors).firstOrNull()
        var convertNeighborsTo = convertUnique?.params?.get(0)
        if (convertNeighborsTo == null) {
            convertUnique = wonder.getMatchingUniques(UniqueType.NaturalWonderConvertNeighborsExcept).firstOrNull()
            convertNeighborsExcept = convertUnique?.params?.get(0)
            convertNeighborsTo = convertUnique?.params?.get(1)
        }

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
                list.forEach {
                    clearTile(it)
                    it.naturalWonder = wonder.name
                    it.baseTerrain = wonder.turnsInto!!
                }
                if (convertNeighborsTo != null) {
                    for (tile in location.neighbors) {
                        if (tile.baseTerrain == convertNeighborsTo) continue
                        if (tile.baseTerrain == convertNeighborsExcept) continue
                        if (convertNeighborsTo == Constants.coast)
                            for (neighbor in tile.neighbors) {
                                // This is so we don't have this tile turn into Coast, and then it's touching a Lake tile.
                                // We just turn the lake tiles into this kind of tile.
                                if (neighbor.baseTerrain == Constants.lakes) {
                                    neighbor.baseTerrain = tile.baseTerrain
                                    neighbor.setTerrainTransients()
                                }
                            }
                        tile.baseTerrain = convertNeighborsTo
                        clearTile(tile)
                    }
                }
                if (MapGenerator.consoleOutput)
                    println("Natural Wonder ${wonder.name} @${location.position}")

                return true
            }
        }

        if (MapGenerator.consoleOutput)
            println("No suitable location for ${wonder.name}")
        return false
    }

    private fun clearTile(tile: TileInfo){
        tile.terrainFeatures = listOf()
        tile.resource = null
        tile.improvement = null
        tile.setTerrainTransients()
    }

    private fun TileInfo.matchesWonderFilter(filter: String) = when (filter) {
        "Elevated" -> baseTerrain == Constants.mountain || isHill()
        "Water" -> isWater
        "Land" -> isLand
        "Hill" -> isHill()
        naturalWonder -> true
        in allTerrainFeatures -> getLastTerrain().name == filter
        else -> baseTerrain == filter
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
