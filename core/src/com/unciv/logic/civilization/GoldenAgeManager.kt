package com.unciv.logic.civilization

import com.unciv.models.helpers.ICloneable
import com.unciv.ui.utils.toPercent

class GoldenAgeManager: ICloneable<GoldenAgeManager> {
    @Transient
    lateinit var civInfo: CivilizationInfo

    var storedHappiness = 0
    private var numberOfGoldenAges = 0
    var turnsLeftForCurrentGoldenAge = 0

    override fun clone(): GoldenAgeManager {
        val toReturn = GoldenAgeManager()
        toReturn.numberOfGoldenAges = numberOfGoldenAges
        toReturn.storedHappiness = storedHappiness
        toReturn.turnsLeftForCurrentGoldenAge = turnsLeftForCurrentGoldenAge
        return toReturn
    }

    fun isGoldenAge(): Boolean = turnsLeftForCurrentGoldenAge > 0

    fun happinessRequiredForNextGoldenAge(): Int {
        return ((500 + numberOfGoldenAges * 250) * civInfo.cities.size.toPercent()).toInt() //https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
    }

    fun enterGoldenAge(unmodifiedNumberOfTurns: Int = 10) {
        var turnsToGoldenAge = unmodifiedNumberOfTurns.toFloat()
        for (unique in civInfo.getMatchingUniques("Golden Age length increased by []%"))
            turnsToGoldenAge *= unique.params[0].toPercent()
        turnsToGoldenAge *= civInfo.gameInfo.gameParameters.gameSpeed.modifier
        turnsLeftForCurrentGoldenAge += turnsToGoldenAge.toInt()
        civInfo.addNotification("You have entered a Golden Age!", "StatIcons/Happiness")
        civInfo.popupAlerts.add(PopupAlert(AlertType.GoldenAge, ""))
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
