package com.unciv.logic.map.mapgenerator.mapregions

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.randomWeighted
import kotlin.math.min
import kotlin.math.pow

object LuxuryResourcePlacementLogic {

    /** Assigns a luxury to each region. No luxury can be assigned to too many regions.
     *  Some luxuries are earmarked for city states. The rest are randomly distributed or
     *  don't occur att all in the map */
    fun assignLuxuries(regions: ArrayList<Region>, tileData: TileDataMap, ruleset: Ruleset): Pair<List<String>, List<String>> {

        // If there are any weightings defined in json, assume they are complete. If there are none, use flat weightings instead
        val fallbackWeightings = ruleset.tileResources.values.none {
            it.resourceType == ResourceType.Luxury &&
                (it.uniqueObjects.any { unique -> unique.isOfType(UniqueType.ResourceWeighting) } || it.hasUnique(
                    UniqueType.LuxuryWeightingForCityStates)) }

        val maxRegionsWithLuxury = when {
            regions.size > 12 -> 3
            regions.size > 8 -> 2
            else -> 1
        }
        val targetCityStateLuxuries = 3 // was probably intended to be "if (tileData.size > 5000) 4 else 3"
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
            val modifiedWeights = candidateLuxuries.map {
                val weightingUnique = it.getMatchingUniques(UniqueType.ResourceWeighting, regionConditional).firstOrNull()
                val relativeWeight = if (weightingUnique == null) 1f else weightingUnique.params[0].toFloat()
                relativeWeight / (1f + amountRegionsWithLuxury[it.name]!!)
            }.shuffled()
            region.luxury = candidateLuxuries.randomWeighted(modifiedWeights).name
            amountRegionsWithLuxury[region.luxury!!] = amountRegionsWithLuxury[region.luxury]!! + 1
        }


        val cityStateLuxuries = assignCityStateLuxuries(
            targetCityStateLuxuries,
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
        val randomLuxuries = remainingLuxuries.drop(targetDisabledLuxuries)
        return randomLuxuries
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

            val weights = candidateLuxuries.map {
                val weightingUnique =
                    it.getMatchingUniques(UniqueType.LuxuryWeightingForCityStates).firstOrNull()
                if (weightingUnique == null)
                    1f
                else
                    weightingUnique.params[0].toFloat()
            }
            val luxury = candidateLuxuries.randomWeighted(weights).name
            cityStateLuxuries.add(luxury)
            amountRegionsWithLuxury[luxury] = 1
        }
        return cityStateLuxuries
    }
}
