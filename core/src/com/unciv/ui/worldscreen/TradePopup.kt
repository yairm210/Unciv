package com.unciv.ui.worldscreen

import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.translations.tr
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.Popup
import com.unciv.ui.utils.addSeparator
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.toLabel
import kotlin.math.max
import kotlin.math.min

class TradePopup(worldScreen: WorldScreen): Popup(worldScreen){
    val viewingCiv = worldScreen.viewingCiv
    val tradeRequest = viewingCiv.tradeRequests.first()

    init{
        val requestingCiv = worldScreen.gameInfo.getCivilization(tradeRequest.requestingCiv)
        val nation = requestingCiv.nation
        val otherCivLeaderName = "[${nation.leaderName}] of [${nation.name}]".tr()

        add(otherCivLeaderName.toLabel())
        addSeparator()

        val trade = tradeRequest.trade
        val tradeOffersTable = Table().apply { defaults().pad(10f) }
        tradeOffersTable.add("[${nation.name}]'s trade offer".toLabel())
        tradeOffersTable.add("Our trade offer".toLabel())
        tradeOffersTable.row()
        val ourResources = viewingCiv.getCivResourcesByName()

        fun getOfferText(offer:TradeOffer): String {
            var tradeText = offer.getOfferText()
            if (offer.type == TradeType.Luxury_Resource || offer.type == TradeType.Strategic_Resource)
                tradeText += "\n" + "Owned: [${ourResources[offer.name]}]".tr()
            return tradeText
        }

        for(i in 0..max(trade.theirOffers.lastIndex, trade.ourOffers.lastIndex)){
            if(trade.theirOffers.lastIndex>=i) tradeOffersTable.add(getOfferText(trade.theirOffers[i]).toLabel())
            else tradeOffersTable.add()
            if(trade.ourOffers.lastIndex>=i) tradeOffersTable.add(getOfferText(trade.ourOffers[i]).toLabel())
            else tradeOffersTable.add()
            tradeOffersTable.row()
        }
        tradeOffersTable.pack()

        val scrollHeight = min(tradeOffersTable.height, worldScreen.stage.height/2)
        add(ScrollPane(tradeOffersTable)).height(scrollHeight).row()

        addGoodSizedLabel(nation.tradeRequest).colspan(columns).row()

        val soundsGoodButton = addButton("Sounds good!"){
            val tradeLogic = TradeLogic(viewingCiv, requestingCiv)
            tradeLogic.currentTrade.set(trade)
            tradeLogic.acceptTrade()
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

        // In the meantime this became invalid, perhaps because we accepted previous trades
        if(!TradeEvaluation().isTradeValid(trade,viewingCiv,requestingCiv))
            soundsGoodButton.actor.disable()

        addButton("Not this time.".tr()){
            val diplomacyManager = requestingCiv.getDiplomacyManager(viewingCiv)
            if(trade.ourOffers.all { it.type==TradeType.Luxury_Resource } && trade.theirOffers.all { it.type==TradeType.Luxury_Resource })
                diplomacyManager.setFlag(DiplomacyFlags.DeclinedLuxExchange,20) // offer again in 20 turns
            if(trade.ourOffers.any { it.name==Constants.researchAgreement })
                diplomacyManager.setFlag(DiplomacyFlags.DeclinedResearchAgreement,20) // offer again in 20 turns

            if(trade.ourOffers.any{ it.type==TradeType.Treaty && it.name== Constants.peaceTreaty })
                diplomacyManager.setFlag(DiplomacyFlags.DeclinedPeace,5)

            close()
            requestingCiv.addNotification("[${viewingCiv.civName}] has denied your trade request", Color.GOLD)

            worldScreen.shouldUpdate=true
        }
        addButton("How about something else...".tr()){
            close()

            val diplomacyScreen= DiplomacyScreen(viewingCiv)
            val tradeTable =  diplomacyScreen.setTrade(requestingCiv)
            tradeTable.tradeLogic.currentTrade.set(trade)
            tradeTable.offerColumnsTable.update()
            worldScreen.game.setScreen(diplomacyScreen)
            worldScreen.shouldUpdate=true
        }
    }

    override fun close() {
        viewingCiv.tradeRequests.remove(tradeRequest)
        super.close()
    }
}
