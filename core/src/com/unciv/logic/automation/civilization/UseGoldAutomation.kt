package com.unciv.logic.automation.civilization

import com.unciv.view.GameView
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.BFS
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
import java.util.*

object UseGoldAutomation {


    /** allow AI to spend money to purchase city-state friendship, buildings & unit */
    fun useGold(civ: Civilization) {
        for (unit in civ.units.getCivUnits())
            UnitAutomation.tryUpgradeUnit(GameView(unit.civ.gameInfo, unit.civ).getMapUnitView(unit))

        if (civ.isMajorCiv)
            useGoldForCityStates(civ)

        val acceptableHurryCostModifier = civ.gold.coerceAtLeast(0)/100
        // Ideally we buy things with good gold:production conversion from purchasing and hard-build the rest, but some mods have high hurryCostModifier on everything leading to too much gold being saved. 
        // Rather than checking the full ruleset for future available constructions and determining if they're worth it, let's gradually accept less efficiency if it turns out we're saving too much

        for (city in civ.cities.sortedByDescending { it.cityStats.currentCityStats.production }) {
            // Low production cities are disallowed from building military and world wonders, and have low priority for xp buildings
            val construction = city.cityConstructions.getCurrentConstruction()
            if (construction !is INonPerpetualConstruction) continue
            val statBuyCost = construction.getStatBuyCost(city, Stat.Gold) ?: continue
            if (!city.cityConstructions.isConstructionPurchaseAllowed(construction, Stat.Gold, statBuyCost)) continue
            if (civ.gold < statBuyCost || construction.hurryCostModifier > acceptableHurryCostModifier) continue
            if (city.cityConstructions.getWorkDone(construction.name) > 0) continue // Don't buy things we already invested production into (may waste the invested production)
            city.cityConstructions.purchaseConstruction(construction, 0, true)
        }

        maybeBuyCityTiles(civ)
    }

    private fun useGoldForCityStates(civ: Civilization) {
        // RARE EDGE CASE: If you ally with a city-state, you may reveal more map that includes ANOTHER civ!
        // So if we don't lock this list, we may later discover that there are more known civs, concurrent modification exception!
        val knownCityStates = civ.getKnownCivs().filter { it.isCityState && MotivationToAttackAutomation.hasAtLeastMotivationToAttack(civ, GameView(civ.gameInfo, civ).getForeignCivView(it), 0f) <= 0 }.toList()

        // canBeMarriedBy checks actual cost, but it can't be below 500*speedmodifier, and the later check is expensive
        if (civ.gold >= 330 && civ.getHappiness() > 0 && civ.hasUnique(UniqueType.CityStateCanBeBoughtForGold)) {
            for (cityState in knownCityStates.toList() ) {  // Materialize sequence as diplomaticMarriage may kill a CS
                if (cityState.cityStateFunctions.canBeMarriedBy(civ))
                    cityState.cityStateFunctions.diplomaticMarriage(civ)
                if (civ.getHappiness() <= 0) break // Stop marrying if happiness is getting too low
            }
        }

        if (civ.gold < 500 || knownCityStates.none()) return // skip checks if tryGainInfluence will bail anyway
        val cityState = knownCityStates
            .filter { it.allyCiv != civ }
            .associateWith { NextTurnAutomation.valueCityStateAlliance(GameView(civ.gameInfo, civ).civView, it, true) }
            .maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
        if (cityState != null) {
            tryGainInfluence(civ, cityState)
        }
    }

    private fun maybeBuyCityTiles(civInfo: Civilization) {
        if (civInfo.gold <= 0)
            return
        // Don't buy tiles in the very early game. It is unlikely that we already have the required
        // tech, the necessary worker and that there is a reasonable threat from another player to
        // grab the tile. We could also check all that, but it would require a lot of cycles each
        // turn and this is probably a good approximation.
        if (civInfo.gameInfo.turns < (civInfo.gameInfo.speed.scienceCostModifier * 20).toInt())
            return

        val highlyDesirableTiles = getHighlyDesirableTilesToCityMap(civInfo)

        // Always try to buy highly desirable tiles if it can be afforded.
        for ((key, value) in highlyDesirableTiles) {
            val cityWithLeastCostToBuy = value.minBy {
                it.getCenterTile().aerialDistanceTo(key)
            }
            val bfs = BFS(cityWithLeastCostToBuy.getCenterTile())
            {
                it.getOwner() == null || it.owningCity == cityWithLeastCostToBuy
            }
            bfs.stepUntilDestination(key)
            val tilesThatNeedBuying =
                bfs.getPathTo(key).filter { it.getOwner() == null }
                    .toList().asReversed() // getPathTo is from destination to source

            // We're trying to acquire everything and revert if it fails, because of the difficult
            // way how tile acquisition cost is calculated. Everytime you buy a tile, the next one
            // gets more expensive and by how much depends on other things such as game speed. To
            // not introduce hidden dependencies on that and duplicate that logic here to calculate
            // the price of the whole path, this is probably simpler.
            var ranOutOfMoney = false
            var goldSpent = 0
            for (tileThatNeedsBuying in tilesThatNeedBuying) {
                val goldCostOfTile =
                    cityWithLeastCostToBuy.expansion.getGoldCostOfTile(tileThatNeedsBuying)
                if (civInfo.gold >= goldCostOfTile) {
                    cityWithLeastCostToBuy.expansion.buyTile(tileThatNeedsBuying)
                    goldSpent += goldCostOfTile
                } else {
                    ranOutOfMoney = true
                    break
                }
            }
            if (ranOutOfMoney) {
                for (tileThatNeedsBuying in tilesThatNeedBuying) {
                    cityWithLeastCostToBuy.expansion.relinquishOwnership(tileThatNeedsBuying)
                }
                civInfo.addGold(goldSpent)
            }
        }
    }

