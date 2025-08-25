package com.unciv.logic.civilization.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.RuinReward
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly
import kotlin.random.Random

class RuinsManager(
    private var lastChosenRewards: MutableList<String> = mutableListOf("", "")
) : IsPartOfGameInfoSerialization {

    @Transient
    lateinit var civInfo: Civilization
    @Transient
    lateinit var validRewards: Collection<RuinReward>

    fun clone() = RuinsManager(ArrayList(lastChosenRewards))  // needs to deep-clone (the List, not the Strings) so undo works

    fun setTransients(civInfo: Civilization) {
        this.civInfo = civInfo
        validRewards = civInfo.gameInfo.ruleset.ruinRewards.values
    }

    private fun rememberReward(reward: String) {
        lastChosenRewards[0] = lastChosenRewards[1]
        lastChosenRewards[1] = reward
    }

    @Readonly
    private fun getShuffledPossibleRewards(triggeringUnit: MapUnit): Iterable<RuinReward> {
        val candidates =
            validRewards.asSequence().filter { isPossibleReward(it, triggeringUnit) }
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

    @Readonly
    private fun isPossibleReward(ruinReward: RuinReward, unit: MapUnit): Boolean {
        if (ruinReward.name in lastChosenRewards) return false
        if (ruinReward.isUnavailableBySettings(civInfo.gameInfo)) return false
        val gameContext = GameContext(civInfo, unit = unit, tile = unit.getTile())
        if (ruinReward.hasUnique(UniqueType.Unavailable, gameContext)) return false
        if (ruinReward.getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals)
                .any { !it.conditionalsApply(gameContext) }) return false
        return true
    }

    fun selectNextRuinsReward(triggeringUnit: MapUnit) {
        for (possibleReward in getShuffledPossibleRewards(triggeringUnit)) {
            var atLeastOneUniqueHadEffect = false
            for (unique in possibleReward.uniqueObjects) {
                val uniqueTriggered =
                    unique.conditionalsApply(triggeringUnit.cache.state) && 
                        UniqueTriggerActivation.triggerUnique(unique, triggeringUnit, notification = possibleReward.notification, triggerNotificationText = "from the ruins")
                atLeastOneUniqueHadEffect = atLeastOneUniqueHadEffect || uniqueTriggered
            }
            if (atLeastOneUniqueHadEffect) {
                rememberReward(possibleReward.name)
                break
            }
        }
    }
}
