package com.unciv.logic.map.mapgenerator.mapregions

import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.randomWeighted
import kotlin.random.Random

/** This class deals with the internals of *how* to place resources in tiles
 * It does not contain the logic of *when* to do so */
object MapRegionResources {

    /** Given a [tileList] and possible [resourceOptions], will place a resource on every [frequency] tiles.
     *  Tries to avoid impacts, but falls back to lowest impact otherwise.
     *  Goes through the list in order, so pre-shuffle it!
     *  Assumes all tiles in the list are of the same terrain type when generating weightings, irrelevant if only one option.
     *  Respects terrainsCanBeFoundOn when there is only one option, unless [forcePlacement] is true.
     *  @return a map of the resources in the options list to number placed. */
    fun placeResourcesInTiles(tileData: TileDataMap, frequency: Int, tileList: List<Tile>, resourceOptions: List<TileResource>,
                              baseImpact: Int = 0, randomImpact: Int = 0, majorDeposit: Boolean = false,
                              forcePlacement: Boolean = false): Map<TileResource, Int> {
        if (tileList.none() || resourceOptions.isEmpty()) return mapOf()
        val impactType = when (resourceOptions.first().resourceType) {
            ResourceType.Strategic -> MapRegions.ImpactType.Strategic
            ResourceType.Bonus -> MapRegions.ImpactType.Bonus
            ResourceType.Luxury -> MapRegions.ImpactType.Luxury
        }
        val conditionalTerrain = StateForConditionals(attackedTile = tileList.firstOrNull())
        val weightings = resourceOptions.map {
            val unique = it.getMatchingUniques(UniqueType.ResourceWeighting, conditionalTerrain).firstOrNull()
            if (unique != null)
                unique.params[0].toFloat()
            else
                1f
        }
        val testTerrains = (resourceOptions.size == 1) && !forcePlacement
        val amountToPlace = (tileList.size / frequency) + 1
        var amountPlaced = 0
        val detailedPlaced = HashMap<TileResource, Int>()
        resourceOptions.forEach { detailedPlaced[it] = 0 }
        val fallbackTiles = ArrayList<Tile>()
        // First pass - avoid impacts entirely
        for (tile in tileList) {
            if (tile.resource != null ||
                (testTerrains &&
                    (tile.lastTerrain.name !in resourceOptions.first().terrainsCanBeFoundOn ||
                        resourceOptions.first().hasUnique(UniqueType.NoNaturalGeneration, conditionalTerrain)) ) ||
                tile.getBaseTerrain().hasUnique(UniqueType.BlocksResources, conditionalTerrain))
                continue // Can't place here, can't be a fallback tile
            if (tileData[tile.position]!!.impacts.containsKey(impactType)) {
                fallbackTiles.add(tile) // Taken but might be a viable fallback tile
            } else {
                // Add a resource to the tile
                val resourceToPlace = resourceOptions.randomWeighted(weightings)
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
            val resourceToPlace = resourceOptions.randomWeighted(weightings)
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
            ResourceType.Luxury -> MapRegions.ImpactType.Luxury
            ResourceType.Strategic -> MapRegions.ImpactType.Strategic
            ResourceType.Bonus -> MapRegions.ImpactType.Bonus
        }

        for (tile in tiles) {
            val conditionalTerrain = StateForConditionals(attackedTile = tile)
            if (tile.resource == null &&
                tile.lastTerrain.name in resource.terrainsCanBeFoundOn &&
                !tile.getBaseTerrain().hasUnique(UniqueType.BlocksResources, conditionalTerrain) &&
                !resource.hasUnique(UniqueType.NoNaturalGeneration, conditionalTerrain) &&
                resource.getMatchingUniques(UniqueType.TileGenerationConditions).none {
                    tile.temperature!! !in it.params[0].toDouble() .. it.params[1].toDouble()
                        || tile.humidity!! !in it.params[2].toDouble() .. it.params[3].toDouble()
                }
            ) {
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
        if (tileList.isEmpty())
            return mapOf()
        val frequency = if (terrain.hasUnique(UniqueType.MajorStrategicFrequency))
            terrain.getMatchingUniques(UniqueType.MajorStrategicFrequency).first().params[0].toInt()
        else 25
        val resourceOptions = ruleset.tileResources.values.filter {
            it.resourceType == ResourceType.Strategic &&
                ((fallbackWeightings && terrain.name in it.terrainsCanBeFoundOn) ||
                    it.uniqueObjects.any { unique -> anonymizeUnique(unique).text == getTerrainRule(terrain,ruleset).text })
        }
        return if (resourceOptions.isNotEmpty())
            placeResourcesInTiles(tileData, frequency, tileList, resourceOptions, baseImpact, randomImpact, true)
        else
            mapOf()
    }

}
