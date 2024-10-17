package com.unciv.logic.automation.civilization

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeRequest
import com.unciv.logic.trade.TradeOfferType
import kotlin.math.min

object TradeAutomation {


    fun respondToTradeRequests(civInfo: Civilization, tradeAndChangeState: Boolean) {
        for (tradeRequest in civInfo.tradeRequests.toList()) {
            val otherCiv = civInfo.gameInfo.getCivilization(tradeRequest.requestingCiv)
            // Treat 'no trade' state as if all trades are invalid - thus AIs will not update its "turns to offer"
            if (!tradeAndChangeState || !TradeEvaluation().isTradeValid(tradeRequest.trade, civInfo, otherCiv))
                continue

            val tradeLogic = TradeLogic(civInfo, otherCiv)
            tradeLogic.currentTrade.set(tradeRequest.trade)
            /** We need to remove this here, so that if the trade is accepted, the updateDetailedCivResources()
             * in tradeLogic.acceptTrade() will not consider *both* the trade *and the trade offer as decreasing the
             * amount of available resources, since that will lead to "Our proposed trade is no longer valid" if we try to offer
             * the same resource to ANOTHER civ in this turn. Complicated!
             */
            civInfo.tradeRequests.remove(tradeRequest)
            if (TradeEvaluation().isTradeAcceptable(tradeLogic.currentTrade, civInfo, otherCiv)) {
                tradeLogic.acceptTrade()
                otherCiv.addNotification("[${civInfo.civName}] has accepted your trade request", NotificationCategory.Trade, NotificationIcon.Trade, civInfo.civName)
            } else {
                val counteroffer = getCounteroffer(civInfo, tradeRequest)
                if (counteroffer != null) {
                    otherCiv.addNotification("[${civInfo.civName}] has made a counteroffer to your trade request", NotificationCategory.Trade, NotificationIcon.Trade, civInfo.civName)
                    otherCiv.tradeRequests.add(counteroffer)
                } else
                    tradeRequest.decline(civInfo)
            }
        }
        civInfo.tradeRequests.clear()
    }

