package com.unciv.logic.civilization.managers

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Counter
import com.unciv.models.ruleset.Milestone
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import yairm210.purity.annotations.Readonly

class VictoryManager : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var civInfo: Civilization

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

    @Readonly
    private fun calculateDiplomaticVotingResults(votesCast: HashMap<String, String?>): Counter<String> {
        val results = Counter<String>()
        // UN Owner gets 2 votes in G&K
        val (_, civOwningUN) = getUNBuildingAndOwnerNames()
        for ((voter, votedFor) in votesCast) {
            if (votedFor == null) continue  // null means Abstained
            results.add(votedFor, if (voter == civOwningUN) 2 else 1)
        }
        return results
    }

    @Readonly
    private fun getVotingCivs() = civInfo.gameInfo.civilizations.asSequence()
        .filterNot { it.isBarbarian || it.isSpectator() || it.isDefeated() }

    /** Finds the Building and Owner of the United Nations (or whatever the Mod called it)
     *  - if it's built at all and only if the owner is alive
     *  @return `first`: Building name, `second`: Owner civ name; both null if not found
     */
    @Readonly
    fun getUNBuildingAndOwnerNames(): Pair<String?, String?> = getVotingCivs()
            .flatMap { civ -> civ.cities.asSequence()
                .flatMap { it.cityConstructions.getBuiltBuildings() }
                .filter { it.hasUnique(UniqueType.OneTimeTriggerVoting, GameContext.IgnoreConditionals) }
                .map { it.name to civ.civName }
            }.firstOrNull() ?: (null to null)

    @Readonly
    private fun votesNeededForDiplomaticVictory(): Int {
        // The original counts "teams ever alive", which excludes Observer and Barbarians.
        // The "ever alive" part sounds unfair - could make a Vote unwinnable?

        // So this is a slightly arbitrary decision: Apply original formula to count of available votes
        // - including catering for the possibility we're voting without a UN thanks to razing -
        val (_, civOwningUN) = getUNBuildingAndOwnerNames()
        val voteCount = getVotingCivs().count() + (if (civOwningUN != null) 1 else 0)

        // CvGame.cpp::DoUpdateDiploVictory() in the source code of the original - same integer math and rounding!
        // To verify run `(1..30).map { voteCount -> voteCount to voteCount * (67 - (1.1 * voteCount).toInt()) / 100 + 1 }`
        // ... and compare with: "4 votes needed to win in a game with 5 players, 7 with 13 and 11 with 28"
        if (voteCount > 28) return voteCount * 35 / 100
        return voteCount * (67 - (1.1 * voteCount).toInt()) / 100 + 1
    }

    @Readonly
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

    data class DiplomaticVictoryVoteBreakdown(val results: Counter<String>, val winnerText: String)
    @Readonly
    fun getDiplomaticVictoryVoteBreakdown(): DiplomaticVictoryVoteBreakdown {
        val results = calculateDiplomaticVotingResults(civInfo.gameInfo.diplomaticVictoryVotesCast)
        val (voteCount, winnerList) = results.asSequence()
            .groupBy({ it.value }, { it.key }).asSequence()
            .sortedByDescending { it.key }  // key is vote count here
            .firstOrNull()
            ?: return DiplomaticVictoryVoteBreakdown(results, "No valid votes were cast.")

        val lines = arrayListOf<String>()
        val minVotes = votesNeededForDiplomaticVictory()
        if (voteCount < minVotes)
            lines += "Minimum votes for electing a world leader: [$minVotes]"
        if (winnerList.size > 1)
            lines += "Tied in first position: [${winnerList.joinToString { it.tr() }}]" // Yes with icons
        val winnerCiv = civInfo.gameInfo.getCivilization(winnerList.first())
        lines += when {
            lines.isNotEmpty() -> "No world leader was elected."
            winnerCiv == civInfo -> "You have been elected world leader!"
            else -> "${winnerCiv.nation.getLeaderDisplayName()} has been elected world leader!"
        }
        return DiplomaticVictoryVoteBreakdown(results, lines.joinToString("\n") { "{$it}" })
    }

    @Readonly
    fun getVictoryTypeAchieved(): String? {
        if (!civInfo.isMajorCiv()) return null
        val enabledVictories = civInfo.gameInfo.gameParameters.victoryTypes
        val victory = civInfo.gameInfo.ruleset.victories
                .filter { it.key != Constants.neutralVictoryType && it.key in enabledVictories }
                .map { it.value }
                .firstOrNull { getNextMilestone(it) == null }
        if (victory != null) return victory.name
        if (civInfo.hasUnique(UniqueType.TriggersVictory))
            return Constants.neutralVictoryType
        return null
    }
    
    @Readonly
    fun getNextMilestone(victory: Victory): Milestone? {
        for (milestone in victory.milestoneObjects) {
            if (!milestone.hasBeenCompletedBy(civInfo))
                return milestone
        }
        return null
    }

    @Readonly
    fun amountMilestonesCompleted(victory: Victory): Int {
        var completed = 0
        for (milestone in victory.milestoneObjects) {
            if (milestone.hasBeenCompletedBy(civInfo))
                ++completed
            else
                break
        }
        return completed
    }

    @Readonly fun hasWon() = getVictoryTypeAchieved() != null
}
