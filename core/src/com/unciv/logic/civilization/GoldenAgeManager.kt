package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color
import com.unciv.UniqueAbility

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

    fun enterGoldenAge() {
        var turnsToGoldenAge = 10.0
        if (civInfo.hasUnique("Golden Age length increases +50%")) turnsToGoldenAge *= 1.5
        if(civInfo.nation.unique == UniqueAbility.ACHAEMENID_LEGACY )
            turnsToGoldenAge*=1.5
        if (civInfo.policies.isAdopted("Freedom Complete")) turnsToGoldenAge *= 1.5
        turnsToGoldenAge *= civInfo.gameInfo.gameParameters.gameSpeed.modifier
        turnsLeftForCurrentGoldenAge += turnsToGoldenAge.toInt()
        civInfo.addNotification("You have entered a golden age!", null, Color.GOLD)
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
