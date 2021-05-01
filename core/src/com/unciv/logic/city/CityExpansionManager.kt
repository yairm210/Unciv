package com.unciv.logic.city

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.automation.Automation
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.withItem
import com.unciv.ui.utils.withoutItem
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class CityExpansionManager {
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
    fun getCultureToNextTile(): Int {
        var cultureToNextTile = 6 * (max(0, tilesClaimed()) + 1.4813).pow(1.3)
        for (unique in cityInfo.civInfo.getMatchingUniques("-[]% Culture cost of acquiring tiles []")) {
            if (cityInfo.matchesFilter(unique.params[1]))
                cultureToNextTile *= (100 - unique.params[0].toFloat()) / 100
        }

        if (cityInfo.civInfo.hasUnique("Increased rate of border expansion")) cultureToNextTile *= 0.75

        return cultureToNextTile.roundToInt()
    }

    fun buyTile(tileInfo: TileInfo) {
        val goldCost = getGoldCostOfTile(tileInfo)

        class NotEnoughGoldToBuyTileException : Exception()
        if (cityInfo.civInfo.gold < goldCost && !cityInfo.civInfo.gameInfo.gameParameters.godMode)
            throw NotEnoughGoldToBuyTileException()
        cityInfo.civInfo.gold -= goldCost
        takeOwnership(tileInfo)
    }

    /**
     * Calculates the cost to buy a specified tile
     *      or a sequence of tiles at the same distance from city center
     * @param[tileInfo] the first tile to buy
     *      used only to determine distance from city center
     * @param[count] the number of tiles to buy
     * @return cost in gold units
     */
    fun getGoldCostOfTile(tileInfo: TileInfo, count: Int = 1): Int {
        val baseCost = 50
        val distanceFromCenter = tileInfo.aerialDistanceTo(cityInfo.getCenterTile())
        val distanceCost = baseCost * (distanceFromCenter - 1)
        val claimed = tilesClaimed()

        var factor = 1f
        for (unique in cityInfo.civInfo.getMatchingUniques("-[]% Gold cost of acquiring tiles []")) {
            if (cityInfo.matchesFilter(unique.params[1]))
                factor *= (100 - unique.params[0].toFloat()) / 100
        }

        var cost = 0
        // Aggregating in a loop instead of exponential math to ensure
        // rounding after unique rebate does not lead to one-off differences
        for (i in claimed until claimed+count){
            cost += ((distanceCost + i * 5.0) * factor).roundToInt()
        }
        return cost
    }


    fun chooseNewTileToOwn(): TileInfo? {
        for (i in 2..5) {
            val tiles = cityInfo.getCenterTile().getTilesInDistance(i)
                    .filter {
                        it.getOwner() == null
                                && it.neighbors.any { tile -> tile.getCity() == cityInfo }
                    }
            val chosenTile = tiles.maxBy { Automation.rankTile(it, cityInfo.civInfo) }
            if (chosenTile != null)
                return chosenTile
        }
        return null
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

        tileInfo.owningCity = null

        cityInfo.civInfo.updateDetailedCivResources()
        cityInfo.cityStats.update()
    }

    fun takeOwnership(tileInfo: TileInfo) {
        if (tileInfo.isCityCenter()) throw Exception("What?!")
        if (tileInfo.getCity() != null)
            tileInfo.getCity()!!.expansion.relinquishOwnership(tileInfo)

        cityInfo.tiles = cityInfo.tiles.withItem(tileInfo.position)
        tileInfo.owningCity = cityInfo
        cityInfo.population.autoAssignPopulation()
        cityInfo.civInfo.updateDetailedCivResources()
        cityInfo.cityStats.update()

        for (unit in tileInfo.getUnits().toList()) // tolisted because we're modifying
            if (!unit.civInfo.canEnterTiles(cityInfo.civInfo))
                unit.movement.teleportToClosestMoveableTile()

        cityInfo.civInfo.updateViewableTiles()
    }

    fun nextTurn(culture: Float) {
        cultureStored += culture.toInt()
        if (cultureStored >= getCultureToNextTile()) {
            val location = addNewTileWithCulture()
            if (location != null)
                cityInfo.civInfo.addNotification("[" + cityInfo.name + "] has expanded its borders!", location, NotificationIcon.Culture)
        }
    }

    fun setTransients() {
        val tiles = cityInfo.getTiles()
        for (tile in tiles)
            tile.owningCity = cityInfo
    }
    //endregion
}
