package com.unciv.logic.map.mapgenerator.resourceplacement

import com.unciv.logic.map.mapgenerator.MapResourceSetting
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.mapregions.*
import com.unciv.logic.map.mapgenerator.mapregions.isWaterOnlyResource
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.utils.randomWeighted
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

object LuxuryResourcePlacementLogic {

    /** Assigns a luxury to each region. No luxury can be assigned to too many regions.
     *  Some luxuries are earmarked for city states. The rest are randomly distributed or
     *  don't occur at all in the map */
    fun assignLuxuries(regions: ArrayList<Region>, tileData: TileDataMap, ruleset: Ruleset): Pair<List<String>, List<String>> {

        // If there are any weightings defined in json, assume they are complete. If there are none, use flat weightings instead
        val fallbackWeightings = ruleset.tileResources.values.none {
            it.resourceType == ResourceType.Luxury &&
                (it.hasUnique(UniqueType.ResourceWeighting) || it.hasUnique(UniqueType.LuxuryWeightingForCityStates)) }

        val maxRegionsWithLuxury = when {
            regions.size > 12 -> 3
            regions.size > 8 -> 2
            else -> 1
        }
        
        val assignableLuxuries = ruleset.tileResources.values.filter {
            it.resourceType == ResourceType.Luxury &&
                !it.hasUnique(UniqueType.LuxurySpecialPlacement) &&
                !it.hasUnique(UniqueType.CityStateOnlyResource) }
        val amountRegionsWithLuxury = HashMap<String, Int>()
        // Init map
        ruleset.tileResources.values
            .forEach { amountRegionsWithLuxury[it.name] = 0 }

        for (region in regions.sortedBy { getRegionPriority(ruleset.terrains[it.type]) } ) {
            val candidateLuxuries = getCandidateLuxuries(
                assignableLuxuries,
                amountRegionsWithLuxury,
                maxRegionsWithLuxury,
                fallbackWeightings,
                region,
                ruleset
            )
            // If there are no candidates (mad modders???) just skip this region
            if (candidateLuxuries.isEmpty()) continue

            // Pick a luxury at random. Weight is reduced if the luxury has been picked before
            val regionConditional = StateForConditionals(region = region)
            region.luxury = candidateLuxuries.randomWeighted {
                val weightingUnique = it.getMatchingUniques(UniqueType.ResourceWeighting, regionConditional).firstOrNull()
                val relativeWeight = if (weightingUnique == null) 1f else weightingUnique.params[0].toFloat()
                relativeWeight / (1f + amountRegionsWithLuxury[it.name]!!)
            }.name
            amountRegionsWithLuxury[region.luxury!!] = amountRegionsWithLuxury[region.luxury]!! + 1
        }


        val cityStateLuxuries = assignCityStateLuxuries(
            3, // was probably intended to be "if (tileData.size > 5000) 4 else 3",
            assignableLuxuries,
            amountRegionsWithLuxury,
            fallbackWeightings
        )

        val randomLuxuries = getLuxuriesForRandomPlacement(assignableLuxuries, amountRegionsWithLuxury, tileData, ruleset)

        return Pair(cityStateLuxuries, randomLuxuries)
    }

    private fun getLuxuriesForRandomPlacement(
        assignableLuxuries: List<TileResource>,
        amountRegionsWithLuxury: HashMap<String, Int>,
        tileData: TileDataMap,
        ruleset: Ruleset
    ): List<String> {
        val remainingLuxuries = assignableLuxuries.filter {
            amountRegionsWithLuxury[it.name] == 0
        }.map { it.name }.shuffled()

        val disabledPercent =
            100 - min(tileData.size.toFloat().pow(0.2f) * 16, 100f).toInt() // Approximately
        val targetDisabledLuxuries = (ruleset.tileResources.values
            .count { it.resourceType == ResourceType.Luxury } * disabledPercent) / 100
        return remainingLuxuries.drop(targetDisabledLuxuries)
    }

