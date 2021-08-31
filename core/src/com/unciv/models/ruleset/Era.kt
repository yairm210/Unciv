package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.civilization.CityStateType
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
    var startPercent = 0
    var friendBonus = HashMap<String, ArrayList<String>>()
    var allyBonus = HashMap<String, ArrayList<String>>()
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

    fun getHexColor() = "#" + getColor().toString().substring(0,6)

    companion object {
        // User for CS bonuses in case the Eras file is missing (legacy mods)
        fun getLegacyCityStateBonusEra(eraNumber: Int) = Era().apply {
            val cultureBonus = if(eraNumber in 0..1) 3 else if (eraNumber in 2..3) 6 else 13
            val happinessBonus = if(eraNumber in 0..1) 2 else 3
            friendBonus[CityStateType.Militaristic.name] = arrayListOf("Provides military units every [20] turns")
            friendBonus[CityStateType.Cultured.name] = arrayListOf("Provides [$cultureBonus] [Culture] per turn")
            friendBonus[CityStateType.Mercantile.name] = arrayListOf("Provides [$happinessBonus] Happiness")
            friendBonus[CityStateType.Maritime.name] = arrayListOf("Provides [2] [Food] [in capital]")
            allyBonus[CityStateType.Militaristic.name] = arrayListOf("Provides military units every [17] turns")
            allyBonus[CityStateType.Cultured.name] = arrayListOf("Provides [${cultureBonus*2}] [Culture] per turn")
            allyBonus[CityStateType.Mercantile.name] = arrayListOf("Provides [$happinessBonus] Happiness", "Provides a unique luxury")
            allyBonus[CityStateType.Maritime.name] = arrayListOf("Provides [2] [Food] [in capital]", "Provides [1] [Food] [in all cities]")
        }
    }
}