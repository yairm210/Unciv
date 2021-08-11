package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.unciv.models.stats.INamed
import com.unciv.ui.utils.colorFromRGB

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
    var startingObsoleteWonders = ArrayList<String>()
    var baseUnitBuyCost = 200
    var mercantileHappiness = 2
    var maritimeCapitalFood = 2
    var maritimeAllCitiesFood = 1
    var culturedFriendCulture = 3
    var culturedAllyCulture = 6
    var militaristicFriendDelay = 20
    var militaristicAllyDelay = 17
    var iconRGB: List<Int>? = null
    
    fun getStartingUnits(): List<String> {
        val startingUnits = mutableListOf<String>()
        repeat(startingSettlerCount) {startingUnits.add(startingSettlerUnit)}
        repeat(startingWorkerCount) {startingUnits.add(startingWorkerUnit)}
        repeat(startingMilitaryUnitCount) {startingUnits.add(startingMilitaryUnit)}
        return startingUnits
    }
    
    fun getColor(): Color {
        if (iconRGB == null) return Color.WHITE.cpy()
        return colorFromRGB(iconRGB!![0], iconRGB!![1], iconRGB!![2])
    }
}