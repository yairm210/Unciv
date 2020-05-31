package com.unciv.logic.map.mapgenerator

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.HexMath
import com.unciv.logic.map.*
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

class MapLandmassGenerator(val randomness: MapGenerationRandomness) {

    fun generateLand(tileMap: TileMap, ruleset: Ruleset) {
        if(ruleset.terrains.values.none { it.type== TerrainType.Water }) {
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

    private fun smooth(tileMap: TileMap, randomness: MapGenerationRandomness) {
        for (tileInfo in tileMap.values) {
            val numberOfLandNeighbors = tileInfo.neighbors.count { it.baseTerrain == Constants.grassland }
            if (randomness.RNG.nextFloat() < 0.5f)
                continue

            if (numberOfLandNeighbors > 3)
                tileInfo.baseTerrain = Constants.grassland
            else if (numberOfLandNeighbors < 3)
                tileInfo.baseTerrain = Constants.ocean
        }
    }

    private fun createPerlin(tileMap: TileMap) {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            spawnLandOrWater(tile, elevation, tileMap.mapParameters.waterThreshold.toDouble())
        }
    }

    private fun createArchipelago(tileMap: TileMap) {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = getRidgedPerlinNoise(tile, elevationSeed)
            spawnLandOrWater(tile, elevation, 0.25 + tileMap.mapParameters.waterThreshold.toDouble())
        }
    }

    private fun createPangea(tileMap: TileMap) {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation = (elevation + getCircularNoise(tile, tileMap) ) / 2.0
            spawnLandOrWater(tile, elevation, tileMap.mapParameters.waterThreshold.toDouble())
        }
    }

    private fun createTwoContinents(tileMap: TileMap) {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation = (elevation + getTwoContinentsTransform(tile, tileMap)) / 2.0
            spawnLandOrWater(tile, elevation, tileMap.mapParameters.waterThreshold.toDouble())
        }
    }

    private fun getCircularNoise(tileInfo: TileInfo, tileMap: TileMap): Double {
        val randomScale = randomness.RNG.nextDouble()
        val distanceFactor =  percentualDistanceToCenter(tileInfo, tileMap)

        return min(0.3, 1.0 - (5.0 * distanceFactor * distanceFactor + randomScale) / 3.0)
    }

    private fun getTwoContinentsTransform(tileInfo: TileInfo, tileMap: TileMap): Double {
        val randomScale = randomness.RNG.nextDouble()
        val longitudeFactor = abs(tileInfo.longitude) / tileMap.maxLongitude

        return min(0.2, -1.0 + (5.0 * longitudeFactor.pow(0.6f) + randomScale) / 3.0)
    }

    private fun percentualDistanceToCenter(tileInfo: TileInfo, tileMap: TileMap): Double {
        val mapRadius = tileMap.mapParameters.size.radius
        if (tileMap.mapParameters.shape == MapShape.hexagonal)
            return HexMath.getDistance(Vector2.Zero, tileInfo.position).toDouble()/mapRadius
        else {
            val size = HexMath.getEquivalentRectangularSize(mapRadius)
            return HexMath.getDistance(Vector2.Zero, tileInfo.position).toDouble() / HexMath.getDistance(Vector2.Zero, Vector2(size.x / 2, size.y / 2))
        }
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
            if (randomness.RNG.nextDouble() < 0.1) return TerrainType.Land else return TerrainType.Water
        }
        if (HexMath.getDistance(Vector2.Zero, tileInfo.position) > 0.85f * mapRadius) {
            if (randomness.RNG.nextDouble() < 0.2) return TerrainType.Land else return TerrainType.Water
        }
        if (randomness.RNG.nextDouble() < 0.55) return TerrainType.Land else return TerrainType.Water
    }

    // endregion
}