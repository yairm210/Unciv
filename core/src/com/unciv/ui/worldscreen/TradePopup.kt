package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeType
import com.unciv.models.gamebasics.tr
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.addSeparator
import com.unciv.ui.utils.toLabel
import com.unciv.ui.worldscreen.optionstable.PopupTable
import kotlin.math.max

class TradePopup(worldScreen: WorldScreen): PopupTable(worldScreen){
    init{
        val currentPlayerCiv = worldScreen.currentPlayerCiv
        val tradeRequest = currentPlayerCiv.tradeRequests.first()

        val requestingCiv = worldScreen.gameInfo.getCivilization(tradeRequest.requestingCiv)
        val translatedNation = requestingCiv.getTranslatedNation()
        val otherCivLeaderName = "[${translatedNation.leaderName}] of [${translatedNation.getNameTranslation()}]".tr()

        add(otherCivLeaderName.toLabel())
        addSeparator()

        val trade = tradeRequest.trade
        val tradeOffersTable = Table().apply { defaults().pad(10f) }
        for(i in 0..max(trade.theirOffers.lastIndex, trade.ourOffers.lastIndex)){
            if(trade.theirOffers.lastIndex>=i) tradeOffersTable.add(trade.theirOffers[i].getOfferText().toLabel())
            else tradeOffersTable.add()
            if(trade.ourOffers.lastIndex>=i) tradeOffersTable.add(trade.ourOffers[i].getOfferText().toLabel())
            else tradeOffersTable.add()
            tradeOffersTable.row()
        }
        add(tradeOffersTable).row()

        addGoodSizedLabel(translatedNation.tradeRequest).colspan(columns).row()

        addButton("Sounds good!".tr()){
            val tradeLogic = TradeLogic(currentPlayerCiv, requestingCiv)
            tradeLogic.currentTrade.set(trade)
            tradeLogic.acceptTrade()
            currentPlayerCiv.tradeRequests.remove(tradeRequest)
            remove()
            PopupTable(worldScreen).apply {
                add(otherCivLeaderName.toLabel()).colspan(2)
                addSeparator()
                addGoodSizedLabel("Excellent!").row()
                addButton("Goodbye."){
                    this.remove()
                    worldScreen.shouldUpdate=true
                    // in all cases, worldScreen.shouldUpdate should be set to true when we remove the last of the popups
                    // in order for the next trade to appear immediately
                }
                open()
            }
        }
        addButton("Not this time.".tr()){
            currentPlayerCiv.tradeRequests.remove(tradeRequest)

            if(trade.ourOffers.all { it.type==TradeType.Luxury_Resource } && trade.theirOffers.all { it.type==TradeType.Luxury_Resource })
                requestingCiv.diplomacy[currentPlayerCiv.civName]!!.flagsCountdown["DeclinedLuxExchange"]=20 // offer again in 20 turns

            if(trade.ourOffers.any{ it.type==TradeType.Treaty && it.name=="Peace Treaty" })
                requestingCiv.diplomacy[currentPlayerCiv.civName]!!.flagsCountdown["DeclinedPeace"]=5 // offer again in 20 turns

            remove()
            worldScreen.shouldUpdate=true
        }
        addButton("How about something else...".tr()){
            currentPlayerCiv.tradeRequests.remove(tradeRequest)
            remove()

            val diplomacyScreen= DiplomacyScreen()
            val tradeTable =  diplomacyScreen.setTrade(requestingCiv)
            tradeTable.tradeLogic.currentTrade.set(trade)
            tradeTable.offerColumnsTable.update()
            worldScreen.game.screen=diplomacyScreen
            worldScreen.shouldUpdate=true
        }
        open()
    }
}