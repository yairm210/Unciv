package com.unciv.logic.map.mapgenerator

import com.unciv.logic.map.*
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class MapLandmassGenerator(
    private val tileMap: TileMap,
    ruleset: Ruleset,
    private val randomness: MapGenerationRandomness
) {
    //region Fields
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
            ruleset.terrains.values.firstOrNull { it.type == type && !it.hasUnique(UniqueType.NoNaturalGeneration) }?.name
                ?: throw Exception("Cannot create map - no $type terrains found!")
    }

    fun generateLand() {
        // This is to accommodate land-only mods
        if (landOnlyMod) {
            for (tile in tileMap.values)
                tile.baseTerrain = landTerrainName
            return
        }

        waterThreshold = tileMap.mapParameters.waterThreshold.toDouble()

        when (tileMap.mapParameters.type) {
            MapType.pangaea -> createPangaea()
            MapType.innerSea -> createInnerSea()
            MapType.continentAndIslands -> createContinentAndIslands()
            MapType.twoContinents -> createTwoContinents()
            MapType.threeContinents -> createThreeContinents()
            MapType.fourCorners -> createFourCorners()
            MapType.archipelago -> createArchipelago()
            MapType.perlin -> createPerlin()
            MapType.fractal -> createFractal()
            MapType.lakes -> createLakes()
            MapType.smallContinents -> createSmallContinents()
        }

        if (tileMap.mapParameters.shape == MapShape.flatEarth) {
            generateFlatEarthExtraWater()
        }
    }

    private fun generateFlatEarthExtraWater() {
        for (tile in tileMap.values) {
            val isCenterTile = tile.latitude == 0 && tile.longitude == 0
            val isEdgeTile = tile.neighbors.count() < 6

            if (!isCenterTile && !isEdgeTile) continue

            /*
            Flat Earth needs a 3 tile wide water perimeter and a 4 tile radius water center.
            This helps map generators to not place important things there which would be destroyed
            when the ice walls are placed there.
            */
            tile.baseTerrain = waterTerrainName
            for (neighbor in tile.neighbors) {
                neighbor.baseTerrain = waterTerrainName
                for (neighbor2 in neighbor.neighbors) {
                    neighbor2.baseTerrain = waterTerrainName
                    if (!isCenterTile) continue
                    for (neighbor3 in neighbor2.neighbors) {
                        neighbor3.baseTerrain = waterTerrainName
                    }
                }
            }
        }
    }

    private fun spawnLandOrWater(tile: Tile, elevation: Double) {
        tile.baseTerrain = if (elevation < waterThreshold) waterTerrainName else landTerrainName
    }

    /** Repeat [function] until [predicate] is `true`, lowering [waterThreshold] on each retry, preventing an endless loop.
     *  The [predicate] receives the proportion of water on the map, the default accepts <= 70%.
     */
    private fun retryLoweringWaterLevel(predicate: (waterPercent: Float) -> Boolean = { it <= 0.7f }, function: () -> Unit) {
        var retries = 0
        while (++retries <= 30) { // 28 is enough to go from +1 to -1 with only the retries acceleration below
            function()
            val waterPercent = tileMap.values.count { it.baseTerrain == waterTerrainName }.toFloat() / tileMap.values.size
            if (waterThreshold < -1f || predicate(waterPercent)) break

            // lower water table to reduce water percentage, with empiric base step and acceleration
            // (tweaked to acceptable performance on huge maps - but feel free to improve)
            waterThreshold -= 0.02 *
                (waterPercent / 0.7f).coerceAtLeast(1f) *
                retries.toFloat().pow(0.5f)
            //Log.debug("retry %d with waterPercent=%f, waterThreshold=%f", retries, waterPercent, waterThreshold)
        }
    }

    //region Type-specific generators
    private fun createPerlin() {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            val elevation = randomness.getPerlinNoise(tile, elevationSeed)
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createFractal() {
        retryLoweringWaterLevel {
            val elevationSeed = randomness.RNG.nextInt().toDouble()
            for (tile in tileMap.values) {
                val maxdim = max(tileMap.maxLatitude, tileMap.maxLongitude)
                var ratio = maxdim / 32.0 // change scale depending on map size so that average number of continents stay the same
                if (tileMap.mapParameters.shape == MapShape.hexagonal || tileMap.mapParameters.shape == MapShape.flatEarth) {
                    ratio *= 0.5 // In hexagonal type map for some reason it tends to make a single continent like pangaea if we don't diminish the scale
                }

                var elevation = randomness.getPerlinNoise(tile, elevationSeed, persistence=0.8, lacunarity=1.5, scale=ratio*30.0)

                elevation += getOceanEdgesTransform(tile)

                spawnLandOrWater(tile, elevation)
            }
        }
    }

    private fun createLakes() {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            val elevation = 0.3 - getRidgedPerlinNoise(tile, elevationSeed, persistence=0.7, lacunarity=1.5)

            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createSmallContinents() {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        waterThreshold += 0.25
        retryLoweringWaterLevel {
            for (tile in tileMap.values) {
                var elevation = getRidgedPerlinNoise(tile, elevationSeed, scale = 22.0)
                elevation += getOceanEdgesTransform(tile)
                spawnLandOrWater(tile, elevation)
            }
        }
    }

    private fun createArchipelago() {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        waterThreshold += 0.25
        for (tile in tileMap.values) {
            val elevation = getRidgedPerlinNoise(tile, elevationSeed)
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createPangaea() {
        val largeContinentThreshold = 25
            .coerceAtMost(tileMap.values.size / 4)  // Or really tiny maps will exhaust retries
            .coerceAtLeast(tileMap.values.size.toFloat().pow(0.333f).toInt())  // kicks in on really large maps: 130k tiles -> 50
        retryLoweringWaterLevel(predicate = { waterPercent ->
            val largeContinents = tileMap.continentSizes.values.count { it > largeContinentThreshold }
            largeContinents == 1 && waterPercent <= 0.7f
        }) {
            val elevationSeed = randomness.RNG.nextInt().toDouble()
            for (tile in tileMap.values) {
                var elevation = randomness.getPerlinNoise(tile, elevationSeed)
                elevation = elevation * (3 / 4f) + getEllipticContinent(tile) / 4
                spawnLandOrWater(tile, elevation)
                tile.setTerrainTransients() // necessary for assignContinents
            }
            tileMap.assignContinents(TileMap.AssignContinentsMode.Reassign) // to support largeContinents above
        }
        tileMap.assignContinents(TileMap.AssignContinentsMode.Clear)
    }

    private fun createInnerSea() {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation -= getEllipticContinent(tile, 0.6) * 0.3
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createContinentAndIslands() {
        val isNorth = randomness.RNG.nextDouble() < 0.5
        val isLatitude =
                if (tileMap.mapParameters.shape == MapShape.hexagonal || tileMap.mapParameters.shape == MapShape.flatEarth) randomness.RNG.nextDouble() > 0.5f
                else if (tileMap.mapParameters.mapSize.height > tileMap.mapParameters.mapSize.width) true
                else if (tileMap.mapParameters.mapSize.width > tileMap.mapParameters.mapSize.height) false
                else randomness.RNG.nextDouble() > 0.5f

        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation = (elevation + getContinentAndIslandsTransform(tile, isNorth, isLatitude)) / 2.0
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createTwoContinents() {
        val isLatitude =
                if (tileMap.mapParameters.shape == MapShape.hexagonal || tileMap.mapParameters.shape == MapShape.flatEarth) randomness.RNG.nextDouble() > 0.5f
                else if (tileMap.mapParameters.mapSize.height > tileMap.mapParameters.mapSize.width) true
                else if (tileMap.mapParameters.mapSize.width > tileMap.mapParameters.mapSize.height) false
                else randomness.RNG.nextDouble() > 0.5f

        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation = (elevation + getTwoContinentsTransform(tile, isLatitude)) / 2.0
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createThreeContinents() {
        val isNorth = randomness.RNG.nextDouble() < 0.5
        // On flat earth maps we can randomly do East or West instead of North or South
        val isEastWest = tileMap.mapParameters.shape == MapShape.flatEarth && randomness.RNG.nextDouble() > 0.5

        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation = (elevation + getThreeContinentsTransform(tile, isNorth, isEastWest)) / 2.0
            spawnLandOrWater(tile, elevation)
        }
    }

    private fun createFourCorners() {
        val elevationSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            var elevation = randomness.getPerlinNoise(tile, elevationSeed)
            elevation = elevation / 2 + getFourCornersTransform(tile) / 2
            spawnLandOrWater(tile, elevation)
        }
    }

    //endregion
    //region Shaping helpers

    /**
     * Create an elevation map that favors a central elliptic continent spanning over 85% - 95% of
     * the map size.
     */
    private fun getEllipticContinent(tile: Tile, percentOfMap: Double = 0.85): Double {
        val randomScale = randomness.RNG.nextDouble()
        val ratio = percentOfMap + 0.1 * randomness.RNG.nextDouble()

        val a = ratio * tileMap.maxLongitude
        val b = ratio * tileMap.maxLatitude
        val x = tile.longitude.toFloat()
        val y = tile.latitude.toFloat()

        val distanceFactor = x * x / (a * a) + y * y / (b * b) + (x+y).pow(2) / (a+b).pow(2)

        return min(0.3, 1.0 - (5.0 * distanceFactor * distanceFactor + randomScale) / 3.0)
    }

    private fun getContinentAndIslandsTransform(tile: Tile, isNorth: Boolean, isLatitude: Boolean): Double {
        // The idea here is to create a water area separating the two land areas.
        // So what we do it create a line of water in the middle - where latitude or longitude is close to 0.
        val randomScale = randomness.RNG.nextDouble()
        var (longitudeFactor, latitudeFactor) = getLonLatFactors(tile)

        // We then pick one side to become islands instead of a continent
        if (isLatitude) {
            if (isNorth && tile.latitude < 0 || !isNorth && tile.latitude > 0)
                latitudeFactor = 0.2f
        } else {
            // In longitude mode North represents West
            if (isNorth && tile.longitude < 0 || !isNorth && tile.longitude > 0)
                longitudeFactor = 0.2f
        }

        var factor = if (isLatitude) latitudeFactor else longitudeFactor

        // If this is a world wrap, we want it to be separated on both sides -
        // so we make the actual strip of water thinner, but we put it both in the middle of the map and on the edges of the map
        if (tileMap.mapParameters.worldWrap)
            factor = min(factor, getReverseLongitudeFactor(tile.longitude)) * 1.1f

        // there's nothing magical about this, it's just what we got from playing around with a lot of different options -
        //   the numbers can be changed if you find that something else creates better looking continents
        return min(0.2, -1.0 + (5.0 * factor.pow(0.6f) + randomScale) / 3.0)
    }

    private fun getTwoContinentsTransform(tile: Tile, isLatitude: Boolean): Double {
        // The idea here is to create a water area separating the two land areas.
        // So what we do it create a line of water in the middle - where latitude or longitude is close to 0.
        val randomScale = randomness.RNG.nextDouble()
        val (longitudeFactor, latitudeFactor) = getLonLatFactors(tile)

        var factor = if (isLatitude) latitudeFactor else longitudeFactor

        // If this is a world wrap, we want it to be separated on both sides -
        // so we make the actual strip of water thinner, but we put it both in the middle of the map and on the edges of the map
        if (tileMap.mapParameters.worldWrap)
            factor = min(longitudeFactor, getReverseLongitudeFactor(tile.longitude)) * 1.5f

        // there's nothing magical about this, it's just what we got from playing around with a lot of different options -
        //   the numbers can be changed if you find that something else creates better looking continents
        return min(0.2, -1.0 + (5.0 * factor.pow(0.6f) + randomScale) / 3.0)
    }

    private fun getThreeContinentsTransform(tile: Tile, isNorth: Boolean, isEastWest: Boolean): Double {
        // The idea here is to create a water area separating the three land areas.
        // So what we do it create a line of water in the middle - where latitude or longitude is close to 0.
        val randomScale = randomness.RNG.nextDouble()
        var (longitudeFactor, latitudeFactor) = getLonLatFactors(tile)

        // 3rd continent should use only half the map width, or if flat earth, only a third
        val sizeReductionFactor = if (tileMap.mapParameters.shape == MapShape.flatEarth) 3f else 2f

        // We then pick one side to be merged into one centered continent instead of two cornered.
        if (isEastWest) {
            // In EastWest mode North represents West
            if (isNorth && tile.longitude < 0 || !isNorth && tile.longitude > 0)
                latitudeFactor = max(0f, tileMap.maxLatitude - abs(tile.latitude * sizeReductionFactor)) / tileMap.maxLatitude
        } else {
            if (isNorth && tile.latitude < 0 || !isNorth && tile.latitude > 0)
                longitudeFactor = max(0f, getReverseLongitudeFactor(abs(tile.longitude * sizeReductionFactor).toInt()))
        }

        var factor = min(longitudeFactor, latitudeFactor)

        // If this is a world wrap, we want it to be separated on both sides -
        // so we make the actual strip of water thinner, but we put it both in the middle of the map and on the edges of the map
        if (tileMap.mapParameters.worldWrap) {
            factor = min(
                factor,
                getReverseLongitudeFactor(tile.longitude)
            ) * 1.5f
        }

        // there's nothing magical about this, it's just what we got from playing around with a lot of different options -
        //   the numbers can be changed if you find that something else creates better looking continents
        return min(0.2, -1.0 + (5.0 * factor.pow(0.5f) + randomScale) / 3.0)
    }

    private fun getFourCornersTransform(tile: Tile): Double {
        // The idea here is to create a water area separating the four land areas.
        // So what we do it create a line of water in the middle - where latitude or longitude is close to 0.
        val randomScale = randomness.RNG.nextDouble()
        val (longitudeFactor, latitudeFactor) = getLonLatFactors(tile)
        
        var factor = sqrt(longitudeFactor * latitudeFactor) /1.5f

        // If this is a world wrap, we want it to be separated on both sides -
        // so we make the actual strip of water thinner, but we put it both in the middle of the map and on the edges of the map
        if (tileMap.mapParameters.worldWrap) {
            factor = min(
                factor,
                getReverseLongitudeFactor(tile.longitude)
            ) * 1.5f
        }

        val shouldBeWater = 1-factor

        // there's nothing magical about this, it's just what we got from playing around with a lot of different options -
        //   the numbers can be changed if you find that something else creates better looking continents
        return 1.0 - (5.0 * shouldBeWater*shouldBeWater + randomScale) / 3.0
    }

    private fun getOceanEdgesTransform(tile: Tile): Double {
        // The idea is to reduce elevation at the border of the map, so that we have mostly ocean there.
        val maxX = tileMap.maxLongitude
        val maxY = tileMap.maxLatitude
        val x = tile.longitude
        val y = tile.latitude

        var elevationOffset = 0.0

        val xdistanceratio = abs(x) / maxX
        val ydistanceratio = abs(y) / maxY
        if (tileMap.mapParameters.shape == MapShape.hexagonal || tileMap.mapParameters.shape == MapShape.flatEarth) {
            val startdropoffratio = 0.8 // distance from center at which we start decreasing elevation linearly
            val xdrsquared = xdistanceratio * xdistanceratio
            val ydrsquared = ydistanceratio * ydistanceratio
            val distancefromcenter = sqrt((xdrsquared+ydrsquared).toFloat())
            var distanceoffset = 0.0
            if (distancefromcenter > startdropoffratio) {
                val dropoffdistance = distancefromcenter - startdropoffratio
                val normalizationDivisor = 1.0 - startdropoffratio // for normalizing to [0;1] range
                distanceoffset = dropoffdistance / normalizationDivisor
            }
            elevationOffset -= distanceoffset * 0.35
        } else {

            var xoffset = 0.0

            val xstartdropoffratio = 0.8
            if (xdistanceratio > xstartdropoffratio) {
                val xdropoffdistance = xdistanceratio - xstartdropoffratio
                val xnormalizationdivisor = 1.0 - xstartdropoffratio // for normalizing to [0;1] range
                xoffset = xdropoffdistance / xnormalizationdivisor
            }

            var yoffset = 0.0

            val ystartdropoffratio = 0.76 // we want to have enough space at the north and south for circumnavigation
            if (ydistanceratio > ystartdropoffratio) {
                val ydropoffdistance = ydistanceratio - ystartdropoffratio
                val ynormalizationdivisor = 1.0 - ystartdropoffratio // for normalizing to [0;1] range
                yoffset = ydropoffdistance / ynormalizationdivisor
            }
            // these factors were just found by trial and error to be adequate for having enough space while not reducing land too much.
            elevationOffset -= xoffset * 0.33
            elevationOffset -= yoffset * 0.35
        }

        return max(elevationOffset, -0.35)
    }

    /**
     * Generates ridged perlin noise. As for parameters see [MapGenerationRandomness.getPerlinNoise]
     */
    private fun getRidgedPerlinNoise(tile: Tile, seed: Double,
                                     nOctaves: Int = 10,
                                     persistence: Double = 0.5,
                                     lacunarity: Double = 2.0,
                                     scale: Double = 10.0): Double {
        val worldCoords = HexMath.hex2WorldCoords(tile.position)
        return Perlin.ridgedNoise3d(worldCoords.x.toDouble(), worldCoords.y.toDouble(), seed, nOctaves, persistence, lacunarity, scale)
    }
    
    /** Returns lon at lat "percentile from center" - numbers between 0.0-0.1 */
    private fun getLonLatFactors(tile: Tile): Pair<Float, Float>{
        val longitudeFactor = abs(tile.longitude) / tileMap.maxLongitude.toFloat()
        val latitudeFactor = abs(tile.latitude) / tileMap.maxLatitude.toFloat()
        return longitudeFactor to latitudeFactor
    }
    
    /** For world wrap maps, we want the "1-N" factor of the tile */
    private fun getReverseLongitudeFactor(longitude: Int): Float = 
        abs(longitude) / tileMap.maxLongitude.toFloat()
    
    //endregion
}
