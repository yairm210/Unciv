package com.unciv.logic.trade

import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceType
import kotlin.math.min
import kotlin.math.sqrt

class TradeEvaluation{
    fun isTradeAcceptable(trade: Trade, evaluator: CivilizationInfo, tradePartner: CivilizationInfo): Boolean {
        val sumOfTheirOffers = trade.theirOffers.asSequence()
                .filter { it.type!= TradeType.Treaty } // since treaties should only be evaluated once for 2 sides
                .map { evaluateBuyCost(it,evaluator,tradePartner) }.sum()
        val sumOfOurOffers = trade.ourOffers.map { evaluateSellCost(it, evaluator, tradePartner)}.sum()
        return sumOfOurOffers <= sumOfTheirOffers
    }

    fun evaluateBuyCost(offer: TradeOffer, civInfo: CivilizationInfo, tradePartner: CivilizationInfo): Int {
        when (offer.type) {
            TradeType.Gold -> return offer.amount
            TradeType.Gold_Per_Turn -> return offer.amount * offer.duration
            TradeType.Treaty -> {
                if (offer.name == "Peace Treaty")
                    return evaluatePeaceCostForThem(civInfo,tradePartner) // Since it will be evaluated twice, once when they evaluate our offer and once when they evaluate theirs
                else return 1000
            }

            TradeType.Luxury_Resource -> {
                val weAreMissingThisLux = !civInfo.hasResource(offer.name)  // first off - do we want this for ourselves?

                val civsWhoWillTradeUsForTheLux = civInfo.diplomacy.values.map { it.civInfo } // secondly - should we buy this in order to resell it?
                        .filter { it != tradePartner }
                        .filter { !it.hasResource(offer.name) } //they don't have
                val ourResourceNames = civInfo.getCivResources().map { it.key.name }
                val civsWithLuxToTrade = civsWhoWillTradeUsForTheLux.filter {
                    // these are other civs who we could trade this lux away to, in order to get a different lux
                    it.getCivResources().any {
                        it.value > 1 && it.key.resourceType == ResourceType.Luxury //they have a lux we don't and will be willing to trade it
                                && !ourResourceNames.contains(it.key.name)
                    }
                }
                var numberOfCivsWhoWouldTradeUsForTheLux = civsWithLuxToTrade.count()

                var numberOfLuxesWeAreWillingToBuy = 0
                var cost = 0
                if (weAreMissingThisLux) {  // for ourselves
                    numberOfLuxesWeAreWillingToBuy += 1
                    cost += 250
                }

                while (numberOfLuxesWeAreWillingToBuy < offer.amount && numberOfCivsWhoWouldTradeUsForTheLux > 0) {
                    numberOfLuxesWeAreWillingToBuy += 1 // for reselling
                    cost += 50
                    numberOfCivsWhoWouldTradeUsForTheLux -= 1
                }

                return cost
            }

            TradeType.Strategic_Resource -> {
                val resources = civInfo.getCivResourcesByName()
                val amountWillingToBuy = resources[offer.name]!! - 2
                if (amountWillingToBuy <= 0) return 0 // we already have enough.
                val amountToBuyInOffer = min(amountWillingToBuy, offer.amount)

                val canUseForBuildings = civInfo.cities
                        .any { city -> city.cityConstructions.getBuildableBuildings().any { it.requiredResource == offer.name } }
                val canUseForUnits = civInfo.cities
                        .any { city -> city.cityConstructions.getConstructableUnits().any { it.requiredResource == offer.name } }
                if (!canUseForBuildings && !canUseForUnits) return 0

                return 50 * amountToBuyInOffer
            }

            TradeType.Technology -> return sqrt(GameBasics.Technologies[offer.name]!!.cost.toDouble()).toInt()*20
            TradeType.Introduction -> return 250
            TradeType.WarDeclaration -> {
                val nameOfCivToDeclareWarOn = offer.name.split(' ').last()
                val civToDeclareWarOn = civInfo.gameInfo.getCivilization(nameOfCivToDeclareWarOn)
                val threatToThem = Automation().threatAssessment(civInfo,civToDeclareWarOn)

                if(civInfo.diplomacy[nameOfCivToDeclareWarOn]!!.diplomaticStatus== DiplomaticStatus.War){
                    when (threatToThem) {
                        ThreatLevel.VeryLow -> return 0
                        ThreatLevel.Low -> return 0
                        ThreatLevel.Medium -> return 100
                        ThreatLevel.High -> return 500
                        ThreatLevel.VeryHigh -> return 1000
                    }
                }
                else return 0 // why should we pay you to go fight someone...?
            }
            TradeType.City -> {
                val city = tradePartner.cities.first { it.name==offer.name }
                val stats = city.cityStats.currentCityStats
                if(civInfo.happiness + city.cityStats.happinessList.values.sum() < 0)
                    return 0 // we can't really afford to go into negative happiness because of buying a city
                val sumOfStats = stats.culture+stats.gold+stats.science+stats.production+stats.happiness+stats.food
                return sumOfStats.toInt() * 100
            }
        }
    }

