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
import com.unciv.ui.components.extensions.withItem
import com.unciv.ui.components.extensions.withoutItem
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class CityExpansionManager : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var city: City

    /** Amount of culture this city has accumulated, serialized */
    var cultureStored: Int = 0

    /**
     *  Previously, all costs were based on a count of owned tiles.
     *  But it's clear [(see issue #6394)](https://github.com/yairm210/Unciv/issues/6394) that:
     *  *   There's free Tiles: Shohone and Citadel should not increase price
     *  *   Culture prograssion is not altered by buying for gold
     *  *   Gold progression is not altered by culture expansion
     *
     *  So we need two new persisted fields, and backward compatibility of some sort.
     *      Two out of: Count free tiles / Count bought tiles / Count culture level
     *  A free tile counter, starting at 0 even for old saves, might have advantages for backward compatibility.
     *  I think I have a better feeling going for persisting the other two, like the orginal does.
     *  Compatibility - let a well-progressed old game loaded with the new code show unchanged
     *  'next culture expansion' times and unchanged buy costs, but start to get cheaper from there.
     *  One could try to locate all Citadels and give their owning Cities a refund - decide against.
     *  What happens if a multiplayer game is traded back and forth between pre/post versions?
     *      The party with new code will buy a tile and see culture expansion not delayed.
     *      Next turn, the party with old code will remove the counters
     *      Compat will run again when the party with new code gets it back,
     *      buying price is OK but culture expansion will show a longer time.
     *  OR forbid that using a new version level (`GameInfo.CURRENT_COMPATIBILITY_NUMBER`)
     *  Decision: Acceptable
     *
     *  Question - what happens when a City is conquered? Does it lose tiles?
     *  Question - what happens when a competitor Citadel steals tiles? Is keeping the counters correct as per Civ5?
     *
     *  Actual cost formulae - my analysis (in the linked issue) show the actual Civ5 calculations are quite different.
     *  Decision: Treat separately, no compat code?.
     *
     *  Phase 1:
     *  *   Introduce counters with a default that backward compatibility will recognize and new games will never have
     *  *   Write backward compatibility to initialize both with count of owned tiles
     *  *   Patch getGoldCostOfTile and getCultureToNextTile
     *      (note: No need to remove the default, the backward compatibility code runs from load *and* new-game)
     *  Phase 2:
     *  *   When the dust settles reduce this comment block to the basics
     *  *   Change the default of the fields to 0 to save some json space
     *  *   Remove backward compatibility code (fields can now be private)
     *  Phase 3:
     *  *   Patch the formulae to match Civ5
     */

    /** Culture level as in Civ5: number of tiles acquired from Culture, serialized */
    var cultureLevel: Int = -1

    /** Number of tiles bought with Gold, serialized */
    var tilesBought: Int = -1

    fun clone(): CityExpansionManager {
        val toReturn = CityExpansionManager()
        toReturn.cultureStored = cultureStored
        toReturn.cultureLevel = cultureLevel
        toReturn.tilesBought = tilesBought
        return toReturn
    }

    /** Has this city xpanded from culture (not by buying)?
     *  Used for `TutorialTrigger.CityExpansion`
     *  @return `true` if culture level of the city is > 0 */
    fun hasExpanded() = cultureLevel > 0

    // This one has conflicting sources -
    // http://civilization.wikia.com/wiki/Mathematics_of_Civilization_V says it's 20+(10(t-1))^1.1
    // https://www.reddit.com/r/civ/comments/58rxkk/how_in_gods_name_do_borders_expand_in_civ_vi/ has it
    //   (per game XML files) at 6*(t+0.4813)^1.3
    // The second seems to be more based, so I'll go with that
    // -- Note (added later) that this last link is specific to civ VI and not civ V
    fun getCultureToNextTile(): Int {
        var cultureToNextTile = 6 * (max(0, cultureLevel) + 1.4813).pow(1.3)

        cultureToNextTile *= city.civ.gameInfo.speed.cultureCostModifier

        if (city.civ.isCityState())
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
        tilesBought++
        takeOwnership(tile)

        // Reapply worked tiles optimization (aka CityFocus) - doing it here means AI profits too
        city.reassignPopulationDeferred()
    }

    fun getGoldCostOfTile(tile: Tile): Int {
        val baseCost = 50
        val distanceFromCenter = tile.aerialDistanceTo(city.getCenterTile())
        var cost = baseCost * (distanceFromCenter - 1) + tilesBought * 5.0

        cost *= city.civ.gameInfo.speed.goldCostModifier

        for (unique in city.getMatchingUniques(UniqueType.TileCostPercentage)) {
            if (city.matchesFilter(unique.params[1]))
                cost *= unique.params[0].toPercent()
        }

        return cost.roundToInt()
    }

    fun getChoosableTiles() = city.getCenterTile().getTilesInDistance(5)
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

        tilesBought = 0
        cultureLevel = 0
    }

    private fun addNewTileWithCulture(): Vector2? {
        val chosenTile = chooseNewTileToOwn()
        if (chosenTile != null) {
            cultureLevel++
            cultureStored -= getCultureToNextTile()
            takeOwnership(chosenTile)
            return chosenTile.position
        }
        return null
    }

    /**
     * Removes one tile from this city's owned tiles, unconditionally, and updates dependent
     * things like worked tiles, locked tiles, and stats.
     *
     * Is treated as free tile regarding culture expansion / tile buying.
     *
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
     * Removes one tile from this city's owned tiles, unconditionally, and updates dependent
     * things like worked tiles, locked tiles, and stats.
     *
     * Is treated as a bought tile, the tilesBought counter will be decremented.
     *
     * @throws IllegalStateException if no tiles were bought.
     * @param tile The tile to relinquish
     */
    fun undoBuyTile(tile: Tile) {
        check(tilesBought > 0) { "CityExpansionManager.undoBuyTile called but no tiles were bought" }
        tilesBought--
        relinquishOwnership(tile)
    }

    /**
     * Takes one tile into possession of this city, either unowned or owned by any other city.
     *
     * Also manages consequences like auto population reassign, stats, and displacing units
     * that are no longer allowed on that tile.
     *
     * Counts as free tile regarding culture expansion / tile buying.
     *
     * @param tile The tile to take over
     */
    fun takeOwnership(tile: Tile) {
        if (tile.isCityCenter()) throw Exception("What?!")
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
