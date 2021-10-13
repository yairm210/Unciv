package com.unciv.logic.civilization.RuinsManager

import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.models.ruleset.RuinReward
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.random.Random

class RuinsManager {
    var lastChosenRewards: MutableList<String> = mutableListOf("", "")
    private fun rememberReward(reward: String) {
        lastChosenRewards[0] = lastChosenRewards[1]
        lastChosenRewards[1] = reward
    }
    
    @Transient
    lateinit var civInfo: CivilizationInfo
    @Transient
    lateinit var validRewards: List<RuinReward> 
    
    fun clone(): RuinsManager {
        val toReturn = RuinsManager()
        toReturn.lastChosenRewards = lastChosenRewards
        return toReturn
    }
    
    fun setTransients(civInfo: CivilizationInfo) {
        this.civInfo = civInfo
        validRewards = civInfo.gameInfo.ruleSet.ruinRewards.values.toList()
    }
    
    fun selectNextRuinsReward(triggeringUnit: MapUnit) {
        val tileBasedRandom = Random(triggeringUnit.getTile().position.toString().hashCode())
        val availableRewards = validRewards.filter { it.name !in lastChosenRewards }
        
        // This might be a dirty way to do this, but it works.
        // For each possible reward, this creates a list with reward.weight amount of copies of this reward
        // These lists are then combined into a single list, and the result is shuffled.
        val possibleRewards = availableRewards.flatMap { reward -> List(reward.weight) { reward } }.shuffled(tileBasedRandom)
        
        for (possibleReward in possibleRewards) {
            if (civInfo.gameInfo.difficulty in possibleReward.excludedDifficulties) continue
            if (possibleReward.hasUnique(UniqueType.HiddenWithoutReligion) && !civInfo.gameInfo.isReligionEnabled()) continue
            if ("Hidden after generating a Great Prophet" in possibleReward.uniques 
                && civInfo.civConstructions.boughtItemsWithIncreasingPrice[civInfo.religionManager.getGreatProphetEquivalent()] ?: 0 > 0
            ) continue
            if (possibleReward.uniqueObjects.any { unique ->
                unique.placeholderText == "Only available after [] turns" 
                && unique.params[0].toInt() < civInfo.gameInfo.turns
            }) continue
            
            var atLeastOneUniqueHadEffect = false
            for (unique in possibleReward.uniqueObjects) {
                atLeastOneUniqueHadEffect = 
                    atLeastOneUniqueHadEffect 
                    || UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo, tile = triggeringUnit.getTile(), notification = possibleReward.notification)
                    || UniqueTriggerActivation.triggerUnitwideUnique(unique, triggeringUnit, notification = possibleReward.notification)
            }
            if (atLeastOneUniqueHadEffect) {
                rememberReward(possibleReward.name)
                break
            }
        }
    }
}
