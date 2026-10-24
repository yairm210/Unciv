package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.trade.TradeOfferType.TradeTypeNumberType
import com.unciv.models.ruleset.Speed
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import yairm210.purity.annotations.Readonly

data class TradeOffer(val name: String, val type: TradeOfferType, var amount: Int = 1, var duration: Int) : IsPartOfGameInfoSerialization {

    constructor(
        name: String,
        type: TradeOfferType,
        amount: Int = 1,
        speed: Speed
    ) : this(name, type, amount, duration = -1) {
        duration = when {
            type.isImmediate -> -1 // -1 for offers that are immediate (e.g. gold transfer)
            name == Constants.peaceTreaty || type == TradeOfferType.PeaceProposal -> speed.peaceDealDuration
            else -> speed.dealDuration
        }
    }

    constructor() : this("", TradeOfferType.Gold, duration = -1) // so that the json deserializer can work

    @Suppress("CovariantEquals", "WrongEqualsTypeParameter")    // This is an overload, not an override of the built-in equals(Any?)
    @Readonly
    fun equals(offer: TradeOffer): Boolean {
        return offer.name == name
                && offer.type == type
                && offer.amount == amount
    }

    @Readonly fun isTradable() = amount > 0

    @Readonly
    fun getOfferText(untradable: Int = 0): String {
        var offerText = when(type) {
            TradeOfferType.WarDeclaration -> "Declare war on [$name]"
            TradeOfferType.PeaceProposal -> "Make peace with [$name]"
            TradeOfferType.Introduction -> "Introduction to [$name]"
            TradeOfferType.City -> {
                val city =
                        UncivGame.Current.gameInfo!!.getCities().firstOrNull { it.id == name }
                city?.run { "{$name} (${population.population})" } ?: "Non-existent city"
            }
            else -> name
        }.tr(hideIcons = true)

        if (type.numberType == TradeTypeNumberType.Simple || type.numberType == TradeTypeNumberType.Gold) offerText += " ($amount)"
        else if (name == Constants.researchAgreement) offerText += " (-$amount${Fonts.gold})"

        if (duration > 0) offerText += "\n" + duration + Fonts.turn

        if (untradable == 1) {
            offerText += "\n" + "+[${untradable}] untradable copy".tr()
        } else if (untradable > 1) {
            offerText += "\n" + "+[${untradable}] untradable copies".tr()
        }

        return offerText
    }
}
