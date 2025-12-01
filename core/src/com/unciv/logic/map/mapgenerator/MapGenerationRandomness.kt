package com.unciv.logic.map.mapgenerator

import com.unciv.logic.map.HexMath
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.debug
import kotlin.math.pow
import kotlin.math.roundToInt
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
        val worldCoords = HexMath.hex2WorldCoords(tile.position)
        return Perlin.noise3d(worldCoords.x.toDouble(), worldCoords.y.toDouble(), seed, nOctaves, persistence, lacunarity, scale)
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