    /** @return a TradeRequest with the same ourOffers as [tradeRequest] but with enough theirOffers
     *  added to make the deal acceptable. Will find a valid counteroffer if any exist, but is not
     *  guaranteed to find the best or closest one. */
    private fun getCounteroffer(civInfo: Civilization, tradeRequest: TradeRequest): TradeRequest? {
        val otherCiv = civInfo.gameInfo.getCivilization(tradeRequest.requestingCiv)
        // AIs counteroffering each other could be problematic if they ping-pong back and forth forever
        // If this happens, that means our trade automation doesn't settle into an equilibrium that's favourable to both parties, so that should be updated when observed
        val evaluation = TradeEvaluation()
        var deltaInOurFavor = evaluation.getTradeAcceptability(tradeRequest.trade, civInfo, otherCiv, true)
        if (deltaInOurFavor > 0) deltaInOurFavor = (deltaInOurFavor / 1.1f).toInt() // They seem very interested in this deal, let's push it a bit.
        val tradeLogic = TradeLogic(civInfo, otherCiv)

        tradeLogic.currentTrade.set(tradeRequest.trade)

        // What do they have that we would want?
        val potentialAsks = HashMap<TradeOffer, Int>()
        val counterofferAsks = HashMap<TradeOffer, Int>()
        val counterofferGifts = ArrayList<TradeOffer>()

        for (offer in tradeLogic.theirAvailableOffers) {
            if ((offer.type == TradeOfferType.Gold || offer.type == TradeOfferType.Gold_Per_Turn)
                && tradeRequest.trade.ourOffers.any { it.type == offer.type })
                continue // Don't want to counteroffer straight gold for gold, that's silly
            if (!offer.isTradable())
                continue // For example resources gained by trade or CS
            if (offer.type == TradeOfferType.City)
                continue // Players generally don't want to give up their cities, and they might misclick
            if (offer.type == TradeOfferType.Luxury_Resource)
                continue // Don't ask for luxuries as counteroffer, players likely don't want to sell them if they didn't offer them already
            if (tradeLogic.currentTrade.theirOffers.any { it.type == offer.type && it.name == offer.name })
                continue // So you don't get double offers of open borders declarations of war etc.
            if (offer.type == TradeOfferType.Treaty)
                continue // Don't try to counter with a defensive pact or research pact

            val value = evaluation.evaluateBuyCostWithInflation(offer, civInfo, otherCiv, tradeRequest.trade)
            if (value > 0)
                potentialAsks[offer] = value
        }

        while (potentialAsks.isNotEmpty() && deltaInOurFavor < 0) {
            // Keep adding their worst offer until we get above the threshold
            val offerToAdd = potentialAsks.minByOrNull { it.value }!!
            deltaInOurFavor += offerToAdd.value
            counterofferAsks[offerToAdd.key] = offerToAdd.value
            potentialAsks.remove(offerToAdd.key)
        }
        if (deltaInOurFavor < 0)
            return null // We couldn't get a good enough deal

        // At this point we are sure to find a good counteroffer
        while (deltaInOurFavor > 0) {
            // Now remove the best offer valued below delta until the deal is barely acceptable
            val offerToRemove = counterofferAsks.filter { it.value <= deltaInOurFavor }.maxByOrNull { it.value }
                ?: break  // Nothing more can be removed, at least en bloc
            deltaInOurFavor -= offerToRemove.value
            counterofferAsks.remove(offerToRemove.key)
        }

        // Only ask for enough of each resource to get maximum price
        for (ask in counterofferAsks.keys.filter { it.type == TradeOfferType.Luxury_Resource || it.type == TradeOfferType.Strategic_Resource }) {
            // Remove 1 amount as long as doing so does not change the price
            val originalValue = counterofferAsks[ask]!!
            while (ask.amount > 1
                && originalValue == evaluation.evaluateBuyCostWithInflation(
                    TradeOffer(ask.name, ask.type, ask.amount - 1, ask.duration),
                    civInfo, otherCiv, tradeRequest.trade) ) {
                ask.amount--
            }
        }

        // Adjust any gold asked for
        val toRemove = ArrayList<TradeOffer>()
        for (goldAsk in counterofferAsks.keys
            .filter { it.type == TradeOfferType.Gold_Per_Turn || it.type == TradeOfferType.Gold }
            .sortedByDescending { it.type.ordinal }) { // Do GPT first
            val valueOfOne = evaluation.evaluateBuyCostWithInflation(TradeOffer(goldAsk.name, goldAsk.type, 1, goldAsk.duration), civInfo, otherCiv, tradeRequest.trade)
            val amountCanBeRemoved = deltaInOurFavor / valueOfOne
            if (amountCanBeRemoved >= goldAsk.amount) {
                deltaInOurFavor -= counterofferAsks[goldAsk]!!
                toRemove.add(goldAsk)
            } else {
                deltaInOurFavor -= valueOfOne * amountCanBeRemoved
                goldAsk.amount -= amountCanBeRemoved
            }
        }

        // If the delta is still very in our favor consider sweetening the pot with some gold
        if (deltaInOurFavor >= 100) {
            deltaInOurFavor = (deltaInOurFavor * 2) / 3 // Only compensate some of it though, they're the ones asking us
            // First give some GPT, then lump sum - but only if they're not already offering the same
            for (ourGold in tradeLogic.ourAvailableOffers
                .filter { it.isTradable() && (it.type == TradeOfferType.Gold || it.type == TradeOfferType.Gold_Per_Turn) }
                .sortedByDescending { it.type.ordinal }) {
                if (tradeLogic.currentTrade.theirOffers.none { it.type == ourGold.type } &&
                    counterofferAsks.keys.none { it.type == ourGold.type } ) {
                    val valueOfOne = evaluation.evaluateSellCostWithInflation(TradeOffer(ourGold.name, ourGold.type, 1, ourGold.duration), civInfo, otherCiv, tradeRequest.trade)
                    val amountToGive = min(deltaInOurFavor / valueOfOne, ourGold.amount)
                    deltaInOurFavor -= amountToGive * valueOfOne
                    if (amountToGive > 0) {
                        counterofferGifts.add(
                            TradeOffer(
                                ourGold.name,
                                ourGold.type,
                                amountToGive,
                                ourGold.duration
                            )
                        )
                    }
                }
            }
        }

        tradeLogic.currentTrade.theirOffers.addAll(counterofferAsks.keys)
        tradeLogic.currentTrade.ourOffers.addAll(counterofferGifts)

        // Trades reversed, because when *they* get it then the 'ouroffers' become 'theiroffers'
        return TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse())
    }


     fun exchangeLuxuries(civInfo: Civilization) {
        val knownCivs = civInfo.getKnownCivs()

        // Player trades are... more complicated.
        // When the AI offers a trade, it's not immediately accepted,
        // so what if it thinks that it has a spare luxury and offers it to two human players?
        // What's to stop the AI "nagging" the player to accept a luxury trade?
        // We should A. add some sort of timer (20? 30 turns?) between luxury trade requests if they're denied - see DeclinedLuxExchange
        // B. have a way for the AI to keep track of the "pending offers" - see DiplomacyManager.resourcesFromTrade

        for (otherCiv in knownCivs.filter {
            it.isMajorCiv() && !it.isAtWarWith(civInfo)
                && !civInfo.getDiplomacyManager(it)!!.hasFlag(DiplomacyFlags.DeclinedLuxExchange)
        }) {

            val isEnemy = civInfo.getDiplomacyManager(otherCiv)!!.isRelationshipLevelLE(RelationshipLevel.Enemy)
            if (isEnemy || otherCiv.tradeRequests.any { it.requestingCiv == civInfo.civName })
                continue

            val trades = potentialLuxuryTrades(civInfo, otherCiv)
            for (trade in trades) {
                val tradeRequest = TradeRequest(civInfo.civName, trade.reverse())
                otherCiv.tradeRequests.add(tradeRequest)
            }
        }
    }

    private fun potentialLuxuryTrades(civInfo: Civilization, otherCivInfo: Civilization): ArrayList<Trade> {
        val tradeLogic = TradeLogic(civInfo, otherCivInfo)
        val ourTradableLuxuryResources = tradeLogic.ourAvailableOffers
            .filter { it.type == TradeOfferType.Luxury_Resource && it.amount > 1 }
        val theirTradableLuxuryResources = tradeLogic.theirAvailableOffers
            .filter { it.type == TradeOfferType.Luxury_Resource && it.amount > 1 }
        val weHaveTheyDont = ourTradableLuxuryResources
            .filter { resource ->
                tradeLogic.theirAvailableOffers
                    .none { it.name == resource.name && it.type == TradeOfferType.Luxury_Resource }
            }
        val theyHaveWeDont = theirTradableLuxuryResources
            .filter { resource ->
                tradeLogic.ourAvailableOffers
                    .none { it.name == resource.name && it.type == TradeOfferType.Luxury_Resource }
            }.sortedBy { civInfo.cities.count { city -> city.demandedResource == it.name } } // Prioritize resources that get WLTKD
        val trades = ArrayList<Trade>()
        for (i in 0..min(weHaveTheyDont.lastIndex, theyHaveWeDont.lastIndex)) {
            val trade = Trade()
            trade.ourOffers.add(weHaveTheyDont[i].copy(amount = 1))
            trade.theirOffers.add(theyHaveWeDont[i].copy(amount = 1))
            trades.add(trade)
        }
        return trades
    }

}
