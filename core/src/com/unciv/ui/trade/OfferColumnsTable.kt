package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.trade.TradeLogic
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.addSeparator
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
    }

    fun update() {
        ourAvailableOffersTable.update()
        ourOffersTable.update()
        theirAvailableOffersTable.update()
        theirOffersTable.update()

    }
}