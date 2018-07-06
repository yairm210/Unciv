package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
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

data class TradeOffer(var name:String, var type:TradeType, var duration:Int, var amount:Int) {

    constructor() : this("",TradeType.Gold,0,0) // so that the json deserializer can work

    fun getText(): String {
        var text = "{$name}"
        if(duration>0) text += " ($duration {turns})"
        return text.tr()
    }
}

class TradeOffersList:ArrayList<TradeOffer>(){
    override fun add(element: TradeOffer): Boolean {
        val equivalentOffer = firstOrNull { it.name==element.name&&it.type==element.type }
        if(equivalentOffer==null){
            super.add(element)
            return true
        }
        equivalentOffer.amount += element.amount
        if(equivalentOffer.amount==0) remove(equivalentOffer)
        return true
    }
}

class Trade{
    fun reverse(): Trade {
        val newTrade = Trade()
        newTrade.theirOffers+=ourOffers.map { it.copy() }
        newTrade.ourOffers+=theirOffers.map { it.copy() }
        return newTrade
    }

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
    val offerButton = TextButton("Offer trade",skin)

    init {
        val closeButton = TextButton("Close".tr(), skin)
        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        stage.addActor(closeButton)
        stage.addActor(table)

        val lowerTable = Table().apply { defaults().pad(10f) }

        lowerTable.add(tradeText).colspan(2).row()

        offerButton.addClickListener {
            if(offerButton.text.toString() == "Offer trade") {
                if (isTradeAcceptable(currentTrade)){
                    tradeText.setText("That is acceptable.")
                    offerButton.setText("Accept")
                }
                else tradeText.setText("I think not.")
            }
            else if(offerButton.text.toString() == "Accept"){
                civInfo.diplomacy[otherCivilization.civName]!!.trades.add(currentTrade)
                otherCivilization.diplomacy[civInfo.civName]!!.trades.add(currentTrade.reverse())
                val newTradeScreen = TradeScreen(otherCivilization)
                UnCivGame.Current.screen = newTradeScreen
                newTradeScreen.tradeText.setText("Pleasure doing business with you!")
            }
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
        val ourAvailableOffersTable = getTableOfOffers(ourAvailableOffers, currentTrade.ourOffers)
        val ourOffersTable = getTableOfOffers(currentTrade.ourOffers, ourAvailableOffers)
        val theirOffersTable = getTableOfOffers(currentTrade.theirOffers, theirAvailableOffers)
        val theirAvailableOffersTable = getTableOfOffers(theirAvailableOffers, currentTrade.theirOffers)
        table.add("Our items")
        table.add("Our trade offer")
        table.add(otherCivilization.civName+"'s trade offer")
        table.add(otherCivilization.civName+"'s items").row()
        table.add(ourAvailableOffersTable).size(stage.width/4,stage.width/2)
        table.add(ourOffersTable).size(stage.width/4,stage.width/2)
        table.add(theirOffersTable).size(stage.width/4,stage.width/2)
        table.add(theirAvailableOffersTable).size(stage.width/4,stage.width/2)
        table.pack()
        table.center(stage)
    }

    fun getTableOfOffers(offers: TradeOffersList, correspondingOffers: TradeOffersList): ScrollPane {
        val table= Table(skin).apply { defaults().pad(5f) }
        for(offer in offers) {
            val tb = TextButton(offer.name+" ("+offer.amount+")",skin)
            val amountPerClick =
                    if(offer.type==TradeType.Gold) 50
                    else 1
            if(offer.amount>0)
                tb.addClickListener {
                    val amountTransferred = min(amountPerClick, offer.amount)
                    offers += offer.copy(amount = -amountTransferred)
                    correspondingOffers += offer.copy(amount = amountTransferred)
                    offerButton.setText("Offer trade")
                    tradeText.setText("What do you have in mind?")
                    update()
                }
            else tb.disable()  // for instance we have negative gold
            table.add(tb).row()
        }
        return ScrollPane(table)
    }

    fun getAvailableOffers(civInfo: CivilizationInfo): TradeOffersList {
        val offers = TradeOffersList()
        for(entry in civInfo.getCivResources().filterNot { it.key.resourceType == ResourceType.Bonus }) {
            val resourceTradeType = if(entry.key.resourceType==ResourceType.Luxury) TradeType.Luxury_Resource
                else TradeType.Strategic_Resource
            offers.add(TradeOffer(entry.key.name, resourceTradeType, 30, entry.value))
        }
        offers.add(TradeOffer("Gold",TradeType.Gold,0,civInfo.gold))
        return offers
    }

    fun isTradeAcceptable(trade:Trade): Boolean {
        val sumOfTheirOffers = trade.theirOffers.map { evaluateOffer(it,false) }.sum()
        val sumOfOurOffers = trade.ourOffers.map { evaluateOffer(it,true)}.sum()
        return sumOfOurOffers >= sumOfTheirOffers
    }

    fun evaluateOffer(offer:TradeOffer, otherCivIsRecieving:Boolean): Int {
        if(offer.type==TradeType.Gold) return 1
        if(offer.type == TradeType.Luxury_Resource){
            var value = 100*offer.amount
            if(!theirAvailableOffers.any { it.name==offer.name }) // We want to take away their last luxury or give them one they don't have
                value += 250
            else if(!otherCivIsRecieving && !ourAvailableOffers.any { it.name==offer.name })
                value += 250 // they're giving us a luxury we don't have yet
            return value // this is useful only as a barter trade to other civs
        }
        if(offer.type == TradeType.Strategic_Resource){
            return 50 * offer.amount
        }
        return 1000 // Dunno what this is?
    }
}