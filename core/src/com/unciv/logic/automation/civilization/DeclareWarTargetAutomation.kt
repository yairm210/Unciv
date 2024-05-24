package com.unciv.logic.automation.civilization

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeRequest
import com.unciv.logic.trade.TradeType
import com.unciv.ui.screens.victoryscreen.RankingType

object DeclareWarTargetAutomation {

    /**
     * Chooses a target civilization along with a plan of attack.
     * Note that this doesn't guarantee that we will declare war on them immedietly, or that we will end up declaring war at all.
     */
    fun chooseDeclareWarTarget(civInfo: Civilization, civAttackMotivations: List<Pair<Civilization, Int>>) {
        val highestValueTargets = civAttackMotivations.sortedByDescending { it.first.getStatForRanking(RankingType.Score) }

        for (target in highestValueTargets) {
            if (tryDeclareWarWithPlan(civInfo, target.first, target.second)) 
                return // We have successfully found a plan and started executing it!
        }
    }

    fun tryDeclareWarWithPlan(civInfo: Civilization, target: Civilization, motivation: Int): Boolean {
        if (motivation > 0 && tryTeamWar(civInfo, target,motivation)) return true

        return false
    }

    /**
     * The safest option for war is to invite a new ally to join the war with us.
     * Together we are stronger and are more likely to take down bigger threats.
     */
    fun tryTeamWar(civInfo: Civilization, target: Civilization, motivation: Int): Boolean {
        val civForce = civInfo.getStatForRanking(RankingType.Force)
        val targetForce = target.getStatForRanking(RankingType.Force)

        val potentialAllies = civInfo.getDiplomacyManager(target).getCommonKnownCivs()
                .filter { !it.isAtWarWith(target) } // Must be a civ not already at war with them
                .sortedByDescending { it.getStatForRanking(RankingType.Force) }
        for (thirdCiv in potentialAllies) {
            // Make sure that they are at least friendly to us
            if (civInfo.getDiplomacyManager(thirdCiv).isRelationshipLevelLT(RelationshipLevel.Favorable)) return false

            // Make sure that they can actually help us with the target
            if (!thirdCiv.threatManager.getNeighboringCivilizaitons().contains(target)) return false

            // They need to be at least half the targets size, and we need to be stronger than the target together
            val thirdCivForce = thirdCiv.getStatForRanking(RankingType.Force) - 0.8f * thirdCiv.getCivsAtWarWith().sumOf { it.getStatForRanking(RankingType.Force) }
            if (thirdCivForce > targetForce / 2) return false

            // A higher motivation means that we can be riskier
            val multiplier = when {
                motivation < 5 -> 1.2f
                motivation < 10 -> 1.1f
                motivation < 20 -> 1f
                else -> 0.8f
            }
            if (thirdCivForce + civForce < targetForce * multiplier) return false

            // Send them an offer
            val tradeLogic = TradeLogic(civInfo, thirdCiv)
            tradeLogic.currentTrade.ourOffers.add(TradeOffer(target.civName, TradeType.WarDeclaration))
            tradeLogic.currentTrade.theirOffers.add(TradeOffer(target.civName, TradeType.WarDeclaration))

            thirdCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))

            return true
        }

        return false
    }
}