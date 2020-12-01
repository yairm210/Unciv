package com.unciv.logic.civilization

import com.unciv.logic.automation.Automation.evaluteCombatStrength
import com.unciv.logic.map.MapUnit
import com.unciv.models.ruleset.unit.UnitType
import kotlin.math.max
import kotlin.math.sqrt

data class MemoryUnit(val civName: String,
                      val type: UnitType,
                      val number: Int = 0
                      )

class MemoryManager() {
    @Transient lateinit var civInfo: CivilizationInfo
    
    var seenLastRound = 0
    var seenPrevLastRound = 0

    var listOfUnits: MutableList<MemoryUnit> = mutableListOf()

    fun clone(): MemoryManager {
        val toReturn = MemoryManager()
        toReturn.listOfUnits.addAll(listOfUnits)
        toReturn.seenLastRound = seenLastRound
        toReturn.seenPrevLastRound = seenPrevLastRound
        return toReturn
    }

    fun addCivUnit(unit: MapUnit) {
        var unitFound = listOfUnits.find { it.civName == unit.civInfo.civName && it.type == unit.type }

        if (unitFound != null) {
            var unitIndex = listOfUnits.indexOf(unitFound)
            listOfUnits[unitIndex] = MemoryUnit(unit.civInfo.civName, unit.type, listOfUnits[unitIndex].number + 1)
        } else {
            listOfUnits.add(MemoryUnit(unit.civInfo.civName, unit.type, 1))
        }
    }
    
    fun subtractCivUnit(unit: MapUnit) {
        var unitFound = listOfUnits.find { it.civName == unit.civInfo.civName && it.type == unit.type }

        if (unitFound != null) {
            var unitIndex = listOfUnits.indexOf(unitFound)
            listOfUnits[unitIndex] = MemoryUnit(unit.civInfo.civName, unit.type, if (listOfUnits[unitIndex].number == 0) 0 else listOfUnits[unitIndex].number - 1)
        } else {
            listOfUnits.add(MemoryUnit(unit.civInfo.civName, unit.type, 1))
        }
    }
    
    fun setCivUnit(unit: MapUnit, unitNumber: Int) {
        var unitFound = listOfUnits.find { it.civName == unit.civInfo.civName && it.type == unit.type }

        if (unitFound != null) {
            var unitIndex = listOfUnits.indexOf(unitFound)
            listOfUnits[unitIndex] = MemoryUnit(unit.civInfo.civName, unit.type, unitNumber)
        } else {
            listOfUnits.add(MemoryUnit(unit.civInfo.civName, unit.type, unitNumber))
        }
    }
    
    fun setBaselineStrength() {
        for (civ in civInfo.getKnownCivs()) {
            var numberOfMilitaryUnits = civ.getCivUnits().filter { it.type.isMilitary() }.count()
            
            var perceivedNumberOfMilitaryUnits = listOfUnits.filter { it.civName == civ.civName }.sumBy { it.number }
            
            if (perceivedNumberOfMilitaryUnits > numberOfMilitaryUnits) { // If Strength is a known demographic then I figure anyone can get a rough estimate by seeing the units and comparing it the leaderboard
                for (unit in listOfUnits.filter { it.civName == civ.civName }) {
                    var unitIndex = listOfUnits.indexOf(unit)
                    listOfUnits[unitIndex] = MemoryUnit(listOfUnits[unitIndex].civName, listOfUnits[unitIndex].type, if (listOfUnits[unitIndex].number <= 0) 0 else listOfUnits[unitIndex].number - 1)
                }
            }
        }

    }
    
}
