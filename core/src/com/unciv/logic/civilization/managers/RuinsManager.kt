package com.unciv.logic.civilization.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.RuinReward
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.random.Random

class RuinsManager(
    private var lastChosenRewards: MutableList<String> = mutableListOf("", "")
) : IsPartOfGameInfoSerialization {

    @Transient
    lateinit var civInfo: Civilization
    @Transient
    lateinit var validRewards: List<RuinReward>

    fun clone() = RuinsManager(ArrayList(lastChosenRewards))  // needs to deep-clone (the List, not the Strings) so undo works

    fun setTransients(civInfo: Civilization) {
        this.civInfo = civInfo
        validRewards = civInfo.gameInfo.ruleset.ruinRewards.values.toList()
    }

    private fun rememberReward(reward: String) {
        lastChosenRewards[0] = lastChosenRewards[1]
        lastChosenRewards[1] = reward
    }

    private fun getShuffledPossibleRewards(triggeringUnit: MapUnit): Iterable<RuinReward> {
        val stateForOnlyAvailableWhen = StateForConditionals(civInfo, unit = triggeringUnit, tile = triggeringUnit.getTile())
        val candidates =
            validRewards.asSequence()
            // Filter out what shouldn't be considered right now, before the random choice
            .filterNot { possibleReward ->
                possibleReward.name in lastChosenRewards
                    || civInfo.gameInfo.difficulty in possibleReward.excludedDifficulties
                    || possibleReward.hasUnique(UniqueType.HiddenWithoutReligion) && !civInfo.gameInfo.isReligionEnabled()
                    || possibleReward.hasUnique(UniqueType.HiddenAfterGreatProphet) && civInfo.religionManager.greatProphetsEarned() > 0
                    || possibleReward.getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals)
                    .any { !it.conditionalsApply(stateForOnlyAvailableWhen) }
            }
            // This might be a dirty way to do this, but it works (we do have randomWeighted in CollectionExtensions, but below we
            // need to choose another when the first choice's TriggerActivations report failure, and that's simpler this way)
            // For each possible reward, this feeds (reward.weight) copies of this reward to the overall Sequence to implement 'weight'.
            .flatMap { reward -> generateSequence { reward }.take(reward.weight) }
            // Convert to List since Sequence.shuffled would do one anyway, Mutable so shuffle doesn't need to pull a copy
            .toMutableList()
        // The resulting List now gets shuffled, using a tile-based random to thwart save-scumming.
        // Note both Sequence.shuffled and Iterable.shuffled (with a 'd') always pull an extra copy of a MutableList internally, even if you feed them one.
        candidates.shuffle(Random(triggeringUnit.getTile().position.hashCode()))
        return candidates
    }

    fun selectNextRuinsReward(triggeringUnit: MapUnit) {
        for (possibleReward in getShuffledPossibleRewards(triggeringUnit)) {
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
