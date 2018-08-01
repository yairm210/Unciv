package com.unciv.ui

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

    val tradeLogic = TradeLogic(otherCivilization)
    val tradeTable = Table(skin)
    val tradeText = Label("What do you have in mind?".tr(),skin)
    val offerButton = TextButton("Offer trade".tr(),skin)


    val onChange = {
        update()
        offerButton.setText("Offer trade".tr())
        tradeText.setText("What do you have in mind?".tr())
    }

    val ourAvailableOffersTable = OffersList(tradeLogic.ourAvailableOffers, tradeLogic.currentTrade.ourOffers,
            tradeLogic.theirAvailableOffers, tradeLogic.currentTrade.theirOffers) { onChange() }
    val ourOffersTable = OffersList(tradeLogic.currentTrade.ourOffers, tradeLogic.ourAvailableOffers,
            tradeLogic.currentTrade.theirOffers, tradeLogic.theirAvailableOffers) { onChange() }
    val theirOffersTable = OffersList(tradeLogic.currentTrade.theirOffers, tradeLogic.theirAvailableOffers,
            tradeLogic.currentTrade.ourOffers, tradeLogic.ourAvailableOffers) { onChange() }
    val theirAvailableOffersTable = OffersList(tradeLogic.theirAvailableOffers, tradeLogic.currentTrade.theirOffers,
            tradeLogic.ourAvailableOffers, tradeLogic.currentTrade.ourOffers) { onChange() }

    init {
        val generalTable = Table()
        val closeButton = TextButton("Close".tr(), skin)
        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        stage.addActor(closeButton)


        tradeTable.add("Our items".tr())
        tradeTable.add("Our trade offer".tr())
        tradeTable.add("[${otherCivilization.civName}]'s trade offer".tr())
        tradeTable.add("[${otherCivilization.civName}]'s items".tr()).row()
        tradeTable.add(ourAvailableOffersTable).size(stage.width/5,stage.height*0.8f)
        tradeTable.add(ourOffersTable).size(stage.width/5,stage.height*0.8f)
        tradeTable.add(theirOffersTable).size(stage.width/5,stage.height*0.8f)
        tradeTable.add(theirAvailableOffersTable).size(stage.width/5,stage.height*0.8f)
        tradeTable.pack()

        generalTable.add(tradeTable).row()

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
                val newTradeScreen = TradeScreen(otherCivilization)
                newTradeScreen.tradeText.setText("Pleasure doing business with you!".tr())
                UnCivGame.Current.screen = newTradeScreen

            }
        }

        lowerTable.add(offerButton)

        lowerTable.pack()
        lowerTable.y = 10f
        generalTable.add(lowerTable)
        generalTable.pack()
        generalTable.center(stage)
        stage.addActor(generalTable)


        update()
    }

    fun update(){
        ourAvailableOffersTable.update()
        ourOffersTable.update()
        theirAvailableOffersTable.update()
        theirOffersTable.update()
    }

}

