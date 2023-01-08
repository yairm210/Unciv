package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOffersList
import com.unciv.logic.trade.TradeType
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.AskNumberPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.surroundWithCircle

/** This is the class that holds the 4 columns of the offers (ours/theirs/ offered/available) in trade */
class OfferColumnsTable(private val tradeLogic: TradeLogic, val screen: DiplomacyScreen, val onChange: () -> Unit): Table(BaseScreen.skin) {

    private fun addOffer(offer: TradeOffer, offerList: TradeOffersList, correspondingOfferList: TradeOffersList) {
        offerList.add(offer.copy())
        if (offer.type == TradeType.Treaty) correspondingOfferList.add(offer.copy())
        onChange()
    }

    private val ourAvailableOffersTable = OffersListScroll("OurAvail") {
        when (it.type) {
            TradeType.Gold -> openGoldSelectionPopup(it, tradeLogic.currentTrade.ourOffers, tradeLogic.ourCivilization.gold)
            TradeType.Gold_Per_Turn -> openGoldSelectionPopup(it, tradeLogic.currentTrade.ourOffers, tradeLogic.ourCivilization.statsForNextTurn.gold.toInt())
            else -> addOffer(it, tradeLogic.currentTrade.ourOffers, tradeLogic.currentTrade.theirOffers)
        }
    }

    private val ourOffersTable = OffersListScroll("OurTrade") {
        when (it.type) {
            TradeType.Gold -> openGoldSelectionPopup(it, tradeLogic.currentTrade.ourOffers, tradeLogic.ourCivilization.gold)
            TradeType.Gold_Per_Turn -> openGoldSelectionPopup(it, tradeLogic.currentTrade.ourOffers, tradeLogic.ourCivilization.statsForNextTurn.gold.toInt())
            else -> addOffer(it.copy(amount = -it.amount), tradeLogic.currentTrade.ourOffers, tradeLogic.currentTrade.theirOffers)
        }
    }
    private val theirOffersTable = OffersListScroll("TheirTrade") {
        when (it.type) {
            TradeType.Gold -> openGoldSelectionPopup(it, tradeLogic.currentTrade.theirOffers, tradeLogic.otherCivilization.gold)
            TradeType.Gold_Per_Turn -> openGoldSelectionPopup(it, tradeLogic.currentTrade.theirOffers, tradeLogic.otherCivilization.statsForNextTurn.gold.toInt())
            else -> addOffer(it.copy(amount = -it.amount), tradeLogic.currentTrade.theirOffers, tradeLogic.currentTrade.ourOffers)
        }
    }
    private val theirAvailableOffersTable = OffersListScroll("TheirAvail") {
        when (it.type) {
            TradeType.Gold -> openGoldSelectionPopup(it, tradeLogic.currentTrade.theirOffers, tradeLogic.otherCivilization.gold)
            TradeType.Gold_Per_Turn -> openGoldSelectionPopup(it, tradeLogic.currentTrade.theirOffers, tradeLogic.otherCivilization.statsForNextTurn.gold.toInt())
            else -> addOffer(it, tradeLogic.currentTrade.theirOffers, tradeLogic.currentTrade.ourOffers)
        }
    }

    init {
        defaults().pad(5f)
        val columnWidth = screen.stage.width / 3

        add("Our items".tr())
        add("[${tradeLogic.otherCivilization.civName}]'s items".tr()).row()

        add(ourAvailableOffersTable).prefSize(columnWidth, screen.stage.height / 2)
        add(theirAvailableOffersTable).prefSize(columnWidth, screen.stage.height / 2).row()

        addSeparator().height(2f)

        add("Our trade offer".tr())
        add("[${tradeLogic.otherCivilization.civName}]'s trade offer".tr()).row()
        add(ourOffersTable).size(columnWidth, screen.stage.height / 3)
        add(theirOffersTable).size(columnWidth, screen.stage.height / 3)
        pack()
        update()
    }

    fun update() {
        val ourFilteredOffers = tradeLogic.ourAvailableOffers.without(tradeLogic.currentTrade.ourOffers)
        val theirFilteredOffers = tradeLogic.theirAvailableOffers.without(tradeLogic.currentTrade.theirOffers)
        val ourUntradables = tradeLogic.ourCivilization.getCivResourcesWithOriginsForTrade()
            .removeAll(Constants.tradable)
        val theirUntradables = tradeLogic.otherCivilization.getCivResourcesWithOriginsForTrade()
            .removeAll(Constants.tradable)
        ourAvailableOffersTable.update(ourFilteredOffers, tradeLogic.theirAvailableOffers, ourUntradables)
        ourOffersTable.update(tradeLogic.currentTrade.ourOffers, tradeLogic.theirAvailableOffers)
        theirOffersTable.update(tradeLogic.currentTrade.theirOffers, tradeLogic.ourAvailableOffers)
        theirAvailableOffersTable.update(theirFilteredOffers, tradeLogic.ourAvailableOffers, theirUntradables)
    }

    private fun openGoldSelectionPopup(offer: TradeOffer, ourOffers: TradeOffersList, maxGold: Int) {
        val existingGoldOffer = ourOffers.firstOrNull { it.type == offer.type }
        if (existingGoldOffer != null)
            offer.amount = existingGoldOffer.amount
        AskNumberPopup(
            screen,
            label = "Enter the amount of gold",
            icon = ImageGetter.getStatIcon("Gold").surroundWithCircle(80f),
            defaultValue = offer.amount.toString(),
            amountButtons =
                if (offer.type == TradeType.Gold) listOf(50, 500)
                else listOf(5, 15),
            bounds = IntRange(0, maxGold),
            actionOnOk = { userInput ->
                offer.amount = userInput
                if (existingGoldOffer == null)
                    ourOffers.add(offer)
                else existingGoldOffer.amount = offer.amount
                if (offer.amount == 0) ourOffers.remove(offer)
                onChange()
            }
        ).open()
    }
}
