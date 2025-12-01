package com.unciv.logic.map.mapgenerator.resourceplacement

import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.mapregions.*
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.Companion.baseMinorDepositFrequency
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions.ImpactType
import com.unciv.logic.map.mapgenerator.mapregions.anonymizeUnique
import com.unciv.logic.map.mapgenerator.mapregions.getTerrainRule
import com.unciv.logic.map.mapgenerator.mapregions.isWaterOnlyResource
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.utils.randomWeighted
import kotlin.math.min
import kotlin.random.Random

object StrategicBonusResourcePlacementLogic {


    /** There are a couple competing/complementary distribution systems at work here. First, major
    deposits are placed according to a frequency defined in the terrains themselves, for each
    tile that is eligible to get a major deposit, there is a weighted random choice between
    resource types.
    Minor deposits are placed by randomly picking a number of land tiles from anywhere on the
    map (so not stratified by terrain type) and assigning a weighted randomly picked resource.
    Bonuses are placed according to a frequency for a rule like "every 8 jungle hills", here
    implemented as a conditional.

    We need to build lists of all tiles following a given rule to place these, which is BY FAR
    the most expensive calculation in this entire class. To save some time we anonymize the
    uniques so we only have to make one list for each set of conditionals, so eg Wheat and
    Horses can share a list since they are both interested in Featureless Plains.
    We also save a list of all land tiles for minor deposit generation. */

    internal fun placeStrategicAndBonuses(tileMap: TileMap, regions: ArrayList<Region>, tileData: TileDataMap) {
        val ruleset = tileMap.ruleset!!
        val strategicResources = ruleset.tileResources.values.filter { it.resourceType == ResourceType.Strategic }
        // As usual, if there are any relevant json definitions, assume they are complete
        val fallbackStrategic = ruleset.tileResources.values.none {
            it.resourceType == ResourceType.Strategic &&
                    it.hasUnique(UniqueType.ResourceWeighting) ||
                    it.hasUnique(UniqueType.MinorDepositWeighting)
        }

        // Determines number tiles per resource
        val bonusMultiplier = tileMap.mapParameters.getMapResources().bonusFrequencyMultiplier
        val landList = ArrayList<Tile>() // For minor deposits

        /** Maps resource uniques for determining frequency/weighting/size to relevant tiles  */ 
        val ruleLists = buildRuleLists(ruleset, tileMap, regions, fallbackStrategic, strategicResources) // For rule-based generation
        
        // Now go through the entire map to build lists
        for (tile in tileMap.values.asSequence().shuffled()) {
            val terrainCondition = GameContext(attackedTile = tile, region = regions.firstOrNull { tile in it.tiles })
            if (tile.getBaseTerrain().hasUnique(UniqueType.BlocksResources, terrainCondition)) continue // Don't count snow hills
            if (tile.isLand) landList.add(tile)
            for ((rule, list) in ruleLists) {
                if (rule.conditionalsApply(terrainCondition)) list.add(tile)
            }
        }
        
        // Keep track of total placed strategic resources in case we need to top them up later
        val totalPlaced = HashMap<TileResource, Int>()
        strategicResources.forEach { totalPlaced[it] = 0 }

        placeMajorDepositsOnLand(ruleset, ruleLists, totalPlaced, tileData, fallbackStrategic)
        placeSmallDepositsOfModernStrategicResourcesOnCityStates(ruleset, strategicResources, tileMap, totalPlaced, tileData)
        placeMinorDepositsOnLand(bonusMultiplier, landList, tileData, strategicResources, fallbackStrategic, totalPlaced)
        placeMajorDepositsOnWater(ruleset, ruleLists, totalPlaced, tileData, fallbackStrategic)
        ensureMinimumResourcesPerCiv(strategicResources, regions, totalPlaced, ruleset, landList, tileMap, tileData)
        placeBonusResources(ruleset, ruleLists, tileData, bonusMultiplier, tileMap)
        placeBonusInThirdRingOfStart(regions, ruleset, tileMap, tileData)
    }

