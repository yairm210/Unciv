package com.unciv.logic.city.managers

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.automation.Automation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toPercent
import com.unciv.utils.withItem
import com.unciv.utils.withoutItem
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class CityExpansionManager : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var city: City
    var cultureStored: Int = 0

    fun clone(): CityExpansionManager {
        val toReturn = CityExpansionManager()
        toReturn.cultureStored = cultureStored
        return toReturn
    }

    fun tilesClaimed(): Int {
        val tilesAroundCity = city.getCenterTile().neighbors
                .map { it.position }
        return city.tiles.count { it != city.location && it !in tilesAroundCity}
    }

    // This one has conflicting sources -
    // http://civilization.wikia.com/wiki/Mathematics_of_Civilization_V says it's 20+(10(t-1))^1.1
    // https://www.reddit.com/r/civ/comments/58rxkk/how_in_gods_name_do_borders_expand_in_civ_vi/ has it
    //   (per game XML files) at 6*(t+0.4813)^1.3
    // The second seems to be more based, so I'll go with that
    // -- Note (added later) that this last link is specific to civ VI and not civ V
    fun getCultureToNextTile(): Int {
        var cultureToNextTile = 6 * (max(0, tilesClaimed()) + 1.4813).pow(1.3)

        cultureToNextTile *= city.civ.gameInfo.speed.cultureCostModifier

        if (city.civ.isCityState)
            cultureToNextTile *= 1.5f   // City states grow slower, perhaps 150% cost?

        for (unique in city.getMatchingUniques(UniqueType.BorderGrowthPercentage))
            if (city.matchesFilter(unique.params[1]))
                cultureToNextTile *= unique.params[0].toPercent()

        return cultureToNextTile.roundToInt()
    }

    fun canBuyTile(tile: Tile): Boolean {
        return when {
            city.isPuppet || city.isBeingRazed -> false
            tile.getOwner() != null -> false
            city.isInResistance() -> false
            tile !in city.tilesInRange -> false
            else -> tile.neighbors.any { it.getCity() == city }
        }
    }

    fun buyTile(tile: Tile) {
        val goldCost = getGoldCostOfTile(tile)

        class TriedToBuyNonContiguousTileException(msg: String) : Exception(msg)
        if (tile.neighbors.none { it.getCity() == city })
            throw TriedToBuyNonContiguousTileException("$city tried to buy $tile, but it owns none of the neighbors")

        class NotEnoughGoldToBuyTileException(msg: String) : Exception(msg)
        if (city.civ.gold < goldCost && !city.civ.gameInfo.gameParameters.godMode)
            throw NotEnoughGoldToBuyTileException("$city tried to buy $tile, but lacks gold (cost $goldCost, has ${city.civ.gold})")

        city.civ.addGold(-goldCost)
        takeOwnership(tile)

        // Reapply worked tiles optimization (aka CityFocus) - doing it here means AI profits too
        city.reassignPopulationDeferred()
    }

    fun getGoldCostOfTile(tile: Tile): Int {
        val baseCost = 50
        val distanceFromCenter = tile.aerialDistanceTo(city.getCenterTile())
        var cost = baseCost * (distanceFromCenter - 1) + tilesClaimed() * 5.0

        cost *= city.civ.gameInfo.speed.goldCostModifier

        for (unique in city.getMatchingUniques(UniqueType.TileCostPercentage)) {
            if (city.matchesFilter(unique.params[1]))
                cost *= unique.params[0].toPercent()
        }

        return cost.roundToInt()
    }

    fun getChoosableTiles() = city.getCenterTile().getTilesInDistance(city.getExpandRange())
        .filter { it.getOwner() == null }

    fun chooseNewTileToOwn(): Tile? {
        // Technically, in the original a random tile with the lowest score was selected
        // However, doing this requires either caching it, which is way more work,
        // or selecting all possible tiles and only choosing one when the border expands.
        // But since the order in which tiles are selected in distance is kinda random anyways,
        // this is fine.
        val localUniqueCache = LocalUniqueCache()
        return getChoosableTiles().minByOrNull {
            Automation.rankTileForExpansion(it, city, localUniqueCache)
        }
    }

    //region state-changing functions
    fun reset() {
        for (tile in city.getTiles())
            relinquishOwnership(tile)

        // The only way to create a city inside an owned tile is if it's in your territory
        // In this case, if you don't assign control of the central tile to the city,
        // It becomes an invisible city and weird shit starts happening
        takeOwnership(city.getCenterTile())

        for (tile in city.getCenterTile().getTilesInDistance(1)
                .filter { it.getCity() == null }) // can't take ownership of owned tiles (by other cities)
            takeOwnership(tile)
    }

    private fun addNewTileWithCulture(): Vector2? {
        val chosenTile = chooseNewTileToOwn()
        if (chosenTile != null) {
            cultureStored -= getCultureToNextTile()
            takeOwnership(chosenTile)
            return chosenTile.position
        }
        return null
    }

    /**
     * Removes one tile from this city's owned tiles, unconditionally, and updates dependent
     * things like worked tiles, locked tiles, and stats.
     * @param tile The tile to relinquish
     */
    fun relinquishOwnership(tile: Tile) {
        city.tiles = city.tiles.withoutItem(tile.position)
        for (city in city.civ.cities) {
            if (city.isWorked(tile)) {
                city.population.stopWorkingTile(tile.position)
                city.population.autoAssignPopulation()
            }
        }

        tile.improvementFunctions.removeCreatesOneImprovementMarker()

        tile.setOwningCity(null)

        city.civ.cache.updateOurTiles()
        city.cityStats.update()

        tile.history.recordRelinquishOwnership(tile)
    }

    /**
     * Takes one tile into possession of this city, either unowned or owned by any other city.
     *
     * Also manages consequences like auto population reassign, stats, and displacing units
     * that are no longer allowed on that tile.
     *
     * @param tile The tile to take over
     */
    fun takeOwnership(tile: Tile) {
        check(!tile.isCityCenter()) { "Trying to found a city in a tile that already has one" }
        if (tile.getCity() != null)
            tile.getCity()!!.expansion.relinquishOwnership(tile)

        city.tiles = city.tiles.withItem(tile.position)
        tile.setOwningCity(city)
        city.population.autoAssignPopulation()
        city.civ.cache.updateOurTiles()
        city.cityStats.update()

        for (unit in tile.getUnits().toList()) // toListed because we're modifying
            if (!unit.civ.diplomacyFunctions.canPassThroughTiles(city.civ))
                unit.movement.teleportToClosestMoveableTile()
            else if (unit.civ == city.civ && unit.isSleeping()) {
                // If the unit is sleeping and is a worker, it might want to build on this tile
                // So lets try to wake it up for the player to notice it
                if (unit.cache.hasUniqueToBuildImprovements || unit.cache.hasUniqueToCreateWaterImprovements) {
                    unit.due = true
                    unit.action = null
                }
            }

        tile.history.recordTakeOwnership(tile)
    }

    fun nextTurn(culture: Float) {
        cultureStored += culture.toInt()
        if (cultureStored >= getCultureToNextTile()) {
            val location = addNewTileWithCulture()
            if (location != null) {
                val locations = LocationAction(location, city.location)
                city.civ.addNotification("[${city.name}] has expanded its borders!", locations,
                    NotificationCategory.Cities, NotificationIcon.Culture)
            }
        }
    }

    fun setTransients() {
        val tiles = city.getTiles()
        for (tile in tiles)
            tile.setOwningCity(city)
    }
    //endregion
}
