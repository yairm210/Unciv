package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.models.Counter
import com.unciv.models.ruleset.Milestone
import com.unciv.models.ruleset.unique.UniqueType

class VictoryManager {
    @Transient
    lateinit var civInfo: CivilizationInfo

    // There is very likely a typo in this name (currents), but as its saved in save files,
    // fixing it is non-trivial
    var currentsSpaceshipParts = Counter<String>()
    var hasEverWonDiplomaticVote = false

    fun clone(): VictoryManager {
        val toReturn = VictoryManager()
        toReturn.currentsSpaceshipParts.putAll(currentsSpaceshipParts)
        toReturn.hasEverWonDiplomaticVote = hasEverWonDiplomaticVote
        return toReturn
    }

    private fun calculateDiplomaticVotingResults(votesCast: HashMap<String, String>): Counter<String> {
        val results = Counter<String>()
        for (castVote in votesCast) {
            results.add(castVote.value, 1)
        }
        return results
    }

    private fun votesNeededForDiplomaticVictory(): Int {
        val civCount = civInfo.gameInfo.civilizations.count { !it.isDefeated() }

        // CvGame.cpp::DoUpdateDiploVictory() in the source code of the original
        return (
            if (civCount > 28) 0.35 * civCount
            else (67 - 1.1 * civCount) / 100 * civCount
        ).toInt()
    }

    fun hasEnoughVotesForDiplomaticVictory(): Boolean {
        val results = calculateDiplomaticVotingResults(civInfo.gameInfo.diplomaticVictoryVotesCast)
        val bestCiv = results.maxByOrNull { it.value } ?: return false

        // If we don't have the highest score, we have not won anyway
        if (bestCiv.key != civInfo.civName) return false

        // If we don't have enough votes, we haven't won
        if (bestCiv.value < votesNeededForDiplomaticVictory()) return false

        // If there's a tie, we haven't won either
        return (results.none { it != bestCiv && it.value == bestCiv.value })
    }

    fun getVictoryTypeAchieved(): String? {
        if (!civInfo.isMajorCiv()) return null
        for (victoryName in civInfo.gameInfo.gameParameters.victoryTypes
            .filter { it != Constants.neutralVictoryType && it in civInfo.gameInfo.ruleSet.victories}) {
            if (getNextMilestone(victoryName) == null)
                return victoryName
        }
        if (civInfo.hasUnique(UniqueType.TriggersVictory))
            return Constants.neutralVictoryType
        return null
    }

    fun getNextMilestone(victory: String): Milestone? {
        for (milestone in civInfo.gameInfo.ruleSet.victories[victory]!!.milestoneObjects) {
            if (!milestone.hasBeenCompletedBy(civInfo))
                return milestone
        }
        return null
    }

    fun amountMilestonesCompleted(victory: String): Int {
        var completed = 0
        for (milestone in civInfo.gameInfo.ruleSet.victories[victory]!!.milestoneObjects) {
            if (milestone.hasBeenCompletedBy(civInfo))
                ++completed
            else
                break
        }
        return completed
    }

    fun hasWon() = getVictoryTypeAchieved() != null
}
