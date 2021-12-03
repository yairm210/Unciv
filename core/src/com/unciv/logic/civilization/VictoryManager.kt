package com.unciv.logic.civilization

import com.unciv.models.Counter
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.unique.UniqueType

class VictoryManager {
    @Transient
    lateinit var civInfo: CivilizationInfo

    var requiredSpaceshipParts = Counter<String>()
    var currentsSpaceshipParts = Counter<String>()
    var hasWonDiplomaticVictory = false

    init {
        requiredSpaceshipParts.add("SS Booster", 3)
        requiredSpaceshipParts.add("SS Cockpit", 1)
        requiredSpaceshipParts.add("SS Engine", 1)
        requiredSpaceshipParts.add("SS Stasis Chamber", 1)
    }

    fun clone(): VictoryManager {
        val toReturn = VictoryManager()
        toReturn.currentsSpaceshipParts.putAll(currentsSpaceshipParts)
        return toReturn
    }

    fun unconstructedSpaceshipParts(): Counter<String> {
        val counter = requiredSpaceshipParts.clone()
        counter.remove(currentsSpaceshipParts)
        return counter
    }

    fun spaceshipPartsRemaining() = requiredSpaceshipParts.values.sum() - currentsSpaceshipParts.values.sum()

    fun calculateDiplomaticVotingResults(votesCast: HashMap<String, String>): Counter<String> {
        val results = Counter<String>()
        for (castVote in votesCast) {
            results.add(castVote.value, 1)
        }
        return results
    }
    
    fun votesNeededForDiplomaticVictory(): Int {
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
        
        if (bestCiv.value < votesNeededForDiplomaticVictory()) return false
        
        // If there's a tie, we haven't won either
        return (results.none { it != bestCiv && it.value == bestCiv.value }) 
    }
    
    private fun hasVictoryType(victoryType: VictoryType) = civInfo.gameInfo.gameParameters.victoryTypes.contains(victoryType)

    fun hasWonScientificVictory() = hasVictoryType(VictoryType.Scientific) && spaceshipPartsRemaining() == 0

    fun hasWonCulturalVictory() = hasVictoryType(VictoryType.Cultural)
            && civInfo.hasUnique(UniqueType.TriggersCulturalVictory)

    fun hasWonDominationVictory(): Boolean {
        return hasVictoryType(VictoryType.Domination)
                && civInfo.gameInfo.civilizations.all { it == civInfo || it.isDefeated() || !it.isMajorCiv() }
    }
    
    fun hasWonDiplomaticVictory() = hasVictoryType(VictoryType.Diplomatic) 
            && civInfo.shouldCheckForDiplomaticVictory()
            && hasEnoughVotesForDiplomaticVictory()

    fun hasWonVictoryType(): VictoryType? {
        if (!civInfo.isMajorCiv()) return null
        if (hasWonDominationVictory()) return VictoryType.Domination
        if (hasWonScientificVictory()) return VictoryType.Scientific
        if (hasWonCulturalVictory()) return VictoryType.Cultural
        if (hasWonDiplomaticVictory()) return VictoryType.Diplomatic
        if (civInfo.hasUnique(UniqueType.TriggersVictory)) return VictoryType.Neutral
        return null
    }

    fun hasWon() = hasWonVictoryType() != null
}