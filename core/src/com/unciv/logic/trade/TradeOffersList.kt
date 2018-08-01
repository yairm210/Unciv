package com.unciv.logic.trade

import java.util.*

class TradeOffersList: ArrayList<TradeOffer>(){
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