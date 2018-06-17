package com.unciv.models.gamebasics.unit

import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.IConstruction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.models.stats.INamed
import com.unciv.ui.utils.tr

class Unit : INamed, IConstruction, ICivilopedia {

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
    var requiredResource:String? = null
    var uniques:HashSet<String>?=null
    var obsoleteTech:String?=null
    var upgradesTo:String? = null
    var replaces:String?=null
    var uniqueTo:String?=null


    override val description: String
        get(){
            return getDescription(false)
        }

    fun getShortDescription(): String {
        val infoList= mutableListOf<String>()
        if(baseDescription!=null) infoList+=baseDescription!!
        if(strength!=0) infoList += "Strength".tr()+": $strength"
        if(rangedStrength!=0) infoList += "Ranged strength".tr()+": $rangedStrength"
        if(movement!=2) infoList+="Movement".tr()+": $movement"
        return infoList.joinToString()
    }

    fun getDescription(forPickerScreen:Boolean): String {
        val sb = StringBuilder()
        if(baseDescription!=null) sb.appendln(baseDescription)
        if(!forPickerScreen) {
            if (unbuildable) sb.appendln("Unbuildable")
            else sb.appendln("Cost: $cost")
            if(requiredResource!=null) sb.appendln("Required resource: $requiredResource")
            if(requiredTech!=null) sb.appendln("Required tech: $requiredTech")
        }
        if(strength!=0){
            sb.append("Strength".tr()+": $strength")
            if(rangedStrength!=0)  sb.append(",  "+"Ranged strength".tr()+": $rangedStrength")
            sb.appendln()
        }

        if(uniques!=null){
            for(unique in uniques!!)
                sb.appendln(unique)
        }
        sb.appendln("Movement".tr()+": $movement")
        return sb.toString()
    }

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

    fun isBuildable(civInfo:CivilizationInfo): Boolean {
        if (unbuildable) return false
        if (requiredTech!=null && !civInfo.tech.isResearched(requiredTech!!)) return false
        if (obsoleteTech!=null && civInfo.tech.isResearched(obsoleteTech!!)) return false
        if(uniqueTo!=null && uniqueTo!=civInfo.civName) return false
        if(GameBasics.Units.values.any { it.uniqueTo==civInfo.civName && it.replaces==name }) return false
        if (requiredResource!=null && !civInfo.getCivResources().keys.any { it.name == requiredResource }) return false
        return true
    }

    override fun isBuildable(construction: CityConstructions): Boolean {
        return isBuildable(construction.cityInfo.civInfo)
    }

    override fun postBuildEvent(construction: CityConstructions) {
        construction.cityInfo.civInfo.placeUnitNearTile(construction.cityInfo.location, name)
    }

    override fun toString(): String = name
}  // for json parsing, we need to have a default constructor
