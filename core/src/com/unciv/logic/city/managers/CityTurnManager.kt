package com.unciv.logic.city.managers

import com.unciv.logic.city.City
import com.unciv.logic.city.CityFlags
import com.unciv.logic.city.CityFocus
import com.unciv.logic.civilization.CityAction
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.OverviewAction
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import kotlin.math.min
import kotlin.random.Random

class CityTurnManager(val city: City) {


    fun startTurn() {
        // Construct units at the beginning of the turn,
        // so they won't be generated out in the open and vulnerable to enemy attacks before you can control them
        city.cityConstructions.constructIfEnough()

        city.tryUpdateRoadStatus()
        city.attackedThisTurn = false

        // The ordering is intentional - you get a turn without WLTKD even if you have the next resource already
        // Also resolve end of resistance before updateCitizens
        if (!city.hasFlag(CityFlags.WeLoveTheKing))
            tryWeLoveTheKing()
        nextTurnFlags()

        if (city.isPuppet) {
            city.setCityFocus(CityFocus.GoldFocus)
            city.reassignAllPopulation()
        } else if (city.shouldReassignPopulation || city.civ.isAI()) {
            city.reassignPopulation()  // includes cityStats.update
        } else
            city.cityStats.update()

        // Seed resource demand countdown
        if (city.demandedResource == "" && !city.hasFlag(CityFlags.ResourceDemand)) {
            city.setFlag(
                CityFlags.ResourceDemand,
                (if (city.isCapital()) 25 else 15) + Random.Default.nextInt(10))
        }
    }

    private fun tryWeLoveTheKing() {
        if (city.demandedResource == "") return
        if (city.getAvailableResourceAmount(city.demandedResource) > 0) {
            city.setFlag(CityFlags.WeLoveTheKing, 20 + 1) // +1 because it will be decremented by 1 in the same startTurn()
            city.civ.addNotification(
                "Because they have [${city.demandedResource}], the citizens of [${city.name}] are celebrating We Love The King Day!",
                CityAction.withLocation(city), NotificationCategory.General, NotificationIcon.City, NotificationIcon.Happiness)
        }
    }

    // cf DiplomacyManager nextTurnFlags
    private fun nextTurnFlags() {
        for (flag in city.flagsCountdown.keys.toList()) {
            if (city.flagsCountdown[flag]!! > 0)
                city.flagsCountdown[flag] = city.flagsCountdown[flag]!! - 1

            if (city.flagsCountdown[flag] == 0) {
                city.flagsCountdown.remove(flag)

                when (flag) {
                    CityFlags.ResourceDemand.name -> {
                        demandNewResource()
                    }
                    CityFlags.WeLoveTheKing.name -> {
                        city.civ.addNotification(
                            "We Love The King Day in [${city.name}] has ended.",
                            CityAction.withLocation(city), NotificationCategory.General, NotificationIcon.City)
                        demandNewResource()
                    }
                    CityFlags.Resistance.name -> {
                        city.shouldReassignPopulation = true
                        city.civ.addNotification(
                            "The resistance in [${city.name}] has ended!",
                            CityAction.withLocation(city), NotificationCategory.General, "StatIcons/Resistance")
                    }
                }
            }
        }
    }


    private fun demandNewResource() {
        val candidates = city.getRuleset().tileResources.values.filter {
            it.resourceType == ResourceType.Luxury && // Must be luxury
                    !it.hasUnique(UniqueType.CityStateOnlyResource) && // Not a city-state only resource eg jewelry
                    it.name != city.demandedResource && // Not same as last time
                    it.name in city.tileMap.resources && // Must exist somewhere on the map
                    city.getCenterTile().getTilesInDistance(city.getWorkRange()).none { nearTile -> nearTile.resource == it.name } // Not in this city's radius
        }
        val missingResources = candidates.filter { !city.civ.hasResource(it.name) }
        
        if (missingResources.isEmpty()) { // hooray happpy day forever!
            city.demandedResource = candidates.randomOrNull()?.name ?: ""
            return // actually triggering "wtlk" is done in tryWeLoveTheKing(), *next turn*
        }

        val chosenResource = missingResources.randomOrNull()
        
        city.demandedResource = chosenResource?.name ?: "" // mods may have no resources as candidates even
        if (city.demandedResource == "") // Failed to get a valid resource, try again some time later
            city.setFlag(CityFlags.ResourceDemand, 15 + Random.Default.nextInt(10))
        else
            city.civ.addNotification("[${city.name}] demands [${city.demandedResource}]!",
                listOf(LocationAction(city.location), OverviewAction(EmpireOverviewCategories.Resources)),
                NotificationCategory.General, NotificationIcon.City, "ResourceIcons/${city.demandedResource}")
    }


    fun endTurn() {
        val stats = city.cityStats.currentCityStats

        city.cityConstructions.endTurn(stats)
        city.expansion.nextTurn(stats.culture)
        if (city.isBeingRazed) {
            val removedPopulation =
                    1 + city.civ.getMatchingUniques(UniqueType.CitiesAreRazedXTimesFaster)
                        .sumOf { it.params[0].toInt() - 1 }

            if (city.population.population <= removedPopulation) {
                city.espionage.removeAllPresentSpies(SpyFleeReason.Other)
                city.civ.addNotification(
                    "[${city.name}] has been razed to the ground!",
                    city.location, NotificationCategory.General,
                    "OtherIcons/Fire"
                )
                city.destroyCity()
            } else { //if not razed yet:
                city.population.addPopulation(-removedPopulation)
                if (city.population.foodStored >= city.population.getFoodToNextPopulation()) { //if surplus in the granary...
                    city.population.foodStored =
                            city.population.getFoodToNextPopulation() - 1 //...reduce below the new growth threshold
                }
            }
        } else city.population.nextTurn(city.foodForNextTurn())

        // This should go after the population change, as that might impact the amount of followers in this city
        if (city.civ.gameInfo.isReligionEnabled()) city.religion.endTurn()

        if (city in city.civ.cities) { // city was not destroyed
            city.health = min(city.health + 20, city.getMaxHealth())
            city.population.unassignExtraPopulation()
        }
    }

}
