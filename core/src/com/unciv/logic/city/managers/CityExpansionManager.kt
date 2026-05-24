package com.unciv.logic.city.managers

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.automation.Automation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toPercent
import com.unciv.utils.withItem
import com.unciv.utils.withoutItem
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class CityExpansionManager : IsPartOfGameInfoSerialization {
    @Transient
    private lateinit var city: City

    @VisibleForTesting
    /** Amount of culture this city has accumulated, serialized */
    var cultureStored: Int = 0

    /** Number of tiles acquired per source, _partially_ serialized - see class doc */
    @Cache
    private val tileCounts = CityExpansionTileCounter()

    @delegate:Transient
    private val foundingUnique by lazy {
        @Suppress("DEPRECATION") // forEachMatchingUnique doesn't allow chaining
        city.civ.getMatchingUniques(UniqueType.OneTimeTakeOverTilesInRadius)
            .firstOrNull { it.hasModifier(UniqueType.TriggerUponFoundingCity) }
    }
    @delegate:Transient
    /** Radius for tiles a city gets assigned when being founded.
     *  * Usually 1, unless the civ has OneTimeTakeOverTilesInRadius with TriggerUponFoundingCity.
     *  * Determines which tiles are considered "base free" and not counting against expansion costs.
     *  * Re-evaluated per turn, since CityExpansionManager gets cloned. Should enable dynamic base size modding.
     */
    internal val foundingRadius by lazy {
        foundingUnique?.let { it.params[1].toInt() } ?: 1
    }
    @delegate:Transient
    /** Filter function for tiles a city gets assigned when being founded.
     *  * Usually returning true, unless the civ has OneTimeTakeOverTilesInRadius with TriggerUponFoundingCity.
     *  * Determines which tiles are considered "base free" and not counting against expansion costs.
     *  * Re-evaluated per turn, since CityExpansionManager gets cloned. Should enable dynamic base size modding.
     */
    internal val foundingTileFilter: (Tile)->Boolean by lazy {
        if (foundingUnique == null || foundingUnique!!.params[0] in Constants.all) fun(tile: Tile) = true
        else fun(tile: Tile) = tile.matchesFilter(foundingUnique!!.params[0], city.civ)
    }

    fun clone(): CityExpansionManager {
        val toReturn = CityExpansionManager()
        toReturn.cultureStored = cultureStored
        toReturn.tileCounts.cloneFrom(tileCounts)
        return toReturn
    }

    @Readonly
    /** Return tiles claimed, per source */
    fun tilesClaimed(source: OwnershipSource = OwnershipSource.Expansion): Int {
        tileCounts.normalize()
        return tileCounts[source]
    }

    // This one has conflicting sources -
    // http://civilization.wikia.com/wiki/Mathematics_of_Civilization_V says it's 20+(10(t-1))^1.1
    // https://www.reddit.com/r/civ/comments/58rxkk/how_in_gods_name_do_borders_expand_in_civ_vi/ has it
    //   (per game XML files) at 6*(t+0.4813)^1.3
    // The second seems to be more based, so I'll go with that
    // -- Note (added later) that this last link is specific to civ VI and not civ V
    @Readonly
    fun getCultureToNextTile(): Int {
        var cultureToNextTile = getLegacyCultureToNextTile() // TODO This is meant to allow using an optional Countables-based moddable formula instead

        cultureToNextTile *= city.civ.gameInfo.speed.cultureCostModifier

        if (city.civ.isCityState)
            cultureToNextTile *= 1.5f   // City states grow slower, perhaps 150% cost?

        for (unique in city.getMatchingUniques(UniqueType.BorderGrowthPercentage))
            if (city.matchesFilter(unique.params[1]))
                cultureToNextTile *= unique.params[0].toPercent()

        return cultureToNextTile.roundToInt()
    }

    @Readonly
    fun getLegacyCultureToNextTile(): Double {
        val tileCount = if (city.civ.gameInfo.useSeparateTileAcquisitionCosts) tilesClaimed(OwnershipSource.Expansion)
        else tilesClaimed(OwnershipSource.All) - tilesClaimed(OwnershipSource.Base)
        return 6 * (max(0, tileCount) + 1.4813).pow(1.3)
    }

    @Readonly
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
        takeOwnership(tile, OwnershipSource.Bought)

        // Reapply worked tiles optimization (aka CityFocus) - doing it here means AI profits too
        city.reassignPopulationDeferred()
    }

    @Readonly
    fun getGoldCostOfTile(tile: Tile): Int {
        var cost = getLegacyGoldCostOfTile(tile) // TODO This is meant to allow using an optional Countables-based moddable formula instead

        cost *= city.civ.gameInfo.speed.goldCostModifier

        for (unique in city.getMatchingUniques(UniqueType.TileCostPercentage)) {
            if (city.matchesFilter(unique.params[1]))
                cost *= unique.params[0].toPercent()
        }

        return cost.roundToInt()
    }

    @Readonly
    private fun getLegacyGoldCostOfTile(tile: Tile): Double {
        val baseCost = 50
        val distanceFromCenter = tile.aerialDistanceTo(city.getCenterTile())
        val tileCount = if (city.civ.gameInfo.useSeparateTileAcquisitionCosts) tilesClaimed(OwnershipSource.Bought)
            else tilesClaimed(OwnershipSource.All) - tilesClaimed(OwnershipSource.Base)
        return baseCost * (distanceFromCenter - 1) + tileCount * 5.0
    }

    @Readonly
    fun getChoosableTiles() = city.getCenterTile().getTilesInDistance(city.getExpandRange())
        .filter { it.getOwner() == null }

    @Readonly
    fun chooseNewTileToOwn(): Tile? {
        // Technically, in the original a random tile with the lowest score was selected
        // However, doing this requires either caching it, which is way more work,
        // or selecting all possible tiles and only choosing one when the border expands.
        // But since the order in which tiles are selected in distance is kinda random anyways,
        // this is fine.
        return getChoosableTiles().minByOrNull {
            Automation.rankTileForExpansion(it, city)
        }
    }

    //region state-changing functions
    /** Relinquishes all tiles and resets tile acquisition source counts.
     *  @see reset */
    fun clear() {
        for (tile in city.getTiles())
            relinquishOwnership(tile)
        tileCounts.reset()
    }

    /** Relinquishes all tiles and resets tile acquisition source counts, and re-acquires the base initial tiles
     *  @see clear */
    fun reset() {
        clear()

        // The only way to create a city inside an owned tile is if it's in your territory
        // In this case, if you don't assign control of the central tile to the city,
        // It becomes an invisible city and weird shit starts happening
        takeOwnership(city.getCenterTile(), OwnershipSource.Base)

        val tilesToTake = city.getCenterTile().getTilesInDistance(foundingRadius)
            .filter { it.getCity() == null && foundingTileFilter(it) } // can't take ownership of owned tiles (by other cities)
        for (tile in tilesToTake)
            takeOwnership(tile, OwnershipSource.Base)
    }

    private fun addNewTileWithCulture(): HexCoord? {
        val chosenTile = chooseNewTileToOwn()
        if (chosenTile != null) {
            cultureStored -= getCultureToNextTile()
            takeOwnership(chosenTile, OwnershipSource.Expansion)
            return chosenTile.position
        }
        return null
    }

    /**
     * Removes one tile from this city's owned tiles, unconditionally, and updates dependent
     * things like worked tiles, locked tiles, and stats.
     * @param tile The tile to relinquish
     */
    fun relinquishOwnership(tile: Tile, source: OwnershipSource? = null) {
        if (tile.owningCity != city) return // UseGoldAutomation will relinquish tiles that is hasn't actually bought
        tileCounts.normalize()
        city.tiles = city.tiles.withoutItem(tile.position)
        tileCounts.relinquishOne(source)

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
     * @param source How the tile was acquired, for accounting
     */
    fun takeOwnership(tile: Tile, source: OwnershipSource) {
        check(!tile.isCityCenter()) { "Trying to found a city in a tile that already has one" }
        if (tile.getCity() != null)
            tile.getCity()!!.expansion.relinquishOwnership(tile)

        if (tile.improvement == Constants.barbarianEncampment)
            tile.setImprovementBasic(null)

        tileCounts.normalize()
        city.tiles = city.tiles.withItem(tile.position)
        tile.setOwningCity(city)
        tileCounts[source]++
        city.population.autoAssignPopulation()
        city.civ.cache.updateOurTiles()
        city.cityStats.update()

        for (unit in tile.getUnits().toList()) // toListed because we're modifying
            if (!unit.civ.diplomacyFunctions.canPassThroughTiles(city.civ))
                unit.movement.teleportToClosestMoveableTile()
            else if (unit.civ == city.civ && unit.isSleeping()) {
                // If the unit is sleeping and is a worker, it might want to build on this tile
                // So let's try to wake it up for the player to notice it
                if (unit.cache.hasUniqueToBuildImprovements || unit.cache.hasUniqueToCreateWaterImprovements) {
                    unit.due = true
                    unit.action = null
                }
            }

        tile.history.recordTakeOwnership(tile)
    }

    @VisibleForTesting
    /** Only unit tests are allowed to call [takeOwnership] without a source, and they will count as free */
    fun takeOwnership(tile: Tile) = takeOwnership(tile, OwnershipSource.Free)

    fun nextTurn(culture: Float) {
        cultureStored += culture.toInt()
        if (cultureStored >= getCultureToNextTile()) {
            val location = addNewTileWithCulture()
            if (location != null) {
                val locations = LocationAction(location, city.location.toHexCoord())
                city.civ.addNotification("[${city.name}] has expanded its borders!", locations,
                    NotificationCategory.Cities, NotificationIcon.Culture)
            }
        }
    }

    fun setTransients(city: City) {
        this.city = city
        tileCounts.setTransients(city)
        val tiles = city.getTiles()
        for (tile in tiles)
            tile.setOwningCity(city)
    }
    //endregion
}
