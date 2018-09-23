package com.unciv.logic.trade

data class TradeOffer(var name:String, var type: TradeType, var duration:Int, var amount:Int=1) {

    constructor() : this("", TradeType.Gold,0,0) // so that the json deserializer can work

    fun equals(offer: TradeOffer): Boolean {
        return offer.name==name
                && offer.type==type
                && offer.amount==amount
    }
}