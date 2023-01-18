package com.unciv.logic.city.managers

import com.unciv.logic.city.CityFlags
import com.unciv.logic.city.CityFocus
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType
import java.util.*
import kotlin.math.min

class CityTurnManager(val cityInfo: CityInfo) {


    fun startTurn() {
        // Construct units at the beginning of the turn,
        // so they won't be generated out in the open and vulnerable to enemy attacks before you can control them
        cityInfo.cityConstructions.constructIfEnough()
        cityInfo.cityConstructions.addFreeBuildings()

        cityInfo.cityStats.update()
        cityInfo.tryUpdateRoadStatus()
        cityInfo.attackedThisTurn = false

        if (cityInfo.isPuppet) {
            cityInfo.cityAIFocus = CityFocus.GoldFocus
            cityInfo.reassignAllPopulation()
        } else if (cityInfo.updateCitizens) {
            cityInfo.reassignPopulation()
            cityInfo.updateCitizens = false
        }

        // The ordering is intentional - you get a turn without WLTKD even if you have the next resource already
        if (!cityInfo.hasFlag(CityFlags.WeLoveTheKing))
            tryWeLoveTheKing()
        nextTurnFlags()

        // Seed resource demand countdown
        if (cityInfo.demandedResource == "" && !cityInfo.hasFlag(CityFlags.ResourceDemand)) {
            cityInfo.setFlag(
                CityFlags.ResourceDemand,
                (if (cityInfo.isCapital()) 25 else 15) + Random().nextInt(10))
        }
    }

    private fun tryWeLoveTheKing() {
        if (cityInfo.demandedResource == "") return
        if (cityInfo.civInfo.getCivResourcesByName()[cityInfo.demandedResource]!! > 0) {
            cityInfo.setFlag(CityFlags.WeLoveTheKing, 20 + 1) // +1 because it will be decremented by 1 in the same startTurn()
            cityInfo.civInfo.addNotification(
                "Because they have [${cityInfo.demandedResource}], the citizens of [${cityInfo.name}] are celebrating We Love The King Day!",
                cityInfo.location, NotificationCategory.General, NotificationIcon.City, NotificationIcon.Happiness)
        }
    }

    // cf DiplomacyManager nextTurnFlags
    private fun nextTurnFlags() {
        for (flag in cityInfo.flagsCountdown.keys.toList()) {
            if (cityInfo.flagsCountdown[flag]!! > 0)
                cityInfo.flagsCountdown[flag] = cityInfo.flagsCountdown[flag]!! - 1

            if (cityInfo.flagsCountdown[flag] == 0) {
                cityInfo.flagsCountdown.remove(flag)

                when (flag) {
                    CityFlags.ResourceDemand.name -> {
                        demandNewResource()
                    }
                    CityFlags.WeLoveTheKing.name -> {
                        cityInfo.civInfo.addNotification(
                            "We Love The King Day in [${cityInfo.name}] has ended.",
                            cityInfo.location, NotificationCategory.General, NotificationIcon.City)
                        demandNewResource()
                    }
                    CityFlags.Resistance.name -> {
                        cityInfo.civInfo.addNotification(
                            "The resistance in [${cityInfo.name}] has ended!",
                            cityInfo.location, NotificationCategory.General, "StatIcons/Resistance")
                    }
                }
            }
        }
    }


    private fun demandNewResource() {
        val candidates = cityInfo.getRuleset().tileResources.values.filter {
            it.resourceType == ResourceType.Luxury && // Must be luxury
                    !it.hasUnique(UniqueType.CityStateOnlyResource) && // Not a city-state only resource eg jewelry
                    it.name != cityInfo.demandedResource && // Not same as last time
                    !cityInfo.civInfo.hasResource(it.name) && // Not one we already have
                    it.name in cityInfo.tileMap.resources && // Must exist somewhere on the map
                    cityInfo.getCenterTile().getTilesInDistance(3).none { nearTile -> nearTile.resource == it.name } // Not in this city's radius
        }

        val chosenResource = candidates.randomOrNull()
        /* What if we had a WLTKD before but now the player has every resource in the game? We can't
           pick a new resource, so the resource will stay stay the same and the city will demand it
           again even if the player still has it. But we shouldn't punish success. */
        if (chosenResource != null)
            cityInfo.demandedResource = chosenResource.name
        if (cityInfo.demandedResource == "") // Failed to get a valid resource, try again some time later
            cityInfo.setFlag(CityFlags.ResourceDemand, 15 + Random().nextInt(10))
        else
            cityInfo.civInfo.addNotification("[${cityInfo.name}] demands [${cityInfo.demandedResource}]!",
                cityInfo.location, NotificationCategory.General, NotificationIcon.City, "ResourceIcons/${cityInfo.demandedResource}")
    }


    fun endTurn() {
        val stats = cityInfo.cityStats.currentCityStats

        cityInfo.cityConstructions.endTurn(stats)
        cityInfo.expansion.nextTurn(stats.culture)
        if (cityInfo.isBeingRazed) {
            val removedPopulation =
                    1 + cityInfo.civInfo.getMatchingUniques(UniqueType.CitiesAreRazedXTimesFaster)
                        .sumOf { it.params[0].toInt() - 1 }
            cityInfo.population.addPopulation(-1 * removedPopulation)

            if (cityInfo.population.population <= 0) {
                cityInfo.civInfo.addNotification(
                    "[${cityInfo.name}] has been razed to the ground!",
                    cityInfo.location, NotificationCategory.General,
                    "OtherIcons/Fire"
                )
                cityInfo.destroyCity()
            } else { //if not razed yet:
                if (cityInfo.population.foodStored >= cityInfo.population.getFoodToNextPopulation()) { //if surplus in the granary...
                    cityInfo.population.foodStored =
                            cityInfo.population.getFoodToNextPopulation() - 1 //...reduce below the new growth threshold
                }
            }
        } else cityInfo.population.nextTurn(cityInfo.foodForNextTurn())

        // This should go after the population change, as that might impact the amount of followers in this city
        if (cityInfo.civInfo.gameInfo.isReligionEnabled()) cityInfo.religion.endTurn()

        if (cityInfo in cityInfo.civInfo.cities) { // city was not destroyed
            cityInfo.health = min(cityInfo.health + 20, cityInfo.getMaxHealth())
            cityInfo.population.unassignExtraPopulation()
        }
    }


}