    private fun getCandidateLuxuries(
        assignableLuxuries: List<TileResource>,
        amountRegionsWithLuxury: HashMap<String, Int>,
        maxRegionsWithLuxury: Int,
        fallbackWeightings: Boolean,
        region: Region,
        ruleset: Ruleset
    ): List<TileResource> {
        val regionConditional = StateForConditionals(region = region)

        var candidateLuxuries = assignableLuxuries.filter {
            amountRegionsWithLuxury[it.name]!! < maxRegionsWithLuxury &&
                // Check that it has a weight for this region type
                (fallbackWeightings ||
                    it.hasUnique(UniqueType.ResourceWeighting, regionConditional)) &&
                // Check that there is enough coast if it is a water based resource
                ((region.terrainCounts["Coastal"] ?: 0) >= 12 ||
                    it.terrainsCanBeFoundOn.any { terrain -> ruleset.terrains[terrain]!!.type != TerrainType.Water })
        }

        // If we couldn't find any options, pick from all luxuries. First try to not pick water luxuries on land regions
        if (candidateLuxuries.isEmpty()) {
            candidateLuxuries = assignableLuxuries.filter {
                amountRegionsWithLuxury[it.name]!! < maxRegionsWithLuxury &&
                    // Ignore weightings for this pass
                    // Check that there is enough coast if it is a water based resource
                    ((region.terrainCounts["Coastal"] ?: 0) >= 12 ||
                        it.terrainsCanBeFoundOn.any { terrain -> ruleset.terrains[terrain]!!.type != TerrainType.Water })
            }
        }
        // If there are still no candidates, ignore water restrictions
        if (candidateLuxuries.isEmpty()) {
            candidateLuxuries = assignableLuxuries.filter {
                amountRegionsWithLuxury[it.name]!! < maxRegionsWithLuxury
                // Ignore weightings and water for this pass
            }
        }
        return candidateLuxuries
    }

    private fun assignCityStateLuxuries(
        targetCityStateLuxuries: Int,
        assignableLuxuries: List<TileResource>,
        amountRegionsWithLuxury: HashMap<String, Int>,
        fallbackWeightings: Boolean
    ): ArrayList<String> {
        val cityStateLuxuries = ArrayList<String>()
        repeat(targetCityStateLuxuries) {
            val candidateLuxuries = assignableLuxuries.filter {
                amountRegionsWithLuxury[it.name] == 0 &&
                        (fallbackWeightings || it.hasUnique(UniqueType.LuxuryWeightingForCityStates))
            }
            if (candidateLuxuries.isEmpty()) return@repeat

            val luxury = candidateLuxuries.randomWeighted {
                val weightingUnique =
                    it.getMatchingUniques(UniqueType.LuxuryWeightingForCityStates).firstOrNull()
                if (weightingUnique == null)
                    1f
                else
                    weightingUnique.params[0].toFloat()
            }.name
            cityStateLuxuries.add(luxury)
            amountRegionsWithLuxury[luxury] = 1
        }
        return cityStateLuxuries
    }


    /** Places all Luxuries onto [tileMap]. Assumes that assignLuxuries and placeMinorCivs have been called. */
    fun placeLuxuries(
        regions: ArrayList<Region>,
        tileMap: TileMap,
        tileData: TileDataMap,
        ruleset: Ruleset,
        cityStateLuxuries: List<String>,
        randomLuxuries: List<String>
    ) {

        placeLuxuriesAtMajorCivStartLocations(regions, tileMap, ruleset, tileData, randomLuxuries)
        placeLuxuriesAtMinorCivStartLocations(tileMap, ruleset, regions, randomLuxuries, cityStateLuxuries, tileData)
        addRegionalLuxuries(tileData, regions, tileMap, ruleset)
        addRandomLuxuries(randomLuxuries, tileData, tileMap, regions, ruleset)


        val specialLuxuries = ruleset.tileResources.values.filter {
            it.resourceType == ResourceType.Luxury &&
                it.hasUnique(UniqueType.LuxurySpecialPlacement)
        }
        val placedSpecials = HashMap<String, Int>()
        specialLuxuries.forEach { placedSpecials[it.name] = 0 } // init map

        addExtraLuxuryToStarts(
            tileMap,
            regions,
            randomLuxuries,
            specialLuxuries,
            cityStateLuxuries,
            tileData,
            ruleset,
            placedSpecials
        )

        fillSpecialLuxuries(specialLuxuries, tileMap, regions, placedSpecials, tileData)
    }

