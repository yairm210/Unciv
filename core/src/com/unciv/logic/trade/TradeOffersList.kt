package com.unciv.logic.trade

import com.unciv.logic.IsPartOfGameInfoSerialization
import yairm210.purity.annotations.InternalState

@InternalState
class TradeOffersList: ArrayList<TradeOffer>(), IsPartOfGameInfoSerialization {
    override fun add(element: TradeOffer): Boolean {
        val equivalentOffer = firstOrNull { it.name == element.name && it.type == element.type }
        if (equivalentOffer == null) {
            super.add(element)
            return true
        }
        equivalentOffer.amount += element.amount
        if (equivalentOffer.amount == 0) remove(equivalentOffer)
        return true
    }

    fun without(otherTradeOffersList: TradeOffersList): TradeOffersList {
        val tradeOffersListCopy = TradeOffersList()
        for (offer in this) tradeOffersListCopy.add(offer.copy())
        for (offer in otherTradeOffersList) tradeOffersListCopy.add(offer.copy(amount = -offer.amount))
        return tradeOffersListCopy
    }
}
