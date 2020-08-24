package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOffersList
import com.unciv.logic.trade.TradeType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

/** This is the class that holds the 4 columns of the offers (ours/theirs/ offered/available) in trade */
class OfferColumnsTable(val tradeLogic: TradeLogic, val screen: DiplomacyScreen, val onChange: ()->Unit): Table(CameraStageBaseScreen.skin) {

    fun addOffer(offer: TradeOffer, offerList: TradeOffersList, correspondingOfferList: TradeOffersList) {
        offerList.add(offer.copy())
        if (offer.type == TradeType.Treaty) correspondingOfferList.add(offer.copy())
        onChange()
    }

    val ourAvailableOffersTable = OffersListScroll {
        if (it.type == TradeType.Gold) openGoldSelectionPopup(it, tradeLogic.currentTrade.ourOffers, tradeLogic.ourCivilization)
        else addOffer(it, tradeLogic.currentTrade.ourOffers, tradeLogic.currentTrade.theirOffers)
    }
    val ourOffersTable = OffersListScroll {
        if (it.type == TradeType.Gold) openGoldSelectionPopup(it, tradeLogic.currentTrade.ourOffers, tradeLogic.ourCivilization)
        else addOffer(it.copy(amount = -it.amount), tradeLogic.currentTrade.ourOffers, tradeLogic.currentTrade.theirOffers)
    }
    val theirOffersTable = OffersListScroll {
        if (it.type == TradeType.Gold) openGoldSelectionPopup(it, tradeLogic.currentTrade.theirOffers, tradeLogic.otherCivilization)
        else addOffer(it.copy(amount = -it.amount), tradeLogic.currentTrade.theirOffers, tradeLogic.currentTrade.ourOffers)
    }
    val theirAvailableOffersTable = OffersListScroll {
        if (it.type == TradeType.Gold) openGoldSelectionPopup(it, tradeLogic.currentTrade.theirOffers, tradeLogic.otherCivilization)
        else addOffer(it, tradeLogic.currentTrade.theirOffers, tradeLogic.currentTrade.ourOffers)
    }

    init {
        defaults().pad(5f)
        val columnWidth = screen.stage.width / 3

        add("Our items".tr())
        add("[${tradeLogic.otherCivilization.civName}]'s items".tr()).row()

        add(ourAvailableOffersTable).size(columnWidth, screen.stage.height / 2)
        add(theirAvailableOffersTable).size(columnWidth, screen.stage.height / 2).row()

        addSeparator().height(2f)

        add("Our trade offer".tr())
        add("[${tradeLogic.otherCivilization.civName}]'s trade offer".tr()).row()
        add(ourOffersTable).size(columnWidth, screen.stage.height / 5)
        add(theirOffersTable).size(columnWidth, screen.stage.height / 5)
        pack()
        update()
    }

    fun update() {
        val ourFilteredOffers = tradeLogic.ourAvailableOffers.without(tradeLogic.currentTrade.ourOffers)
        val theirFilteredOffers = tradeLogic.theirAvailableOffers.without(tradeLogic.currentTrade.theirOffers)
        ourAvailableOffersTable.update(ourFilteredOffers, tradeLogic.theirAvailableOffers)
        ourOffersTable.update(tradeLogic.currentTrade.ourOffers, tradeLogic.theirAvailableOffers)
        theirOffersTable.update(tradeLogic.currentTrade.theirOffers, tradeLogic.ourAvailableOffers)
        theirAvailableOffersTable.update(theirFilteredOffers, tradeLogic.ourAvailableOffers)
    }


    fun openGoldSelectionPopup(offer: TradeOffer, ourOffers: TradeOffersList, offeringCiv: CivilizationInfo) {
        val selectionPopup = Popup(screen)
        val existingGoldOffer = ourOffers.firstOrNull { it.type == TradeType.Gold }
        if (existingGoldOffer != null)
            offer.amount = existingGoldOffer.amount
        val amountLabel = offer.amount.toLabel()
        val minitable = Table().apply { defaults().pad(5f) }

        fun incrementAmount(delta: Int) {
            offer.amount += delta
            if (offer.amount < 0) offer.amount = 0
            if (offer.amount > offeringCiv.gold) offer.amount = offeringCiv.gold
            amountLabel.setText(offer.amount)
        }

        minitable.add("-500".toTextButton().onClick { incrementAmount(-500) })
        minitable.add("-50".toTextButton().onClick { incrementAmount(-50) })
        minitable.add(amountLabel)
        minitable.add("+50".toTextButton().onClick { incrementAmount(50) })
        minitable.add("+500".toTextButton().onClick { incrementAmount(500) })
        selectionPopup.add(minitable).row()

        selectionPopup.addCloseButton {
            if (existingGoldOffer == null)
                ourOffers.add(offer)
            else existingGoldOffer.amount = offer.amount
            onChange()
        }
        selectionPopup.open()
    }

}