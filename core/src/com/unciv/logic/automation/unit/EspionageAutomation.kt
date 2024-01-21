package com.unciv.logic.automation.unit

import com.unciv.logic.civilization.Civilization
import com.unciv.models.SpyAction

object EspionageAutomation {

    fun automateSpies(civInfo: Civilization) {
        val civsToStealFrom: List<Civilization> by lazy { 
            civInfo.getKnownCivs().filter {otherCiv -> otherCiv.isMajorCiv() && otherCiv.cities.any { it.getCenterTile().isVisible(civInfo) } 
                && civInfo.espionageManager.getTechsToSteal(otherCiv).isNotEmpty() }.toList()
        }

        val getCivsToStealFromSorted: List<Civilization> =
            civsToStealFrom.sortedBy { otherCiv -> civInfo.espionageManager.spyList
                .count { it.isDoingWork() && it.getLocation()?.civ == otherCiv } 
            }.toList()

        for (spy in civInfo.espionageManager.spyList) {
            if (spy.isDoingWork()) continue
            if (civsToStealFrom.isNotEmpty()) {
                // We want to move the spy to the city with the highest science generation
                // Players can't usually figure this out so lets do highest population instead
                spy.moveTo(getCivsToStealFromSorted.first().cities.filter { it.getCenterTile().isVisible(civInfo) }.maxByOrNull { it.population.population })
                continue
            }
            if (spy.action == SpyAction.None) {
                spy.moveTo(civInfo.getKnownCivs().filter { otherCiv -> otherCiv.isMajorCiv() && otherCiv.cities.any { it.getCenterTile().isVisible(civInfo) }}
                    .toList().randomOrNull()?.cities?.filter { it.getCenterTile().isVisible(civInfo) }?.randomOrNull())
            }
        }
    }
}
