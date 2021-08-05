package com.unciv.logic.map

import com.unciv.models.ruleset.UniqueTriggerActivation
import com.unciv.models.ruleset.unit.Promotion

class UnitPromotions{
    @Transient lateinit var unit:MapUnit
    @Suppress("PropertyName")
    var XP = 0
    var promotions = HashSet<String>()
    // The number of times this unit has been promoted
    // some promotions don't come from being promoted but from other things,
    // like from being constructed in a specific city etc.
    var numberOfPromotions = 0

    fun xpForNextPromotion() = (numberOfPromotions+1)*10
    fun canBePromoted(): Boolean {
        if (XP < xpForNextPromotion()) return false
        if (getAvailablePromotions().isEmpty()) return false
        return true
    }

    fun addPromotion(promotionName: String, isFree: Boolean = false){
        if (!isFree) {
            XP -= xpForNextPromotion()
            numberOfPromotions++
        }

        val promotion = unit.civInfo.gameInfo.ruleSet.unitPromotions[promotionName]!!
        doDirectPromotionEffects(promotion)
        
        // This usage of a promotion name as its identifier is deprecated since 3.15.6
        if (promotionName != "Heal Instantly" && promotion.uniqueObjects.none { it.placeholderText == "Doing so will consume this opportunity to choose a Promotion" }) 
            promotions.add(promotionName)

        unit.updateUniques()

        // Since some units get promotions upon construction, they will get the addPromotion from the unit.postBuildEvent
        // upon creation, BEFORE they are assigned to a tile, so the updateVisibleTiles() would crash.
        // So, if the addPromotion was triggered from there, simply don't update
        unit.updateVisibleTiles()  // some promotions/uniques give the unit bonus sight
    }
    
    fun doDirectPromotionEffects(promotion: Promotion) {
        for (unique in promotion.uniqueObjects) {
            UniqueTriggerActivation.triggerUnitwideUnique(unique, unit)       
        }
    }

    fun getAvailablePromotions(): List<Promotion> {
        return unit.civInfo.gameInfo.ruleSet.unitPromotions.values
                .filter { unit.type.toString() in it.unitTypes && it.name !in promotions }
                .filter { it.prerequisites.isEmpty() || it.prerequisites.any { p->p in promotions } }
    }

    fun clone(): UnitPromotions {
        val toReturn = UnitPromotions()
        toReturn.XP = XP
        toReturn.promotions.addAll(promotions)
        toReturn.numberOfPromotions = numberOfPromotions
        return toReturn
    }

    fun totalXpProduced(): Int {
        var sum = XP
        for(i in 1..numberOfPromotions) sum += 10*i
        return sum
    }

}