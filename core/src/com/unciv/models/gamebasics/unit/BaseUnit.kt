package com.unciv.models.gamebasics.unit

import com.unciv.Constants
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.IConstruction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.models.gamebasics.Translations
import com.unciv.models.gamebasics.tr
import com.unciv.models.stats.INamed

// This is BaseUnit because Unit is already a base Kotlin class and to avoid mixing the two up

/** This is the basic info of the units, as specified in Units.json,
 in contrast to MapUnit, which is a specific unit of a certain type that appears on the map */
class BaseUnit : INamed, IConstruction, ICivilopedia {

    override lateinit var name: String
    var cost: Int = 0
    var hurryCostModifier: Int = 0
    var movement: Int = 0
    var strength:Int = 0
    var rangedStrength:Int = 0
    var range:Int = 2
    var interceptRange = 0
    lateinit var unitType: UnitType
    internal var unbuildable: Boolean = false // for special units like great people
    var requiredTech:String? = null
    var requiredResource:String? = null
    var uniques =HashSet<String>()
    var promotions =HashSet<String>()
    var obsoleteTech:String?=null
    var upgradesTo:String? = null
    var replaces:String?=null
    var uniqueTo:String?=null
    var attackSound:String?=null

    override val description: String
        get(){
            return getDescription(false)
        }



    fun getShortDescription(): String {
        val infoList= mutableListOf<String>()
        for(unique in uniques)
            infoList+=Translations.translateBonusOrPenalty(unique)
        for(promotion in promotions)
            infoList += promotion.tr()
        if(strength!=0) infoList += "{Strength}: $strength".tr()
        if(rangedStrength!=0) infoList += "{Ranged strength}: $rangedStrength".tr()
        if(movement!=2) infoList+="{Movement}: $movement".tr()
        return infoList.joinToString()
    }

    fun getDescription(forPickerScreen:Boolean): String {
        val sb = StringBuilder()
        if(requiredResource!=null) sb.appendln("{Requires} {$requiredResource}".tr())
        if(!forPickerScreen) {
            if(uniqueTo!=null) sb.appendln("Unique to [$uniqueTo], replaces [$replaces]".tr())
            if (unbuildable) sb.appendln("Unbuildable".tr())
            else sb.appendln("{Cost}: $cost".tr())
            if(requiredTech!=null) sb.appendln("Required tech: [$requiredTech]".tr())
            if(upgradesTo!=null) sb.appendln("Upgrades to [$upgradesTo]".tr())
            if(obsoleteTech!=null) sb.appendln("Obsolete with [$obsoleteTech]".tr())
        }
        if(strength!=0){
            sb.append("{Strength}: $strength".tr())
            if(rangedStrength!=0)  sb.append(", {Ranged strength}: $rangedStrength".tr())
            if(rangedStrength!=0)  sb.append(", {Range}: $range".tr())
            sb.appendln()
        }

        for(unique in uniques)
            sb.appendln(Translations.translateBonusOrPenalty(unique))

        for(promotion in promotions)
            sb.appendln(promotion.tr())

        sb.appendln("{Movement}: $movement".tr())
        return sb.toString()
    }

    fun getMapUnit(): MapUnit {
        val unit = MapUnit()
        unit.name = name

        unit.setTransients() // must be after setting name because it sets the baseUnit according to the name

        return unit
    }

    override fun canBePurchased(): Boolean {
        return true
    }

    override fun getProductionCost(civInfo: CivilizationInfo): Int {
        var productionCost = cost.toFloat()
        if (civInfo.isPlayerCivilization())
            productionCost *= civInfo.getDifficulty().unitCostModifier
        else
            productionCost *= civInfo.gameInfo.getDifficulty().aiUnitCostModifier
        productionCost *= civInfo.gameInfo.gameParameters.gameSpeed.getModifier()
        return productionCost.toInt()
    }

    fun getBaseGoldCost() = Math.pow((30 * cost).toDouble(), 0.75) * (1 + hurryCostModifier / 100)

    override fun getGoldCost(civInfo: CivilizationInfo): Int {
        var cost = getBaseGoldCost()
        if (civInfo.policies.adoptedPolicies.contains("Mercantilism")) cost *= 0.75
        if (civInfo.policies.adoptedPolicies.contains("Militarism")) cost *= 0.66f
        if (civInfo.containsBuildingUnique("-15% to purchasing items in cities")) cost *= 0.85
        return (cost / 10).toInt() * 10 // rounded down o nearest ten
    }

    fun getDisbandGold() = getBaseGoldCost().toInt()/20

    override fun shouldBeDisplayed(construction: CityConstructions): Boolean {
        val rejectionReason = getRejectionReason(construction)
        return rejectionReason=="" || rejectionReason.startsWith("Requires")
    }

    fun getRejectionReason(construction: CityConstructions): String {
        val civRejectionReason = getRejectionReason(construction.cityInfo.civInfo)
        if(civRejectionReason!="") return civRejectionReason
        if(unitType.isWaterUnit() && !construction.cityInfo.getCenterTile().isCoastalTile())
            return "Can't build water units by the coast"
        return ""
    }

    fun getRejectionReason(civInfo: CivilizationInfo): String {
        if (unbuildable) return "Unbuildable"
        if (requiredTech!=null && !civInfo.tech.isResearched(requiredTech!!)) return "$requiredTech not researched"
        if (obsoleteTech!=null && civInfo.tech.isResearched(obsoleteTech!!)) return "Obsolete by $obsoleteTech"
        if (uniqueTo!=null && uniqueTo!=civInfo.civName) return "Unique to $uniqueTo"
        if (GameBasics.Units.values.any { it.uniqueTo==civInfo.civName && it.replaces==name }) return "Our unique unit replaces this"
        if (uniques.contains("Requires Manhattan Project") && !civInfo.containsBuildingUnique("Enables nuclear weapon"))
            return "Requires Manhattan Project"
        if (requiredResource!=null && !civInfo.hasResource(requiredResource!!)) return "Requires [$requiredResource]"
        if (name == Constants.settler && civInfo.isCityState()) return "No settler for city-states"
        if (name == Constants.settler && civInfo.isPlayerOneCityChallenger()) return "No settler for players in One City Challenge"
        return ""
    }

    fun isBuildable(civInfo: CivilizationInfo) = getRejectionReason(civInfo)==""

    override fun isBuildable(construction: CityConstructions): Boolean {
        return getRejectionReason(construction) == ""
    }

    override fun postBuildEvent(construction: CityConstructions) {
        val unit = construction.cityInfo.civInfo.placeUnitNearTile(construction.cityInfo.location, name)
        if(unit==null) return // couldn't place the unit, so there's actually no unit =(
        unit.promotions.XP += construction.getBuiltBuildings().sumBy { it.xpForNewUnits }
        if(construction.cityInfo.civInfo.policies.isAdopted("Total War"))
            unit.promotions.XP += 15

        if(unit.type in listOf(UnitType.Melee,UnitType.Mounted,UnitType.Armor)
            && construction.cityInfo.containsBuildingUnique("All newly-trained melee, mounted, and armored units in this city receive the Drill I promotion"))
            unit.promotions.addPromotion("Drill I", isFree = true)
    }

    fun getDirectUpgradeUnit(civInfo: CivilizationInfo):BaseUnit{
        return civInfo.getEquivalentUnit(upgradesTo!!)
    }

    override fun toString(): String = name
}
