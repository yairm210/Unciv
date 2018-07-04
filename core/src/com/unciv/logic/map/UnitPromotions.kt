package com.unciv.logic.map

import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.Promotion

class UnitPromotions{
    @Transient lateinit var unit:MapUnit
    var XP=0
    var promotions = HashSet<String>()
    var numberOfPromotions = 0 // The number of times this unit has been promoted - some promotions don't come from being promoted but from other things!

    fun xpForNextPromotion() = (numberOfPromotions+1)*10
    fun canBePromoted() = XP >= xpForNextPromotion()

    fun addPromotion(promotionName:String){
        XP -= xpForNextPromotion()
        promotions.add(promotionName)
        numberOfPromotions++
    }

    fun getAvailablePromotions(): List<Promotion> {
        return GameBasics.UnitPromotions.values
                .filter { unit.getBaseUnit().unitType.toString() in it.unitTypes && it.name !in promotions }
                .filter { it.prerequisites.isEmpty() || it.prerequisites.any { p->p in promotions } }
    }
}