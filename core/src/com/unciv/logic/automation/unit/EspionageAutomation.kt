package com.unciv.logic.automation.unit

import com.unciv.logic.civilization.Civilization
import com.unciv.models.SpyAction

object EspionageAutomation {

    fun automateSpies(civInfo: Civilization) {
        val civsToStealFrom: List<Civilization> by lazy { 
            civInfo.getKnownCivs().filter {otherCiv -> otherCiv.cities.any { it.getCenterTile().isVisible(civInfo) } 
                && civInfo.espionageManager.getTechsToSteal(otherCiv).isNotEmpty() }.toList()
        }

        val getCivsToStealFromSorted: List<Civilization> =
            civsToStealFrom.sortedBy { otherCiv -> civInfo.espionageManager.spyList
                .count { it.isDoingWork() && it.getLocation()?.civ == otherCiv } 
            }.toList()

        for (spy in civInfo.espionageManager.spyList) {
            if (spy.action == SpyAction.StealingTech) continue
            if (civsToStealFrom.isNotEmpty()) {
                spy.moveTo(getCivsToStealFromSorted.first().cities.filter { it.getCenterTile().isVisible(civInfo) }.random())
            }
        }
    }
}