package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.translations.tr

data class TradeOffer(var name:String, var type: TradeType, var amount:Int=1, var duration:Int=-1) {

    init {
        // Duration needs to be part of the variables defined at the top, so that it will be copied over with copy()
        duration = when(type){
            TradeType.Gold, TradeType.Technology, TradeType.Introduction, TradeType.WarDeclaration, TradeType.City -> -1 /** -1 for offers that are immediate (e.g. gold transfer) */
            else -> when(UncivGame.Current.gameInfo.gameParameters.gameSpeed){
                GameSpeed.Quick -> if (name==Constants.peaceTreaty) 10 else 25
                else -> ((if (name==Constants.peaceTreaty) 10 else 30) * UncivGame.Current.gameInfo.gameParameters.gameSpeed.modifier).toInt()
            }
        }
    }
    constructor() : this("", TradeType.Gold) // so that the json deserializer can work
    fun equals(offer: TradeOffer): Boolean {
        return offer.name==name
                && offer.type==type
                && offer.amount==amount
    }


    fun getOfferText(): String {
        var offerText = when(type){
            TradeType.WarDeclaration -> "Declare war on [$name]"
            TradeType.Introduction -> "Introduction to [$name]"
            TradeType.City -> UncivGame.Current.gameInfo.getCities().firstOrNull{ it.id == name }?.name ?: "Non-existent city"
            else -> name
        }.tr()
        if (type !in tradesToNotHaveNumbers || name=="Research Agreement") offerText += " ($amount)"
        if (duration > 0) offerText += "\n" + duration + " {turns}".tr()
        return offerText
    }

    private companion object{
        val tradesToNotHaveNumbers = listOf(TradeType.Technology, TradeType.City,
            TradeType.Introduction, TradeType.Treaty, TradeType.WarDeclaration)
    }

}