package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.ui.utils.colorFromRGB

class Era : INamed, IHasUniques {
    override var name: String = ""
    var eraNumber: Int = -1
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

    var friendBonus = HashMap<String, List<String>>()
    var allyBonus = HashMap<String, List<String>>()
    val friendBonusObjects: Map<CityStateType, List<CityStateBonus>> by lazy { initBonuses(friendBonus) }
    val allyBonusObjects: Map<CityStateType, List<CityStateBonus>> by lazy { initBonuses(allyBonus) }

    var iconRGB: List<Int>? = null
    override var uniques: ArrayList<String> = arrayListOf()
    override val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }

    private fun initBonuses(bonusMap: Map<String, List<String>>): Map<CityStateType, List<CityStateBonus>> {
        val objectMap = HashMap<CityStateType, List<CityStateBonus>>()
        for (pair in bonusMap) {
            objectMap[CityStateType.valueOf(pair.key)] = pair.value.map { CityStateBonus(it) }
        }
        return objectMap
    }

    fun getCityStateBonuses(cityStateType: CityStateType, relationshipLevel: RelationshipLevel): List<CityStateBonus> {
        return when (relationshipLevel) {
            RelationshipLevel.Ally   -> allyBonusObjects[cityStateType]!!
            RelationshipLevel.Friend -> friendBonusObjects[cityStateType]!!
            else -> emptyList()
        }
    }

    fun undefinedCityStateBonuses(): Boolean {
        return friendBonus.isEmpty() || allyBonus.isEmpty()
    }

    fun getStartingUnits(): List<String> {
        val startingUnits = mutableListOf<String>()
        repeat(startingSettlerCount) { startingUnits.add(startingSettlerUnit) }
        repeat(startingWorkerCount) { startingUnits.add(startingWorkerUnit) }
        repeat(startingMilitaryUnitCount) { startingUnits.add(startingMilitaryUnit) }
        return startingUnits
    }

    fun getColor(): Color {
        if (iconRGB == null) return Color.WHITE.cpy()
        return colorFromRGB(iconRGB!![0], iconRGB!![1], iconRGB!![2])
    }

    fun getHexColor() = "#" + getColor().toString().substring(0, 6)

    /** This is used for display purposes in templates */ 
    override fun toString() = name
}

class CityStateBonus(val text:String) {
    private val placeholder = text.getPlaceholderText()
    private val params = text.getPlaceholderParameters()

    val type = when (placeholder) {
        "Provides [] [] []" -> CityStateBonusTypes.AmountStatInCityFilter
        "Provides [] [] per turn" -> CityStateBonusTypes.AmountStat
        "Provides [] Happiness" -> CityStateBonusTypes.AmountHappiness
        "Provides military units every ≈[] turns" -> CityStateBonusTypes.MilitaryUnit
        "Provides a unique luxury" -> CityStateBonusTypes.UniqueLuxury
        else -> CityStateBonusTypes.valueOf("illegal bonus in Eras.json: $placeholder")  // Crash game
    }
    val amount = if (type == CityStateBonusTypes.UniqueLuxury) 0f else params[0].toFloat()
    val stat = when (type) {
        CityStateBonusTypes.AmountStatInCityFilter -> Stat.valueOf(params[1])
        CityStateBonusTypes.AmountStat -> Stat.valueOf(params[1])
        CityStateBonusTypes.AmountHappiness -> Stat.Happiness
        else -> null
    }
    val cityFilter = if (type == CityStateBonusTypes.AmountStatInCityFilter) params[2] else ""
}

enum class CityStateBonusTypes {
    AmountStatInCityFilter,
    AmountStat, // Should not be Happiness
    AmountHappiness,
    MilitaryUnit,
    UniqueLuxury,
}
