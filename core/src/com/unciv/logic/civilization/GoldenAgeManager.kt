package com.unciv.logic.civilization

class GoldenAgeManager {
    @Transient
    lateinit var civInfo: CivilizationInfo

    var storedHappiness = 0
    private var numberOfGoldenAges = 0
    var turnsLeftForCurrentGoldenAge = 0

    fun isGoldenAge(): Boolean = turnsLeftForCurrentGoldenAge > 0

    fun happinessRequiredForNextGoldenAge(): Int {
        return ((500 + numberOfGoldenAges * 250) * (1 + civInfo.cities.size / 100.0)).toInt() //https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
    }

    fun enterGoldenAge() {
        var turnsToGoldenAge = 10.0
        if (civInfo.buildingUniques.contains("GoldenAgeLengthIncrease")) turnsToGoldenAge *= 1.5
        if (civInfo.policies.isAdopted("Freedom Complete")) turnsToGoldenAge *= 1.5
        turnsLeftForCurrentGoldenAge += turnsToGoldenAge.toInt()
        civInfo.gameInfo.addNotification("You have entered a golden age!", null)
    }

    fun nextTurn(happiness: Int) {
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
