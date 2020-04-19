package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOffersList
import com.unciv.logic.trade.TradeType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.addSeparator

/** This is the class that holds the 4 columns of the offers (ours/theirs/ offered/available) in trade */
class OfferColumnsTable(val tradeLogic: TradeLogic, stage: Stage, val onChange: ()->Unit): Table(CameraStageBaseScreen.skin) {

    fun addOffer(offer:TradeOffer, offerList:TradeOffersList, correspondingOfferList:TradeOffersList){
        offerList.add(offer.copy())
        if(offer.type==TradeType.Treaty) correspondingOfferList.add(offer.copy())
        onChange()
    }

    // todo - add logic that treaties are added and removed from both sides of the table
    val ourAvailableOffersTable = OffersListScroll { addOffer(it,tradeLogic.currentTrade.ourOffers, tradeLogic.currentTrade.theirOffers) }
    val ourOffersTable = OffersListScroll { addOffer(it.copy(amount=-it.amount),tradeLogic.currentTrade.ourOffers, tradeLogic.currentTrade.theirOffers) }
    val theirOffersTable = OffersListScroll { addOffer(it.copy(amount=-it.amount),tradeLogic.currentTrade.theirOffers, tradeLogic.currentTrade.ourOffers) }
    val theirAvailableOffersTable = OffersListScroll { addOffer(it,tradeLogic.currentTrade.theirOffers, tradeLogic.currentTrade.ourOffers) }

    init {
        defaults().pad(5f)
        val columnWidth = stage.width / 3

        add("Our items".tr())
        add("[${tradeLogic.otherCivilization.civName}]'s items".tr()).row()

        add(ourAvailableOffersTable).size(columnWidth,stage.height/2)
        add(theirAvailableOffersTable).size(columnWidth,stage.height/2).row()

        addSeparator().height(2f)

        add("Our trade offer".tr())
        add("[${tradeLogic.otherCivilization.civName}]'s trade offer".tr()).row()
        add(ourOffersTable).size(columnWidth,stage.height/5)
        add(theirOffersTable).size(columnWidth,stage.height/5)
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
}