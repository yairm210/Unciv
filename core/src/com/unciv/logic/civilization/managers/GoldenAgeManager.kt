package com.unciv.logic.civilization.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.CivilopediaAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.PopupAlert
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toPercent
import yairm210.purity.annotations.Readonly

class GoldenAgeManager : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var civInfo: Civilization

    var storedHappiness = 0
    private var numberOfGoldenAges = 0
    var turnsLeftForCurrentGoldenAge = 0

    fun clone(): GoldenAgeManager {
        val toReturn = GoldenAgeManager()
        toReturn.numberOfGoldenAges = numberOfGoldenAges
        toReturn.storedHappiness = storedHappiness
        toReturn.turnsLeftForCurrentGoldenAge = turnsLeftForCurrentGoldenAge
        return toReturn
    }

    @Readonly fun isGoldenAge(): Boolean = turnsLeftForCurrentGoldenAge > 0
    
    fun addHappiness(amount: Int) {
        storedHappiness += amount
    }

    @Readonly
    fun happinessRequiredForNextGoldenAge(): Int {
        var cost = (500 + numberOfGoldenAges * 250).toFloat()
        cost *= civInfo.cities.size.toPercent()  //https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
        cost *= civInfo.gameInfo.speed.modifier
        return cost.toInt()
    }

    @Readonly
    fun calculateGoldenAgeLength(unmodifiedNumberOfTurns: Int): Int {
        var turnsToGoldenAge = unmodifiedNumberOfTurns.toFloat()
        for (unique in civInfo.getMatchingUniques(UniqueType.GoldenAgeLength))
            turnsToGoldenAge *= unique.params[0].toPercent()
        turnsToGoldenAge *= civInfo.gameInfo.speed.goldenAgeLengthModifier
        return turnsToGoldenAge.toInt()
    }

    fun enterGoldenAge(unmodifiedNumberOfTurns: Int = 10) {
        turnsLeftForCurrentGoldenAge += calculateGoldenAgeLength(unmodifiedNumberOfTurns)
        civInfo.addNotification("You have entered a Golden Age!",
            CivilopediaAction("Tutorial/Golden Age"),
            NotificationCategory.General, "StatIcons/Happiness")
        civInfo.popupAlerts.add(PopupAlert(AlertType.GoldenAge, ""))

        for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponEnteringGoldenAge))
            UniqueTriggerActivation.triggerUnique(unique, civInfo)
        //Golden Age can happen mid turn with Great Artist effects
        for (city in civInfo.cities)
            city.cityStats.update()
    }

    fun endTurn(happiness: Int) {
        if (!isGoldenAge())
            storedHappiness = (storedHappiness + happiness).coerceAtLeast(0)

        if (isGoldenAge()){
            turnsLeftForCurrentGoldenAge--
            if (turnsLeftForCurrentGoldenAge <= 0)
                for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUpponEndingGoldenAge))
                    UniqueTriggerActivation.triggerUnique(unique, civInfo)
        }
                
        else if (storedHappiness > happinessRequiredForNextGoldenAge()) {
            storedHappiness -= happinessRequiredForNextGoldenAge()
            enterGoldenAge()
            numberOfGoldenAges++
        }
    }

}
