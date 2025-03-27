package com.unciv.ui.screens.diplomacyscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeRequest
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.basescreen.BaseScreen

class TradeTable(
    private val civ: Civilization,
    private val otherCivilization: Civilization,
    diplomacyScreen: DiplomacyScreen
): Table(BaseScreen.skin) {
    internal val tradeLogic = TradeLogic(civ, otherCivilization)
    internal val offerColumnsTable = OfferColumnsTable(tradeLogic, diplomacyScreen , civ, otherCivilization) { onChange() }
    // This is so that after a trade has been traded, we can switch out the offersToDisplay to start anew - this is the easiest way
    private val offerColumnsTableWrapper = Table()

    val offerTradeText = "{Offer trade}\n({They'll decide on their turn})"
    private val offerButton = offerTradeText.toTextButton()

    private fun isTradeOffered() = otherCivilization.tradeRequests.any { it.requestingCiv == civ.civName }

    private fun retractOffer() {
        otherCivilization.tradeRequests.removeAll { it.requestingCiv == civ.civName }
        civ.cache.updateCivResources()
        offerButton.setText(offerTradeText.tr())
    }

    init {
        offerColumnsTableWrapper.add(offerColumnsTable)
        add(offerColumnsTableWrapper).row()

        val lowerTable = Table().apply { defaults().pad(10f) }

        val existingOffer = otherCivilization.tradeRequests.firstOrNull { it.requestingCiv == civ.civName }
        if (existingOffer != null) {
            tradeLogic.currentTrade.set(existingOffer.trade.reverse())
            offerColumnsTable.update()
        }

        if (isTradeOffered()) offerButton.setText("Retract offer".tr())
        else offerButton.apply { isEnabled = false }.setText(offerTradeText.tr())

        offerButton.onClick {
            if (isTradeOffered()) {
                retractOffer()
                return@onClick
            }
            // If there is a research agreement trade, make sure both civilizations should be able to pay for it.
            // If not lets add an extra gold offer to satisfy this.
            // There must be enough gold to add to the offer to satisfy this, otherwise the research agreement button would be disabled
            if (tradeLogic.currentTrade.ourOffers.any { it.name == Constants.researchAgreement}) {
                val researchCost = civ.diplomacyFunctions.getResearchAgreementCost(otherCivilization)
                val currentPlayerOfferedGold = tradeLogic.currentTrade.ourOffers.firstOrNull { it.type == TradeOfferType.Gold }?.amount ?: 0
                val otherCivOfferedGold = tradeLogic.currentTrade.theirOffers.firstOrNull { it.type == TradeOfferType.Gold }?.amount ?: 0
                val newCurrentPlayerGold = civ.gold + otherCivOfferedGold - researchCost
                val newOtherCivGold = otherCivilization.gold + currentPlayerOfferedGold - researchCost
                // Check if we require more gold from them
                if (newCurrentPlayerGold < 0) {
                    offerColumnsTable.addOffer( tradeLogic.theirAvailableOffers.first { it.type == TradeOfferType.Gold }
                            .copy(amount = -newCurrentPlayerGold), tradeLogic.currentTrade.theirOffers, tradeLogic.currentTrade.ourOffers)
                }
                // Check if they require more gold from us
                if (newOtherCivGold < 0) {
                    offerColumnsTable.addOffer( tradeLogic.ourAvailableOffers.first { it.type == TradeOfferType.Gold }
                            .copy(amount = -newOtherCivGold), tradeLogic.currentTrade.ourOffers, tradeLogic.currentTrade.theirOffers)
                }
            }

            otherCivilization.tradeRequests.add(TradeRequest(civ.civName, tradeLogic.currentTrade.reverse()))
            civ.cache.updateCivResources()
            offerButton.setText("Retract offer".tr())
        }

        lowerTable.add(offerButton)

        lowerTable.pack()
        lowerTable.y = 10f
        add(lowerTable)
        pack()
    }

    private fun onChange() {
        offerColumnsTable.update()
        retractOffer()
        offerButton.isEnabled = !(tradeLogic.currentTrade.theirOffers.size == 0 && tradeLogic.currentTrade.ourOffers.size == 0)
    }

    fun enableOfferButton(isEnabled: Boolean) {
        offerButton.isEnabled = isEnabled
    }
}
