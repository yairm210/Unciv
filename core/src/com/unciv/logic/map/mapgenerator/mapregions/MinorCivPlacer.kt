package com.unciv.logic.map.mapgenerator.mapregions

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.math.min

object MinorCivPlacer {

    /** Assigns [civs] to regions or "uninhabited" land and places them. Depends on
     *  assignLuxuries having been called previously.
     *  Note: can silently fail to place all city states if there is too little room.
     *  Currently our GameStarter fills out with random city states, Civ V behavior is to
     *  forget about the discarded city states entirely. */
    fun placeMinorCivs(regions: List<Region>, tileMap: TileMap, civs: List<Civilization>, tileData: TileDataMap, ruleset: Ruleset) {
        if (civs.isEmpty()) return

        // Some but not all city states are assigned to regions directly. Determine the CS density.
        val unassignedCivs = assignMinorCivsDirectlyToRegions(civs, regions)

        // Some city states are assigned to "uninhabited" continents - unless it's an archipelago type map
        // (Because then every continent will have been assigned to a region anyway)
        val uninhabitedCoastal = ArrayList<Tile>()
        val uninhabitedHinterland = ArrayList<Tile>()
        val civAssignedToUninhabited = ArrayList<Civilization>()
        if (!tileMap.usingArchipelagoRegions()) {
            spreadCityStatesBetweenHabitedAndUninhabited(
                tileMap,
                regions,
                tileData,
                uninhabitedCoastal,
                uninhabitedHinterland,
                civs,
                unassignedCivs,
                civAssignedToUninhabited
            )
        }

        assignCityStatesToRegionsWithCommonLuxuries(regions, unassignedCivs)
        spreadCityStatesEvenlyBetweenRegions(unassignedCivs, regions)
        assignRemainingCityStatesToWorstFertileRegions(regions, unassignedCivs)

        // After we've finished assigning, NOW we actually place them
        placeAssignedMinorCivs(
            civAssignedToUninhabited,
            tileMap,
            uninhabitedCoastal,
            tileData,
            ruleset,
            uninhabitedHinterland,
            regions
        )
    }

    private fun spreadCityStatesBetweenHabitedAndUninhabited(
        tileMap: TileMap,
        regions: List<Region>,
        tileData: TileDataMap,
        uninhabitedCoastal: ArrayList<Tile>,
        uninhabitedHinterland: ArrayList<Tile>,
        civs: List<Civilization>,
        unassignedCivs: MutableList<Civilization>,
        civAssignedToUninhabited: ArrayList<Civilization>
    ) {
        val uninhabitedContinents = tileMap.continentSizes.filter {
            it.value >= 4 && // Don't bother with tiny islands
                regions.none { region -> region.continentID == it.key }
        }.keys
        var numInhabitedTiles = 0
        var numUninhabitedTiles = 0
        // Go through the entire map to build the data
        for (tile in tileMap.values) {
            if (!canPlaceMinorCiv(tile, tileData)) continue
            val continent = tile.getContinent()
            if (continent in uninhabitedContinents) {
                if (tile.isCoastalTile())
                    uninhabitedCoastal.add(tile)
                else
                    uninhabitedHinterland.add(tile)
                numUninhabitedTiles++
            } else
                numInhabitedTiles++
        }
        // Determine how many minor civs to put on uninhabited continents.
        val maxByUninhabited =
            (3 * civs.size * numUninhabitedTiles) / (numInhabitedTiles + numUninhabitedTiles)
        val maxByRatio = (civs.size + 1) / 2
        val targetForUninhabited = min(maxByRatio, maxByUninhabited)
        val civsToAssign = unassignedCivs.take(targetForUninhabited)
        unassignedCivs.removeAll(civsToAssign)
        civAssignedToUninhabited.addAll(civsToAssign)
    }

    /** If there are still unassigned minor civs, assign extra ones to regions that share their
     *  luxury type with two others, as compensation. Because starting close to a city state is good??
     */
    private fun assignCityStatesToRegionsWithCommonLuxuries(
        regions: List<Region>,
        unassignedCivs: MutableList<Civilization>
    ) {
        if (unassignedCivs.isEmpty()) return
        val regionsWithCommonLuxuries = regions.filter {
            regions.count { other -> other.luxury == it.luxury } >= 3
        }
        // assign one civ each to regions with common luxuries if there are enough to go around
        if (regionsWithCommonLuxuries.isNotEmpty() &&
            regionsWithCommonLuxuries.size <= unassignedCivs.size
        ) {
            regionsWithCommonLuxuries.forEach {
                val civToAssign = unassignedCivs.first()
                unassignedCivs.remove(civToAssign)
                it.assignedMinorCivs.add(civToAssign)
            }
        }
    }

    /** Add one extra to each region as long as there are enough to go around */
    private fun spreadCityStatesEvenlyBetweenRegions(
        unassignedCivs: MutableList<Civilization>,
        regions: List<Region>
    ) {
        if (unassignedCivs.isEmpty()) return
        while (unassignedCivs.size >= regions.size) {
            regions.forEach {
                val civToAssign = unassignedCivs.first()
                unassignedCivs.remove(civToAssign)
                it.assignedMinorCivs.add(civToAssign)
            }
        }
    }


