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
 * This popup opens itself unless it can be answered automatically (e.g. `declineAllEmbassyRequests` is on).
 *
 * @param worldScreen The parent screen
 */
class TradePopup(private val worldScreen: WorldScreen) : Popup(worldScreen) {
    private val viewingCiv = worldScreen.selectedGameView.civView.getCiv()
    private val ourResources = viewingCiv.getCivResourcesByName()
    private val tradeRequest = viewingCiv.tradeRequests.first()
    private val trade = tradeRequest.trade
    private val isAskingForEmbassy = trade.ourOffers.any { it.type == TradeOfferType.Embassy }
    private val requestingCiv = worldScreen.gameInfo.getCivilization(tradeRequest.requestingCiv)
    private val leaderIntroTable = LeaderIntroTable(requestingCiv)

    companion object {
        private const val tutorialTaskDeclineAll = "How to undo 'Decline all embassies' shown"
    }

    init {
        if (isAskingForEmbassy && viewingCiv.declineAllEmbassyRequests) {
            tradeRequest.decline(viewingCiv)
            viewingCiv.addNotification(
                "We have automatically declined an [Embassy] request from [${requestingCiv.civName}].",
                DiplomacyAction(requestingCiv),
                NotificationCategory.Trade, requestingCiv.civName, NotificationIcon.Trade
            )
            close()
        } else {
            update()
            open()
        }
    }

    private fun update() {
        val nation = requestingCiv.nation

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

        val scrollHeight = min(tradeOffersTable.height, worldScreen.stage.height / 2)
        add(ScrollPane(tradeOffersTable)).height(scrollHeight).colspan(2).row()

        addSeparator(Color.DARK_GRAY, colSpan = 2, height = 1f)

        // Starting playback here assumes the TradePopup is shown immediately
        UncivGame.Current.musicController.playVoice("${requestingCiv.civName}.tradeRequest")
        addGoodSizedLabel(nation.tradeRequest).pad(15f).colspan(2).row()

        addButton("Sounds good!", 'y', action = ::accept).colspan(2).row()

        if (isAskingForEmbassy) {
            addButton("Not this time.", 'n', action = ::decline)
            addButton("Decline all [Embassy] requests.", 'n', action = ::declineAll).row()
        } else {
            addButton("Not this time.", 'n', action = ::decline).colspan(2).row()
        }

        addButton("How about something else...", 'e', action = ::counterOffer).colspan(2).row()
    }

    @Readonly
    fun getOfferText(offer:TradeOffer): String {
        var tradeText = offer.getOfferText()
        if (offer.type == TradeOfferType.Luxury_Resource || offer.type == TradeOfferType.Strategic_Resource)
            tradeText += "\n" + "Owned by you: [${ourResources[offer.name]}]".tr()
        return tradeText
    }

    private fun reply(text: String) {
        requestingCiv.addNotification(
            "[${viewingCiv.civName}] has $text your trade request",
            NotificationCategory.Trade, viewingCiv.civName, NotificationIcon.Trade
        )
    }

    private fun accept() {
        val tradeLogic = TradeLogic(viewingCiv, requestingCiv)
        tradeLogic.currentTrade.set(trade)
        tradeLogic.acceptTrade()
        close()
        TradeThanksPopup()
        reply("accepted")
    }

    private fun decline() {
        tradeRequest.decline(viewingCiv)
        close()
        reply("denied")
        worldScreen.shouldUpdate = true
    }

    private fun declineAll() {
        viewingCiv.declineAllEmbassyRequests = true
        decline()
        if (worldScreen.game.settings.tutorialTasksCompleted.add(tutorialTaskDeclineAll))
            DeclineAllTutorial()
    }

    private fun counterOffer() {
        close()
        worldScreen.game.pushScreen {
            DiplomacyScreen(
                worldScreen.selectedGameView.civView,
                worldScreen.selectedGameView.getForeignCivView(requestingCiv),
                trade
            )
        }
        worldScreen.shouldUpdate = true
    }

    override fun close() {
        viewingCiv.tradeRequests.remove(tradeRequest)
        super.close()
    }

    private inner class TradeThanksPopup : Popup(worldScreen) {
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

    private inner class DeclineAllTutorial : Popup(worldScreen) {
        init {
            addGoodSizedLabel("From now on, for the rest of this game, all trade requests to accept an [Embassy] are declined as if you chose \"Not this time\".").row()
            addGoodSizedLabel("Simply offer an Embassy trade yourself to show such trade requests again - even if you retract it right away.").row()
            addCloseButton()
            open()
        }
    }
}
