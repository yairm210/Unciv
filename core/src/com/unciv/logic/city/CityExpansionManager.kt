package com.unciv.logic.city

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.automation.Automation
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.utils.extensions.toPercent
import com.unciv.ui.utils.extensions.withItem
import com.unciv.ui.utils.extensions.withoutItem
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class CityExpansionManager : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var cityInfo: CityInfo
    var cultureStored: Int = 0

    fun clone(): CityExpansionManager {
        val toReturn = CityExpansionManager()
        toReturn.cultureStored = cultureStored
        return toReturn
    }

    fun tilesClaimed(): Int {
        val tilesAroundCity = cityInfo.getCenterTile().neighbors
                .map { it.position }
        return cityInfo.tiles.count { it != cityInfo.location && it !in tilesAroundCity}
    }

    // This one has conflicting sources -
    // http://civilization.wikia.com/wiki/Mathematics_of_Civilization_V says it's 20+(10(t-1))^1.1
    // https://www.reddit.com/r/civ/comments/58rxkk/how_in_gods_name_do_borders_expand_in_civ_vi/ has it
    //   (per game XML files) at 6*(t+0.4813)^1.3
    // The second seems to be more based, so I'll go with that
    // -- Note (added later) that this last link is specific to civ VI and not civ V
    fun getCultureToNextTile(): Int {
        var cultureToNextTile = 6 * (max(0, tilesClaimed()) + 1.4813).pow(1.3)

        if (cityInfo.civInfo.isCityState())
            cultureToNextTile *= 1.5f   // City states grow slower, perhaps 150% cost?

        for (unique in cityInfo.getMatchingUniques(UniqueType.BorderGrowthPercentage))
            if (cityInfo.matchesFilter(unique.params[1]))
                cultureToNextTile *= unique.params[0].toPercent()

        return cultureToNextTile.roundToInt()
    }

    fun buyTile(tileInfo: TileInfo) {
        val goldCost = getGoldCostOfTile(tileInfo)

        class NotEnoughGoldToBuyTileException : Exception()
        if (cityInfo.civInfo.gold < goldCost && !cityInfo.civInfo.gameInfo.gameParameters.godMode)
            throw NotEnoughGoldToBuyTileException()
        cityInfo.civInfo.addGold(-goldCost)
        takeOwnership(tileInfo)
    }

    fun getGoldCostOfTile(tileInfo: TileInfo): Int {
        val baseCost = 50
        val distanceFromCenter = tileInfo.aerialDistanceTo(cityInfo.getCenterTile())
        var cost = baseCost * (distanceFromCenter - 1) + tilesClaimed() * 5.0

        for (unique in cityInfo.getMatchingUniques(UniqueType.TileCostPercentage)) {
            if (cityInfo.matchesFilter(unique.params[1]))
                cost *= unique.params[0].toPercent()
        }

        return cost.roundToInt()
    }

    fun getChoosableTiles() = cityInfo.getCenterTile().getTilesInDistance(5)
        .filter { it.getOwner() == null }

    fun chooseNewTileToOwn(): TileInfo? {
        // Technically, in the original a random tile with the lowest score was selected
        // However, doing this requires either caching it, which is way more work,
        // or selecting all possible tiles and only choosing one when the border expands.
        // But since the order in which tiles are selected in distance is kinda random anyways,
        // this is fine.
        val localUniqueCache = LocalUniqueCache()
        return getChoosableTiles().minByOrNull {
            Automation.rankTileForExpansion(it, cityInfo, localUniqueCache)
        }
    }

    //region state-changing functions
    fun reset() {
        for (tile in cityInfo.getTiles())
            relinquishOwnership(tile)

        // The only way to create a city inside an owned tile is if it's in your territory
        // In this case, if you don't assign control of the central tile to the city,
        // It becomes an invisible city and weird shit starts happening
        takeOwnership(cityInfo.getCenterTile())

        for (tile in cityInfo.getCenterTile().getTilesInDistance(1)
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
     * @param tileInfo The tile to relinquish
     */
    fun relinquishOwnership(tileInfo: TileInfo) {
        cityInfo.tiles = cityInfo.tiles.withoutItem(tileInfo.position)
        for (city in cityInfo.civInfo.cities) {
            if (city.isWorked(tileInfo)) {
                city.workedTiles = city.workedTiles.withoutItem(tileInfo.position)
                city.population.autoAssignPopulation()
            }
            if (city.lockedTiles.contains(tileInfo.position))
                city.lockedTiles.remove(tileInfo.position)
        }

        tileInfo.removeCreatesOneImprovementMarker()

        tileInfo.setOwningCity(null)

        cityInfo.civInfo.updateDetailedCivResources()
        cityInfo.cityStats.update()
    }

    /**
     * Takes one tile into possession of this city, either unowned or owned by any other city.
     *
     * Also manages consequences like auto population reassign, stats, and displacing units
     * that are no longer allowed on that tile.
     *
     * @param tileInfo The tile to take over
     */
    fun takeOwnership(tileInfo: TileInfo) {
        if (tileInfo.isCityCenter()) throw Exception("What?!")
        if (tileInfo.getCity() != null)
            tileInfo.getCity()!!.expansion.relinquishOwnership(tileInfo)

        cityInfo.tiles = cityInfo.tiles.withItem(tileInfo.position)
        tileInfo.setOwningCity(cityInfo)
        cityInfo.population.autoAssignPopulation()
        cityInfo.civInfo.updateDetailedCivResources()
        cityInfo.cityStats.update()

        for (unit in tileInfo.getUnits().toList()) // toListed because we're modifying
            if (!unit.civInfo.canPassThroughTiles(cityInfo.civInfo))
                unit.movement.teleportToClosestMoveableTile()

        cityInfo.civInfo.updateViewableTiles()
    }

    fun nextTurn(culture: Float) {
        cultureStored += culture.toInt()
        if (cultureStored >= getCultureToNextTile()) {
            val location = addNewTileWithCulture()
            if (location != null) {
                val locations = LocationAction(location, cityInfo.location)
                cityInfo.civInfo.addNotification("[" + cityInfo.name + "] has expanded its borders!", locations, NotificationIcon.Culture)
            }
        }
    }

    fun setTransients() {
        val tiles = cityInfo.getTiles()
        for (tile in tiles)
            tile.setOwningCity(cityInfo)
    }
    //endregion
}