    /** top up marble-type specials if needed */
    private fun fillSpecialLuxuries(
        specialLuxuries: List<TileResource>,
        tileMap: TileMap,
        regions: ArrayList<Region>,
        placedSpecials: HashMap<String, Int>,
        tileData: TileDataMap
    ) {
        for (special in specialLuxuries) {
            val targetNumber = (regions.size * tileMap.mapParameters.getMapResources().specialLuxuriesTargetFactor).toInt()
            val numberToPlace = max(2, targetNumber - placedSpecials[special.name]!!)
            MapRegionResources.tryAddingResourceToTiles(
                tileData, special, numberToPlace, tileMap.values.asSequence().shuffled(), 1f,
                true, 6, 0
            )
        }
    }

    private fun addExtraLuxuryToStarts(
        tileMap: TileMap,
        regions: ArrayList<Region>,
        randomLuxuries: List<String>,
        specialLuxuries: List<TileResource>,
        cityStateLuxuries: List<String>,
        tileData: TileDataMap,
        ruleset: Ruleset,
        placedSpecials: HashMap<String, Int>
    ) {
        if (tileMap.mapParameters.mapResources == MapResourceSetting.sparse.label) return
        for (region in regions) {
            val tilesToCheck = tileMap[region.startPosition!!].getTilesInDistanceRange(1..2)
            val candidateLuxuries = randomLuxuries.shuffled().toMutableList()
            if (!tileMap.mapParameters.getStrategicBalance())
                candidateLuxuries += specialLuxuries.shuffled()
                    .map { it.name } // Include marble!
            candidateLuxuries += cityStateLuxuries.shuffled()
            candidateLuxuries += regions.mapNotNull { it.luxury }.shuffled()
            for (luxury in candidateLuxuries) {
                if (MapRegionResources.tryAddingResourceToTiles(
                        tileData,
                        ruleset.tileResources[luxury]!!,
                        1,
                        tilesToCheck
                    ) > 0
                ) {
                    if (placedSpecials.containsKey(luxury)) // Keep track of marble-type specials as they may be placed now.
                        placedSpecials[luxury] = placedSpecials[luxury]!! + 1
                    break
                }
            }
        }
    }

    private fun addRandomLuxuries(
        randomLuxuries: List<String>,
        tileData: TileDataMap,
        tileMap: TileMap,
        regions: ArrayList<Region>,
        ruleset: Ruleset
    ) {
        if (randomLuxuries.isEmpty()) return
        var targetRandomLuxuries = tileData.size.toFloat().pow(0.45f).toInt() // Approximately
        targetRandomLuxuries *= tileMap.mapParameters.getMapResources().randomLuxuriesPercent
        targetRandomLuxuries /= 100
        targetRandomLuxuries += Random.nextInt(regions.size) // Add random number based on number of civs
        val minimumRandomLuxuries = tileData.size.toFloat().pow(0.2f).toInt() // Approximately
        val worldTiles = tileMap.values.asSequence().shuffled()
        for ((index, luxury) in randomLuxuries.shuffled().withIndex()) {
            val targetForThisLuxury = if (randomLuxuries.size > 8) targetRandomLuxuries / 10
            else {
                val minimum = max(3, minimumRandomLuxuries - index)
                max(
                    minimum,
                    (targetRandomLuxuries * MapRegions.randomLuxuryRatios[randomLuxuries.size]!![index] + 0.5f).toInt()
                )
            }
            MapRegionResources.tryAddingResourceToTiles(
                tileData,
                ruleset.tileResources[luxury]!!,
                targetForThisLuxury,
                worldTiles,
                0.25f,
                true,
                4,
                2
            )
        }
    }

