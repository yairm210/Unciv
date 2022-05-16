package com.unciv.logic.map.mapgenerator

import com.unciv.logic.HexMath
import com.unciv.logic.map.*
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

class MapLandmassGenerator(val ruleset: Ruleset, val randomness: MapGenerationRandomness) {
    //region _Fields
    private val landTerrainName = getInitializationTerrain(ruleset, TerrainType.Land)
    private val waterTerrainName: String = try {
        getInitializationTerrain(ruleset, TerrainType.Water)
    } catch (_: Exception) {
        landTerrainName
    }
    private val landOnlyMod: Boolean = waterTerrainName == landTerrainName
    private var waterThreshold = 0.0
    //endregion

    companion object {
        // this is called from TileMap constructors as well
        internal fun getInitializationTerrain(ruleset: Ruleset, type: TerrainType) =
            ruleset.terrains.values.firstOrNull { it.type == type }?.name
                ?: throw Exception("Cannot create map - no $type terrains found!")
    }

    fun generateLand(tileMap: TileMap) {
        // This is to accommodate land-only mods
        if (landOnlyMod) {
            for (tile in tileMap.values)
                tile.baseTerrain = landTerrainName
            return
        }

        waterThreshold = tileMap.mapParameters.waterThreshold.toDouble()

        when (tileMap.mapParameters.type) {
            MapType.pangaea -> createPangaea(tileMap)
            MapType.innerSea -> createInnerSea(tileMap)
            MapType.continents -> createTwoContinents(tileMap)
            MapType.fourCorners -> createFourCorners(tileMap)
            MapType.perlin -> createPerlin(tileMap)
            MapType.archipelago -> createArchipelago(tileMap)
            MapType.default -> generateLandCellularAutomata(tileMap)
        }
    }

    private fun spawnLandOrWater(tile: TileInfo, elevation: Double) {
        tile.baseTerrain = if (elevation < waterThreshold) waterTerrainName else landTerrainName
    }

    /**
     * Smoothen the map by clustering landmass and oceans with probability [threshold].
     * [TileInfo]s with more than 3 land neighbours are converted to land.
     * [TileInfo]s with more than 3 water neighbours are converted to water.
     */
    private fun smoothen(tileMap: TileMap, threshold: Double = 1.0) {
        for (tileInfo in tileMap.values) {
            if (randomness.RNG.nextFloat() > threshold)
                continue

            val numberOfLandNeighbors = tileInfo.neighbors.count { it.baseTerrain == landTerrainName }
            if (numberOfLandNeighbors > 3)
                tileInfo.baseTerrain = landTerrainName
            else if (numberOfLandNeighbors < 3)
                tileInfo.baseTerrain = waterTerrainName
        }
    }