    private fun placeBonusInThirdRingOfStart(
        regions: ArrayList<Region>,
        ruleset: Ruleset,
        tileMap: TileMap,
        tileData: TileDataMap
    ) {
        for (region in regions) {
            val terrain = if (region.type == "Hybrid") region.terrainCounts.filterNot { it.key == "Coastal" }
                .maxByOrNull { it.value }!!.key
            else region.type
            val resourceUnique =
                ruleset.terrains[terrain]!!.getMatchingUniques(UniqueType.RegionExtraResource).firstOrNull()
            // If this region has an explicit "this is the bonus" unique go with that, else random appropriate
            val resource = if (resourceUnique != null) ruleset.tileResources[resourceUnique.params[0]]!!
            else {
                val possibleResources =
                    ruleset.tileResources.values.filter { it.resourceType == ResourceType.Bonus && terrain in it.terrainsCanBeFoundOn }
                if (possibleResources.isEmpty()) continue
                possibleResources.random()
            }
            val candidateTiles = tileMap[region.startPosition!!].getTilesAtDistance(3).shuffled()
            val amount = if (resourceUnique != null) 2 else 1 // Place an extra if the region type requests it
            if (MapRegionResources.tryAddingResourceToTiles(tileData, resource, amount, candidateTiles) == 0) {
                // We couldn't place any, try adding a fish instead
                val fishyBonus = ruleset.tileResources.values.filter {
                    it.resourceType == ResourceType.Bonus &&
                            it.terrainsCanBeFoundOn.any { terrainName -> ruleset.terrains[terrainName]!!.type == TerrainType.Water }
                }
                    .randomOrNull()
                if (fishyBonus != null)
                    MapRegionResources.tryAddingResourceToTiles(tileData, fishyBonus, 1, candidateTiles)
            }
        }
    }

    private fun placeBonusResources(
        ruleset: Ruleset,
        ruleLists: HashMap<Unique, MutableList<Tile>>,
        tileData: TileDataMap,
        bonusMultiplier: Float,
        tileMap: TileMap
    ) {
        // Figure out if bonus generation rates are defined in json. Assume that if there are any, the definitions are complete.
        val useFallbackBonuses = ruleset.tileResources.values.none { it.hasUnique(UniqueType.ResourceFrequency) }
        
        // Water-based bonuses go last and have extra impact, because coasts are very common and we don't want too much clustering
        val sortedResourceList = ruleset.tileResources.values.sortedBy { isWaterOnlyResource(it, ruleset) }
        
        for (resource in sortedResourceList) {
            val extraImpact = if (isWaterOnlyResource(resource, ruleset)) 1 else 0
            for (rule in resource.uniqueObjects.filter { it.type == UniqueType.ResourceFrequency }) {
                // Figure out which list applies, if any
                val simpleRule = anonymizeUnique(rule)
                val list = ruleLists.filterKeys { it.text == simpleRule.text }.values.firstOrNull()
                // If there is no matching list, it is because the rule was determined to be impossible and so can be safely skipped
                    ?: continue
                // Place the resources
                MapRegionResources.placeResourcesInTiles(
                    tileData,
                    (rule.params[0].toFloat() * bonusMultiplier).toInt(),
                    list,
                    listOf(resource),
                    0 + extraImpact,
                    2 + extraImpact,
                    false
                )
            }
            
            if (useFallbackBonuses && resource.resourceType == ResourceType.Bonus) {
                // Since we haven't been able to generate any rule-based lists, just generate new ones on the fly
                // Increase impact to avoid clustering since there is no terrain type stratification.
                val fallbackList =
                    tileMap.values.filter { it.lastTerrain.name in resource.terrainsCanBeFoundOn }.shuffled()
                MapRegionResources.placeResourcesInTiles(
                    tileData,
                    (20 * bonusMultiplier).toInt(),
                    fallbackList,
                    listOf(resource),
                    2 + extraImpact,
                    2 + extraImpact,
                    false
                )
            }
        }
    }

