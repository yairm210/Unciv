package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags

class Trade : IsPartOfGameInfoSerialization {

    val theirOffers = TradeOffersList()
    val ourOffers = TradeOffersList()

    fun reverse(): Trade {
        val newTrade = Trade()
        newTrade.theirOffers+=ourOffers.map { it.copy() }
        newTrade.ourOffers+=theirOffers.map { it.copy() }
        return newTrade
    }

    fun equalTrade(trade: Trade):Boolean{
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

    fun isPeaceTreaty() = ourOffers.any { it.type == TradeType.Treaty && it.name == Constants.peaceTreaty }
}


class TradeRequest : IsPartOfGameInfoSerialization {
    fun decline(decliningCiv:CivilizationInfo) {
        val requestingCivInfo = decliningCiv.gameInfo.getCivilization(requestingCiv)
        val diplomacyManager = requestingCivInfo.getDiplomacyManager(decliningCiv)
        // the numbers of the flags (20,5) are the amount of turns to wait until offering again
        if (trade.ourOffers.all { it.type == TradeType.Luxury_Resource }
            && trade.theirOffers.all { it.type==TradeType.Luxury_Resource })
            diplomacyManager.setFlag(DiplomacyFlags.DeclinedLuxExchange,20)
        if (trade.ourOffers.any { it.name == Constants.researchAgreement })
            diplomacyManager.setFlag(DiplomacyFlags.DeclinedResearchAgreement,20)
        if (trade.isPeaceTreaty()) diplomacyManager.setFlag(DiplomacyFlags.DeclinedPeace, 5)

        requestingCivInfo.addNotification("[${decliningCiv.civName}] has denied your trade request", decliningCiv.civName, NotificationIcon.Trade)
    }


    lateinit var requestingCiv: String

    /** Their offers are what they offer us, and our offers are what they want in return */
    lateinit var trade: Trade

    constructor()  // for json serialization

    constructor(requestingCiv: String, trade: Trade) {
        this.requestingCiv = requestingCiv
        this.trade = trade
    }
}
