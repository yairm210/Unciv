package com.unciv.logic.automation.civilization

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.ui.screens.victoryscreen.RankingType

/**
 * Contains the logic for evaluating how we want to declare war on another civ.
 */
object DeclareWarPlanEvaluator {

    /**
     * How much motivation [civInfo] has to do a team war with [teamCiv] against [target].
     *
     * This style of declaring war favors fighting stronger civilizations.
     * @return The movtivation of the plan. If it is > 0 then we can declare the war.
     */
    fun evaluateTeamWarPlan(civInfo: Civilization, target: Civilization, teamCiv: Civilization, givenMotivation: Int?): Int {
        if (civInfo.getDiplomacyManager(teamCiv).isRelationshipLevelLT(RelationshipLevel.Neutral)) return -1000

        var motivation = givenMotivation
            ?: MotivationToAttackAutomation.hasAtLeastMotivationToAttack(civInfo, target, 0)

        if (civInfo.getDiplomacyManager(teamCiv).isRelationshipLevelEQ(RelationshipLevel.Neutral)) motivation -= 5
        // Make sure that they can actually help us with the target
        if (!teamCiv.threatManager.getNeighboringCivilizations().contains(target)) {
            motivation -= 40
        }

        val civForce = civInfo.getStatForRanking(RankingType.Force)
        val targetForce = target.getStatForRanking(RankingType.Force)
        val teamCivForce = (teamCiv.getStatForRanking(RankingType.Force) - 0.8f * teamCiv.threatManager.getCombinedForceOfWarringCivs()).coerceAtLeast(100f)

        // A higher motivation means that we can be riskier
        val multiplier = when {
            motivation < 5 -> 1.2f
            motivation < 10 -> 1.1f
            motivation < 20 -> 1f
            else -> 0.8f
        }
        if (civForce + teamCivForce < targetForce * multiplier) {
            // We are weaker then them even with our combined forces
            // If they have twice our combined force we will have -30 motivation
            motivation -= (30 * ((targetForce * multiplier) / (teamCivForce + civForce) - 1)).toInt()
        } else if (civForce + teamCivForce > targetForce * 2) {
            // Why gang up on such a weaker enemy when we can declare war ourselves?
            // If our combined force is twice their force we will have -20 motivation
            motivation -= (20 * ((civForce + teamCivForce) / targetForce * 2) - 1).toInt()
        }

        val civScore = civInfo.getStatForRanking(RankingType.Score)
        val teamCivScore = teamCiv.getStatForRanking(RankingType.Score)
        val targetCivScore = target.getStatForRanking(RankingType.Score)

        if (teamCivScore > civScore * 1.4f && teamCivScore >= targetCivScore) {
            // If teamCiv has more score than us and the target they are likely in a good position already
            motivation -= (20 * ((teamCivScore / (civScore * 1.4f)) - 1)).toInt()
        }
        return motivation - 20
    }

    /**
     * How much motivation [civInfo] has to join [civToJoin] in their war against [target].
     *
     * Favors protecting allies.
     * @return The movtivation of the plan. If it is > 0 then we can declare the war.
     */
    fun evaluateJoinWarPlan(civInfo: Civilization, target: Civilization, civToJoin: Civilization, givenMotivation: Int?): Int {
        if (civInfo.getDiplomacyManager(civToJoin).isRelationshipLevelLE(RelationshipLevel.Favorable)) return -1000

        var motivation = givenMotivation
            ?: MotivationToAttackAutomation.hasAtLeastMotivationToAttack(civInfo, target, 0)
        // We need to be able to trust the thirdCiv at least somewhat
        val thirdCivDiplo = civInfo.getDiplomacyManager(civToJoin)
        if (thirdCivDiplo.diplomaticStatus != DiplomaticStatus.DefensivePact &&
            thirdCivDiplo.opinionOfOtherCiv() + motivation * 2 < 80) {
            motivation -= 80 - (thirdCivDiplo.opinionOfOtherCiv() + motivation * 2).toInt()
        }
        if (!civToJoin.threatManager.getNeighboringCivilizations().contains(target)) {
            motivation -= 20
        }

        val targetForce = target.getStatForRanking(RankingType.Force) - 0.8f * target.getCivsAtWarWith().sumOf { it.getStatForRanking(RankingType.Force) }.coerceAtLeast(100)
        val civForce = civInfo.getStatForRanking(RankingType.Force)

        // They need to be at least half the targets size, and we need to be stronger than the target together
        val civToJoinForce = (civToJoin.getStatForRanking(RankingType.Force) - 0.8f * civToJoin.getCivsAtWarWith().sumOf { it.getStatForRanking(RankingType.Force) }).coerceAtLeast(100f)
        if (civToJoinForce < targetForce / 2) {
            // Make sure that there is no wrap around
            motivation -= (10 * (targetForce / civToJoinForce)).toInt().coerceIn(-1000, 1000)
        }

        // A higher motivation means that we can be riskier
        val multiplier = when {
            motivation < 10 -> 1.4f
            motivation < 15 -> 1.3f
            motivation < 20 -> 1.2f
            motivation < 25 -> 1f
            else -> 0.8f
        }
        if (civToJoinForce + civForce < targetForce * multiplier) {
            motivation -= (20 * (targetForce * multiplier) / (civToJoinForce + civForce)).toInt().coerceIn(-1000, 1000)
        }

        return motivation - 15
    }

