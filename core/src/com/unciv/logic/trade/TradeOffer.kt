package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.translations.tr
import com.unciv.ui.utils.Fonts
import com.unciv.logic.trade.TradeType.TradeTypeNumberType

data class TradeOffer(val name:String, val type:TradeType, var amount:Int = 1, private var _duration: Int? = null) {

    /**
     * It could be that UncivGame.Current is not initialized when unzipping saves, leading to crashes
     * The easiest way around this, is to only access the UncivGame.Current whenever the data is needed
     * instead of when the object is created. The obvious way to do that would be to use a lazy.
     * Those are, sadly, immutable, and as duration also contains the amount of turns left for a trade,
     * it needs to be mutable. So the next logical solution would be to use a function for accessing
     * the value, which is basically what is done here. The duration variable is used in the API,
     * while internally values are saved in the _duration variable.
    */
    var duration: Int
        get() {
            if (_duration == null) {
                // Do *not* access UncivGame.Current.gameInfo in the default constructor!
                val gameSpeed = UncivGame.Current.gameInfo.gameParameters.gameSpeed
                val correctDuration = when {
                    type.isImmediate -> -1 // -1 for offers that are immediate (e.g. gold transfer)
                    name == Constants.peaceTreaty -> 10
                    gameSpeed == GameSpeed.Quick -> 25
                    else -> (30 * gameSpeed.modifier).toInt()
                }
                _duration = correctDuration
            }
            return _duration!!
        }
        set (newValue: Int) {
            _duration = newValue
        }
    
    constructor() : this("", TradeType.Gold, _duration = null) // so that the json deserializer can work

    @Suppress("CovariantEquals")    // This is an overload, not an override of the built-in equals(Any?)
    fun equals(offer: TradeOffer): Boolean {
        return offer.name == name
                && offer.type == type
                && offer.amount == amount
    }

    fun getOfferText(untradable: Int = 0): String {
        var offerText = when(type){
            TradeType.WarDeclaration -> "Declare war on [$name]"
            TradeType.Introduction -> "Introduction to [$name]"
            TradeType.City -> UncivGame.Current.gameInfo.getCities().firstOrNull{ it.id == name }?.name ?: "Non-existent city"
            else -> name
        }.tr()

        if (type.numberType == TradeTypeNumberType.Simple || name == Constants.researchAgreement) offerText += " ($amount)"
        else if (type.numberType == TradeTypeNumberType.Gold) offerText += " ($amount)"
       
        if (duration > 0) offerText += "\n" + duration + Fonts.turn

        if (untradable == 1) {
            offerText += "\n" + "+[${untradable}] untradable copy".tr()
        } else if (untradable > 1) {
            offerText += "\n" + "+[${untradable}] untradable copies".tr()
        }

        return offerText
    }
}
