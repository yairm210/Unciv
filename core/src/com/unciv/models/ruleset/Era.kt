package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.stats.INamed
import com.unciv.ui.utils.colorFromRGB

class Era : RulesetObject(), IHasUniques {
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
    val friendBonusObjects: Map<CityStateType, List<Unique>> by lazy { initBonuses(friendBonus) }
    val allyBonusObjects: Map<CityStateType, List<Unique>> by lazy { initBonuses(allyBonus) }

    var iconRGB: List<Int>? = null
    override fun getUniqueTarget() = UniqueTarget.Era
    override fun makeLink() = "" // No own category on Civilopedia screen

    private fun initBonuses(bonusMap: Map<String, List<String>>): Map<CityStateType, List<Unique>> {
        val objectMap = HashMap<CityStateType, List<Unique>>()
        for ((cityStateType, bonusList) in bonusMap) {
            objectMap[CityStateType.valueOf(cityStateType)] = bonusList.map { Unique(it, UniqueTarget.CityState) }
        }
        return objectMap
    }

    fun getCityStateBonuses(cityStateType: CityStateType, relationshipLevel: RelationshipLevel): List<Unique> {
        return when (relationshipLevel) {
            RelationshipLevel.Ally   -> allyBonusObjects[cityStateType]   ?: emptyList()
            RelationshipLevel.Friend -> friendBonusObjects[cityStateType] ?: emptyList()
            else -> emptyList()
        }
    }

    /** Since 3.19.5 we have a warning for mods without bonuses, eventually we should treat such mods as providing no bonus */
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
}
