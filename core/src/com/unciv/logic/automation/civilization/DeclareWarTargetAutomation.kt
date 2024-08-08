package com.unciv.logic.automation.civilization

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeRequest
import com.unciv.logic.trade.TradeOfferType
import com.unciv.ui.screens.victoryscreen.RankingType

object DeclareWarTargetAutomation {

    /**
     * Chooses a target civilization along with a plan of attack.
     * Note that this doesn't guarantee that we will declare war on them immediatly, or that we will end up declaring war at all.
     */
    fun chooseDeclareWarTarget(civInfo: Civilization, civAttackMotivations: List<Pair<Civilization, Float>>) {
        val highestValueTargets = civAttackMotivations.sortedByDescending { it.first.getStatForRanking(RankingType.Score) }

        for (target in highestValueTargets) {
            if (tryDeclareWarWithPlan(civInfo, target.first, target.second))
                return // We have successfully found a plan and started executing it!
        }
    }

    /**
     * Determines a war plan against this [target] and executes it if able.
     */
    private fun tryDeclareWarWithPlan(civInfo: Civilization, target: Civilization, motivation: Float): Boolean {

        if (!target.isCityState) {
            if (motivation > 5 && tryTeamWar(civInfo, target, motivation)) return true

            if (motivation >= 15 && tryJoinWar(civInfo, target, motivation)) return true
        }

        if (motivation >= 20 && declareWar(civInfo, target, motivation)) return true

        if (motivation >= 15 && prepareWar(civInfo, target, motivation)) return true

        return false
    }

    /**
     * The safest option for war is to invite a new ally to join the war with us.
     * Together we are stronger and are more likely to take down bigger threats.
     */
    private fun tryTeamWar(civInfo: Civilization, target: Civilization, motivation: Float): Boolean {
        val potentialAllies = civInfo.getDiplomacyManager(target)!!.getCommonKnownCivs()
            .filter {
                it.isMajorCiv()
                    && !civInfo.getDiplomacyManager(it)!!.hasFlag(DiplomacyFlags.DeclinedJoinWarOffer)
                    && civInfo.getDiplomacyManager(it)!!.isRelationshipLevelGE(RelationshipLevel.Neutral)
                    && !it.isAtWarWith(target)
            }.sortedByDescending { it.getStatForRanking(RankingType.Force) }

        for (thirdCiv in potentialAllies) {
            if (DeclareWarPlanEvaluator.evaluateTeamWarPlan(civInfo, target, thirdCiv, motivation) <= 0) continue

            // Send them an offer
            val tradeLogic = TradeLogic(civInfo, thirdCiv)
            tradeLogic.currentTrade.ourOffers.add(TradeOffer(target.civName, TradeOfferType.WarDeclaration, speed = civInfo.gameInfo.speed))
            tradeLogic.currentTrade.theirOffers.add(TradeOffer(target.civName, TradeOfferType.WarDeclaration, speed = civInfo.gameInfo.speed))

            thirdCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))

            return true
        }

        return false
    }

    /**
     * The next safest aproach is to join an existing war on the side of an ally that is already at war with [target].
     */
    private fun tryJoinWar(civInfo: Civilization, target: Civilization, motivation: Float): Boolean {
        val potentialAllies = civInfo.getDiplomacyManager(target)!!.getCommonKnownCivs()
            .filter {
                it.isMajorCiv()
                    && !civInfo.getDiplomacyManager(it)!!.hasFlag(DiplomacyFlags.DeclinedJoinWarOffer)
                    && civInfo.getDiplomacyManager(it)!!.isRelationshipLevelGE(RelationshipLevel.Favorable)
                    && it.isAtWarWith(target)
            } // Must be a civ not already at war with them
            .sortedByDescending { it.getStatForRanking(RankingType.Force) }

        for (thirdCiv in potentialAllies) {
            if (DeclareWarPlanEvaluator.evaluateJoinWarPlan(civInfo, target, thirdCiv, motivation) <= 0) continue

            // Send them an offer
            val tradeLogic = TradeLogic(civInfo, thirdCiv)
            tradeLogic.currentTrade.ourOffers.add(TradeOffer(target.civName, TradeOfferType.WarDeclaration, speed = civInfo.gameInfo.speed))
            // TODO: Maybe add in payment requests in some situations
            thirdCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))

            return true
        }

        return false
    }

    /**
     * Lastly, if our motivation is high enough and we don't have any better plans then lets just declare war.
     */
    private fun declareWar(civInfo: Civilization, target: Civilization, motivation: Float): Boolean {
        if (DeclareWarPlanEvaluator.evaluateDeclareWarPlan(civInfo, target, motivation) > 0) {
            civInfo.getDiplomacyManager(target)!!.declareWar()
            return true
        }
        return false
    }

    /**
     * Slightly safter is to silently plan an invasion and declare war later.
     */
    private fun prepareWar(civInfo: Civilization, target: Civilization, motivation: Float): Boolean {
        // TODO: We use negative values in WaryOf for now so that we aren't adding any extra fields to the save file
        // This will very likely change in the future and we will want to build upon it
        val diploManager = civInfo.getDiplomacyManager(target)!!
        if (DeclareWarPlanEvaluator.evaluateStartPreparingWarPlan(civInfo, target, motivation) > 0) {
            diploManager.setFlag(DiplomacyFlags.WaryOf, -1)
            return true
        }
        return false
    }

}

