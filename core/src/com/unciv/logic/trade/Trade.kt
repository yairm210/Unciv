package com.unciv.logic.trade

class Trade{
    fun reverse(): Trade {
        val newTrade = Trade()
        newTrade.theirOffers+=ourOffers.map { it.copy() }
        newTrade.ourOffers+=theirOffers.map { it.copy() }
        return newTrade
    }


    val theirOffers = TradeOffersList()
    val ourOffers = TradeOffersList()

    fun equals(trade: Trade):Boolean{
       if(trade.ourOffers.size!=ourOffers.size
           || trade.theirOffers.size!=theirOffers.size) return false

        for(offer in trade.ourOffers)
            if(ourOffers.none { it.equals(offer)})
                return false
        for(offer in trade.theirOffers)
            if(theirOffers.none { it.equals(offer)})
                return false
        return true
    }
}