    private fun createPerlin(tileMap: TileMap) {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            val elevation = randomness.getPerlinNoise(tile, elevationSeed)
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createArchipelago(tileMap: TileMap) {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        waterThreshold += 0.25
        for (tile in tileMap.values) {
            val elevation = getRidgedPerlinNoise(tile, elevationSeed)
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createPangaea(tileMap: TileMap) {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation = (elevation + getEllipticContinent(tile, tileMap)) / 2.0
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createInnerSea(tileMap: TileMap) {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation -= getEllipticContinent(tile, tileMap, 0.6) * 0.3
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createTwoContinents(tileMap: TileMap) {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation = (elevation + getTwoContinentsTransform(tile, tileMap)) / 2.0
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createFourCorners(tileMap: TileMap) {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation = (elevation + getFourCornersTransform(tile, tileMap)) / 2.0
            spawnLandOrWater(tile, elevation)
        }
    }

    /**
     * Create an elevation map that favors a central elliptic continent spanning over 85% - 95% of
     * the map size.
     */
    private fun getEllipticContinent(tileInfo: TileInfo, tileMap: TileMap, percentOfMap: Double = 0.85): Double {
        val randomScale = randomness.RNG.nextDouble()
        val ratio = percentOfMap + 0.1 * randomness.RNG.nextDouble()

        val a = ratio * tileMap.maxLongitude
        val b = ratio * tileMap.maxLatitude
        val x = tileInfo.longitude
        val y = tileInfo.latitude

        val distanceFactor = x * x / (a * a) + y * y / (b * b)

        return min(0.3, 1.0 - (5.0 * distanceFactor * distanceFactor + randomScale) / 3.0)
    }

    private fun getTwoContinentsTransform(tileInfo: TileInfo, tileMap: TileMap): Double {
        // The idea here is to create a water area separating the two land areas.
        // So what we do it create a line of water in the middle - where longitude is close to 0.
        val randomScale = randomness.RNG.nextDouble()
        var longitudeFactor = abs(tileInfo.longitude) / tileMap.maxLongitude

        // If this is a world wrap, we want it to be separated on both sides -
        // so we make the actual strip of water thinner, but we put it both in the middle of the map and on the edges of the map
        if (tileMap.mapParameters.worldWrap)
            longitudeFactor = min(longitudeFactor,
                (tileMap.maxLongitude - abs(tileInfo.longitude)) / tileMap.maxLongitude) * 1.5f

        // there's nothing magical about this, it's just what we got from playing around with a lot of different options -
        //   the numbers can be changed if you find that something else creates better looking continents
        return min(0.2, -1.0 + (5.0 * longitudeFactor.pow(0.6f) + randomScale) / 3.0)
    }

    private fun getFourCornersTransform(tileInfo: TileInfo, tileMap: TileMap): Double {
        // The idea here is to create a water area separating the two four areas.
        // So what we do it create a line of water in the middle - where longitude is close to 0.
        val randomScale = randomness.RNG.nextDouble()
        var longitudeFactor = abs(tileInfo.longitude) / tileMap.maxLongitude
        var latitudeFactor = abs(tileInfo.latitude) / tileMap.maxLatitude

        // If this is a world wrap, we want it to be separated on both sides -
        // so we make the actual strip of water thinner, but we put it both in the middle of the map and on the edges of the map
        if (tileMap.mapParameters.worldWrap) {
            longitudeFactor = min(
                longitudeFactor,
                (tileMap.maxLongitude - abs(tileInfo.longitude)) / tileMap.maxLongitude
            ) * 1.5f
            latitudeFactor = min(
                latitudeFactor,
                (tileMap.maxLatitude - abs(tileInfo.latitude)) / tileMap.maxLatitude
            ) * 1.5f
        }
        // there's nothing magical about this, it's just what we got from playing around with a lot of different options -
        //   the numbers can be changed if you find that something else creates better looking continents

        val landFactor = min(longitudeFactor, latitudeFactor)

        return min(0.2, -1.0 + (5.0 * landFactor.pow(0.5f) + randomScale) / 3.0)
    }

    /**
     * Generates ridged perlin noise. As for parameters see [MapGenerationRandomness.getPerlinNoise]
     */
    private fun getRidgedPerlinNoise(tile: TileInfo, seed: Double,
                                     nOctaves: Int = 10,
                                     persistence: Double = 0.5,
                                     lacunarity: Double = 2.0,
                                     scale: Double = 15.0): Double {
        val worldCoords = HexMath.hex2WorldCoords(tile.position)
        return Perlin.ridgedNoise3d(worldCoords.x.toDouble(), worldCoords.y.toDouble(), seed, nOctaves, persistence, lacunarity, scale)
    }

    private fun generateLandCellularAutomata(tileMap: TileMap) {
        // Empiric nice looking range: 0.37..0.72 giving ~15%-85% water
        // The old code had 0.55 (inverted) hard-coded, so to get the same we need waterThreshold = -0.055 for the MainMenu background.
        waterThreshold = 0.545 + 1.75 * waterThreshold

        for (tile in tileMap.values) {
            spawnLandOrWater(tile, randomness.RNG.nextDouble())
            tile.setTransients()
        }

        smoothen(tileMap)
    }
}
