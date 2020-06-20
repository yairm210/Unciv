package com.unciv.logic.map.mapgenerator

import com.unciv.Constants
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import kotlin.math.abs
import kotlin.math.round

class NaturalWonderGenerator(val ruleset: Ruleset){

    /*
    https://gaming.stackexchange.com/questions/95095/do-natural-wonders-spawn-more-closely-to-city-states/96479
    https://www.reddit.com/r/civ/comments/1jae5j/information_on_the_occurrence_of_natural_wonders/
    */
    fun spawnNaturalWonders(tileMap: TileMap, randomness: MapGenerationRandomness) {
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
            val random = randomness.RNG.nextDouble()
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
                && it.neighbors.count{ neighbor -> neighbor.getBaseTerrain().name == Constants.mountain || neighbor.getBaseTerrain().name == Constants.hill } <= 4
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
                && it.neighbors.none { neighbor -> neighbor.getLastTerrain().name == Constants.ice }
        }

        val location = trySpawnOnSuitableLocation(suitableLocations, wonder)
        if (location != null) {
            for (tile in location.neighbors) {
                if (tile.baseTerrain == Constants.coast) continue
                tile.baseTerrain = Constants.coast
                tile.terrainFeature = null
                tile.resource = null
                tile.improvement = null
                tile.setTerrainTransients()
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
                tile.setTerrainTransients()
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
                neighbor.getBaseTerrain().name == Constants.hill
        } >= 3
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
}