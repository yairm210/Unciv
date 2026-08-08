package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.multiplayer.AuthoritativeUnitActions
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.ui.screens.diplomacyscreen.LeaderIntroTable
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import yairm210.purity.annotations.Readonly
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
class TradePopup(val worldScreen: WorldScreen) : Popup(worldScreen) {
    val viewingCiv = worldScreen.viewingCiv
    val tradeRequest = viewingCiv.tradeRequests.first()

    init {
        val requestingCiv = worldScreen.gameInfo.getCivilization(tradeRequest.requestingCiv)
        val nation = requestingCiv.nation
        val trade = tradeRequest.trade


        val ourResources = viewingCiv.getCivResourcesByName()

        val leaderIntroTable = LeaderIntroTable(requestingCiv)
        add(leaderIntroTable)
        addSeparator()

        val tradeOffersTable = Table().apply { defaults().pad(10f) }
        tradeOffersTable.add("[${nation.name}]'s trade offer".toLabel())
        // empty column to separate offers columns better
        tradeOffersTable.add().pad(0f, 15f)
        tradeOffersTable.add("Our trade offer".toLabel())
        tradeOffersTable.row()

        
        @Readonly
        fun getOfferText(offer:TradeOffer): String {
            var tradeText = offer.getOfferText()
            if (offer.type == TradeOfferType.Luxury_Resource || offer.type == TradeOfferType.Strategic_Resource)
                tradeText += "\n" + "Owned by you: [${ourResources[offer.name]}]".tr()
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

        // Starting playback here assumes the TradePopup is shown immediately
        UncivGame.Current.musicController.playVoice("${requestingCiv.civName}.tradeRequest")
        addGoodSizedLabel(nation.tradeRequest).pad(15f).row()

        addButton("Sounds good!", 'y') {
            val tradeLogic = TradeLogic(viewingCiv, requestingCiv)
            tradeLogic.currentTrade.set(trade)
            tradeLogic.acceptTrade()
            val requestingName = tradeRequest.requestingCiv
            close()
            TradeThanksPopup(leaderIntroTable, worldScreen)
            requestingCiv.addNotification("[${viewingCiv.civName}] has accepted your trade request", NotificationCategory.Trade, viewingCiv.civName, NotificationIcon.Trade)
            postTradeAction("acceptTrade", requestingName, reloadOnSuccess = true)
        }.row()

        addButton("Not this time.", 'n') {
            tradeRequest.decline(viewingCiv)
            val requestingName = tradeRequest.requestingCiv
            close()
            requestingCiv.addNotification("[${viewingCiv.civName}] has denied your trade request", NotificationCategory.Trade, viewingCiv.civName, NotificationIcon.Trade)
            worldScreen.shouldUpdate = true
            postTradeAction("declineTrade", requestingName, reloadOnSuccess = false)
        }.row()

        addButton("How about something else...", 'e') {
            val requestingName = tradeRequest.requestingCiv
            close()
            worldScreen.game.pushScreen(DiplomacyScreen(viewingCiv, requestingCiv, trade))
            worldScreen.shouldUpdate = true
            postTradeAction("dismissTrade", requestingName, reloadOnSuccess = false)
        }.row()
    }

    private fun postTradeAction(type: String, requestingCiv: String, reloadOnSuccess: Boolean) {
        if (!AuthoritativeUnitActions.isEnabled(worldScreen.gameInfo)) return
        val gameId = worldScreen.gameInfo.gameId
        Concurrency.run("AuthTrade-$type") {
            var errMsg = "Server-authoritative $type failed"
            val newSave = AuthoritativeUnitActions.requestTradeAction(gameId, type, requestingCiv) {
                errMsg = it
            }
            launchOnGLThread {
                if (newSave == null) {
                    ToastPopup(errMsg, worldScreen)
                    return@launchOnGLThread
                }
                if (reloadOnSuccess) {
                    worldScreen.game.loadGame(AuthoritativeUnitActions.loadReturnedSave(newSave))
                }
            }
        }
    }

    override fun close() {
        viewingCiv.tradeRequests.remove(tradeRequest)
        if (AuthoritativeUnitActions.isEnabled(worldScreen.gameInfo)) {
            AuthoritativeUnitActions.rememberDismissedTradeRequest(
                tradeRequest.requestingCiv,
                AuthoritativeUnitActions.tradeSummary(tradeRequest.trade)
            )
        }
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