    /** place up to 2 extra deposits of each resource type if there is < 1 per civ */
    private fun ensureMinimumResourcesPerCiv(
        strategicResources: List<TileResource>,
        regions: ArrayList<Region>,
        totalPlaced: HashMap<TileResource, Int>,
        ruleset: Ruleset,
        landList: ArrayList<Tile>,
        tileMap: TileMap,
        tileData: TileDataMap
    ) {
        for (resource in strategicResources) {
            val extraNeeded = min(2, regions.size - totalPlaced[resource]!!)
            if (extraNeeded > 0) {
                val tilesToAddTo = if (!isWaterOnlyResource(resource, ruleset)) landList.asSequence()
                else tileMap.values.asSequence().filter { it.isWater }.shuffled()

                MapRegionResources.tryAddingResourceToTiles(
                    tileData,
                    resource,
                    extraNeeded,
                    tilesToAddTo,
                    respectImpacts = true
                )
            }
        }
    }


    // Extra impact because we don't want them too clustered and there is usually lots to go around
    private fun placeMajorDepositsOnWater(
        ruleset: Ruleset,
        ruleLists: HashMap<Unique, MutableList<Tile>>,
        totalPlaced: HashMap<TileResource, Int>,
        tileData: TileDataMap,
        fallbackStrategic: Boolean
    ) {
        for (terrain in ruleset.terrains.values.filter { it.type == TerrainType.Water }) {
            // Figure out if we generated a list for this terrain
            val list = ruleLists.filterKeys { it.text == getTerrainRule(terrain, ruleset).text }.values.firstOrNull()
                ?: continue // If not the terrain can be safely skipped
            totalPlaced += MapRegionResources.placeMajorDeposits(
                tileData,
                ruleset,
                list,
                terrain,
                fallbackStrategic,
                4,
                3
            )
        }
    }

    private fun placeMinorDepositsOnLand(
        bonusMultiplier: Float,
        landList: ArrayList<Tile>,
        tileData: TileDataMap,
        strategicResources: List<TileResource>,
        fallbackStrategic: Boolean,
        totalPlaced: HashMap<TileResource, Int>
    ) {
        val frequency = (baseMinorDepositFrequency * bonusMultiplier).toInt()
        val minorDepositsToAdd =
            (landList.size / frequency) + 1 // I sometimes have division by zero errors on this line
        var minorDepositsAdded = 0
        for (tile in landList) {
            if (tile.resource != null || tileData[tile.position]!!.impacts.containsKey(ImpactType.Strategic))
                continue
            val conditionalTerrain = GameContext(attackedTile = tile)
            if (tile.getBaseTerrain().hasUnique(UniqueType.BlocksResources, conditionalTerrain))
                continue
            val weightings = strategicResources.map {
                if (fallbackStrategic) {
                    if (it.generatesNaturallyOn(tile)) 1f else 0f
                } else {
                    val uniques = it.getMatchingUniques(UniqueType.MinorDepositWeighting, conditionalTerrain).toList()
                    uniques.sumOf { unique -> unique.params[0].toInt() }.toFloat()
                }
            }
            if (weightings.sum() <= 0) continue

            val resourceToPlace = strategicResources.randomWeighted(weightings)
            tile.setTileResource(resourceToPlace, majorDeposit = false)
            tileData.placeImpact(ImpactType.Strategic, tile, Random.nextInt(2) + Random.nextInt(2))
            totalPlaced[resourceToPlace] = totalPlaced[resourceToPlace]!! + 1
            minorDepositsAdded++
            if (minorDepositsAdded >= minorDepositsToAdd)
                break
        }
    }

