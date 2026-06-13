package com.unciv.logic.civilization.managers.quests

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.Proximity
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit
import yairm210.purity.annotations.Readonly

internal object QuestTargetHelpers {
    // by turn so the same civ doesn't give the same quests always, and by civID so on the same turn different civs give different quests
    @Readonly
    fun Civilization.getRandom() = state.stateBasedRandom("QuestManager")
    @Readonly
    fun Civilization.getRandom(challenger: Civilization?) =
        if (challenger == null) getRandom()
        else getDiplomacyManager(challenger)?.state?.stateBasedRandom("QuestManager")
            ?: getRandom()

    /**
     * Returns a random [com.unciv.logic.map.tile.Tile] containing a Barbarian encampment within 8 tiles of [this city-state][this]
     * to be destroyed
     */
    @Readonly
    fun Civilization.getBarbarianEncampmentForQuest(challenger: Civilization? = null): Tile? {
        @Suppress("DEPRECATION")
        val encampments = getCapital()!!.getCenterTile().getTilesInDistance(8)
            .filter { it.improvement == Constants.barbarianEncampment }.toList()

        return encampments.randomOrNull(getRandom(challenger))
    }

    /**
     * Returns a random resource to be connected to the [challenger]'s trade route as a quest.
     * The resource must be a [Luxury][com.unciv.models.ruleset.tile.ResourceType.Luxury] or
     * [Strategic][com.unciv.models.ruleset.tile.ResourceType.Strategic] resource, must not be owned
     * by [this city-state][this] and the [challenger], and must be viewable by the [challenger];
     * if none exists, it returns null.
     */
    @Readonly
    fun Civilization.getResourceForQuest(challenger: Civilization): TileResource? {
        val ownedByCityStateResources = detailedCivResources.map { it.resource }
        val ownedByMajorResources = challenger.detailedCivResources.map { it.resource }

        val resourcesOnMap = gameInfo.tileMap.values.asSequence().mapNotNull { it.tileResource }.distinct()
        val viewableResourcesForChallenger = resourcesOnMap.filter { challenger.canSeeResource(it) }

        val notOwnedResources = viewableResourcesForChallenger.filter {
            it.resourceType != ResourceType.Bonus &&
                !ownedByCityStateResources.contains(it) &&
                !ownedByMajorResources.contains(it)
        }.toList()

        return notOwnedResources.randomOrNull(getRandom(challenger))
    }


    @Readonly
    fun Civilization.getWonderToBuildForQuest(challenger: Civilization): Building? {
        @Readonly
        fun isMoreThanAQuarterDone(city: City, buildingName: String) =
            city.cityConstructions.getWorkDone(buildingName) * 3 > city.cityConstructions.getRemainingWork(buildingName)
        fun isBuiltOrBeingBuilt(city: City, buildingName: String) =
            city.cityConstructions.isBuilt(buildingName) || isMoreThanAQuarterDone(city, buildingName)
        @Suppress("DEPRECATION")
        val wonders = gameInfo.ruleset.buildings.values
            .filter { building ->
                // Buildable wonder
                building.isWonder
                    && challenger.tech.isResearched(building)
                    // Can't be disabled
                    && !building.isUnavailableBySettings(gameInfo)
                    // Can't be a unique wonder
                    && building.uniqueTo == null
                    // Big loop last: Exists or more than 25% built anywhere
                    && gameInfo.getCities().none { isBuiltOrBeingBuilt(it, building.name) }
            }

        return wonders.randomOrNull(getRandom(challenger))
    }

    /**
     * Returns a random Natural Wonder not yet discovered by [challenger], or [the city-state][this] dispatching the quest.
     *
     * @param challenger The Civilization that will be receiving the quest.
     */
    @Suppress("ConvertArgumentToSet")
    @Readonly
    fun Civilization.getNaturalWonderToFindForQuest(challenger: Civilization): String? =
        gameInfo.tileMap.naturalWonders
            .subtract(challenger.naturalWonders)
            .subtract(naturalWonders)
            .randomOrNull(getRandom(challenger))

    /**
     * Returns a Great Person [com.unciv.models.ruleset.unit.BaseUnit] that is not owned by both the [challenger] and [this city-state][this]
     */
    @Readonly
    fun Civilization.getGreatPersonForQuest(challenger: Civilization): BaseUnit? {
        val ruleset = gameInfo.ruleset

        val existingGreatPeople =
            // concatenate sequences of existing GP for the challenger (a player) and our `civ` (the quest-giving city-state)
            (challenger.units.getCivGreatPeople() + units.getCivGreatPeople())
                .map { it.baseUnit.getReplacedUnit(ruleset) }.toSet()

        val greatPeople = challenger.greatPeople.getGreatPeople()
            .map { it.getReplacedUnit(ruleset) }
            .distinct()
            // The hidden test is already done by getGreatPeople for the civ-specific units,
            // repeat for the replaced one we'll be asking for
            .filterNot { it in existingGreatPeople || it.isUnavailableBySettings(gameInfo) }
            .toList()

        return greatPeople.randomOrNull(getRandom(challenger))
    }

    /**
     * Returns a random [Civilization] (major) that [challenger] has met, but whose territory he
     * cannot see; if none exists, it returns null.
     */
    @Readonly
    fun Civilization.getCivilizationToFindForQuest(challenger: Civilization): Civilization? {
        @Suppress("DEPRECATION")
        val civilizationsToFind = challenger.getKnownCivs()
            .filter { it.isAlive() && it.isMajorCiv() && !challenger.hasMetCivTerritory(it) }
            .toList()

        return civilizationsToFind.randomOrNull(getRandom(challenger))
    }

    /**
     * Returns a city-state [Civilization] that [this city-state][this] wants to target for hostile quests
     */
    @Readonly
    fun Civilization.getCityStateTarget(challenger: Civilization): Civilization? {
        @Suppress("DEPRECATION")
        val closestProximity = gameInfo.getAliveCityStates()
            .mapNotNull { proximity[it.civID] }.filter { it != Proximity.None }.minByOrNull { it.ordinal }

        if (closestProximity == null || closestProximity == Proximity.Distant) // None close enough
            return null

        @Suppress("DEPRECATION")
        val validTargets = getKnownCivs().filter { it.isCityState && challenger.knows(it)
            && proximity[it.civID] == closestProximity }

        return validTargets.toList().randomOrNull(getRandom(challenger))
    }

    /** Returns a [Civilization] of the civ that most recently bullied [this city-state][this].
     *  Note: forgets after 20 turns has passed! */
    @Readonly
    fun Civilization.getMostRecentBully(): String? {
        val bullies = diplomacy.values.filter { it.hasFlag(DiplomacyFlags.Bullied) }
        return bullies.maxByOrNull { it.getFlag(DiplomacyFlags.Bullied) }?.otherCivName
    }
}
