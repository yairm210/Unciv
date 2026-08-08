package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.DiplomacyAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.ui.screens.diplomacyscreen.LeaderIntroTable
import kotlin.math.max
import kotlin.math.min
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

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
class TradePopup(private val worldScreen: WorldScreen) : Popup(worldScreen) {
    private val viewingCiv = worldScreen.viewingCiv
    private val tradeRequest = viewingCiv.tradeRequests.first()
    private val isAskingForEmbassy = tradeRequest.trade.ourOffers.any { it.type == TradeOfferType.Embassy }
    private val requestingCiv = worldScreen.gameInfo.getCivilization(tradeRequest.requestingCiv)
    private val nation = requestingCiv.nation
    private val trade = tradeRequest.trade
    private val ourResources = viewingCiv.getCivResourcesByName()
    private val leaderIntroTable = LeaderIntroTable(requestingCiv)

    init {
        if (isAskingForEmbassy && viewingCiv.declineAllEmbassyRequests) {
            tradeRequest.decline(viewingCiv)
            viewingCiv.addNotification(
                "We have automatically declined an [Embassy] request from [${requestingCiv.civName}].",
                DiplomacyAction(requestingCiv),
                NotificationCategory.Diplomacy)
            close()
        } else {
            update()
            open()
        }
    }

    private fun update() {
        add(leaderIntroTable).colspan(2)
        addSeparator(colSpan = 2)

        val tradeOffersTable = Table().apply { defaults().pad(10f) }
        tradeOffersTable.add("[${nation.name}]'s trade offer".toLabel())
        // empty column to separate offers columns better
        tradeOffersTable.add().pad(0f, 15f)
        tradeOffersTable.add("Our trade offer".toLabel())
        tradeOffersTable.row()

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
        add(ScrollPane(tradeOffersTable)).height(scrollHeight).colspan(2).row()

        addSeparator(Color.DARK_GRAY, colSpan = 2, height = 1f)

        // Starting playback here assumes the TradePopup is shown immediately
        UncivGame.Current.musicController.playVoice("${requestingCiv.civName}.tradeRequest")
        addGoodSizedLabel(nation.tradeRequest).pad(15f).colspan(2).row()

        addButton("Sounds good!", 'y', action = ::accept).colspan(2).row()

        if (isAskingForEmbassy) {
            addButton("Not this time.", 'n', action = ::decline)
            addButton("Decline all [Embassy] requests.", 'n') {
                viewingCiv.declineAllEmbassyRequests = true
                decline()
            }.row()
        } else {
            addButton("Not this time.", 'n', action = ::decline).colspan(2).row()
        }

        addButton("How about something else...", 'e', action = ::counterOffer).colspan(2).row()
    }

    private fun getOfferText(offer: TradeOffer): String {
        var tradeText = offer.getOfferText()
        if (offer.type == TradeOfferType.Luxury_Resource || offer.type == TradeOfferType.Strategic_Resource)
            tradeText += "\n" + "Owned by you: [${ourResources[offer.name]}]".tr()
        return tradeText
    }

    private fun accept() {
        val tradeLogic = TradeLogic(viewingCiv, requestingCiv)
        tradeLogic.currentTrade.set(trade)
        tradeLogic.acceptTrade()
        close()
        TradeThanksPopup(leaderIntroTable, worldScreen)
        requestingCiv.addNotification("[${viewingCiv.civName}] has accepted your trade request", NotificationCategory.Trade, viewingCiv.civName, NotificationIcon.Trade)
    }

    private fun decline() {
        tradeRequest.decline(viewingCiv)
        close()
        requestingCiv.addNotification("[${viewingCiv.civName}] has denied your trade request", NotificationCategory.Trade, viewingCiv.civName, NotificationIcon.Trade)
        worldScreen.shouldUpdate = true
    }

    private fun counterOffer() {
        close()
        worldScreen.game.pushScreen(DiplomacyScreen(viewingCiv, requestingCiv, trade))
        worldScreen.shouldUpdate = true
    }

    override fun close() {
        viewingCiv.tradeRequests.remove(tradeRequest)
        super.close()
    }

    class TradeThanksPopup(leaderIntroTable: LeaderIntroTable, worldScreen: WorldScreen) : Popup(worldScreen) {
        init {
            add(leaderIntroTable)
            addSeparator().padBottom(15f)
            addGoodSizedLabel("Excellent!").row()
            addCloseButton("Farewell.", KeyCharAndCode.SPACE) {
                worldScreen.shouldUpdate = true
                // in all cases, worldScreen.shouldUpdate should be set to true when we remove the last of the popups
                // in order for the next trade to appear immediately
            }
            open()
        }
    }
}
