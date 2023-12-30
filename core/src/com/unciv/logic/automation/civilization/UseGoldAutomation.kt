package com.unciv.logic.automation.civilization

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.BFS
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import java.util.SortedMap
import java.util.TreeMap

object UseGoldAutomation {


    /** allow AI to spend money to purchase city-state friendship, buildings & unit */
    fun useGold(civ: Civilization) {
        if (civ.isMajorCiv())
            useGoldForCityStates(civ)

        for (city in civ.cities.sortedByDescending { it.population.population }) {
            val construction = city.cityConstructions.getCurrentConstruction()
            if (construction is PerpetualConstruction) continue
            if ((construction as INonPerpetualConstruction).canBePurchasedWithStat(city, Stat.Gold)
                && city.civ.gold / 3 >= construction.getStatBuyCost(city, Stat.Gold)!!) {
                city.cityConstructions.purchaseConstruction(construction, 0, true)
            }
        }

        maybeBuyCityTiles(civ)
    }


    private fun useGoldForCityStates(civ: Civilization) {
        // RARE EDGE CASE: If you ally with a city-state, you may reveal more map that includes ANOTHER civ!
        // So if we don't lock this list, we may later discover that there are more known civs, concurrent modification exception!
        val knownCityStates = civ.getKnownCivs().filter { it.isCityState() }.toList()

        // canBeMarriedBy checks actual cost, but it can't be below 500*speedmodifier, and the later check is expensive
        if (civ.gold >= 330 && civ.getHappiness() > 0 && civ.hasUnique(UniqueType.CityStateCanBeBoughtForGold)) {
            for (cityState in knownCityStates.toList() ) {  // Materialize sequence as diplomaticMarriage may kill a CS
                if (cityState.cityStateFunctions.canBeMarriedBy(civ))
                    cityState.cityStateFunctions.diplomaticMarriage(civ)
                if (civ.getHappiness() <= 0) break // Stop marrying if happiness is getting too low
            }
        }

        if (civ.gold < 250) return  // skip checks if tryGainInfluence will bail anyway
        if (civ.wantsToFocusOn(Victory.Focus.Culture)) {
            for (cityState in knownCityStates.filter { it.cityStateFunctions.canProvideStat(Stat.Culture) }) {
                val diploManager = cityState.getDiplomacyManager(civ)
                if (diploManager.getInfluence() < 40) { // we want to gain influence with them
                    tryGainInfluence(civ, cityState)
                }
            }
        }

        if (civ.gold < 250 || knownCityStates.none()) return
        val cityState = knownCityStates
            .filter { it.getAllyCiv() != civ.civName }
            .associateWith { NextTurnAutomation.valueCityStateAlliance(civ, it, true) }
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

        val highlyDesirableTiles: SortedMap<Tile, MutableSet<City>> = getHighlyDesirableTilesToCityMap(civInfo)

        // Always try to buy highly desirable tiles if it can be afforded.
        for (highlyDesirableTile in highlyDesirableTiles) {
            val cityWithLeastCostToBuy = highlyDesirableTile.value.minBy {
                it.getCenterTile().aerialDistanceTo(highlyDesirableTile.key)
            }
            val bfs = BFS(cityWithLeastCostToBuy.getCenterTile())
            {
                it.getOwner() == null || it.owningCity == cityWithLeastCostToBuy
            }
            bfs.stepUntilDestination(highlyDesirableTile.key)
            val tilesThatNeedBuying =
                bfs.getPathTo(highlyDesirableTile.key).filter { it.getOwner() == null }
                    .toList().reversed() // getPathTo is from destination to source

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

    private fun getHighlyDesirableTilesToCityMap(civInfo: Civilization): SortedMap<Tile, MutableSet<City>> {
        val highlyDesirableTiles: SortedMap<Tile, MutableSet<City>> = TreeMap(
            compareByDescending<Tile?> { it?.naturalWonder != null }
                .thenByDescending { it?.resource != null && it.tileResource.resourceType == ResourceType.Luxury }
                .thenByDescending { it?.resource != null && it.tileResource.resourceType == ResourceType.Strategic }
                // This is necessary, so that the map keeps Tiles with the same resource as two
                // separate entries.
                .thenBy { it.hashCode() }
        )

        for (city in civInfo.cities.filter { !it.isPuppet && !it.isBeingRazed }) {
            val highlyDesirableTilesInCity = city.tilesInRange.filter {
                isHighlyDesirableTile(it, civInfo, city)
            }
            for (highlyDesirableTileInCity in highlyDesirableTilesInCity) {
                highlyDesirableTiles.getOrPut(highlyDesirableTileInCity) { mutableSetOf() }
                    .add(city)
            }
        }
        return highlyDesirableTiles
    }

    private fun isHighlyDesirableTile(it: Tile, civInfo: Civilization, city: City): Boolean {
        if (!it.isVisible(civInfo)) return false
        if (it.getOwner() != null) return false
        if (it.neighbors.none { neighbor -> neighbor.getCity() == city }) return false

        fun hasNaturalWonder() = it.naturalWonder != null

        fun hasLuxuryCivDoesntOwn() =
            it.hasViewableResource(civInfo)
                && it.tileResource.resourceType == ResourceType.Luxury
                && !civInfo.hasResource(it.resource!!)

        fun hasResourceCivHasNoneOrLittle() =
            it.hasViewableResource(civInfo)
                && it.tileResource.resourceType == ResourceType.Strategic
                && civInfo.getResourceAmount(it.resource!!) <= 3

        return (hasNaturalWonder() || hasLuxuryCivDoesntOwn() || hasResourceCivHasNoneOrLittle())
    }

    private fun tryGainInfluence(civInfo: Civilization, cityState: Civilization) {
        if (civInfo.gold < 250) return // Save up
        if (cityState.getDiplomacyManager(civInfo).getInfluence() >= 20
            && civInfo.gold < 500) {
            // Only make a small investment if we have a bit of influence already to build off of so we don't waste our money
            cityState.cityStateFunctions.receiveGoldGift(civInfo, 250)
            return
        }
        if (civInfo.gold < 500) return // It's not worth it to invest now, wait until you have enough for 2
        cityState.cityStateFunctions.receiveGoldGift(civInfo, 500)
        return
    }

}
