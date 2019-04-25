package com.unciv.logic.trade

class Trade{

    val theirOffers = TradeOffersList()
    val ourOffers = TradeOffersList()

    fun reverse(): Trade {
        val newTrade = Trade()
        newTrade.theirOffers+=ourOffers.map { it.copy() }
        newTrade.ourOffers+=theirOffers.map { it.copy() }
        return newTrade
    }

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

    fun clone():Trade{
        val toReturn = Trade()
        toReturn.theirOffers.addAll(theirOffers)
        toReturn.ourOffers.addAll(ourOffers)
        return toReturn
    }

    fun set(trade: Trade) {
        ourOffers.clear()
        ourOffers.addAll(trade.ourOffers)
        theirOffers.clear()
        theirOffers.addAll(trade.theirOffers)
    }
}