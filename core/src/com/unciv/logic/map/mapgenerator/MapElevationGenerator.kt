package com.unciv.logic.map.mapgenerator

import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.utils.Log
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

internal class MapElevationGenerator(
    private val tileMap: TileMap,
    private val ruleset: Ruleset,
    terrains: List<MapGenerator.TerrainOccursRange>,
    private val randomness: MapGenerationRandomness
) {
    companion object {
        private const val rising = "~Raising~"
        private const val lowering = "~Lowering~"
    }

    private val flats = terrains.filter {!it.terrain.impassable && it.terrain.type == TerrainType.Land && !it.terrain.hasUnique(UniqueType.RoughTerrain) }
    private val hills = terrains.filter {(it.terrain.type == TerrainType.Land || it.terrain.type == TerrainType.TerrainFeature) && it.occursInGroups}
    private val mountains = terrains.filter {(it.terrain.type == TerrainType.Land || it.terrain.type == TerrainType.TerrainFeature) && it.occursInChains}

    /**
     * [MapParameters.elevationExponent] favors high elevation
     */
    fun raiseMountainsAndHills() {
        if (flats.isEmpty()) {
            Log.debug("Ruleset seems to contain no flat terrain - can't generate heightmap")
            return
        }

        val elevationSeed = randomness.RNG.nextInt().toDouble()
        val exponent = 1.0 - tileMap.mapParameters.elevationExponent.toDouble()
        fun Double.powSigned(exponent: Double) = abs(this).pow(exponent) * sign(this)

        tileMap.setTransients(ruleset)

        for (tile in tileMap.values) {
            if (tile.isWater) continue
            val elevation = randomness.getPerlinNoise(tile, elevationSeed, scale = 2.0).powSigned(exponent)
            if (elevation > 0.7 && mountains.isNotEmpty()) {
                applyTerrain(tile, mountains)
            } else if (elevation > 0.5 && hills.isNotEmpty()) {
                applyTerrain(tile, hills)
            } else {
                applyTerrain(tile, flats)
            }
        }

        cellularMountainRanges()
        cellularHills()

        for (tile in tileMap.values) {
            tile.setTerrainTransients()
        }
    }

    private fun cellularMountainRanges() {
        if (mountains.isEmpty()) return
        Log.debug("Mountain-like generation")

        val targetMountains = tileMap.values.count { it.isMountainTerrain()}  * 2
        val impassableTerrains = ruleset.terrains.values.filter { it.impassable }.map { it.name }.toSet()

        for (i in 1..5) {
            var totalMountains = tileMap.values.count { it.isMountainTerrain()}  * 2

            for (tile in tileMap.values) {
                if (tile.isWater) continue
                val isMountain = tile.isMountainTerrain()
                val adjacentMountains = tile.neighbors.count { it.isMountainTerrain()}
                val adjacentImpassible = tile.neighbors.count { it.baseTerrain in impassableTerrains }

                if (adjacentMountains == 0) {
                    if (isMountain && randomness.RNG.nextInt(until = 4) == 0)
                        tile.addTerrainFeature(lowering)
                } else if (adjacentMountains == 1) {
                    if (!isMountain && randomness.RNG.nextInt(until = 10) == 0)
                        tile.addTerrainFeature(rising)
                } else if (adjacentImpassible == 3) {
                    if (isMountain && randomness.RNG.nextInt(until = 2) == 0)
                        tile.addTerrainFeature(lowering)
                } else if (isMountain && adjacentImpassible > 3) {
                    tile.addTerrainFeature(lowering)
                }
            }

            for (tile in tileMap.values) {
                if (tile.isWater) continue
                if (tile.terrainFeatures.contains(rising)) {
                    tile.removeTerrainFeature(rising)
                    if (totalMountains >= targetMountains) continue
                    totalMountains++
                    applyTerrain(tile, mountains)
                }
                if (tile.terrainFeatures.contains(lowering)) {
                    tile.removeTerrainFeature(lowering)
                    if (totalMountains * 2 <= targetMountains) continue
                    totalMountains--
                    applyTerrain(tile, flats)
                }
            }
        }
    }

    private fun cellularHills() {
        if (hills.isEmpty()) return
        Log.debug("Hill-like generation")

        val targetHills = tileMap.values.count { it.isHillTerrain()}

        for (i in 1..5) {
            var totalHills = tileMap.values.count { it.isHillTerrain()}

            for (tile in tileMap.values) {
                val isMountain = tile.hasTerrain(mountains)
                if (tile.isWater || isMountain) continue
                val isHill = tile.isHillTerrain()
                val adjacentMountains = tile.neighbors.count { it.isMountainTerrain() }
                val adjacentHills = tile.neighbors.count { it.isHillTerrain() }

                if (adjacentHills <= 1 && adjacentMountains == 0 && isHill && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.addTerrainFeature(lowering)
                } else if (adjacentHills > 3 && adjacentMountains == 0 && isHill && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.addTerrainFeature(lowering)
                } else if (adjacentHills + adjacentMountains in 2..3 && !isHill && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.addTerrainFeature(rising)
                }

            }

            for (tile in tileMap.values) {
                if (tile.terrainFeatures.contains(rising)) {
                    tile.removeTerrainFeature(rising)
                    if (totalHills > targetHills && i != 1) continue
                    totalHills++
                    applyTerrain(tile, hills)
                }
                if (tile.terrainFeatures.contains(lowering)) {
                    tile.removeTerrainFeature(lowering)
                    if (totalHills >= targetHills * 0.9f || i == 1) {
                        totalHills--
                        applyTerrain(tile, flats)
                    }
                }
            }
        }
    }
    
    private fun Tile.isMountainTerrain(): Boolean {
        val terrainObj = ruleset.terrains[baseTerrain] ?: return false
        if (terrainObj.hasUnique(UniqueType.OccursInChains)) return true
        for (feature in terrainFeatures) {
            val featureObj = ruleset.terrains[feature] ?: continue
            if (featureObj.hasUnique(UniqueType.OccursInChains)) return true
        }
        return false
    }

    private fun Tile.isHillTerrain(): Boolean {
        val terrainObj = ruleset.terrains[baseTerrain] ?: return false
        if (terrainObj.hasUnique(UniqueType.OccursInGroups)) return true
        for (feature in terrainFeatures) {
            val featureObj = ruleset.terrains[feature] ?: continue
            if (featureObj.hasUnique(UniqueType.OccursInGroups)) return true
        }
        return false
    }

    private fun Tile.dropElevatedFeatures() {
        for (feature in terrainFeatures) {
            val featureObj = ruleset.terrains[feature] ?: continue
            if (featureObj.hasUnique(UniqueType.OccursInChains)
                || featureObj.hasUnique(UniqueType.OccursInGroups)) {
                removeTerrainFeature(feature)
                return
            }
        }
    }
        
    private fun Tile.hasTerrain(terrains: List<MapGenerator.TerrainOccursRange>): Boolean {
        for (terrain in terrains) {
            if (terrain.terrain.type == TerrainType.Land) {
                if (baseTerrain == terrain.name)
                    return true
            } else { // must be TerrainType.TerrainFeature
                if (terrainFeatures.contains(terrain.name))
                    return true
            }
        }
        return false
    }

    private fun applyTerrain(tile: Tile, terrains: List<MapGenerator.TerrainOccursRange>) {
        val desiredTerrain = (terrains.filter {it.matches(tile) }.ifEmpty { terrains }).random(randomness.RNG)
        tile.dropElevatedFeatures()
        if (desiredTerrain.terrain.type == TerrainType.Land) {
            tile.baseTerrain = desiredTerrain.name
        } else { // must be TerrainType.TerrainFeature
            val baseTerrains = flats.filter { desiredTerrain.terrain.occursOn.contains(it.name) && it.matches(tile) }
                .ifEmpty { flats.filter { desiredTerrain.terrain.occursOn.contains(it.name) } }
            if (baseTerrains.isEmpty()) {
                Log.debug("Ruleset seems to contain no flat terrain that ${desiredTerrain.name} can apply to. Failing feature.")
                return
            }
            val baseTerrain = baseTerrains.random(randomness.RNG)
            tile.baseTerrain = baseTerrain.name
            tile.addTerrainFeature(desiredTerrain.name)
        }
    }
}