    private fun addRegionalLuxuries(
        tileData: TileDataMap,
        regions: ArrayList<Region>,
        tileMap: TileMap,
        ruleset: Ruleset
    ) {
        val idealCivsForMapSize = max(2, tileData.size / 500)
        var regionTargetNumber =
            (tileData.size / 600) - (0.3f * abs(regions.size - idealCivsForMapSize)).toInt()
        regionTargetNumber += tileMap.mapParameters.getMapResources().regionalLuxuriesDelta
        regionTargetNumber = max(1, regionTargetNumber)
        for (region in regions) {
            val resource = ruleset.tileResources[region.luxury] ?: continue
            fun Tile.isShoreOfContinent(continent: Int) =
                isWater && neighbors.any { it.getContinent() == continent }

            val candidates = if (isWaterOnlyResource(resource, ruleset))
                tileMap.getTilesInRectangle(region.rect)
                    .filter { it.isShoreOfContinent(region.continentID) }
            else region.tiles.asSequence()
            MapRegionResources.tryAddingResourceToTiles(
                tileData,
                resource,
                regionTargetNumber,
                candidates.shuffled(),
                0.4f,
                true,
                4,
                2
            )
        }
    }

    private fun placeLuxuriesAtMinorCivStartLocations(
        tileMap: TileMap,
        ruleset: Ruleset,
        regions: ArrayList<Region>,
        randomLuxuries: List<String>,
        cityStateLuxuries: List<String>,
        tileData: TileDataMap
    ) {
        for (startLocation in tileMap.startingLocationsByNation
            .filterKeys { ruleset.nations[it]!!.isCityState }.map { it.value.first() }) {
            val region = regions.firstOrNull { startLocation in it.tiles }
            val tilesToCheck = startLocation.getTilesInDistanceRange(1..2)
            // 75% probability that we first attempt to place a "city state" luxury, then a random or regional one
            // 25% probability of going the other way around
            val globalLuxuries =
                if (region?.luxury != null) randomLuxuries + listOf(region.luxury) else randomLuxuries
            val candidateLuxuries = if (Random.nextInt(100) >= 25)
                cityStateLuxuries.shuffled() + globalLuxuries.shuffled()
            else
                globalLuxuries.shuffled() + cityStateLuxuries.shuffled()
            // Now try adding one until we are successful
            for (luxury in candidateLuxuries) {
                if (MapRegionResources.tryAddingResourceToTiles(
                        tileData,
                        ruleset.tileResources[luxury]!!,
                        1,
                        tilesToCheck
                    ) > 0
                ) break
            }
        }
    }

    private fun placeLuxuriesAtMajorCivStartLocations(
        regions: ArrayList<Region>,
        tileMap: TileMap,
        ruleset: Ruleset,
        tileData: TileDataMap,
        randomLuxuries: List<String>
    ) {
        val averageFertilityDensity =
            regions.sumOf { it.totalFertility } / regions.sumOf { it.tiles.size }.toFloat()
        for (region in regions) {
            var targetLuxuries = 2
            if (tileMap.mapParameters.getLegendaryStart())
                targetLuxuries++
            if (region.totalFertility / region.tiles.size.toFloat() < averageFertilityDensity) {
                targetLuxuries++
            }

            val luxuryToPlace = ruleset.tileResources[region.luxury] ?: continue
            // First check 2 inner rings
            val firstPass = tileMap[region.startPosition!!].getTilesInDistanceRange(1..2)
                .shuffled().sortedBy { it.getTileFertility(false) } // Check bad tiles first
            targetLuxuries -= MapRegionResources.tryAddingResourceToTiles(
                tileData,
                luxuryToPlace,
                targetLuxuries,
                firstPass,
                0.5f
            ) // Skip every 2nd tile on first pass

            if (targetLuxuries > 0) {
                val secondPass = firstPass + tileMap[region.startPosition!!].getTilesAtDistance(3)
                    .shuffled().sortedBy { it.getTileFertility(false) } // Check bad tiles first
                targetLuxuries -= MapRegionResources.tryAddingResourceToTiles(
                    tileData,
                    luxuryToPlace,
                    targetLuxuries,
                    secondPass
                )
            }
            if (targetLuxuries > 0) {
                // Try adding in 1 luxury from the random rotation as compensation
                for (luxury in randomLuxuries) {
                    if (MapRegionResources.tryAddingResourceToTiles(
                            tileData, ruleset.tileResources[luxury]!!, 1, firstPass) > 0
                    ) break
                }
            }
        }
    }

}