    /**
     * How much motivation [civInfo] has for [civToJoin] to join them in their war against [target].
     *
     * @return The movtivation of the plan. If it is > 0 then we can declare the war.
     */
    fun evaluateJoinOurWarPlan(civInfo: Civilization, target: Civilization, civToJoin: Civilization, givenMotivation: Int?): Int {
        if (civInfo.getDiplomacyManager(civToJoin).isRelationshipLevelLT(RelationshipLevel.Favorable)) return -1000
        var motivation = givenMotivation ?: 0
        if (!civToJoin.threatManager.getNeighboringCivilizations().contains(target)) {
            motivation -= 50
        }

        val targetForce = target.getStatForRanking(RankingType.Force)
        val civForce = civInfo.getStatForRanking(RankingType.Force)

        // They need to be at least half the targets size
        val thirdCivForce = (civToJoin.getStatForRanking(RankingType.Force) - 0.8f * civToJoin.getCivsAtWarWith().sumOf { it.getStatForRanking(RankingType.Force) }).coerceAtLeast(100f)
        motivation += (20 * thirdCivForce / targetForce.toFloat()).toInt().coerceAtMost(40)

        // If we have less relative force then the target then we have more motivation to accept
        motivation += (30 * (1 - (civForce / targetForce.toFloat()))).toInt().coerceIn(-30, 30)

        return motivation - 20
    }

    /**
     * How much motivation [civInfo] has to declare war against [target] this turn.
     * This can be through a prepared war or a suprise war.
     *
     * @return The movtivation of the plan. If it is > 0 then we can declare the war.
     */
    fun evaluateDeclareWarPlan(civInfo: Civilization, target: Civilization, givenMotivation: Int?): Int {
        val motivation = givenMotivation
            ?: MotivationToAttackAutomation.hasAtLeastMotivationToAttack(civInfo, target, 0)

        val diploManager = civInfo.getDiplomacyManager(target)

        if (diploManager.hasFlag(DiplomacyFlags.WaryOf) && diploManager.getFlag(DiplomacyFlags.WaryOf) < 0) {
            val turnsToPlan = (10 - (motivation / 10)).coerceAtLeast(3)
            val turnsToWait = turnsToPlan + diploManager.getFlag(DiplomacyFlags.WaryOf)
            return motivation - turnsToWait * 3
        }

        return motivation - 40
    }

    /**
     * How much motivation [civInfo] has to start preparing for a war agaist [target].
     *
     * @return The motivation of the plan. If it is > 0 then we can start planning the war.
     */
    fun evaluateStartPreparingWarPlan(civInfo: Civilization, target: Civilization, givenMotivation: Int?): Int {
        val motivation = givenMotivation
            ?: MotivationToAttackAutomation.hasAtLeastMotivationToAttack(civInfo, target, 0)

        // TODO: We use negative values in WaryOf for now so that we aren't adding any extra fields to the save file
        // This will very likely change in the future and we will want to build upon it
        val diploManager = civInfo.getDiplomacyManager(target)
        if (diploManager.hasFlag(DiplomacyFlags.WaryOf)) return 0

        return motivation - 15
    }
}

