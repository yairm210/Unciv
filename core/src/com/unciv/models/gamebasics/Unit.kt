package com.unciv.models.gamebasics

import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.IConstruction
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.UnitType
import com.unciv.models.stats.INamed

class Unit : INamed, IConstruction, ICivilopedia {
    override val description: String
        get(){
            val sb = StringBuilder()
            sb.appendln(baseDescription)
            if(unbuildable) sb.appendln("Unbuildable")
            else sb.appendln("Cost: $cost")
            if(strength!=0)  sb.appendln("Strength: $strength")
            if(rangedStrength!=0)  sb.appendln("Ranged strength: $rangedStrength")
            return sb.toString()
        }

    override lateinit var name: String
    var baseDescription: String? = null
    var cost: Int = 0
    var hurryCostModifier: Int = 0
    var movement: Int = 0
    var strength:Int = 0
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


    override fun getProductionCost(adoptedPolicies: HashSet<String>): Int = cost

    override fun getGoldCost(adoptedPolicies: HashSet<String>): Int {
        return (Math.pow((30 * cost).toDouble(), 0.75) * (1 + hurryCostModifier / 100) / 10).toInt() * 10
    }

    override fun isBuildable(construction: CityConstructions): Boolean {
        return !unbuildable
    }

    override fun postBuildEvent(construction: CityConstructions) {
        construction.cityInfo.civInfo.placeUnitNearTile(construction.cityInfo.location, name)
    }

    override fun toString(): String = name
}  // for json parsing, we need to have a default constructor
