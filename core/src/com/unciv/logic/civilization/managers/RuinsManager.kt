package com.unciv.logic.civilization.managers
// Why is this the only file in its own package?

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.RuinReward
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.random.Random

class RuinsManager : IsPartOfGameInfoSerialization {
    var lastChosenRewards: MutableList<String> = mutableListOf("", "")
    private fun rememberReward(reward: String) {
        lastChosenRewards[0] = lastChosenRewards[1]
        lastChosenRewards[1] = reward
    }

    @Transient
    lateinit var civInfo: Civilization
    @Transient
    lateinit var validRewards: List<RuinReward>

    fun clone(): RuinsManager {
        val toReturn = RuinsManager()
        toReturn.lastChosenRewards = lastChosenRewards
        return toReturn
    }

    fun setTransients(civInfo: Civilization) {
        this.civInfo = civInfo
        validRewards = civInfo.gameInfo.ruleset.ruinRewards.values.toList()
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
            if (possibleReward.hasUnique(UniqueType.HiddenAfterGreatProphet)
                && (civInfo.civConstructions.boughtItemsWithIncreasingPrice[civInfo.religionManager.getGreatProphetEquivalent()?.name] ?: 0) > 0
            ) continue

            if (possibleReward.getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals)
                        .any { !it.conditionalsApply(StateForConditionals(civInfo, unit=triggeringUnit, tile = triggeringUnit.getTile()) ) })
                continue

            var atLeastOneUniqueHadEffect = false
            for (unique in possibleReward.uniqueObjects) {
                atLeastOneUniqueHadEffect =
                    atLeastOneUniqueHadEffect
                    || UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo, tile = triggeringUnit.getTile(), notification = possibleReward.notification, triggerNotificationText = "from the ruins")
                    || UniqueTriggerActivation.triggerUnitwideUnique(unique, triggeringUnit, notification = possibleReward.notification)
            }
            if (atLeastOneUniqueHadEffect) {
                rememberReward(possibleReward.name)
                break
            }
        }
    }
}
