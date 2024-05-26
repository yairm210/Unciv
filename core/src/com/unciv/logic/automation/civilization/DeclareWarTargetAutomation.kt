package com.unciv.logic.automation.civilization

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
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

    /**
     * Determines a war plan against this [target] and executes it if able.
     * TODO: Multiturn war plans so units can get into position
     */
    private fun tryDeclareWarWithPlan(civInfo: Civilization, target: Civilization, motivation: Int): Boolean {

        if (!target.isCityState()) {
            if (motivation > 0 && tryTeamWar(civInfo, target, motivation)) return true

            if (motivation >= 10 && tryJoinWar(civInfo, target, motivation)) return true
        }

        if (motivation >= 50 && declareWar(civInfo, target, motivation)) return true

        if (motivation >= 20 && declarePlannedWar(civInfo, target, motivation)) return true

        return false
    }

    /**
     * The safest option for war is to invite a new ally to join the war with us.
     * Together we are stronger and are more likely to take down bigger threats.
     */
    private fun tryTeamWar(civInfo: Civilization, target: Civilization, motivation: Int): Boolean {
        val civForce = civInfo.getStatForRanking(RankingType.Force)
        val targetForce = target.getStatForRanking(RankingType.Force)

        val potentialAllies = civInfo.getDiplomacyManager(target).getCommonKnownCivs()
                .filter { it.isMajorCiv() 
                        && !civInfo.getDiplomacyManager(it).hasFlag(DiplomacyFlags.DeclinedJoinWarOffer) 
                        && civInfo.getDiplomacyManager(it).isRelationshipLevelGE(RelationshipLevel.Favorable) 
                        && !it.isAtWarWith(target) } 
                .sortedByDescending { it.getStatForRanking(RankingType.Force) }

        for (thirdCiv in potentialAllies) {
            // Make sure that they can actually help us with the target
            if (!thirdCiv.threatManager.getNeighboringCivilizaitons().contains(target)) return false

            // They need to be at least half the targets size, and we need to be stronger than the target together
            val thirdCivForce = thirdCiv.getStatForRanking(RankingType.Force) - 0.8f * thirdCiv.threatManager.getCombinedForceOfWarringCivs()
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

    /**
     * The next safest aproach is to join an existing war on the side of an ally that is already at war with [target].
     */
    private fun tryJoinWar(civInfo: Civilization, target: Civilization, motivation: Int): Boolean {
        val civForce = civInfo.getStatForRanking(RankingType.Force)
        val targetForce = target.getStatForRanking(RankingType.Force)

        val potentialAllies = civInfo.getDiplomacyManager(target).getCommonKnownCivs()
                .filter { it.isMajorCiv()  
                        && !civInfo.getDiplomacyManager(it).hasFlag(DiplomacyFlags.DeclinedJoinWarOffer) 
                        && civInfo.getDiplomacyManager(it).isRelationshipLevelGE(RelationshipLevel.Favorable) 
                        && it.isAtWarWith(target) } // Must be a civ not already at war with them
                .sortedByDescending { it.getStatForRanking(RankingType.Force) }

        for (thirdCiv in potentialAllies) {
            // We need to be able to trust the thirdCiv at least somewhat
            val thirdCivDiplo = civInfo.getDiplomacyManager(thirdCiv)
            if (thirdCivDiplo.diplomaticStatus != DiplomaticStatus.DefensivePact &&
                    thirdCivDiplo.opinionOfOtherCiv() + motivation * 2 < 80) return false

            // They need to be at least half the targets size, and we need to be stronger than the target together
            val thirdCivForce = thirdCiv.getStatForRanking(RankingType.Force) - 0.8f * thirdCiv.getCivsAtWarWith().sumOf { it.getStatForRanking(RankingType.Force) }
            if (thirdCivForce < targetForce / 2) return false

            // A higher motivation means that we can be riskier
            val multiplier = when {
                motivation < 5 -> 1.4f
                motivation < 10 -> 1.3f
                motivation < 15 -> 1.2f
                motivation < 20 -> 1f
                else -> 0.8f
            }
            if (thirdCivForce + civForce < targetForce * multiplier) return false

            // Send them an offer
            val tradeLogic = TradeLogic(civInfo, thirdCiv)
            tradeLogic.currentTrade.ourOffers.add(TradeOffer(target.civName, TradeType.WarDeclaration))
            // TODO: Maybe add in payment requests in some situations

            thirdCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))

            return true
        }

        return false
    }

    /**
     * Slightly safter is to silently plan an invasion and declare war later.
     */
    private fun declarePlannedWar(civInfo: Civilization, target: Civilization, motivation: Int): Boolean {
        val diploManager = civInfo.getDiplomacyManager(target)
        val turnsToWait = when {
            motivation < 10 -> 8
            motivation < 20 -> 6
            motivation < 30 -> 5
            motivation < 40 -> 4
            else -> 3
        }

        // TODO: We use negative values in WaryOf for now so that we aren't adding any extra fields to the save file
        // This will very likely change in the future and we will want to build upon it
        if (diploManager.hasFlag(DiplomacyFlags.WaryOf)) {
            if (diploManager.flagsCountdown[DiplomacyFlags.WaryOf.name]!! < -turnsToWait)
                civInfo.getDiplomacyManager(target).declareWar()
        } else {
            diploManager.setFlag(DiplomacyFlags.WaryOf, -1)
        }
        return true
    }

    /**
     * Lastly, if our motivation is high enough and we don't have any better plans then lets just declare war.
     */
    private fun declareWar(civInfo: Civilization, target: Civilization, motivation: Int): Boolean {
        civInfo.getDiplomacyManager(target).declareWar()
        return true
    }
}