    fun evaluateSellCost(offer: TradeOffer, civInfo: CivilizationInfo, tradePartner: CivilizationInfo): Int {
        when (offer.type) {
            TradeType.Gold -> return offer.amount
            TradeType.Gold_Per_Turn -> return offer.amount * offer.duration
            TradeType.Treaty -> {
                if (offer.name == "Peace Treaty")
                    return evaluatePeaceCostForThem(civInfo,tradePartner) // Since it will be evaluated twice, once when they evaluate our offer and once when they evaluate theirs
                else return 1000
            }
            TradeType.Luxury_Resource -> {
                if(civInfo.getCivResourcesByName()[offer.name]!!>1)
                    return 250 // fair price
                else return 500 // you want to take away our last lux of this type?!
            }
            TradeType.Strategic_Resource -> return 50*offer.amount
            TradeType.Technology -> return sqrt(GameBasics.Technologies[offer.name]!!.cost.toDouble()).toInt()*20
            TradeType.Introduction -> return 250
            TradeType.WarDeclaration -> {
                val nameOfCivToDeclareWarOn = offer.name.split(' ').last()
                val civToDeclareWarOn = civInfo.gameInfo.getCivilization(nameOfCivToDeclareWarOn)
                val threatToUs = Automation().threatAssessment(civInfo, civToDeclareWarOn)

                when (threatToUs) {
                    ThreatLevel.VeryLow -> return 100
                    ThreatLevel.Low -> return 250
                    ThreatLevel.Medium -> return 500
                    ThreatLevel.High -> return 1000
                    ThreatLevel.VeryHigh -> return 10000 // no way boyo
                }
            }

            TradeType.City -> {
                val city = civInfo.cities.first { it.name==offer.name }
                val stats = city.cityStats.currentCityStats
                val sumOfStats = stats.culture+stats.gold+stats.science+stats.production+stats.happiness+stats.food
                return sumOfStats.toInt() * 100
            }
        }
    }

    fun evaluatePeaceCostForThem(ourCivilization: CivilizationInfo, otherCivilization: CivilizationInfo): Int {
        val ourCombatStrength = Automation().evaluteCombatStrength(ourCivilization)
        val theirCombatStrength = Automation().evaluteCombatStrength(otherCivilization)
        if(ourCombatStrength==theirCombatStrength) return 0
        if(ourCombatStrength==0) return -1000
        if(theirCombatStrength==0) return 1000 // Chumps got no cities or units
        if(ourCombatStrength>theirCombatStrength){
            val absoluteAdvantage = ourCombatStrength-theirCombatStrength
            val percentageAdvantage = absoluteAdvantage / theirCombatStrength.toFloat()
            return (absoluteAdvantage*percentageAdvantage).toInt() * 10
        }
        else{
            val absoluteAdvantage = theirCombatStrength-ourCombatStrength
            val percentageAdvantage = absoluteAdvantage / ourCombatStrength.toFloat()
            return -(absoluteAdvantage*percentageAdvantage).toInt() * 10
        }
    }

}