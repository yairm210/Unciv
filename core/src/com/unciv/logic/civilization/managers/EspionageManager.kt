package com.unciv.logic.civilization.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Spy
import com.unciv.models.ruleset.unique.UniqueType


class EspionageManager : IsPartOfGameInfoSerialization {

    var spyList = ArrayList<Spy>()
    val erasSpyEarnedFor = LinkedHashSet<String>()

    @Transient
    lateinit var civInfo: Civilization

    /**
     * Part of the nextTurnAction of MoveSpies.
     * We need to store if the player has clicked the button already
     */
    @Transient
    var dismissedShouldMoveSpies = false

    fun clone(): EspionageManager {
        val toReturn = EspionageManager()
        spyList.mapTo(toReturn.spyList) { it.clone() }
        toReturn.erasSpyEarnedFor.addAll(erasSpyEarnedFor)
        return toReturn
    }

    fun setTransients(civInfo: Civilization) {
        this.civInfo = civInfo
        for (spy in spyList) {
            spy.setTransients(civInfo)
        }
    }

    fun endTurn() {
        for (spy in spyList.toList())
            spy.endTurn()
    }

    fun getSpyName(): String {
        val usedSpyNames = spyList.map { it.name }.toHashSet()
        val validSpyNames = civInfo.nation.spyNames.filter { it !in usedSpyNames }
        return validSpyNames.randomOrNull()
            ?: "Spy ${spyList.size + 1}" // +1 as non-programmers count from 1
    }

    fun addSpy(): Spy {
        val spyName = getSpyName()
        val newSpy = Spy(spyName, getStartingSpyRank())
        newSpy.setTransients(civInfo)
        spyList.add(newSpy)
        return newSpy
    }

    fun getTilesVisibleViaSpies(): Sequence<Tile> {
        return spyList.asSequence()
            .filter { it.isSetUp() }
            .mapNotNull { it.getCityOrNull() }
            .flatMap { it.getCenterTile().getTilesInDistance(1) }
    }

    fun getTechsToSteal(otherCiv: Civilization): Set<String> {
        val techsToSteal = mutableSetOf<String>()
        for (tech in otherCiv.tech.techsResearched) {
            if (civInfo.tech.isResearched(tech)) continue
            if (!civInfo.tech.canBeResearched(tech)) continue
            techsToSteal.add(tech)
        }
        return techsToSteal
    }

    fun getSpiesInCity(city: City): List<Spy> {
        return spyList.filterTo(mutableListOf()) { it.getCityOrNull() == city }
    }

    fun getStartingSpyRank(): Int = 1 + civInfo.getMatchingUniques(UniqueType.SpyStartingLevel).sumOf { it.params[0].toInt() }

    /**
     * Returns a list of all cities with our spies in them.
     * The list needs to be stable across calls on the same turn.
     */
    fun getCitiesWithOurSpies(): List<City> = spyList.filter { it.isSetUp() }.mapNotNull { it.getCityOrNull() }

    fun getSpyAssignedToCity(city: City): Spy? = spyList.firstOrNull { it.getCityOrNull() == city }

    /**
     * Determines whether the NextTurnAction MoveSpies should be shown or not
     * @return true if there are spies waiting to be moved
     */
    fun shouldShowMoveSpies(): Boolean = !dismissedShouldMoveSpies && spyList.any { it.isIdle() }
            && civInfo.gameInfo.getCities().any { civInfo.hasExplored(it.getCenterTile()) && getSpyAssignedToCity(it) == null }

    fun getIdleSpies(): List<Spy> {
        return spyList.filterTo(mutableListOf()) { it.isIdle() }
    }

    /**
     * Takes all spies away from their cities.
     * Called when the civ is destroyed.
     */
    fun removeAllSpies() {
        spyList.forEach { it.moveTo(null) }
    }
}
