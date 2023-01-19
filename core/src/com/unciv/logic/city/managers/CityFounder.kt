package com.unciv.logic.city.managers

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Proximity
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType

class CityFounder {
    fun foundCity(civInfo: CivilizationInfo, cityLocation: Vector2) :CityInfo{
        val cityInfo = CityInfo()

        cityInfo.foundingCiv = civInfo.civName
        cityInfo.turnAcquired = civInfo.gameInfo.turns
        cityInfo.location = cityLocation
        cityInfo.setTransients(civInfo)

        cityInfo.name = generateNewCityName(
            civInfo,
            civInfo.gameInfo.civilizations.asSequence().filter { civ -> civ.isAlive() }.toSet(),
            arrayListOf("New ", "Neo ", "Nova ", "Altera ")
        ) ?: "City Without A Name"

        cityInfo.isOriginalCapital = civInfo.citiesCreated == 0
        if (cityInfo.isOriginalCapital) {
            civInfo.hasEverOwnedOriginalCapital = true
            // if you have some culture before the 1st city is found, you may want to adopt the 1st policy
            civInfo.policies.shouldOpenPolicyPicker = true
        }
        civInfo.citiesCreated++

        civInfo.cities = civInfo.cities.toMutableList().apply { add(cityInfo) }

        val startingEra = civInfo.gameInfo.gameParameters.startingEra

        addStartingBuildings(cityInfo, civInfo, startingEra)

        cityInfo.expansion.reset()

        cityInfo.tryUpdateRoadStatus()

        val tile = cityInfo.getCenterTile()
        for (terrainFeature in tile.terrainFeatures.filter {
            cityInfo.getRuleset().tileImprovements.containsKey(
                "Remove $it"
            )
        })
            tile.removeTerrainFeature(terrainFeature)

        tile.changeImprovement(null)
        tile.improvementInProgress = null

        val ruleset = civInfo.gameInfo.ruleSet
        cityInfo.workedTiles = hashSetOf() //reassign 1st working tile

        cityInfo.population.setPopulation(ruleset.eras[startingEra]!!.settlerPopulation)

        if (civInfo.religionManager.religionState == ReligionState.Pantheon) {
            cityInfo.religion.addPressure(
                civInfo.religionManager.religion!!.name,
                200 * cityInfo.population.population
            )
        }

        cityInfo.population.autoAssignPopulation()

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

        triggerCitiesSettledNearOtherCiv(cityInfo)
        civInfo.gameInfo.cityDistances.setDirty()

        return cityInfo
    }


    /**
     * Generates and returns a new city name for the [foundingCiv].
     *
     * This method attempts to return the first unused city name of the [foundingCiv], taking used
     * city names into consideration (including foreign cities). If that fails, it then checks
     * whether the civilization has [UniqueType.BorrowsCityNames] and, if true, returns a borrowed
     * name. Else, it repeatedly attaches one of the given [prefixes] to the list of names up to ten
     * times until an unused name is successfully generated. If all else fails, null is returned.
     *
     * @param foundingCiv The civilization that founded this city.
     * @param aliveCivs Every civilization currently alive.
     * @param prefixes Prefixes to add when every base name is taken, ordered.
     * @return A new city name in [String]. Null if failed to generate a name.
     */
    private fun generateNewCityName(
        foundingCiv: CivilizationInfo,
        aliveCivs: Set<CivilizationInfo>,
        prefixes: List<String>
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
        var candidate: String?
        for (number in (1..10)) {
            for (prefix in prefixes) {
                val currentPrefix: String = prefix.repeat(number)
                candidate = foundingCiv.nation.cities.firstOrNull { cityName ->
                    (currentPrefix + cityName) !in usedCityNames
                }
                if (candidate != null) return currentPrefix + candidate
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
        foundingCiv: CivilizationInfo,
        aliveCivs: Set<CivilizationInfo>,
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
                foundingCiv.gameInfo.ruleSet.nations.values.asSequence().filter { nation ->
                    nation.isMajorCiv() && nation !in aliveMajorNations
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


    private fun addStartingBuildings(cityInfo: CityInfo, civInfo: CivilizationInfo, startingEra: String) {
        val ruleset = civInfo.gameInfo.ruleSet
        if (civInfo.cities.size == 1) cityInfo.cityConstructions.addBuilding(cityInfo.capitalCityIndicator())

        // Add buildings and pop we get from starting in this era
        for (buildingName in ruleset.eras[startingEra]!!.settlerBuildings) {
            val building = ruleset.buildings[buildingName] ?: continue
            val uniqueBuilding = civInfo.getEquivalentBuilding(building)
            if (uniqueBuilding.isBuildable(cityInfo.cityConstructions))
                cityInfo.cityConstructions.addBuilding(uniqueBuilding.name)
        }

        civInfo.civConstructions.tryAddFreeBuildings()
        cityInfo.cityConstructions.addFreeBuildings()
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
    private fun triggerCitiesSettledNearOtherCiv(cityInfo: CityInfo) {
        val citiesWithin6Tiles =
                cityInfo.civInfo.gameInfo.civilizations.asSequence()
                    .filter { it.isMajorCiv() && it != cityInfo.civInfo }
                    .flatMap { it.cities }
                    .filter { it.getCenterTile().aerialDistanceTo(cityInfo.getCenterTile()) <= 6 }
        val civsWithCloseCities =
                citiesWithin6Tiles
                    .map { it.civInfo }
                    .distinct()
                    .filter { it.knows(cityInfo.civInfo) && it.hasExplored(cityInfo.location) }
        for (otherCiv in civsWithCloseCities)
            otherCiv.getDiplomacyManager(cityInfo.civInfo).setFlag(DiplomacyFlags.SettledCitiesNearUs, 30)
    }
}
