package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.trade.TradeLogic
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.addSeparator

/** This is the class that holds the 4 columns of the offers (ours/theirs/ offered/available) in trade */
class OfferColumnsTable(val tradeLogic: TradeLogic, stage: Stage, onChange: ()->Unit): Table(CameraStageBaseScreen.skin) {

    val ourAvailableOffersTable = OffersListScroll { tradeLogic.currentTrade.ourOffers.add(it); onChange() }
    val ourOffersTable = OffersListScroll { tradeLogic.currentTrade.ourOffers.add(it.copy(amount = -it.amount)); onChange() }
    val theirOffersTable = OffersListScroll { tradeLogic.currentTrade.theirOffers.add(it.copy(amount = -it.amount)); onChange() }
    val theirAvailableOffersTable = OffersListScroll { tradeLogic.currentTrade.theirOffers.add(it); onChange() }

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
        ourAvailableOffersTable.update(tradeLogic.ourAvailableOffers.without(tradeLogic.currentTrade.ourOffers))
        ourOffersTable.update(tradeLogic.currentTrade.ourOffers)
        theirOffersTable.update(tradeLogic.currentTrade.theirOffers)
        theirAvailableOffersTable.update(tradeLogic.theirAvailableOffers.without(tradeLogic.currentTrade.theirOffers))
    }
}