package com.unciv.logic.civilization.diplomacy

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeType
import com.unciv.models.Counter
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.models.gamebasics.tr

enum class DiplomacyFlags{
    DeclinedLuxExchange,
    DeclinedPeace
}

class DiplomacyManager() {
    @Transient lateinit var civInfo: CivilizationInfo
    // since this needs to be checked a lot during travel, putting it in a transient is a good performance booster
    @Transient var hasOpenBorders=false

    lateinit var otherCivName:String
    var trades = ArrayList<Trade>()
    var diplomaticStatus = DiplomaticStatus.War
    /** Contains various flags (declared war, promised to not settle, declined luxury trade) and the number of turns in which they will expire */
    var flagsCountdown = HashMap<DiplomacyFlags,Int>()

    fun clone(): DiplomacyManager {
        val toReturn = DiplomacyManager()
        toReturn.otherCivName=otherCivName
        toReturn.diplomaticStatus=diplomaticStatus
        toReturn.trades.addAll(trades.map { it.clone() })
        toReturn.flagsCountdown.putAll(flagsCountdown)
        toReturn.hasOpenBorders=hasOpenBorders
        return toReturn
    }

    constructor(civilizationInfo: CivilizationInfo, OtherCivName:String) : this() {
        civInfo=civilizationInfo
        otherCivName=OtherCivName
        updateHasOpenBorders()
    }

    //region pure functions
    fun turnsToPeaceTreaty(): Int {
        for(trade in trades)
            for(offer in trade.ourOffers)
                if(offer.name=="Peace Treaty" && offer.duration > 0) return offer.duration
        return 0
    }

    fun canDeclareWar() = (turnsToPeaceTreaty()==0 && diplomaticStatus != DiplomaticStatus.War)

    fun otherCiv() = civInfo.gameInfo.getCivilization(otherCivName)

    fun goldPerTurn():Int{
        var goldPerTurnForUs = 0
        for(trade in trades) {
            for (offer in trade.ourOffers.filter { it.type == TradeType.Gold_Per_Turn })
                goldPerTurnForUs -= offer.amount
            for (offer in trade.theirOffers.filter { it.type == TradeType.Gold_Per_Turn })
                goldPerTurnForUs += offer.amount
        }
        return goldPerTurnForUs
    }

    fun resourcesFromTrade(): Counter<TileResource> {
        val counter = Counter<TileResource>()
        for(trade in trades){
            for(offer in trade.ourOffers)
                if(offer.type== TradeType.Strategic_Resource || offer.type== TradeType.Luxury_Resource)
                    counter.add(GameBasics.TileResources[offer.name]!!,-offer.amount)
            for(offer in trade.theirOffers)
                if(offer.type== TradeType.Strategic_Resource || offer.type== TradeType.Luxury_Resource)
                    counter.add(GameBasics.TileResources[offer.name]!!,offer.amount)
        }
        for(tradeRequest in otherCiv().tradeRequests.filter { it.requestingCiv==civInfo.civName }){
            for(offer in tradeRequest.trade.theirOffers) // "theirOffers" in the other civ's trade request, is actually out civ's offers
                if(offer.type== TradeType.Strategic_Resource || offer.type== TradeType.Luxury_Resource)
                    counter.add(GameBasics.TileResources[offer.name]!!,-offer.amount)
        }
        return counter
    }
    //endregion

    //region state-changing functions
    fun removeUntenebleTrades(){
        val negativeCivResources = civInfo.getCivResources().filter { it.value<0 }.map { it.key.name }
        for(trade in trades.toList()) {
            for (offer in trade.ourOffers) {
                if (offer.type in listOf(TradeType.Luxury_Resource, TradeType.Strategic_Resource)
                    && offer.name in negativeCivResources){
                    trades.remove(trade)
                    val otherCivTrades = otherCiv().getDiplomacyManager(civInfo).trades
                    otherCivTrades.removeAll{ it.equals(trade.reverse()) }
                    civInfo.addNotification("One of our trades with [$otherCivName] has been cut short!".tr(),null, Color.GOLD)
                    otherCiv().addNotification("One of our trades with [${civInfo.civName}] has been cut short!".tr(),null, Color.GOLD)
                }
            }
        }
    }

    // for performance reasons we don't want to call this every time we want to see if a unit can move through a tile
    fun updateHasOpenBorders(){
        hasOpenBorders=false
        for(trade in trades)
            for(offer in trade.theirOffers)
                if(offer.name=="Open Borders" && offer.duration > 0){
                    hasOpenBorders=true
                    return
                }
    }

    fun nextTurn(){
        for(trade in trades.toList()){
            for(offer in trade.ourOffers.union(trade.theirOffers).filter { it.duration>0 })
                offer.duration--

            if(trade.ourOffers.all { it.duration<=0 } && trade.theirOffers.all { it.duration<=0 }) {
                trades.remove(trade)
                civInfo.addNotification("One of our trades with [$otherCivName] has ended!".tr(),null, Color.YELLOW)
            }
        }
        removeUntenebleTrades()
        updateHasOpenBorders()

        for(flag in flagsCountdown.keys.toList()) {
            flagsCountdown[flag] = flagsCountdown[flag]!! - 1
            if(flagsCountdown[flag]==0) flagsCountdown.remove(flag)
        }

    }

    fun declareWar(){
        diplomaticStatus = DiplomaticStatus.War
        val otherCiv = otherCiv()
        val otherCivDiplomacy = otherCiv.getDiplomacyManager(civInfo)

        // Cancel all trades.
        for(trade in trades)
            for(offer in trade.theirOffers.filter { it.duration>0 })
                civInfo.addNotification("["+offer.name+"] from [$otherCivName] has ended",null, Color.GOLD)
        trades.clear()
        updateHasOpenBorders()

        for(trade in otherCivDiplomacy.trades)
            for(offer in trade.theirOffers.filter { it.duration>0 })
                otherCiv.addNotification("["+offer.name+"] from [$otherCivName] has ended",null, Color.GOLD)
        otherCivDiplomacy.trades.clear()
        otherCivDiplomacy.updateHasOpenBorders()


        otherCiv.getDiplomacyManager(civInfo).diplomaticStatus = DiplomaticStatus.War
        otherCiv.addNotification("[${civInfo.civName}] has declared war on us!",null, Color.RED)
        otherCiv.popupAlerts.add(PopupAlert(AlertType.WarDeclaration,civInfo.civName))

        /// AI won't propose peace for 10 turns
        flagsCountdown[DiplomacyFlags.DeclinedPeace]=10
        otherCiv.getDiplomacyManager(civInfo).flagsCountdown[DiplomacyFlags.DeclinedPeace]=10
    }
    //endregion
}