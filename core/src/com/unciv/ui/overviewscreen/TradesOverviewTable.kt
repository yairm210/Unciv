package com.unciv.ui.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffersList
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.toLabel

class TradesOverviewTab(
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    init {
        defaults().pad(10f)
        val diplomacies = viewingPlayer.diplomacy.values.filter { it.trades.isNotEmpty() }
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
        for (diplomacy in diplomacies) {
            for (trade in diplomacy.trades)
                add(createTradeTable(trade, diplomacy.otherCiv())).row()
        }
    }

    private fun createTradeTable(trade: Trade, otherCiv: CivilizationInfo): Table {
        val generalTable = Table()
        generalTable.add(createOffersTable(viewingPlayer, trade.ourOffers, trade.theirOffers.size)).minWidth(overviewScreen.stage.width/4).fillY()
        generalTable.add(createOffersTable(otherCiv, trade.theirOffers, trade.ourOffers.size)).minWidth(overviewScreen.stage.width/4).fillY()
        return generalTable
    }

    private fun createOffersTable(civ: CivilizationInfo, offersList: TradeOffersList, numberOfOtherSidesOffers: Int): Table {
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
        for (i in 1..numberOfOtherSidesOffers - offersList.size)
            table.add("\n".toLabel()).row() // we want both sides of the general table to have the same number of rows
        return table
    }
}
