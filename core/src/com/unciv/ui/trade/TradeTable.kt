package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeRequest
import com.unciv.models.translations.tr
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toTextButton

class TradeTable(val otherCivilization: CivilizationInfo, stage: DiplomacyScreen): Table(BaseScreen.skin) {
    val currentPlayerCiv = otherCivilization.gameInfo.getCurrentPlayerCivilization()
    var tradeLogic = TradeLogic(currentPlayerCiv,otherCivilization)
    var offerColumnsTable = OfferColumnsTable(tradeLogic, stage) { onChange() }
    // This is so that after a trade has been traded, we can switch out the offersToDisplay to start anew - this is the easiest way
    private var offerColumnsTableWrapper = Table()
    val offerButton = "Offer trade".toTextButton()

    private fun isTradeOffered() = otherCivilization.tradeRequests.any { it.requestingCiv == currentPlayerCiv.civName }

    private fun retractOffer(){
        otherCivilization.tradeRequests.removeAll { it.requestingCiv == currentPlayerCiv.civName }
        currentPlayerCiv.updateDetailedCivResources()
        offerButton.setText("Offer trade".tr())
    }

    init{
        offerColumnsTableWrapper.add(offerColumnsTable)
        add(offerColumnsTableWrapper).row()

        val lowerTable = Table().apply { defaults().pad(10f) }

        val existingOffer = otherCivilization.tradeRequests.firstOrNull { it.requestingCiv == currentPlayerCiv.civName }
        if (existingOffer != null){
            tradeLogic.currentTrade.set(existingOffer.trade.reverse())
            offerColumnsTable.update()
        }

        if (isTradeOffered()) offerButton.setText("Retract offer".tr())
        else offerButton.setText("Offer trade".tr())

        offerButton.onClick {
            if(isTradeOffered()) {
                retractOffer()
                return@onClick
            }

            otherCivilization.tradeRequests.add(TradeRequest(currentPlayerCiv.civName,tradeLogic.currentTrade.reverse()))
            currentPlayerCiv.updateDetailedCivResources()
            offerButton.setText("Retract offer".tr())
        }

        lowerTable.add(offerButton)

        lowerTable.pack()
        lowerTable.y = 10f
        add(lowerTable)
        pack()
    }

    private fun onChange(){
        offerColumnsTable.update()
        retractOffer()
        offerButton.isEnabled = !(tradeLogic.currentTrade.theirOffers.size == 0 && tradeLogic.currentTrade.ourOffers.size == 0)
    }

}
