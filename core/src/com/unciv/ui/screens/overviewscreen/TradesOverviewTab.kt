package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffersList
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen

class TradesOverviewTab(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    val game = overviewScreen.game

    init {
        defaults().pad(10f)
        val diplomaciesWithPendingTrade = viewingPlayer.diplomacy.values.filter { it.otherCiv.tradeRequests.any { it.requestingCiv == viewingPlayer.civName } }
        if (diplomaciesWithPendingTrade.isNotEmpty())
            add("Pending trades".toLabel(fontSize = Constants.headingFontSize)).padTop(10f).row()
        for (diplomacy in diplomaciesWithPendingTrade) {
            for (tradeRequest in diplomacy.otherCiv.tradeRequests.filter { it.requestingCiv == viewingPlayer.civName })
                add(createTradeTable(tradeRequest.trade.reverse(), diplomacy.otherCiv)).row()
        }

        val diplomaciesWithExistingTrade = viewingPlayer.diplomacy.values.filter { it.trades.isNotEmpty() }
            .sortedWith { diplomacyManager1, diplomacyManager2 ->
                val d1OffersFromFirstTrade = diplomacyManager1.trades.first().ourOffers
                val d2OffersFromFirstTrade = diplomacyManager2.trades.first().ourOffers
                val d1MaxDuration = if (d1OffersFromFirstTrade.isEmpty()) 0 else d1OffersFromFirstTrade.maxByOrNull { it.duration }!!.duration
                val d2MaxDuration = if (d2OffersFromFirstTrade.isEmpty()) 0 else d2OffersFromFirstTrade.maxByOrNull { it.duration }!!.duration
                when {
                    d1MaxDuration > d2MaxDuration -> 1
                    d1MaxDuration == d2MaxDuration -> 0
                    else -> -1
                }
            }
        if (diplomaciesWithExistingTrade.isNotEmpty())
            add("Current trades".toLabel(fontSize = Constants.headingFontSize)).padTop(10f).row()
        for (diplomacy in diplomaciesWithExistingTrade) {
            for (trade in diplomacy.trades)
                add(createTradeTable(trade, diplomacy.otherCiv)).row()
        }
    }

    private fun createTradeTable(trade: Trade, otherCiv: Civilization) = Table().apply {
        add(createOffersTable(viewingPlayer, trade.ourOffers, trade.theirOffers.size)).minWidth(overviewScreen.stage.width/4).fillY()
        add(createOffersTable(otherCiv, trade.theirOffers, trade.ourOffers.size)).minWidth(overviewScreen.stage.width/4).fillY()
    }

    private fun createOffersTable(civ: Civilization, offersList: TradeOffersList, numberOfOtherSidesOffers: Int): Table {
        val table = Table()
        table.defaults().pad(10f)
        table.background = BaseScreen.skinStrings.getUiBackground(
            "OverviewScreen/TradesOverviewTab/OffersTable",
            tintColor = civ.nation.getOuterColor()
        )
        table.add(civ.civName.toLabel(civ.nation.getInnerColor())).row()
        table.addSeparator()
        for (offer in offersList) {
            var offerText = offer.getOfferText()
            if (!offerText.contains("\n")) offerText += "\n"
            table.add(offerText.toLabel(civ.nation.getInnerColor())).row()
        }
        repeat(numberOfOtherSidesOffers - offersList.size) {
            table.add("\n".toLabel())
                .row() // we want both sides of the general table to have the same number of rows
        }
        return table
    }
}
