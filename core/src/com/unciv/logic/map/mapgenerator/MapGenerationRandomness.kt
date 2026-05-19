package com.unciv.logic.map.mapgenerator

import com.unciv.logic.map.HexMath
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.debug
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class MapGenerationRandomness {
    var RNG = Random(42)

    fun seedRNG(seed: Long = 42) {
        RNG = Random(seed)
    }

    /**
     * Generates a perlin noise channel combining multiple octaves
     * Default settings generate mostly within [-0.55, 0.55], but clustered around 0.0
     * About 28% are < -0.1 and 28% are > 0.1
     *
     * @param tile Source for x / x coordinates.
     * @param seed Misnomer: actually the z value the Perlin cloud is 'cut' on.
     * @param nOctaves is the number of octaves.
     * @param persistence is the scaling factor of octave amplitudes.
     * @param lacunarity is the scaling factor of octave frequencies.
     * @param scale is the distance the noise is observed from.
     */
    fun getPerlinNoise(
        tile: Tile,
        seed: Double,
        nOctaves: Int = 6,
        persistence: Double = 0.5,
        lacunarity: Double = 2.0,
        scale: Double = 30.0
    ): Double {
        return getNoise(tile, seed, nOctaves, persistence, lacunarity, scale, Perlin::noise3d)
    }

    /**
     * Generates a noise value using the specified noise function, accounting for world wrap.
     *
     * @param tile Source for coordinates.
     * @param seed Z-axis value (often used as a variation seed).
     * @param noiseFunction The underlying noise function to use (e.g., Perlin::noise3d or Perlin::ridgedNoise3d).
     */
    inline fun getNoise(
        tile: Tile,
        seed: Double,
        nOctaves: Int,
        persistence: Double,
        lacunarity: Double,
        scale: Double,
        noiseFunction: (Double, Double, Double, Int, Double, Double, Double) -> Double
    ): Double {
        val worldCoords = HexMath.hex2WorldCoords(tile.position)
        val params = tile.tileMap.mapParameters

        // Non-wrapping maps use standard 2D noise which naturally has a seam at the edges.
        if (!params.worldWrap)
            return noiseFunction(worldCoords.x.toDouble(), worldCoords.y.toDouble(), seed, nOctaves, persistence, lacunarity, scale)

        // We wrap the 2D map plane into a 3D cylinder. By calculating noise on the surface of this 
        // cylinder, the left edge (0°) and right edge (360°) of the map occupy the exact same 
        // coordinates in 3D noise space, making the transition mathematically perfect.
        val radius = if (params.shape == MapShape.rectangular) {
            val width = params.mapSize.width
            val adjustedWidth = if (width % 2 != 0) width - 1 else width
            adjustedWidth / 2.0
        } else params.mapSize.radius.toDouble()

        // Map horizontal world coordinates to a circle. In Unciv HexMath, a full wrap is 3.0 * radius.
        val wrapPeriod = 3.0 * radius
        val angle = (worldCoords.x / wrapPeriod) * 2 * PI
        val cylinderRadius = wrapPeriod / (2 * PI)

        // OPTIMIZATION NOTE: Since `seed` and `scale` are constant for all tiles during a single 
        // generation pass (e.g., generating all landmasses), calculating these offsets could be 
        // moved out of this per-tile function and passed in/cached to save thousands of redundant 
        // bit-shifts and floating-point math operations per map generation.

        // Unciv's Perlin noise algorithm uses a 256-element permutation array, meaning the core 
        // noise pattern mathematically repeats every 256 integer units.
        // Because the input coordinates are divided by `scale` inside the noise function, the actual
        // spatial period before the terrain pattern repeats is exactly `256.0 * scale`.
        val maxOffset = 256.0 * scale

        // Generate three independent, uncorrelated spatial offsets from the single map seed.
        // - The hex values are borrowed SplitMix64 constants (the first is the golden ratio/Weyl 
        //   increment, the others are bit-mixers). Used here as a one-off multiplicative hash, 
        //   they perfectly scramble the seed into three distinct coordinates.
        // - 'ushr 32' and '/ 4294967296.0' (2^32) extract the top 32 bits into a [0.0, 1.0) fraction.
        // - Multiplying by `maxOffset` maps this fraction exactly to the full period of the noise domain.
        //   This guarantees that ALL map seed variations are mathematically possible (no limitations)
        //   and that every phase shift is equally likely to be generated (no statistical bias).
        // Check out:
        // - https://en.wikipedia.org/wiki/Weyl_sequence
        // - https://rosettacode.org/wiki/Pseudo-random_numbers/Splitmix64
        val seedLong = seed.toLong()
        val offsetX = (((seedLong * 0x9E3779B97F4A7C15uL.toLong()) ushr 32).toDouble() / 4294967296.0) * maxOffset
        val offsetY = (((seedLong * 0xBF58476D1CE4E5B9uL.toLong()) ushr 32).toDouble() / 4294967296.0) * maxOffset
        val offsetZ = (((seedLong * 0x94D049BB133111EBuL.toLong()) ushr 32).toDouble() / 4294967296.0) * maxOffset

        // Map the 2D coordinates onto a 3D cylinder to achieve seamless X-axis world wrapping.
        // The X coordinate becomes an angle wrapped around the circumference (nx, ny), 
        // while the Y coordinate runs straight up and down the cylinder's height (nz).
        val nx = cylinderRadius * cos(angle) + offsetX
        val ny = cylinderRadius * sin(angle) + offsetY
        val nz = worldCoords.y.toDouble() + offsetZ

        return noiseFunction(nx, ny, nz, nOctaves, persistence, lacunarity, scale)
    }

    fun chooseSpreadOutLocations(number: Int, suitableTiles: List<Tile>, mapRadius: Int): ArrayList<Tile> {
        if (number <= 0) return ArrayList(0)

        // Determine sensible initial distance from number of desired placements and mapRadius
        // empiric formula comes very close to eliminating retries for distance.
        // The `if` means if we need to fill 60% or more of the available tiles, no sense starting with minimum distance 2.
        val sparsityFactor = (HexMath.getHexagonalRadiusForArea(suitableTiles.size) / mapRadius).pow(0.333f)
        val initialDistance = if (number == 1 || number * 5 >= suitableTiles.size * 3) 1
            else (mapRadius * 0.666f / HexMath.getHexagonalRadiusForArea(number).pow(0.9f) * sparsityFactor).roundToInt().coerceAtLeast(1)

        // If possible, we want to equalize the base terrains upon which
        //  the resources are found, so we save how many have been
        //  found for each base terrain and try to get one from the lowest
        val baseTerrainsToChosenTiles = HashMap<String, Int>()
        // Once we have a preference to choose from a specific base terrain, we want quick lookup of the available candidates
        val suitableTilesGrouped = LinkedHashMap<String, MutableSet<Tile>>(8)  // 8 is > number of base terrains in vanilla
        // Prefill both with all existing base terrains as keys, and group suitableTiles into base terrain buckets
        for (tile in suitableTiles) {
            val terrain = tile.baseTerrain
            if (terrain !in baseTerrainsToChosenTiles)
                baseTerrainsToChosenTiles[terrain] = 0
            suitableTilesGrouped.getOrPut(terrain) { mutableSetOf() }.add(tile)
        }

        fun LinkedHashMap<String, MutableSet<Tile>>.deepClone(): LinkedHashMap<String, MutableSet<Tile>> {
            // map { it.key to it.value.toMutableSet() }.toMap() is marginally less efficient
            val result = LinkedHashMap<String, MutableSet<Tile>>(size)
            for ((key, value) in this)
                result[key] = value.toMutableSet()
            return result
        }

        for (distanceBetweenResources in initialDistance downTo 1) {
            val availableTiles = suitableTilesGrouped.deepClone()
            val chosenTiles = ArrayList<Tile>(number)

            for (terrain in baseTerrainsToChosenTiles.keys)
                baseTerrainsToChosenTiles[terrain] = 0

            for (i in 1..number) {
                val orderedKeys = baseTerrainsToChosenTiles.entries
                    .sortedBy { it.value }.map { it.key }
                val firstKeyWithTilesLeft = orderedKeys
                    .firstOrNull { availableTiles[it]!!.isNotEmpty() }
                    ?: break
                val chosenTile = availableTiles[firstKeyWithTilesLeft]!!.random(RNG)
                val closeTiles = chosenTile.getTilesInDistance(distanceBetweenResources).toSet()
                for (availableSet in availableTiles.values)
                    availableSet.removeAll(closeTiles)
                chosenTiles.add(chosenTile)
                baseTerrainsToChosenTiles[firstKeyWithTilesLeft] = baseTerrainsToChosenTiles[firstKeyWithTilesLeft]!! + 1
            }
            if (chosenTiles.size == number || distanceBetweenResources == 1) {
                // Either we got them all, or we're not going to get anything better
                if (distanceBetweenResources < initialDistance)
                    debug("chooseSpreadOutLocations: distance %d < initial %d", distanceBetweenResources, initialDistance)
                return chosenTiles
            }
        }
        // unreachable due to last loop iteration always returning and initialDistance >= 1
        throw Exception("Unreachable code reached!")
    }
}
