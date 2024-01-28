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

class MapElevationGenerator(
    private val tileMap: TileMap,
    private val ruleset: Ruleset,
    private val randomness: MapGenerationRandomness
) {
    companion object {
        private const val rising = "~Raising~"
        private const val lowering = "~Lowering~"
    }

    private val flat = ruleset.terrains.values.firstOrNull {
            !it.impassable && it.type == TerrainType.Land && !it.hasUnique(UniqueType.RoughTerrain)
        }?.name
    private val hillMutator: ITileMutator
    private val mountainMutator: ITileMutator
    private val dummyMutator by lazy { TileDummyMutator() }

    init {
        mountainMutator = getTileMutator(UniqueType.OccursInChains, flat)
        hillMutator = getTileMutator(UniqueType.OccursInGroups, flat)
    }

    private fun getTileMutator(type: UniqueType, flat: String?): ITileMutator {
        if (flat == null) return dummyMutator
        val terrain = ruleset.terrains.values.firstOrNull { it.hasUnique(type) }
            ?: return dummyMutator
        return if (terrain.type == TerrainType.TerrainFeature)
            TileFeatureMutator(terrain.name)
        else TileBaseMutator(flat, terrain.name)
    }

    /**
     * [MapParameters.elevationExponent] favors high elevation
     */
    fun raiseMountainsAndHills() {
        if (flat == null) {
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
            tile.baseTerrain = flat // in case both mutators are TileFeatureMutator
            hillMutator.setElevated(tile, elevation > 0.5 && elevation <= 0.7)
            mountainMutator.setElevated(tile, elevation > 0.7)
            tile.setTerrainTransients()
        }

        cellularMountainRanges()
        cellularHills()
    }

    private fun cellularMountainRanges() {
        if (mountainMutator is TileDummyMutator) return
        Log.debug("Mountain-like generation for %s", mountainMutator.name)

        val targetMountains = mountainMutator.count(tileMap.values) * 2
        val impassableTerrains = ruleset.terrains.values.filter { it.impassable }.map { it.name }.toSet()

        for (i in 1..5) {
            var totalMountains = mountainMutator.count(tileMap.values)

            for (tile in tileMap.values) {
                if (tile.isWater) continue
                val adjacentMountains = mountainMutator.count(tile.neighbors)
                val adjacentImpassible = tile.neighbors.count { it.baseTerrain in impassableTerrains }

                if (adjacentMountains == 0 && mountainMutator.isElevated(tile)) {
                    if (randomness.RNG.nextInt(until = 4) == 0)
                        tile.addTerrainFeature(lowering)
                } else if (adjacentMountains == 1) {
                    if (randomness.RNG.nextInt(until = 10) == 0)
                        tile.addTerrainFeature(rising)
                } else if (adjacentImpassible == 3) {
                    if (randomness.RNG.nextInt(until = 2) == 0)
                        tile.addTerrainFeature(lowering)
                } else if (adjacentImpassible > 3) {
                    tile.addTerrainFeature(lowering)
                }
            }

            for (tile in tileMap.values) {
                if (tile.isWater) continue
                if (tile.terrainFeatures.contains(rising)) {
                    tile.removeTerrainFeature(rising)
                    if (totalMountains >= targetMountains) continue
                    if (!mountainMutator.isElevated(tile)) totalMountains++
                    hillMutator.lower(tile)
                    mountainMutator.raise(tile)
                }
                if (tile.terrainFeatures.contains(lowering)) {
                    tile.removeTerrainFeature(lowering)
                    if (totalMountains * 2 <= targetMountains) continue
                    if (mountainMutator.isElevated(tile)) totalMountains--
                    mountainMutator.lower(tile)
                    hillMutator.raise(tile)
                }
            }
        }
    }

    private fun cellularHills() {
        if (hillMutator is TileDummyMutator) return
        Log.debug("Hill-like generation for %s", hillMutator.name)

        val targetHills = hillMutator.count(tileMap.values)

        for (i in 1..5) {
            var totalHills = hillMutator.count(tileMap.values)

            for (tile in tileMap.values) {
                if (tile.isWater || mountainMutator.isElevated(tile)) continue
                val adjacentMountains = mountainMutator.count(tile.neighbors)
                val adjacentHills = hillMutator.count(tile.neighbors)

                if (adjacentHills <= 1 && adjacentMountains == 0 && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.addTerrainFeature(lowering)
                } else if (adjacentHills > 3 && adjacentMountains == 0 && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.addTerrainFeature(lowering)
                } else if (adjacentHills + adjacentMountains in 2..3 && randomness.RNG.nextInt(until = 2) == 0) {
                    tile.addTerrainFeature(rising)
                }

            }

            for (tile in tileMap.values) {
                if (tile.isWater || mountainMutator.isElevated(tile)) continue
                if (tile.terrainFeatures.contains(rising)) {
                    tile.removeTerrainFeature(rising)
                    if (totalHills > targetHills && i != 1) continue
                    if (!hillMutator.isElevated(tile)) {
                        hillMutator.raise(tile)
                        totalHills++
                    }
                }
                if (tile.terrainFeatures.contains(lowering)) {
                    tile.removeTerrainFeature(lowering)
                    if (totalHills >= targetHills * 0.9f || i == 1) {
                        if (hillMutator.isElevated(tile)) {
                            hillMutator.lower(tile)
                            totalHills--
                        }
                    }
                }
            }
        }
    }

    private interface ITileMutator {
        val name: String // logging only
        fun lower(tile: Tile)
        fun raise(tile: Tile)
        fun isElevated(tile: Tile): Boolean
        fun setElevated(tile: Tile, value: Boolean) = if (value) raise(tile) else lower(tile)
        fun count(tiles: Iterable<Tile>) = tiles.count { isElevated(it) }
        fun count(tiles: Sequence<Tile>) = tiles.count { isElevated(it) }
    }

    private class TileDummyMutator : ITileMutator {
        override val name get() = ""
        override fun lower(tile: Tile) {}
        override fun raise(tile: Tile) {}
        override fun isElevated(tile: Tile) = false
    }

    private class TileBaseMutator(
        private val flat: String,
        private val elevated: String
    ) : ITileMutator {
        override val name get() = elevated
        override fun lower(tile: Tile) {
            tile.baseTerrain = flat
        }
        override fun raise(tile: Tile) {
            tile.baseTerrain = elevated
        }
        override fun isElevated(tile: Tile) = tile.baseTerrain == elevated
    }

    private class TileFeatureMutator(
        val elevated: String
    ) : ITileMutator {
        override val name get() = elevated
        override fun lower(tile: Tile) {
            tile.removeTerrainFeature(elevated)
        }
        override fun raise(tile: Tile) {
            tile.addTerrainFeature(elevated)
        }
        override fun isElevated(tile: Tile) = elevated in tile.terrainFeatures
    }
}
