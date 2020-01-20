package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeType
import com.unciv.models.translations.tr
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.addSeparator
import com.unciv.ui.utils.toLabel
import com.unciv.ui.utils.Popup
import kotlin.math.max
import kotlin.math.min

class TradePopup(worldScreen: WorldScreen): Popup(worldScreen){
    init{
        val viewingCiv = worldScreen.viewingCiv
        val tradeRequest = viewingCiv.tradeRequests.first()

        val requestingCiv = worldScreen.gameInfo.getCivilization(tradeRequest.requestingCiv)
        val translatedNation = requestingCiv.getTranslatedNation()
        val otherCivLeaderName = "[${translatedNation.leaderName}] of [${translatedNation.getNameTranslation()}]".tr()

        add(otherCivLeaderName.toLabel())
        addSeparator()

        val trade = tradeRequest.trade
        val tradeOffersTable = Table().apply { defaults().pad(10f) }
        tradeOffersTable.add("[${translatedNation.getNameTranslation()}]'s trade offer".toLabel())
        tradeOffersTable.add("Our trade offer".toLabel())
        tradeOffersTable.row()
        for(i in 0..max(trade.theirOffers.lastIndex, trade.ourOffers.lastIndex)){
            if(trade.theirOffers.lastIndex>=i) tradeOffersTable.add(trade.theirOffers[i].getOfferText().toLabel())
            else tradeOffersTable.add()
            if(trade.ourOffers.lastIndex>=i) tradeOffersTable.add(trade.ourOffers[i].getOfferText().toLabel())
            else tradeOffersTable.add()
            tradeOffersTable.row()
        }
        tradeOffersTable.pack()

        val scrollHeight = min(tradeOffersTable.height, worldScreen.stage.height/2)
        add(ScrollPane(tradeOffersTable)).height(scrollHeight).row()

        addGoodSizedLabel(translatedNation.tradeRequest).colspan(columns).row()

        addButton("Sounds good!"){
            val tradeLogic = TradeLogic(viewingCiv, requestingCiv)
            tradeLogic.currentTrade.set(trade)
            tradeLogic.acceptTrade()
            viewingCiv.tradeRequests.remove(tradeRequest)
            close()
            Popup(worldScreen).apply {
                add(otherCivLeaderName.toLabel()).colspan(2)
                addSeparator()
                addGoodSizedLabel("Excellent!").row()
                addButton("Farewell."){
                    close()
                    worldScreen.shouldUpdate=true
                    // in all cases, worldScreen.shouldUpdate should be set to true when we remove the last of the popups
                    // in order for the next trade to appear immediately
                }
                open()
            }
            requestingCiv.addNotification("[${viewingCiv.civName}] has accepted your trade request", Color.GOLD)
        }
        addButton("Not this time.".tr()){
            viewingCiv.tradeRequests.remove(tradeRequest)

            val diplomacyManager = requestingCiv.getDiplomacyManager(viewingCiv)
            if(trade.ourOffers.all { it.type==TradeType.Luxury_Resource } && trade.theirOffers.all { it.type==TradeType.Luxury_Resource })
                diplomacyManager.setFlag(DiplomacyFlags.DeclinedLuxExchange,20) // offer again in 20 turns

            if(trade.ourOffers.any{ it.type==TradeType.Treaty && it.name== Constants.peaceTreaty })
                diplomacyManager.setFlag(DiplomacyFlags.DeclinedPeace,5)

            close()
            requestingCiv.addNotification("[${viewingCiv.civName}] has denied your trade request", Color.GOLD)

            worldScreen.shouldUpdate=true
        }
        addButton("How about something else...".tr()){
            viewingCiv.tradeRequests.remove(tradeRequest)
            close()

            val diplomacyScreen= DiplomacyScreen(viewingCiv)
            val tradeTable =  diplomacyScreen.setTrade(requestingCiv)
            tradeTable.tradeLogic.currentTrade.set(trade)
            tradeTable.offerColumnsTable.update()
            worldScreen.game.setScreen(diplomacyScreen)
            worldScreen.shouldUpdate=true
        }
        open()
    }
}
