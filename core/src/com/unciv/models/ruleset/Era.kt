package com.unciv.models.ruleset

import com.unciv.models.stats.INamed

class Era : INamed {
    override var name: String = ""
    var researchAgreementCost = 300
    var startingSettlerCount = 1
    var startingSettlerUnit = "Settler" // For mods which have differently named settlers
    var startingWorkerCount = 0
    var startingWorkerUnit = "Worker"
    var startingMilitaryUnitCount = 1
    var startingMilitaryUnit = "Warrior"
    var startingGold = 0
    var startingCulture = 0
    var settlerPopulation = 1
    var settlerBuildings = ArrayList<String>()
    var obsoleteWonders = ArrayList<String>()
    
    fun getStartingUnits(): List<String> {
        val startingUnits = mutableListOf<String>()
        repeat(startingSettlerCount) {startingUnits.add(startingSettlerUnit)}
        repeat(startingWorkerCount) {startingUnits.add(startingWorkerUnit)}
        repeat(startingMilitaryUnitCount) {startingUnits.add(startingMilitaryUnit)}
        println("$name: $startingUnits")
        return startingUnits
    }
}