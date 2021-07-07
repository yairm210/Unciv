package com.unciv.logic.city

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.automation.Automation
import com.unciv.logic.civilization.LocationAction
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
        
        for (unique in cityInfo.getMatchingUniques("[]% cost of natural border growth")) 
            cultureToNextTile *= 1 + unique.params[0].toFloat() / 100f
        
        // Unique deprecated since 3.15.10 (seems unused, and should be replaced by the unique above)
            if (cityInfo.civInfo.hasUnique("Increased rate of border expansion")) cultureToNextTile *= 0.75
        //

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

        for (unique in cityInfo.civInfo.getMatchingUniques("-[]% Gold cost of acquiring tiles []")) {
            if (cityInfo.matchesFilter(unique.params[1]))
                cost *= (100 - unique.params[0].toFloat()) / 100
        }
        return cost.roundToInt()
    }


    fun chooseNewTileToOwn(): TileInfo? {
        for (i in 2..5) {
            val tiles = cityInfo.getCenterTile().getTilesInDistance(i)
                    .filter {
                        it.getOwner() == null
                                && it.neighbors.any { tile -> tile.getCity() == cityInfo }
                    }
            val chosenTile = tiles.maxByOrNull { Automation.rankTile(it, cityInfo.civInfo) }
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

        tileInfo.owningCity = null

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
        tileInfo.owningCity = cityInfo
        cityInfo.population.autoAssignPopulation()
        cityInfo.civInfo.updateDetailedCivResources()
        cityInfo.cityStats.update()

        for (unit in tileInfo.getUnits().toList()) // toListed because we're modifying
            if (!unit.civInfo.canEnterTiles(cityInfo.civInfo))
                unit.movement.teleportToClosestMoveableTile()

        cityInfo.civInfo.updateViewableTiles()
    }

    fun nextTurn(culture: Float) {
        cultureStored += culture.toInt()
        if (cultureStored >= getCultureToNextTile()) {
            val location = addNewTileWithCulture()
            if (location != null) {
                val locations = LocationAction(listOf(location, cityInfo.location))
                cityInfo.civInfo.addNotification("[" + cityInfo.name + "] has expanded its borders!", locations, NotificationIcon.Culture)
            }
        }
    }

    fun setTransients() {
        val tiles = cityInfo.getTiles()
        for (tile in tiles)
            tile.owningCity = cityInfo
    }
    //endregion
}
