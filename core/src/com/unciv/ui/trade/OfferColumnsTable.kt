package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.trade.TradeLogic
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.tr

class OfferColumnsTable(tradeLogic: TradeLogic, stage: Stage, onChange: ()->Unit): Table(CameraStageBaseScreen.skin) {

    val ourAvailableOffersTable = OffersList(tradeLogic.ourAvailableOffers, tradeLogic.currentTrade.ourOffers,
            tradeLogic.theirAvailableOffers, tradeLogic.currentTrade.theirOffers) { onChange() }
    val ourOffersTable = OffersList(tradeLogic.currentTrade.ourOffers, tradeLogic.ourAvailableOffers,
            tradeLogic.currentTrade.theirOffers, tradeLogic.theirAvailableOffers) { onChange() }
    val theirOffersTable = OffersList(tradeLogic.currentTrade.theirOffers, tradeLogic.theirAvailableOffers,
            tradeLogic.currentTrade.ourOffers, tradeLogic.ourAvailableOffers) { onChange() }
    val theirAvailableOffersTable = OffersList(tradeLogic.theirAvailableOffers, tradeLogic.currentTrade.theirOffers,
            tradeLogic.ourAvailableOffers, tradeLogic.currentTrade.ourOffers) { onChange() }

    init {
        add("Our items".tr())
        add("Our trade offer".tr())
        add("[${tradeLogic.otherCivilization.civName}]'s trade offer".tr())
        add("[${tradeLogic.otherCivilization.civName}]'s items".tr()).row()
        val columnWidth = stage.width / 5f
        val columnHeight = stage.height * 0.8f
        add(ourAvailableOffersTable).size(columnWidth,columnHeight)
        add(ourOffersTable).size(columnWidth,columnHeight)
        add(theirOffersTable).size(columnWidth,columnHeight)
        add(theirAvailableOffersTable).size(columnWidth,columnHeight)
        pack()
    }

    fun update() {
        ourAvailableOffersTable.update()
        ourOffersTable.update()
        theirAvailableOffersTable.update()
        theirOffersTable.update()

    }
}