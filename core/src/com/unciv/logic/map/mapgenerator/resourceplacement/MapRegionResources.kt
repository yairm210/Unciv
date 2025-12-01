package com.unciv.logic.map.mapgenerator.resourceplacement

import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.ImpactType
import com.unciv.logic.map.mapgenerator.mapregions.TileDataMap
import com.unciv.logic.map.mapgenerator.mapregions.anonymizeUnique
import com.unciv.logic.map.mapgenerator.mapregions.getTerrainRule
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.utils.randomWeighted
import kotlin.random.Random

/** This class deals with the internals of *how* to place resources in tiles
 * It does not contain the logic of *when* to do so */
object MapRegionResources {

    /** Given a [tileList] and possible [resourceOptions], will place a resource on every [frequency] tiles.
     *  Tries to avoid impacts, but falls back to lowest impact otherwise.
     *  Goes through the list in order, so pre-shuffle it!
     *  Assumes all tiles in the list are of the same terrain type when generating weightings, irrelevant if only one option.
     *  @return a map of the resources in the options list to number placed. */
    fun placeResourcesInTiles(
        tileData: TileDataMap, frequency: Int, tileList: List<Tile>, resourceOptions: List<TileResource>,
        baseImpact: Int = 0, randomImpact: Int = 0, majorDeposit: Boolean = false
    ): Map<TileResource, Int> {
        if (tileList.none() || resourceOptions.isEmpty()) return mapOf()
        if (frequency == 0) return mapOf()
        val impactType = when (resourceOptions.first().resourceType) {
            ResourceType.Strategic -> ImpactType.Strategic
            ResourceType.Bonus -> ImpactType.Bonus
            ResourceType.Luxury -> ImpactType.Luxury
        }
        val conditionalTerrain = GameContext(attackedTile = tileList.firstOrNull())
        val weightings = resourceOptions.associateWith {
            val unique = it.getMatchingUniques(UniqueType.ResourceWeighting, conditionalTerrain).firstOrNull()
            val weight = if (unique != null) unique.params[0].toFloat() else 1f
            weight
        }
        val amountToPlace = (tileList.size / frequency) + 1
        var amountPlaced = 0
        val detailedPlaced = HashMap<TileResource, Int>()
        resourceOptions.forEach { detailedPlaced[it] = 0 }
        val fallbackTiles = ArrayList<Tile>()
        // First pass - avoid impacts entirely
        for (tile in tileList) {
            if (tile.resource != null) continue
            val possibleResourcesForTile = resourceOptions.filter { it.generatesNaturallyOn(tile) }
            if (possibleResourcesForTile.isEmpty()) continue

            if (tileData[tile.position]!!.impacts.containsKey(impactType)) {
                fallbackTiles.add(tile) // Taken but might be a viable fallback tile
            } else {
                // Add a resource to the tile
                val resourceToPlace = possibleResourcesForTile.randomWeighted { weightings[it] ?: 0f }
                tile.setTileResource(resourceToPlace, majorDeposit)
                tileData.placeImpact(impactType, tile, baseImpact + Random.nextInt(randomImpact + 1))
                amountPlaced++
                detailedPlaced[resourceToPlace] = detailedPlaced[resourceToPlace]!! + 1
                if (amountPlaced >= amountToPlace) {
                    return detailedPlaced
                }
            }
        }
        // Second pass - place on least impacted tiles
        while (amountPlaced < amountToPlace && fallbackTiles.isNotEmpty()) {
            // Sorry, we do need to re-sort the list for every pass since new impacts are made with every placement
            val bestTile = fallbackTiles.minByOrNull { tileData[it.position]!!.impacts[impactType]!! }!!
            fallbackTiles.remove(bestTile)
            val possibleResourcesForTile = resourceOptions.filter { it.generatesNaturallyOn(bestTile) }
            val resourceToPlace = possibleResourcesForTile.randomWeighted { weightings[it] ?: 0f }
            bestTile.setTileResource(resourceToPlace, majorDeposit)
            tileData.placeImpact(impactType, bestTile, baseImpact + Random.nextInt(randomImpact + 1))
            amountPlaced++
            detailedPlaced[resourceToPlace] = detailedPlaced[resourceToPlace]!! + 1
        }
        return detailedPlaced
    }

    /** Attempts to place [amount] [resource] on [tiles], checking tiles in order. A [ratio] below 1 means skipping
     *  some tiles, ie ratio = 0.25 will put a resource on every 4th eligible tile. Can optionally respect impact flags,
     *  and places impact if [baseImpact] >= 0. Returns number of placed resources. */
    fun tryAddingResourceToTiles(tileData: TileDataMap, resource: TileResource, amount: Int, tiles: Sequence<Tile>, ratio: Float = 1f,
                                 respectImpacts: Boolean = false, baseImpact: Int = -1, randomImpact: Int = 0,
                                 majorDeposit: Boolean = false): Int {
        if (amount <= 0) return 0
        var amountAdded = 0
        var ratioProgress = 1f
        val impactType = when (resource.resourceType) {
            ResourceType.Luxury -> ImpactType.Luxury
            ResourceType.Strategic -> ImpactType.Strategic
            ResourceType.Bonus -> ImpactType.Bonus
        }

        for (tile in tiles) {
            if (tile.resource == null && resource.generatesNaturallyOn(tile)) {
                if (ratioProgress >= 1f &&
                    !(respectImpacts && tileData[tile.position]!!.impacts.containsKey(impactType))) {
                    tile.setTileResource(resource, majorDeposit)
                    ratioProgress -= 1f
                    amountAdded++
                    if (baseImpact + randomImpact >= 0)
                        tileData.placeImpact(impactType, tile, baseImpact + Random.nextInt(
                            randomImpact + 1
                        )
                        )
                    if (amountAdded >= amount) break
                }
                ratioProgress += ratio
            }
        }
        return amountAdded
    }

    /** Attempts to place major deposits in a [tileList] consisting exclusively of [terrain] tiles.
     *  Lifted out of the main function to allow postponing water resources.
     *  @return a map of resource types to placed deposits. */
    fun placeMajorDeposits(tileData: TileDataMap, ruleset: Ruleset, tileList: List<Tile>, terrain: Terrain, fallbackWeightings: Boolean, baseImpact: Int, randomImpact: Int): Map<TileResource, Int> {
        if (tileList.isEmpty()) return mapOf()

        val frequency = if (terrain.hasUnique(UniqueType.MajorStrategicFrequency))
            terrain.getMatchingUniques(UniqueType.MajorStrategicFrequency).first().params[0].toInt()
        else 25

        val terrainRule = getTerrainRule(terrain, ruleset)
        val resourceOptions = ruleset.tileResources.values.filter {
            it.resourceType == ResourceType.Strategic &&
                ((fallbackWeightings && terrain.name in it.terrainsCanBeFoundOn) ||
                    it.uniqueObjects.any { unique -> anonymizeUnique(unique).text == terrainRule.text })
        }

        return if (resourceOptions.isNotEmpty())
            placeResourcesInTiles(tileData, frequency, tileList, resourceOptions, baseImpact, randomImpact, true)
        else
            mapOf()
    }

}

