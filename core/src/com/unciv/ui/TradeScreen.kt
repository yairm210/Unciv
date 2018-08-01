package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.trade.TradeLogic
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.center
import com.unciv.ui.utils.tr

class TradeScreen(otherCivilization: CivilizationInfo) : CameraStageBaseScreen(){

    init {
        val closeButton = TextButton("Close".tr(), skin)
        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        stage.addActor(closeButton)


        val generalTable = TradeTable(otherCivilization,stage)
        generalTable.center(stage)

        stage.addActor(generalTable)
    }

}

class TradeTable(val otherCivilization: CivilizationInfo, stage: Stage):Table(CameraStageBaseScreen.skin){
    var tradeLogic = TradeLogic(otherCivilization)
    var offerColumnsTable = OfferColumnsTable(tradeLogic,stage) {onChange()}
    var offerColumnsTableWrapper = Table() // This is so that after a trade has been traded, we can switch out the offers to start anew - this is the easiest way
    val tradeText = Label("What do you have in mind?".tr(), CameraStageBaseScreen.skin)
    val offerButton = TextButton("Offer trade".tr(), CameraStageBaseScreen.skin)


    init{
        offerColumnsTableWrapper.add(offerColumnsTable)
        add(offerColumnsTableWrapper).row()

        val lowerTable = Table().apply { defaults().pad(10f) }

        lowerTable.add(tradeText).colspan(2).row()

        offerButton.addClickListener {
            if(offerButton.text.toString() == "Offer trade".tr()) {
                if(tradeLogic.currentTrade.theirOffers.size==0 && tradeLogic.currentTrade.ourOffers.size==0){
                    tradeText.setText("There's nothing on the table.".tr())
                }
                else if (tradeLogic.isTradeAcceptable()){
                    tradeText.setText("That is acceptable.".tr())
                    offerButton.setText("Accept".tr())
                }
                else{
                    tradeText.setText("I think not.".tr())
                }
            }
            else if(offerButton.text.toString() == "Accept".tr()){
                tradeLogic.acceptTrade()
                tradeLogic = TradeLogic(otherCivilization)
                offerColumnsTable = OfferColumnsTable(tradeLogic,stage){onChange()}
                offerColumnsTableWrapper.clear()
                offerColumnsTableWrapper.add(offerColumnsTable)
                tradeText.setText("Pleasure doing business with you!".tr())
            }
        }

        lowerTable.add(offerButton)

        lowerTable.pack()
        lowerTable.y = 10f
        add(lowerTable)
        pack()
    }

    private fun onChange(){
        offerColumnsTable.update()
        offerButton.setText("Offer trade".tr())
        tradeText.setText("What do you have in mind?".tr())
    }

}


class OfferColumnsTable(tradeLogic: TradeLogic, stage: Stage, onChange: ()->Unit):Table(CameraStageBaseScreen.skin) {

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