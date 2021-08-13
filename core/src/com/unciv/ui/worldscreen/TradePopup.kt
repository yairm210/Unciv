package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.translations.tr
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.trade.LeaderIntroTable
import com.unciv.ui.utils.*
import kotlin.math.max
import kotlin.math.min
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

/* TODO:
    different Notification wording for peace treaties?
    Let Notification jump to Diplomacy.trade with empty offers (accepted)
        or a clone of the rejected offer (denied) ...?
*/

/**
 * [Popup] communicating trade offers of others to the player.
 * 
 * Called in [WorldScreen].update, which checks if there are any in viewingCiv.tradeRequests.
 * 
 * @param worldScreen The parent screen
 */
class TradePopup(worldScreen: WorldScreen): Popup(worldScreen){
    val viewingCiv = worldScreen.viewingCiv
    val tradeRequest = viewingCiv.tradeRequests.first()

    init{
        val requestingCiv = worldScreen.gameInfo.getCivilization(tradeRequest.requestingCiv)
        val nation = requestingCiv.nation

        val leaderIntroTable = LeaderIntroTable(requestingCiv)
        add(leaderIntroTable)
        addSeparator()

        val trade = tradeRequest.trade
        val tradeOffersTable = Table().apply { defaults().pad(10f) }
        tradeOffersTable.add("[${nation.name}]'s trade offer".toLabel())
        // empty column to separate offers columns better
        tradeOffersTable.add().pad(0f, 15f)
        tradeOffersTable.add("Our trade offer".toLabel())
        tradeOffersTable.row()
        val ourResources = viewingCiv.getCivResourcesByName()

        fun getOfferText(offer:TradeOffer): String {
            var tradeText = offer.getOfferText()
            if (offer.type == TradeType.Luxury_Resource || offer.type == TradeType.Strategic_Resource)
                tradeText += "\n" + "Owned: [${ourResources[offer.name]}]".tr()
            return tradeText
        }

        for (i in 0..max(trade.theirOffers.lastIndex, trade.ourOffers.lastIndex)) {
            if (trade.theirOffers.lastIndex < i) tradeOffersTable.add()
            else tradeOffersTable.add(getOfferText(trade.theirOffers[i]).toLabel())
            tradeOffersTable.add()
            if (trade.ourOffers.lastIndex < i) tradeOffersTable.add()
            else tradeOffersTable.add(getOfferText(trade.ourOffers[i]).toLabel())
            tradeOffersTable.row()
        }
        tradeOffersTable.pack()

        val scrollHeight = min(tradeOffersTable.height, worldScreen.stage.height/2)
        add(ScrollPane(tradeOffersTable)).height(scrollHeight).row()

        addSeparator(Color.DARK_GRAY, height = 1f)

        addGoodSizedLabel(nation.tradeRequest).pad(15f).row()

        val soundsGoodButton = addButton("Sounds good!", 'y') {
            val tradeLogic = TradeLogic(viewingCiv, requestingCiv)
            tradeLogic.currentTrade.set(trade)
            tradeLogic.acceptTrade()
            close()
            TradeThanksPopup(leaderIntroTable, worldScreen)
            requestingCiv.addNotification("[${viewingCiv.civName}] has accepted your trade request", viewingCiv.civName, NotificationIcon.Trade)
        }

        // In the meantime this became invalid, perhaps because we accepted previous trades
        if(!TradeEvaluation().isTradeValid(trade,viewingCiv,requestingCiv))
            soundsGoodButton.actor.disable()

        addButton("Not this time.", 'n') {
            val diplomacyManager = requestingCiv.getDiplomacyManager(viewingCiv)
            if(trade.ourOffers.all { it.type == TradeType.Luxury_Resource } && trade.theirOffers.all { it.type==TradeType.Luxury_Resource })
                diplomacyManager.setFlag(DiplomacyFlags.DeclinedLuxExchange,20) // offer again in 20 turns
            if(trade.ourOffers.any { it.name == Constants.researchAgreement })
                diplomacyManager.setFlag(DiplomacyFlags.DeclinedResearchAgreement,20) // offer again in 20 turns
            if(trade.ourOffers.any { it.type == TradeType.Treaty && it.name == Constants.peaceTreaty })
                diplomacyManager.setFlag(DiplomacyFlags.DeclinedPeace,5)

            close()
            requestingCiv.addNotification("[${viewingCiv.civName}] has denied your trade request", viewingCiv.civName, NotificationIcon.Trade)

            worldScreen.shouldUpdate = true
        }

        addButton("How about something else...", 'e') {
            close()

            val diplomacyScreen = DiplomacyScreen(viewingCiv)
            val tradeTable = diplomacyScreen.setTrade(requestingCiv)
            tradeTable.tradeLogic.currentTrade.set(trade)
            tradeTable.offerColumnsTable.update()
            worldScreen.game.setScreen(diplomacyScreen)
            worldScreen.shouldUpdate = true
        }
    }

    override fun close() {
        viewingCiv.tradeRequests.remove(tradeRequest)
        super.close()
    }
    
    class TradeThanksPopup(leaderIntroTable: LeaderIntroTable, worldScreen: WorldScreen): Popup(worldScreen) {
        init {
            add(leaderIntroTable)
            addSeparator().padBottom(15f)
            addGoodSizedLabel("Excellent!").row()
            addCloseButton("Farewell.", KeyCharAndCode.SPACE) {
                worldScreen.shouldUpdate=true
                // in all cases, worldScreen.shouldUpdate should be set to true when we remove the last of the popups
                // in order for the next trade to appear immediately
            }
            open()
        }
    }
}