    private fun placeSmallDepositsOfModernStrategicResourcesOnCityStates(
        ruleset: Ruleset,
        strategicResources: List<TileResource>,
        tileMap: TileMap,
        totalPlaced: HashMap<TileResource, Int>,
        tileData: TileDataMap
    ) {
        val lastEra = ruleset.eras.values.maxOf { it.eraNumber }
        val modernOptions = strategicResources.filter {
            it.revealedBy != null &&
                    ruleset.eras[ruleset.technologies[it.revealedBy]!!.era()]!!.eraNumber >= lastEra / 2
        }

        if (modernOptions.any())
            for (cityStateLocation in tileMap.startingLocationsByNation
                .filterKeys { ruleset.nations[it]!!.isCityState }.values.map { it.first() }) {
                val resourceToPlace = modernOptions.random()
                totalPlaced[resourceToPlace] =
                    totalPlaced[resourceToPlace]!! + MapRegionResources.tryAddingResourceToTiles(
                        tileData,
                        resourceToPlace,
                        1,
                        cityStateLocation.getTilesInDistanceRange(1..3)
                    )
            }
    }

    private fun placeMajorDepositsOnLand(
        ruleset: Ruleset,
        ruleLists: HashMap<Unique, MutableList<Tile>>,
        totalPlaced: HashMap<TileResource, Int>,
        tileData: TileDataMap,
        fallbackStrategic: Boolean
    ) {
        for (terrain in ruleset.terrains.values.filter { it.type != TerrainType.Water }) {
            // Figure out if we generated a list for this terrain
            val terrainRule = getTerrainRule(terrain, ruleset)
            val list = ruleLists.filterKeys { it.text == terrainRule.text }.values.firstOrNull()
                ?: continue // If not the terrain can be safely skipped
            totalPlaced += MapRegionResources.placeMajorDeposits(
                tileData,
                ruleset,
                list,
                terrain,
                fallbackStrategic,
                2,
                2
            )
        }
    }

    private fun buildRuleLists(
        ruleset: Ruleset,
        tileMap: TileMap,
        regions: ArrayList<Region>,
        fallbackStrategic: Boolean,
        strategicResources: List<TileResource>
    ): HashMap<Unique, MutableList<Tile>> {
        val ruleLists = HashMap<Unique, MutableList<Tile>>() // For rule-based generation

        // Figure out which rules (sets of conditionals) need lists built
        for (resource in ruleset.tileResources.values.filter {
            it.resourceType == ResourceType.Strategic ||
                    it.resourceType == ResourceType.Bonus
        }) {

            for (rule in resource.uniqueObjects.filter { unique ->
                unique.type == UniqueType.ResourceFrequency ||
                        unique.type == UniqueType.ResourceWeighting ||
                        unique.type == UniqueType.MinorDepositWeighting
            }) {
                // Weed out some clearly impossible rules straight away to save time later
                if (rule.modifiers.any { conditional ->
                        (conditional.type == UniqueType.ConditionalOnWaterMaps && !tileMap.usingArchipelagoRegions()) ||
                                (conditional.type == UniqueType.ConditionalInRegionOfType && regions.none { region -> region.type == conditional.params[0] }) ||
                                (conditional.type == UniqueType.ConditionalInRegionExceptOfType && regions.all { region -> region.type == conditional.params[0] })
                    })
                    continue
                val simpleRule = anonymizeUnique(rule)
                if (ruleLists.keys.none { it.text == simpleRule.text }) // Need to do text comparison since the uniques will not be equal otherwise
                    ruleLists[simpleRule] = ArrayList()
            }
        }

        // Make up some rules for placing strategics in a fallback situation
        if (fallbackStrategic) {
            val interestingTerrains =
                strategicResources.flatMap { it.terrainsCanBeFoundOn }.map { ruleset.terrains[it]!! }.toSet()
            for (terrain in interestingTerrains) {
                val fallbackRule = if (terrain.type == TerrainType.TerrainFeature)
                    Unique("RULE <in [${terrain.name}] tiles>")
                else
                    Unique("RULE <in [Featureless] [${terrain.name}] tiles>")
                if (ruleLists.keys.none { it.text == fallbackRule.text }) // Need to do text comparison since the uniques will not be equal otherwise
                    ruleLists[fallbackRule] = ArrayList()
            }
        }
        return ruleLists
    }
}
