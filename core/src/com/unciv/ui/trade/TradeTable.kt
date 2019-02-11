package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.trade.TradeLogic
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick

class TradeTable(val otherCivilization: CivilizationInfo, stage: Stage, onTradeComplete: () -> Unit): Table(CameraStageBaseScreen.skin){
    val currentPlayerCiv = otherCivilization.gameInfo.getCurrentPlayerCivilization()
    var tradeLogic = TradeLogic(currentPlayerCiv,otherCivilization)
    var offerColumnsTable = OfferColumnsTable(tradeLogic, stage) { onChange() }
    var offerColumnsTableWrapper = Table() // This is so that after a trade has been traded, we can switch out the offers to start anew - this is the easiest way
    val tradeText = Label(otherCivilization.getNation().neutralLetsHearIt.random().tr(), CameraStageBaseScreen.skin)
    val offerButton = TextButton("Offer trade".tr(), CameraStageBaseScreen.skin)


    init{
        offerColumnsTableWrapper.add(offerColumnsTable)
        add(offerColumnsTableWrapper).row()

        val lowerTable = Table().apply { defaults().pad(10f) }

        lowerTable.add(tradeText).colspan(2).row()

        offerButton.onClick {
            if(offerButton.text.toString() == "Offer trade".tr()) {
                if(tradeLogic.currentTrade.theirOffers.size==0 && tradeLogic.currentTrade.ourOffers.size==0){
                    tradeText.setText(otherCivilization.getNation().neutralLetsHearIt.random().tr())
                }
                else if (tradeLogic.isTradeAcceptable()){
                    tradeText.setText(otherCivilization.getNation().neutralYes.random().tr())
                    offerButton.setText("Accept".tr())
                }
                else{
                    tradeText.setText(otherCivilization.getNation().neutralNo.random().tr())
                }
            }
            else if(offerButton.text.toString() == "Accept".tr()){
                tradeLogic.acceptTrade()
                tradeLogic = TradeLogic(currentPlayerCiv,otherCivilization)
                offerColumnsTable = OfferColumnsTable(tradeLogic, stage) { onChange() }
                offerColumnsTableWrapper.clear()
                offerColumnsTableWrapper.add(offerColumnsTable)
                tradeText.setText("Pleasure doing business with you!".tr())
                onTradeComplete()
                offerButton.setText("Offer trade".tr())
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
        tradeText.setText(otherCivilization.getNation().neutralLetsHearIt.random().tr())
    }

}