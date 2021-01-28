package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color

class GoldenAgeManager{
    @Transient
    lateinit var civInfo: CivilizationInfo

    var storedHappiness = 0
    private var numberOfGoldenAges = 0
    var turnsLeftForCurrentGoldenAge = 0

    fun clone(): GoldenAgeManager {
        val toReturn = GoldenAgeManager()
        toReturn.numberOfGoldenAges=numberOfGoldenAges
        toReturn.storedHappiness=storedHappiness
        toReturn.turnsLeftForCurrentGoldenAge=turnsLeftForCurrentGoldenAge
        return toReturn
    }

    fun isGoldenAge(): Boolean = turnsLeftForCurrentGoldenAge > 0

    fun happinessRequiredForNextGoldenAge(): Int {
        return ((500 + numberOfGoldenAges * 250) * (1 + civInfo.cities.size / 100.0)).toInt() //https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
    }

    fun enterGoldenAge(unmodifiedNumberOfTurns: Int = 10) {
        var turnsToGoldenAge = unmodifiedNumberOfTurns.toFloat()
        for(unique in civInfo.getMatchingUniques("Golden Age length increased by []%"))
            turnsToGoldenAge *= (unique.params[0].toFloat()/100 + 1)
        turnsToGoldenAge *= civInfo.gameInfo.gameParameters.gameSpeed.modifier
        turnsLeftForCurrentGoldenAge += turnsToGoldenAge.toInt()
        civInfo.addNotification("You have entered a Golden Age!", null, Color.GOLD)
        civInfo.popupAlerts.add(PopupAlert(AlertType.GoldenAge,""))
    }

    fun endTurn(happiness: Int) {
        if (happiness > 0 && !isGoldenAge()) storedHappiness += happiness

        if (isGoldenAge())
            turnsLeftForCurrentGoldenAge--
        else if (storedHappiness > happinessRequiredForNextGoldenAge()) {
            storedHappiness -= happinessRequiredForNextGoldenAge()
            enterGoldenAge()
            numberOfGoldenAges++
        }
    }

}
