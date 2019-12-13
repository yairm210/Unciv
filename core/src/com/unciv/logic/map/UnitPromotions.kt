package com.unciv.logic.map

import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.ruleset.unit.UnitType

class UnitPromotions{
    @Transient lateinit var unit:MapUnit
    var XP=0
    var promotions = HashSet<String>()
    // The number of times this unit has been promoted
    // some promotions don't come from being promoted but from other things,
    // like from being constructed in a specific city etc.
    var numberOfPromotions = 0

    fun xpForNextPromotion() = (numberOfPromotions+1)*10
    fun canBePromoted(): Boolean {
        if(unit.type==UnitType.Missile) return false
        return XP >= xpForNextPromotion()
    }

    fun addPromotion(promotionName: String, isFree: Boolean = false){
        if (!isFree) {
            XP -= xpForNextPromotion()
            numberOfPromotions++
        }

        if(promotionName=="Heal Instantly") unit.healBy(50)
        else promotions.add(promotionName)

        unit.updateUniques()

        // Since some units get promotions upon construction, they will get the addPromotion from the unit.postBuildEvent
        // upon creation, BEFORE they are assigned to a tile, so the updateVisibleTiles() would crash.
        // So, if the addPromotion was triggered from there, simply don't update
        unit.updateVisibleTiles()  // some promotions/uniques give the unit bonus sight
    }

    fun getAvailablePromotions(): List<Promotion> {
        return unit.civInfo.gameInfo.ruleSet.UnitPromotions.values
                .filter { unit.type.toString() in it.unitTypes && it.name !in promotions }
                .filter { it.prerequisites.isEmpty() || it.prerequisites.any { p->p in promotions } }
    }

    fun clone(): UnitPromotions {
        val toReturn = UnitPromotions()
        toReturn.XP=XP
        toReturn.promotions.addAll(promotions)
        toReturn.numberOfPromotions=numberOfPromotions
        return toReturn
    }

    fun totalXpProduced(): Int {
        var sum = XP
        for(i in 1..numberOfPromotions) sum += 10*i
        return sum
    }

}