    /** At this point there is at least for sure less remaining city states than regions
        Sort regions by fertility and put extra city states in the worst ones. */
    private fun assignRemainingCityStatesToWorstFertileRegions(
        regions: List<Region>,
        unassignedCivs: MutableList<Civilization>
    ) {
        if (unassignedCivs.isEmpty()) return
        val worstRegions = regions.sortedBy { it.totalFertility }.take(unassignedCivs.size)
        worstRegions.forEach {
            val civToAssign = unassignedCivs.first()
            unassignedCivs.remove(civToAssign)
            it.assignedMinorCivs.add(civToAssign)
        }
    }

    /** Actually placee the minor civs, after they have been sorted into groups and assigned to regions */
    private fun placeAssignedMinorCivs(
        civAssignedToUninhabited: ArrayList<Civilization>,
        tileMap: TileMap,
        uninhabitedCoastal: ArrayList<Tile>,
        tileData: TileDataMap,
        ruleset: Ruleset,
        uninhabitedHinterland: ArrayList<Tile>,
        regions: List<Region>
    ) {
        // All minor civs are assigned - now place them
        // First place the "uninhabited continent" ones, preferring coastal starts
        tryPlaceMinorCivsInTiles(
            civAssignedToUninhabited, tileMap, uninhabitedCoastal, tileData, ruleset
        )
        tryPlaceMinorCivsInTiles(
            civAssignedToUninhabited, tileMap, uninhabitedHinterland, tileData, ruleset
        )
        // Fallback to a random region for civs that couldn't be placed in the wilderness
        for (unplacedCiv in civAssignedToUninhabited) {
            regions.random().assignedMinorCivs.add(unplacedCiv)
        }

        // Now place the ones assigned to specific regions.
        for (region in regions) {
            tryPlaceMinorCivsInTiles(
                region.assignedMinorCivs, tileMap, region.tiles.toMutableList(), tileData, ruleset
            )
        }
    }

    private fun assignMinorCivsDirectlyToRegions(
        civs: List<Civilization>,
        regions: List<Region>
    ): MutableList<Civilization> {
        val minorCivRatio = civs.size.toFloat() / regions.size
        val minorCivPerRegion = when {
            minorCivRatio > 14f -> 10 // lol
            minorCivRatio > 11f -> 8
            minorCivRatio > 8f -> 6
            minorCivRatio > 5.7f -> 4
            minorCivRatio > 4.35f -> 3
            minorCivRatio > 2.7f -> 2
            minorCivRatio > 1.35f -> 1
            else -> 0
        }
        val unassignedCivs = civs.shuffled().toMutableList()
        if (minorCivPerRegion > 0) {
            regions.forEach {
                val civsToAssign = unassignedCivs.take(minorCivPerRegion)
                it.assignedMinorCivs.addAll(civsToAssign)
                unassignedCivs.removeAll(civsToAssign)
            }
        }
        return unassignedCivs
    }

    /** Attempts to randomly place civs from [civsToPlace] in tiles from [tileList]. Assumes that
     *  [tileList] is pre-vetted and only contains habitable land tiles.
     *  Will modify both [civsToPlace] and [tileList] as it goes! */
    private fun tryPlaceMinorCivsInTiles(civsToPlace: MutableList<Civilization>, tileMap: TileMap, tileList: MutableList<Tile>, tileData: TileDataMap, ruleset: Ruleset) {
        while (tileList.isNotEmpty() && civsToPlace.isNotEmpty()) {
            val chosenTile = tileList.random()
            tileList.remove(chosenTile)
            val data = tileData[chosenTile.position]!!
            // If the randomly chosen tile is too close to a player or a city state, discard it
            if (data.impacts.containsKey(MapRegions.ImpactType.MinorCiv))
                continue
            // Otherwise, go ahead and place the minor civ
            val civToAdd = civsToPlace.first()
            civsToPlace.remove(civToAdd)
            placeMinorCiv(civToAdd, tileMap, chosenTile, tileData, ruleset)
        }
    }

    private fun canPlaceMinorCiv(tile: Tile, tileData: TileDataMap) = !tile.isWater && !tile.isImpassible() &&
        !tileData[tile.position]!!.isJunk &&
        tile.getBaseTerrain().getMatchingUniques(UniqueType.HasQuality).none { it.params[0] == "Undesirable" } && // So we don't get snow hills
        tile.neighbors.count() == 6 // Avoid map edges

    private fun placeMinorCiv(civ: Civilization, tileMap: TileMap, tile: Tile, tileData: TileDataMap, ruleset: Ruleset) {
        tileMap.addStartingLocation(civ.civName, tile)
        tileData.placeImpact(MapRegions.ImpactType.MinorCiv,tile, 4)
        tileData.placeImpact(MapRegions.ImpactType.Luxury,  tile, 3)
        tileData.placeImpact(MapRegions.ImpactType.Strategic,tile, 0)
        tileData.placeImpact(MapRegions.ImpactType.Bonus,   tile, 3)

        StartNormalizer.normalizeStart(tile, tileMap, tileData, ruleset, isMinorCiv = true)
    }

}
