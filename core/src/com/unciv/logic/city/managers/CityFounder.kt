package com.unciv.logic.city.managers

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.Proximity
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType

class CityFounder {
    fun foundCity(civInfo: Civilization, cityLocation: Vector2, unit: MapUnit? = null): City {
        val city = City()

        city.foundingCiv = civInfo.civName
        city.turnAcquired = civInfo.gameInfo.turns
        city.location = cityLocation
        city.setTransients(civInfo)

        city.name = generateNewCityName(
            civInfo,
            civInfo.gameInfo.civilizations.asSequence().filter { civ -> civ.isAlive() }.toSet()
        ) ?: NamingConstants.fallback

        city.isOriginalCapital = civInfo.citiesCreated == 0
        if (city.isOriginalCapital) {
            civInfo.hasEverOwnedOriginalCapital = true
            // if you have some culture before the 1st city is founded, you may want to adopt the 1st policy
            civInfo.policies.shouldOpenPolicyPicker = true
        }
        civInfo.citiesCreated++

        civInfo.cities = civInfo.cities.toMutableList().apply { add(city) }

        val startingEra = civInfo.gameInfo.gameParameters.startingEra

        city.expansion.reset()

        city.tryUpdateRoadStatus()

        val tile = city.getCenterTile()
        for (terrainFeature in tile.terrainFeatures.filter {
            city.getRuleset().tileImprovements.containsKey(
                "Remove $it"
            )
        })
            tile.removeTerrainFeature(terrainFeature)

        if (civInfo.gameInfo.ruleset.tileImprovements.containsKey(Constants.cityCenter))
            tile.setImprovement(Constants.cityCenter, civInfo)
        tile.stopWorkingOnImprovement()

        val ruleset = civInfo.gameInfo.ruleset
        city.workedTiles = hashSetOf() //reassign 1st working tile

        city.population.setPopulation(ruleset.eras[startingEra]!!.settlerPopulation)

        if (civInfo.religionManager.religionState == ReligionState.Pantheon) {
            city.religion.addPressure(
                civInfo.religionManager.religion!!.name,
                200 * city.population.population
            )
        }

        city.population.autoAssignPopulation()

        // Update proximity rankings for all civs
        for (otherCiv in civInfo.gameInfo.getAliveMajorCivs()) {
            if (civInfo.getProximity(otherCiv) != Proximity.Neighbors) // unless already neighbors
                civInfo.cache.updateProximity(otherCiv,
                    otherCiv.cache.updateProximity(civInfo))
        }
        for (otherCiv in civInfo.gameInfo.getAliveCityStates()) {
            if (civInfo.getProximity(otherCiv) != Proximity.Neighbors) // unless already neighbors
                civInfo.cache.updateProximity(otherCiv,
                    otherCiv.cache.updateProximity(civInfo))
        }

        triggerCitiesSettledNearOtherCiv(city)

        addStartingBuildings(city, civInfo, startingEra)

        for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponFoundingCity,
            StateForConditionals(civInfo, city, unit)
        ))
            UniqueTriggerActivation.triggerUnique(unique, civInfo, city, unit, triggerNotificationText = "due to founding a city")
        if (unit != null)
            for (unique in unit.getTriggeredUniques(UniqueType.TriggerUponFoundingCity,
                StateForConditionals(civInfo, city, unit)))
                UniqueTriggerActivation.triggerUnique(unique, civInfo, city, unit, triggerNotificationText = "due to founding a city")

        return city
    }

    private object NamingConstants {
        /** Prefixes to add when every base name is taken, ordered. */
        val prefixes = arrayListOf("New", "Neo", "Nova", "Altera")
        const val fallback = "City Without A Name"
    }

    /**
     * Generates and returns a new city name for the [foundingCiv].
     *
     * This method attempts to return the first unused city name of the [foundingCiv], taking used
     * city names into consideration (including foreign cities). If that fails, it then checks
     * whether the civilization has [UniqueType.BorrowsCityNames] and, if true, returns a borrowed
     * name. Else, it repeatedly attaches one of the given [prefixes][NamingConstants.prefixes] to the list of names
     * up to ten times until an unused name is successfully generated. If all else fails, null is returned.
     *
     * @param foundingCiv The civilization that founded this city.
     * @param aliveCivs Every civilization currently alive.
     * @return A new city name in [String]. Null if failed to generate a name.
     */
    private fun generateNewCityName(
        foundingCiv: Civilization,
        aliveCivs: Set<Civilization>
    ): String? {
        val usedCityNames: Set<String> =
                aliveCivs.asSequence().flatMap { civilization ->
                    civilization.cities.asSequence().map { city -> city.name }
                }.toSet()

        // Attempt to return the first missing name from the list of city names
        for (cityName in foundingCiv.nation.cities) {
            if (cityName !in usedCityNames) return cityName
        }

        // If all names are taken and this nation borrows city names,
        // return a random borrowed city name
        if (foundingCiv.hasUnique(UniqueType.BorrowsCityNames)) {
            return borrowCityName(foundingCiv, aliveCivs, usedCityNames)
        }

        // If the nation doesn't have the unique above,
        // return the first missing name with an increasing number of prefixes attached
        // TODO: Make prefixes moddable per nation? Support suffixes?
        for (number in (1..10)) {
            for (prefix in NamingConstants.prefixes) {
                val repeatedPredix = "$prefix [".repeat(number)
                val suffix = "]".repeat(number)
                val candidate = foundingCiv.nation.cities.asSequence()
                    .map { repeatedPredix + it + suffix }
                    .firstOrNull { it !in usedCityNames }
                if (candidate != null) return candidate
            }
        }

        // If all else fails (by using some sort of rule set mod without city names),
        return null
    }

    /**
     * Borrows a city name from another major civilization.
     *
     * @param foundingCiv The civilization that founded this city.
     * @param aliveCivs Every civilization currently alive.
     * @param usedCityNames Every city name that have already been taken.
     * @return A new city named in [String]. Null if failed to generate a name.
     */
    private fun borrowCityName(
        foundingCiv: Civilization,
        aliveCivs: Set<Civilization>,
        usedCityNames: Set<String>
    ): String? {
        val aliveMajorNations: Sequence<Nation> =
                aliveCivs.asSequence().filter { civ -> civ.isMajorCiv() }.map { civ -> civ.nation }

        /*
        We take the last unused city name for each other major nation in this game,
        skipping nations whose names are exhausted,
        and choose a random one from that pool if it's not empty.
        */
        val otherMajorNations: Sequence<Nation> =
                aliveMajorNations.filter { nation -> nation != foundingCiv.nation }
        var newCityNames: Set<String> =
                otherMajorNations.mapNotNull { nation ->
                    nation.cities.lastOrNull { city -> city !in usedCityNames }
                }.toSet()
        if (newCityNames.isNotEmpty()) return newCityNames.random()

        // As per fandom wiki, once the names from the other nations in the game are exhausted,
        // names are taken from the rest of the major nations in the rule set
        val absentMajorNations: Sequence<Nation> =
                foundingCiv.gameInfo.ruleset.nations.values.asSequence().filter { nation ->
                    nation.isMajorCiv && nation !in aliveMajorNations
                }
        newCityNames =
                absentMajorNations.flatMap { nation ->
                    nation.cities.asSequence().filter { city -> city !in usedCityNames }
                }.toSet()
        if (newCityNames.isNotEmpty()) return newCityNames.random()

        // If for some reason we have used every single city name in the game,
        // (are we using some sort of rule set mod without city names?)
        return null
    }


    private fun addStartingBuildings(city: City, civInfo: Civilization, startingEra: String) {
        val ruleset = civInfo.gameInfo.ruleset
        if (civInfo.cities.size == 1) {
            val capitalCityIndicator = civInfo.capitalCityIndicator(city)
            if (capitalCityIndicator != null)
                city.cityConstructions.addBuilding(capitalCityIndicator, tryAddFreeBuildings = false)
        }

        // Add buildings and pop we get from starting in this era
        for (buildingName in ruleset.eras[startingEra]!!.settlerBuildings) {
            val building = ruleset.buildings[buildingName] ?: continue
            val uniqueBuilding = civInfo.getEquivalentBuilding(building)
            if (uniqueBuilding.isBuildable(city.cityConstructions))
                city.cityConstructions.addBuilding(uniqueBuilding, tryAddFreeBuildings = false)
        }

        civInfo.civConstructions.tryAddFreeBuildings()
    }


    /*
     When someone settles a city within 6 tiles of another civ, this makes the AI unhappy and it starts a rolling event.
     The SettledCitiesNearUs flag gets added to the AI so it knows this happened,
        and on its turn it asks the player to stop (with a DemandToStopSettlingCitiesNear alert type)
     If the player says "whatever, I'm not promising to stop", they get a -10 modifier which gradually disappears in 40 turns
     If they DO agree, then if they keep their promise for ~100 turns they get a +10 modifier for keeping the promise,
     But if they don't keep their promise they get a -20 that will only fully disappear in 160 turns.
     There's a lot of triggering going on here.
     */
    private fun triggerCitiesSettledNearOtherCiv(city: City) {
        val citiesWithin6Tiles =
                city.civ.gameInfo.civilizations.asSequence()
                    .filter { it.isMajorCiv() && it != city.civ }
                    .flatMap { it.cities }
                    .filter { it.getCenterTile().aerialDistanceTo(city.getCenterTile()) <= 6 }
        val civsWithCloseCities =
                citiesWithin6Tiles
                    .map { it.civ }
                    .distinct()
                    .filter { it.knows(city.civ) && it.hasExplored(city.getCenterTile()) }
        for (otherCiv in civsWithCloseCities)
            otherCiv.getDiplomacyManager(city.civ)!!.setFlag(DiplomacyFlags.SettledCitiesNearUs, 30)
    }
}
