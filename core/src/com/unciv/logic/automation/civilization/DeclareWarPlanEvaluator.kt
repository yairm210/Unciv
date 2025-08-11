package com.unciv.logic.automation.civilization

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.ruleset.nation.PersonalityValue
import com.unciv.ui.screens.victoryscreen.RankingType
import yairm210.purity.annotations.Readonly

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
    @Readonly
    fun evaluateTeamWarPlan(civInfo: Civilization, target: Civilization, teamCiv: Civilization, givenMotivation: Float?): Float {
        val teamCivDiplo = civInfo.getDiplomacyManager(teamCiv)!!
        if (civInfo.getPersonality()[PersonalityValue.DeclareWar] == 0f) return -1000f
        if (teamCivDiplo.isRelationshipLevelLT(RelationshipLevel.Neutral)) return -1000f

        var motivation = givenMotivation
            ?: MotivationToAttackAutomation.hasAtLeastMotivationToAttack(civInfo, target, 0f)

        if (teamCivDiplo.isRelationshipLevelEQ(RelationshipLevel.Neutral)) motivation -= 5f
        // Make sure that they can actually help us with the target
        if (!teamCiv.threatManager.getNeighboringCivilizations().contains(target)) {
            motivation -= 40f
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
            motivation -= 30 * ((targetForce * multiplier) / (teamCivForce + civForce) - 1)
        } else if (civForce + teamCivForce > targetForce * 2) {
            // Why gang up on such a weaker enemy when we can declare war ourselves?
            // If our combined force is twice their force we will have -20 motivation
            motivation -= 20 * ((civForce + teamCivForce) / targetForce * 2) - 1
        }

        val civScore = civInfo.getStatForRanking(RankingType.Score)
        val teamCivScore = teamCiv.getStatForRanking(RankingType.Score)
        val targetCivScore = target.getStatForRanking(RankingType.Score)

        if (teamCivScore > civScore * 1.4f && teamCivScore >= targetCivScore) {
            // If teamCiv has more score than us and the target they are likely in a good position already
            motivation -= 20 * ((teamCivScore / (civScore * 1.4f)) - 1)
        }
        return motivation - 20f
    }

    /**
     * How much motivation [civInfo] has to join [civToJoin] in their war against [target].
     *
     * Favors protecting allies.
     * @return The movtivation of the plan. If it is > 0 then we can declare the war.
     */
    @Readonly
    fun evaluateJoinWarPlan(civInfo: Civilization, target: Civilization, civToJoin: Civilization, givenMotivation: Float?): Float {
        val thirdCivDiplo = civInfo.getDiplomacyManager(civToJoin)!!
        if (civInfo.getPersonality()[PersonalityValue.DeclareWar] == 0f) return -1000f
        if (thirdCivDiplo.isRelationshipLevelLE(RelationshipLevel.Favorable)) return -1000f

        var motivation = givenMotivation
            ?: MotivationToAttackAutomation.hasAtLeastMotivationToAttack(civInfo, target, 0f)
        // We need to be able to trust the thirdCiv at least somewhat
        if (thirdCivDiplo.diplomaticStatus != DiplomaticStatus.DefensivePact &&
            thirdCivDiplo.opinionOfOtherCiv() + motivation * 2 < 80) {
            motivation -= 80f - thirdCivDiplo.opinionOfOtherCiv() + motivation * 2
        }
        if (!civToJoin.threatManager.getNeighboringCivilizations().contains(target)) {
            motivation -= 20f
        }

        val targetForce = target.getStatForRanking(RankingType.Force) - 0.8f * target.getCivsAtWarWith().sumOf { it.getStatForRanking(RankingType.Force) }.coerceAtLeast(100)
        val civForce = civInfo.getStatForRanking(RankingType.Force)

        // They need to be at least half the targets size, and we need to be stronger than the target together
        val civToJoinForce = (civToJoin.getStatForRanking(RankingType.Force) - 0.8f * civToJoin.getCivsAtWarWith().sumOf { it.getStatForRanking(RankingType.Force) }).coerceAtLeast(100f)
        if (civToJoinForce < targetForce / 2) {
            // Make sure that there is no wrap around
            motivation -= 10 * (targetForce / civToJoinForce).coerceIn(-1000f, 1000f)
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
            motivation -= 20 * (targetForce * multiplier) / (civToJoinForce + civForce).coerceIn(-1000f, 1000f)
        }

        return motivation - 15
    }

    /**
     * How much motivation [civInfo] has for [civToJoin] to join them in their war against [target].
     *
     * @return The movtivation of the plan. If it is >= 0 then we can accept their war offer.
     */
    @Readonly
    fun evaluateJoinOurWarPlan(civInfo: Civilization, target: Civilization, civToJoin: Civilization, givenMotivation: Float?): Float {
        if (civInfo.getDiplomacyManager(civToJoin)!!.isRelationshipLevelLT(RelationshipLevel.Favorable)) return -1000f
        var motivation = givenMotivation ?: 0f
        if (!civToJoin.threatManager.getNeighboringCivilizations().contains(target)) {
            motivation -= 50f
        }

        val targetForce = target.getStatForRanking(RankingType.Force)
        val civForce = civInfo.getStatForRanking(RankingType.Force)

        // If we have more force than all enemies and overpower this enemy then we don't need help
        if (civForce - civInfo.threatManager.getCombinedForceOfWarringCivs() > targetForce * 2) return 0f

        // They should to be at least half the targets size
        val thirdCivForce = (civToJoin.getStatForRanking(RankingType.Force) - 0.8f * civToJoin.getCivsAtWarWith().sumOf { it.getStatForRanking(RankingType.Force) }).coerceAtLeast(100f)
        motivation += (20 * (1 - thirdCivForce / targetForce.toFloat())).coerceAtMost(40f)

        // If we have less relative force then the target then we have more motivation to accept
        motivation += 20 * (1 - civForce / targetForce.toFloat()).coerceIn(-40f, 40f)

        return motivation - 20
    }

    /**
     * How much motivation [civInfo] has to declare war against [target] this turn.
     * This can be through a prepared war or a suprise war.
     *
     * @return The movtivation of the plan. If it is > 0 then we can declare the war.
     */
    @Readonly
    fun evaluateDeclareWarPlan(civInfo: Civilization, target: Civilization, givenMotivation: Float?): Float {
        if (civInfo.getPersonality()[PersonalityValue.DeclareWar] == 0f) return -1000f
        val motivation = givenMotivation
            ?: MotivationToAttackAutomation.hasAtLeastMotivationToAttack(civInfo, target, 0f)

        val diploManager = civInfo.getDiplomacyManager(target)!!

        if (diploManager.hasFlag(DiplomacyFlags.WaryOf) && diploManager.getFlag(DiplomacyFlags.WaryOf) < 0) {
            val turnsToPlan = (10 - (motivation / 10)).coerceAtLeast(3f)
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
    @Readonly
    fun evaluateStartPreparingWarPlan(civInfo: Civilization, target: Civilization, givenMotivation: Float?): Float {
        val motivation = givenMotivation
            ?: MotivationToAttackAutomation.hasAtLeastMotivationToAttack(civInfo, target, 0f)

        // TODO: We use negative values in WaryOf for now so that we aren't adding any extra fields to the save file
        // This will very likely change in the future and we will want to build upon it
        val diploManager = civInfo.getDiplomacyManager(target)!!
        if (diploManager.hasFlag(DiplomacyFlags.WaryOf)) return 0f

        return motivation - 15
    }
}

