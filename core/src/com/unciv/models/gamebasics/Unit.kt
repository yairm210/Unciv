package com.unciv.models.gamebasics

import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.IConstruction
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.UnitType
import com.unciv.models.stats.INamed

class Unit : INamed, IConstruction {
    override lateinit var name: String
    var description: String? = null
    var cost: Int = 0
    var hurryCostModifier: Int = 0
    var movement: Int = 0
    var strength:Int = 1
    var rangedStrength:Int = 0
    lateinit var unitType: UnitType
    internal var unbuildable: Boolean = false // for special units like great people
    var requiredTech:String? = null

    fun getMapUnit(): MapUnit {
        val unit = MapUnit()
        unit.name = name
        unit.maxMovement = movement
        unit.currentMovement = movement.toFloat()
        return unit
    }


    override fun getProductionCost(adoptedPolicies: List<String>): Int {
        return cost
    }

    override fun getGoldCost(adoptedPolicies: List<String>): Int {
        return (Math.pow((30 * cost).toDouble(), 0.75) * (1 + hurryCostModifier / 100) / 10).toInt() * 10
    }

    override fun isBuildable(construction: CityConstructions): Boolean {
        return !unbuildable
    }

    override fun postBuildEvent(construction: CityConstructions) {
        construction.cityInfo.civInfo.placeUnitNearTile(construction.cityInfo.cityLocation, name)
    }
}  // for json parsing, we need to have a default constructor
