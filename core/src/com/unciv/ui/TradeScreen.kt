package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Counter
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.ui.utils.*
import kotlin.math.min


enum class TradeType{
    Luxury_Resource,
    Strategic_Resource,
    Gold,
    Gold_Per_Turn,
    City
}

data class TradeOffer(val name:String, val type:TradeType, val duration:Int) {
    fun getText(): String {
        var text = "{$name}"
        if(duration>0) text += " ($duration {turns})"
        return text.tr()
    }
}

class TradeOffersList():Counter<TradeOffer>(){}

class Trade(){
    val theirOffers = TradeOffersList()
    val ourOffers = TradeOffersList()
}


class TradeScreen(val otherCivilization: CivilizationInfo) : CameraStageBaseScreen(){

    val civInfo = UnCivGame.Current.gameInfo.getPlayerCivilization()
    val ourAvailableOffers = getAvailableOffers(civInfo)
    val theirAvailableOffers = getAvailableOffers(otherCivilization)
    val currentTrade = Trade()

    val table = Table(skin)
    val tradeText = Label("What do you have in mind?",skin)

    init {
        val closeButton = TextButton("Close".tr(), skin)
        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        stage.addActor(closeButton)
        stage.addActor(table)

        val lowerTable = Table().apply { defaults().pad(10f) }

        lowerTable.add(tradeText).row()
        val offerButton = TextButton("Offer trade",skin)
        offerButton.addClickListener {
            if(isTradeAcceptable(currentTrade)) tradeText.setText("That is acceptable.")
            else tradeText.setText("I think not.")
        }
        lowerTable.add(offerButton)

        lowerTable.pack()
        lowerTable.centerX(stage)
        lowerTable.y = 10f
        stage.addActor(lowerTable)


        update()
    }

    fun update(){
        table.clear()
        val ourAvailableOffersTable = ScrollPane(getTableOfOffers(ourAvailableOffers, currentTrade.ourOffers))
        val ourOffersTable = ScrollPane(getTableOfOffers(currentTrade.ourOffers, ourAvailableOffers))
        val theirOffersTable = ScrollPane(getTableOfOffers(currentTrade.theirOffers, theirAvailableOffers))
        val theirAvailableOffersTable = ScrollPane(getTableOfOffers(theirAvailableOffers, currentTrade.theirOffers))
        table.add("Our items")
        table.add("Our trade offer")
        table.add(otherCivilization.civName+"'s trade offer")
        table.add(otherCivilization.civName+"'s items").row()
        table.add(ourAvailableOffersTable).width(stage.width/4)
        table.add(ourOffersTable).width(stage.width/4)
        table.add(theirOffersTable).width(stage.width/4)
        table.add(theirAvailableOffersTable).width(stage.width/4)
        table.pack()
        table.center(stage)
    }

    fun getTableOfOffers(offers: TradeOffersList, correspondingOffers: TradeOffersList): Table {
        val table= Table(skin).apply { defaults().pad(5f) }
        for(offer in offers) {
            val tb = TextButton(offer.key.name+" ("+offer.value+")",skin)
            val amountPerClick =
                    if(offer.key.type==TradeType.Gold) 50
                    else 1
            if(offer.value>0) // for instance we have negative gold
                tb.addClickListener {
                    val amountTransfered = min(amountPerClick, offer.value)
                    offers.add(offer.key,-amountTransfered)
                    correspondingOffers.add(offer.key,amountTransfered)
                    update()
                }
            else tb.disable()
            table.add(tb).row()
        }
        return table
    }

    fun getAvailableOffers(civInfo: CivilizationInfo): TradeOffersList {
        val offers = TradeOffersList()
        for(entry in civInfo.getCivResources().filterNot { it.key.resourceType == ResourceType.Bonus }) {
            val resourceTradeType = if(entry.key.resourceType==ResourceType.Luxury) TradeType.Luxury_Resource
                else TradeType.Strategic_Resource
            offers.add(TradeOffer(entry.key.name, resourceTradeType, 30), entry.value)
        }
        offers.add(TradeOffer("Gold",TradeType.Gold,0),civInfo.gold)
        return offers
    }

    fun isTradeAcceptable(trade:Trade): Boolean {
        val sumOfTheirOffers = trade.theirOffers.map { evaluateOffer(it.key,false)*it.value }.sum()
        val sumOfOurOffers = trade.ourOffers.map { evaluateOffer(it.key,true)*it.value }.sum()
        return sumOfOurOffers >= sumOfTheirOffers
    }

    fun evaluateOffer(offer:TradeOffer, otherCivIsRecieving:Boolean): Int {
        if(offer.type==TradeType.Gold) return 1
        if(offer.type == TradeType.Luxury_Resource){
            if(!theirAvailableOffers.containsKey(offer)) // We want to take away their last luxury or give them one they don't have
                return 250
            if(!otherCivIsRecieving && !ourAvailableOffers.containsKey(offer)) return 250 // they're giving us a luxury we don't have yet
            return 100 // this is useful only as a barter trade to other civs
        }
        if(offer.type == TradeType.Strategic_Resource){
            return 50
        }
        return 1000 // Dunno what this is?
    }
}