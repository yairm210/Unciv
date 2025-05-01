package com.unciv.logic.automation.unit

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.Spy
import com.unciv.models.SpyAction
import kotlin.random.Random

class EspionageAutomation(val civInfo: Civilization) {
    private val civsToStealFrom: List<Civilization> by lazy {
        civInfo.getKnownCivs().filter {otherCiv -> otherCiv.isMajorCiv()
            && otherCiv.cities.any { it.getCenterTile().isExplored(civInfo) && civInfo.espionageManager.getSpyAssignedToCity(it) == null }
            && civInfo.espionageManager.getTechsToSteal(otherCiv).isNotEmpty() }.toList()
    }

    private val getCivsToStealFromSorted: List<Civilization> =
        civsToStealFrom.sortedBy { otherCiv -> civInfo.espionageManager.spyList
            .count { it.isDoingWork() && it.getCityOrNull()?.civ == otherCiv }
        }.toList()

    private val cityStatesToRig: List<Civilization> by lazy {
        civInfo.getKnownCivs().filter { otherCiv -> otherCiv.isMinorCiv() && otherCiv.knows(civInfo) && !civInfo.isAtWarWith(otherCiv) }.toList()
    }

    fun automateSpies() {
        val spies = civInfo.espionageManager.spyList
        val spiesToMove = spies.filter { it.isAlive() && !it.isDoingWork() }
        for (spy in spiesToMove) {
            val randomSeed = spies.size + spies.indexOf(spy) + civInfo.gameInfo.turns
            val randomAction = Random(randomSeed).nextInt(10)

            // Try each operation based on the random value and the success rate
            // If an operation was not successfull try the next one
            if (randomAction <= 7 && automateSpyStealTech(spy)) {
                continue
            } else if (randomAction <= 9 && automateSpyRigElection(spy)) {
                continue
            } else if (automateSpyCounterInteligence(spy)) {
                continue
            } else if (spy.isDoingWork()) {
                continue // We might have been doing counter intelligence and wanted to look for something better
            } else {
                // Retry all of the operations one more time
                if (automateSpyStealTech(spy)) continue
                if (automateSpyRigElection(spy)) continue
                if(automateSpyCounterInteligence(spy)) continue
            }
            // There is nothing for our spy to do, put it in a random city
            val randomCity = civInfo.gameInfo.getCities().filter { spy.canMoveTo(it) }.toList().randomOrNull()
            spy.moveTo(randomCity)
        }
        spies.forEach { checkIfShouldStageCoup(it) }
    }

    /**
     * Moves the spy to a city that we can steal a tech from
     */
    fun automateSpyStealTech(spy: Spy): Boolean {
        if (civsToStealFrom.isEmpty()) return false
        // We want to move the spy to the city with the highest science generation
        // Players can't usually figure this out so lets do highest population instead
        val cityToMoveTo = getCivsToStealFromSorted.first().cities.filter { spy.canMoveTo(it) }.maxByOrNull { it.population.population }
        spy.moveTo(cityToMoveTo)
        return cityToMoveTo != null
    }

    /**
     * Moves the spy to a random city-state
     */
    private fun automateSpyRigElection(spy: Spy): Boolean {
        val cityToMoveTo = cityStatesToRig.flatMap { it.cities }.filter { !it.isBeingRazed && spy.canMoveTo(it) && (it.civ.getDiplomacyManager(civInfo)!!.getInfluence() < 150 || it.civ.getAllyCivName() != civInfo.civName) }
            .maxByOrNull { it.civ.getDiplomacyManager(civInfo)!!.getInfluence() }
        spy.moveTo(cityToMoveTo)
        return cityToMoveTo != null
    }

    /**
     * Moves the spy to a random city of ours
     */
    private fun automateSpyCounterInteligence(spy: Spy): Boolean {
        spy.moveTo(civInfo.cities.filter { spy.canMoveTo(it) }.randomOrNull())
        return spy.action == SpyAction.CounterIntelligence
    }

    private fun checkIfShouldStageCoup(spy: Spy) {
        if (!spy.canDoCoup()) return
        if (spy.getCoupChanceOfSuccess(false) < .7) return
        val allyCiv = spy.getCity().civ.getAllyCiv()
        // Don't coup city-states whose allies are out friends
        if (allyCiv != null && civInfo.getDiplomacyManager(allyCiv)?.isRelationshipLevelGE(RelationshipLevel.Friend) == true) return
        val spies = civInfo.espionageManager.spyList
        val randomSeed = spies.size + spies.indexOf(spy) + civInfo.gameInfo.turns
        val randomAction = Random(randomSeed).nextInt(100)
        if (randomAction < 20) spy.setAction(SpyAction.Coup, 1)
    }
}