    @Readonly
    private fun getHighlyDesirableTilesToCityMap(civInfo: Civilization): List<Map.Entry<Tile, Set<City>>> {
        val valueCache = hashMapOf<Tile, Int>()
        /** Lower is better. Passing no city means relying on a previously cached value.
         *  Evaluating this once even though several cities might be able to expand to a tile is a simplification.
         *  The negative effect of this should average out over time.
         */
        fun Tile.getTileRank(city: City?) = if (city == null) 2000
            else valueCache.getOrPut(this) {
                city.expansion.rankTileForExpansion(this)
            }

        val comparator = compareByDescending<Tile> { it.naturalWonder != null }
            .thenByDescending { it.tileResource?.resourceType == ResourceType.Luxury }
            .thenByDescending { it.tileResource?.resourceType == ResourceType.Strategic }
            .thenBy { it.getTileRank(null) }

        fun isHighlyDesirableTile(tile: Tile, civInfo: Civilization, city: City, eagerToBuy: Boolean): Boolean {
            if (!tile.isVisible(civInfo)) return false
            if (tile.getOwner() != null) return false
            if (tile.neighbors.none { neighbor -> neighbor.getCity() == city }) return false

            if (tile.naturalWonder != null) return true

            fun hasImportantResource(): Boolean {
                val resource = tile.tileResource
                return civInfo.canSeeResource(resource) &&
                    (resource.resourceType == ResourceType.Luxury || resource.resourceType == ResourceType.Strategic) &&
                    (eagerToBuy || civInfo.getResourceAmount(resource) < 2) // At 2 or more, we haven't been able to trade it away for another duplicate...
            }
            fun hasHighYields(): Boolean {
                val rank = tile.getTileRank(city) // always cache this
                if (eagerToBuy) return rank < 300
                val tileStats = tile.stats.getTileStats(civInfo)
                return tileStats.food + tileStats.production >= 3f
            }
            return hasImportantResource() || hasHighYields()
        }

        fun scanTiles(radius: Int, eagerToBuy: Boolean): List<Map.Entry<Tile, Set<City>>> {
            val tileToCities = HashMap<Tile, MutableSet<City>>()
            for (city in civInfo.cities) {
                if (city.isPuppet || city.isBeingRazed) continue
                city.getCenterTile().forEachTileInDistance(radius) {
                    if (isHighlyDesirableTile(it, civInfo, city, eagerToBuy)) {
                        @LocalState val desirableForCities = tileToCities.getOrPut(it) { mutableSetOf() }
                        desirableForCities.add(city)
                    }
                }
            }
            return tileToCities.entries.sortedWith(compareBy(comparator) { it.key })
        }

        // Only consider second ring tiles: further tiles may be as good or better, but have much higher gold cost
        // Ring 1 included for the rare case such a tile was lost after founding
        val highlyDesirableTiles = scanTiles(2, false)
        // TODO this needs a better heuristic
        val fuzzyFactor = (50 * civInfo.gameInfo.speed.goldCostModifier).toInt() + getAverageTileBuyCost(civInfo)
        val reserveGold = getPotentialProductionBuyCost(civInfo) + fuzzyFactor
        val eagerToBuyCount = (civInfo.gold - reserveGold) / fuzzyFactor
        if (eagerToBuyCount <= 0 || highlyDesirableTiles.isNotEmpty())
            return highlyDesirableTiles

        // We have a large gold surplus - so try to buy more tiles
        val somewhatDesirableTiles = scanTiles(3, true)
        if (somewhatDesirableTiles.size <= eagerToBuyCount)
            return somewhatDesirableTiles
        return somewhatDesirableTiles.take(eagerToBuyCount)
    }

    @Readonly
    private fun getPotentialProductionBuyCost(civ: Civilization): Int {
        var result = 0
        for (city in civ.cities) {
            if (city.isPuppet || city.isBeingRazed) continue
            val construction = city.cityConstructions.getCurrentConstruction()
            if (construction !is INonPerpetualConstruction) continue
            val statBuyCost = construction.getStatBuyCost(city, Stat.Gold) ?: continue
            result += statBuyCost
        }
        return result
    }

    @Readonly
    private fun getAverageTileBuyCost(civ: Civilization): Int {
        var result = 0
        var count = 0
        for (city in civ.cities) {
            if (city.isPuppet || city.isBeingRazed) continue
            val tile = city.expansion.chooseNewTileToOwn() ?: continue
            result += city.expansion.getGoldCostOfTile(tile)
            count++
        }
        return if (count == 0) 1 else result / count
    }

    private fun tryGainInfluence(civInfo: Civilization, cityState: Civilization) {
        if (civInfo.gold < 500) return // Save up, giving 500 gold in one go typically grants +5 influence compared to giving 2×250 gold
        val influence = cityState.getDiplomacyManager(civInfo)!!.getInfluence()
        val stopSpending =  influence > 60 + 2 * NextTurnAutomation.valueCityStateAlliance(GameView(civInfo.gameInfo, civInfo).civView, cityState, true)
        // Don't go into a gold gift race: be content with friendship for cheap, or use the gold on more productive uses,
        // for example upgrading an army to conquer the player who's contesting our city states
        if (influence < 10 || stopSpending) return
        // Only make an investment if we got our Pledge to Protect influence at the highest level
        if (civInfo.gold >= 1000) cityState.cityStateFunctions.receiveGoldGift(civInfo, 1000)
        else cityState.cityStateFunctions.receiveGoldGift(civInfo, 500)
        return
    }

}
