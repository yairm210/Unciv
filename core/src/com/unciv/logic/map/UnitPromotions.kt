package com.unciv.logic.map

class UnitPromotions{
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
}