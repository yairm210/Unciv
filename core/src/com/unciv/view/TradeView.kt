package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffersList
import com.unciv.logic.trade.TradeRequest
import yairm210.purity.annotations.Readonly

/** View of a trade negotiation between [civ] (always the viewer's own civ) and [otherCiv].
 *  Always built fresh, same as [TradeLogic] itself - staged offers live in this instance only. */
class TradeView(private val civ: Civilization, private val otherCiv: Civilization) : GameBasedView<Civilization>(civ, civ) {
    private val tradeLogic = TradeLogic(civ, otherCiv)

    // Data retrieval - lists are the live mutable instances, callers may add/remove offers directly
    @Readonly fun ourAvailableOffers(): TradeOffersList = tradeLogic.ourAvailableOffers
    @Readonly fun theirAvailableOffers(): TradeOffersList = tradeLogic.theirAvailableOffers
    @Readonly fun ourStagedOffers(): TradeOffersList = tradeLogic.currentTrade.ourOffers
    @Readonly fun theirStagedOffers(): TradeOffersList = tradeLogic.currentTrade.theirOffers
    @Readonly fun hasPendingOfferFromUs(): Boolean =
        otherCiv.tradeRequests.any { it.requestingCiv == civ.civID }

    // Actions - staging
    fun setStagedTrade(trade: Trade) = tradeLogic.currentTrade.set(trade)

    /** Restores a trade we already sent to [otherCiv] (if any) into staging, so it can be re-displayed or retracted. */
    fun tryLoadOurPendingOffer(): Boolean {
        val existing = otherCiv.tradeRequests.firstOrNull { it.requestingCiv == civ.civID } ?: return false
        tradeLogic.currentTrade.set(existing.trade.reverse())
        return true
    }

    // Actions - proposing
    fun tryProposeStagedTrade(): Boolean {
        if (tradeLogic.currentTrade.ourOffers.isEmpty() && tradeLogic.currentTrade.theirOffers.isEmpty()) return false
        otherCiv.tradeRequests.add(TradeRequest(civ.civID, tradeLogic.currentTrade.reverse()))
        civ.cache.updateCivResources()
        return true
    }

    fun tryRetractOffer(): Boolean {
        if (!hasPendingOfferFromUs()) return false
        otherCiv.tradeRequests.removeAll { it.requestingCiv == civ.civID }
        civ.cache.updateCivResources()
        return true
    }
}
