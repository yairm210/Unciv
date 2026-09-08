package com.unciv.ui.screens.diplomacyscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOffersList
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AskNumberPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.view.ForeignCivView
import com.unciv.view.TradeView

/** This is the class that holds the 4 columns of the offers (ours/theirs/ offered/available) in trade */
class OfferColumnsTable(
    private val tradeView: TradeView,
    private val screen: DiplomacyScreen,
    private val ourCiv: ForeignCivView,
    private val theirCiv: ForeignCivView,
    private val onChange: () -> Unit
): Table(BaseScreen.skin) {

    fun addOffer(offer: TradeOffer, offerList: TradeOffersList, correspondingOfferList: TradeOffersList) {
        offerList.add(offer.copy())
        if (offer.type == TradeOfferType.Treaty) correspondingOfferList.add(offer.copy())
        onChange()
    }

    private fun offerClickImplementation(
        offer: TradeOffer,
        invert: Boolean,
        list: TradeOffersList,
        counterList: TradeOffersList,
        civ: ForeignCivView
    ) {
        when (offer.type) {
            TradeOfferType.Gold -> openGoldSelectionPopup(offer, list, civ.gold)
            TradeOfferType.Gold_Per_Turn -> openGoldSelectionPopup(offer, list, civ.getGoldPerTurn())
            else -> addOffer(if (invert) offer.copy(amount = -offer.amount) else offer, list, counterList)
        }
    }

    private val ourAvailableOffersTable = OffersListScroll("OurAvail") {
        offerClickImplementation(it, false, tradeView.ourStagedOffers(), tradeView.theirStagedOffers(), ourCiv)
    }
    private val ourOffersTable = OffersListScroll("OurTrade") {
        offerClickImplementation(it, true, tradeView.ourStagedOffers(), tradeView.theirStagedOffers(), ourCiv)
    }
    private val theirOffersTable = OffersListScroll("TheirTrade") {
        offerClickImplementation(it, true, tradeView.theirStagedOffers(), tradeView.ourStagedOffers(), theirCiv)
    }
    private val theirAvailableOffersTable = OffersListScroll("TheirAvail") {
        offerClickImplementation(it, false, tradeView.theirStagedOffers(), tradeView.ourStagedOffers(), theirCiv)
    }

    init {
        defaults().pad(5f)

        val isPortraitMode = screen.isNarrowerThan4to3()

        val columnWidth = screen.getTradeColumnsWidth() - 20f // Subtract padding: ours and OffersListScroll's

        if (!isPortraitMode) {
            // In landscape, arrange in 4 panels: ours left / theirs right ; items top / offers bottom.
            add("Our items".tr())
            add("[${theirCiv.civName}]'s items".tr()).row()

            add(ourAvailableOffersTable).prefSize(columnWidth, screen.stage.height / 2)
            add(theirAvailableOffersTable).prefSize(columnWidth, screen.stage.height / 2).row()

            addSeparator().height(2f)

            add("Our trade offer".tr())
            add("[${theirCiv.civName}]'s trade offer".tr()).row()
            add(ourOffersTable).size(columnWidth, screen.stage.height / 3)
            add(theirOffersTable).size(columnWidth, screen.stage.height / 3)
        } else {
            // In portrait, this will arrange the items lists vertically
            // and the offers still side-by-side below that
            add("Our items".tr()).colspan(2).row()
            add(ourAvailableOffersTable).height(screen.stage.height / 4f).colspan(2).row()

            addSeparator().height(2f)

            add("[${theirCiv.civName}]'s items".tr()).colspan(2).row()
            add(theirAvailableOffersTable).height(screen.stage.height / 4f).colspan(2).row()

            addSeparator().height(5f)

            add("Our trade offer".tr())
            add("[${theirCiv.civName}]'s trade offer".tr()).row()
            add(ourOffersTable).height(screen.stage.height / 4f).width(columnWidth)
            add(theirOffersTable).height(screen.stage.height / 4f).width(columnWidth)
        }
        pack()
        update()
    }

    fun update() {
        val ourFilteredOffers = tradeView.ourAvailableOffers().without(tradeView.ourStagedOffers())
        val theirFilteredOffers = tradeView.theirAvailableOffers().without(tradeView.theirStagedOffers())
        val ourUntradables = ourCiv.getPerTurnResourcesWithOriginsForTrade()
            .removeAll(Constants.tradable)
        val theirUntradables = theirCiv.getPerTurnResourcesWithOriginsForTrade()
            .removeAll(Constants.tradable)
        ourAvailableOffersTable.update(ourFilteredOffers, tradeView.theirAvailableOffers(), ourUntradables, ourCiv, theirCiv)
        ourOffersTable.update(tradeView.ourStagedOffers(), tradeView.theirAvailableOffers(), ourCiv = ourCiv, theirCiv = theirCiv)
        theirOffersTable.update(tradeView.theirStagedOffers(), tradeView.ourAvailableOffers(), ourCiv = ourCiv, theirCiv = theirCiv)
        theirAvailableOffersTable.update(theirFilteredOffers, tradeView.ourAvailableOffers(), theirUntradables, ourCiv, theirCiv)
    }

    private fun openGoldSelectionPopup(offer: TradeOffer, ourOffers: TradeOffersList, maxGold: Int) {
        val existingGoldOffer = ourOffers.firstOrNull { it.type == offer.type }
        if (existingGoldOffer != null)
            offer.amount = existingGoldOffer.amount
        AskNumberPopup(
            screen,
            label = "Enter the amount of gold",
            icon = ImageGetter.getStatIcon("Gold").surroundWithCircle(80f),
            defaultValue = offer.amount,
            amountButtons =
            if (offer.type == TradeOfferType.Gold) listOf(50, 500)
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
