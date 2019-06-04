package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick

class TradeTable(val otherCivilization: CivilizationInfo, stage: Stage, onTradeComplete: () -> Unit): Table(CameraStageBaseScreen.skin){
    val currentPlayerCiv = otherCivilization.gameInfo.getCurrentPlayerCivilization()
    var tradeLogic = TradeLogic(currentPlayerCiv,otherCivilization)
    var offerColumnsTable = OfferColumnsTable(tradeLogic, stage) { onChange() }
    var offerColumnsTableWrapper = Table() // This is so that after a trade has been traded, we can switch out the offersToDisplay to start anew - this is the easiest way
    val tradeText = Label("", CameraStageBaseScreen.skin)
    val offerButton = TextButton("Offer trade".tr(), CameraStageBaseScreen.skin)

    fun letsHearIt(){
        val relationshipLevel = otherCivilization.getDiplomacyManager(currentPlayerCiv).relationshipLevel()
        if(relationshipLevel <= RelationshipLevel.Enemy)
            tradeText.setText(otherCivilization.getTranslatedNation().hateLetsHearIt.random().tr())
        else tradeText.setText(otherCivilization.getTranslatedNation().neutralLetsHearIt.random().tr())
    }

    init{
        letsHearIt()
        offerColumnsTableWrapper.add(offerColumnsTable)
        add(offerColumnsTableWrapper).row()

        val lowerTable = Table().apply { defaults().pad(10f) }

        lowerTable.add(tradeText).colspan(2).row()

        offerButton.onClick {
            val relationshipLevel = otherCivilization.getDiplomacyManager(currentPlayerCiv).relationshipLevel()
            if(offerButton.text.toString() == "Offer trade".tr()) {
                if(tradeLogic.currentTrade.theirOffers.size==0 && tradeLogic.currentTrade.ourOffers.size==0){
                    letsHearIt()
                }
                else if (TradeEvaluation().isTradeAcceptable(tradeLogic.currentTrade.reverse(),otherCivilization,currentPlayerCiv)){
                    if(relationshipLevel<=RelationshipLevel.Enemy)
                        tradeText.setText(otherCivilization.getTranslatedNation().hateYes.random().tr())
                    else tradeText.setText(otherCivilization.getTranslatedNation().neutralYes.random().tr())
                    offerButton.setText("Accept".tr())
                }
                else{
                    if(relationshipLevel<=RelationshipLevel.Enemy)
                        tradeText.setText(otherCivilization.getTranslatedNation().hateNo.random().tr())
                    tradeText.setText(otherCivilization.getTranslatedNation().neutralNo.random().tr())
                }
            }
            else if(offerButton.text.toString() == "Accept".tr()){
                tradeLogic.acceptTrade()
                offerColumnsTable = OfferColumnsTable(tradeLogic, stage) { onChange() }
                offerColumnsTableWrapper.clear()
                offerColumnsTableWrapper.add(offerColumnsTable)
                if(tradeLogic.currentTrade.ourOffers.any { it.name== Constants.peaceTreaty })
                    tradeText.setText(otherCivilization.getTranslatedNation().afterPeace)
                else tradeText.setText("Pleasure doing business with you!".tr())
                onTradeComplete()

                tradeLogic = TradeLogic(currentPlayerCiv,otherCivilization)
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
        letsHearIt()
    }

}