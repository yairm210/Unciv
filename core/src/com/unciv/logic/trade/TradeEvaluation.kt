package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.utils.extensions.toPercent
import com.unciv.ui.victoryscreen.RankingType
import kotlin.math.min
import kotlin.math.sqrt

class TradeEvaluation {

    fun isTradeValid(trade: Trade, offerer: CivilizationInfo, tradePartner: CivilizationInfo): Boolean {

        // Edge case time! Guess what happens if you offer a peace agreement to the AI for all their cities except for the capital,
        //  and then capture their capital THAT SAME TURN? It can agree, leading to the civilization getting instantly destroyed!
        if (trade.ourOffers.count { it.type == TradeType.City } == offerer.cities.size
                || trade.theirOffers.count { it.type == TradeType.City } == tradePartner.cities.size)
            return false

        for (offer in trade.ourOffers)
            if (!isOfferValid(offer, offerer, tradePartner))
                return false
        for (offer in trade.theirOffers)
            if (!isOfferValid(offer, tradePartner, offerer))
                return false
        return true
    }

    private fun isOfferValid(tradeOffer: TradeOffer, offerer: CivilizationInfo, tradePartner: CivilizationInfo): Boolean {

        fun hasResource(tradeOffer: TradeOffer): Boolean {
            val resourcesByName = offerer.getCivResourcesByName()
            return resourcesByName.containsKey(tradeOffer.name) && resourcesByName[tradeOffer.name]!! >= 0
        }

        return when (tradeOffer.type) {
            TradeType.Gold -> true // even if they go negative it's okay
            TradeType.Gold_Per_Turn -> true // even if they go negative it's okay
            TradeType.Treaty -> true
            TradeType.Agreement -> true
            TradeType.Luxury_Resource -> hasResource(tradeOffer)
            TradeType.Strategic_Resource -> hasResource(tradeOffer)
            TradeType.Technology -> true
            TradeType.Introduction -> !tradePartner.knows(tradeOffer.name) // You can't introduce them to someone they already know!
            TradeType.WarDeclaration -> true
            TradeType.City -> offerer.cities.any { it.id == tradeOffer.name }
        }
    }

    fun isTradeAcceptable(trade: Trade, evaluator: CivilizationInfo, tradePartner: CivilizationInfo): Boolean {
        return getTradeAcceptability(trade, evaluator, tradePartner) >= 0
    }

    fun getTradeAcceptability(trade: Trade, evaluator: CivilizationInfo, tradePartner: CivilizationInfo): Int {
        val sumOfTheirOffers = trade.theirOffers.asSequence()
                .filter { it.type != TradeType.Treaty } // since treaties should only be evaluated once for 2 sides
                .map { evaluateBuyCost(it, evaluator, tradePartner) }.sum()

        var sumOfOurOffers = trade.ourOffers.map { evaluateSellCost(it, evaluator, tradePartner) }.sum()

        // If we're making a peace treaty, don't try to up the bargain for people you don't like.
        // Leads to spartan behaviour where you demand more, the more you hate the enemy...unhelpful
        if (trade.ourOffers.none { it.name == Constants.peaceTreaty || it.name == Constants.researchAgreement }) {
            val relationshipLevel = evaluator.getDiplomacyManager(tradePartner).relationshipLevel()
            if (relationshipLevel == RelationshipLevel.Enemy) sumOfOurOffers = (sumOfOurOffers * 1.5).toInt()
            else if (relationshipLevel == RelationshipLevel.Unforgivable) sumOfOurOffers *= 2
        }

        return sumOfTheirOffers - sumOfOurOffers
    }

    fun evaluateBuyCost(offer: TradeOffer, civInfo: CivilizationInfo, tradePartner: CivilizationInfo): Int {
        when (offer.type) {
            TradeType.Gold -> return offer.amount
            TradeType.Gold_Per_Turn -> return offer.amount * offer.duration
            TradeType.Treaty -> {
                return when (offer.name) {
                    // Since it will be evaluated twice, once when they evaluate our offer and once when they evaluate theirs
                    Constants.peaceTreaty -> evaluatePeaceCostForThem(civInfo, tradePartner)
                    Constants.researchAgreement -> -offer.amount
                    else -> 1000
                }
            }

            TradeType.Luxury_Resource -> {
                val weLoveTheKingPotential = civInfo.cities.count { it.demandedResource == offer.name } * 50
                return if(!civInfo.hasResource(offer.name)) { // we can't trade on resources, so we are only interested in 1 copy for ourselves
                        weLoveTheKingPotential + when { // We're a lot more interested in luxury if low on happiness (AI is never low on happiness though)
                            civInfo.getHappiness() < 0 -> 450
                            civInfo.getHappiness() < 10 -> 350
                            else -> 300 // Higher than corresponding sell cost since a trade is mutually beneficial!
                        }
                    } else
                        0
            }

            TradeType.Strategic_Resource -> {
                val resources = civInfo.getCivResourcesByName()
                val amountWillingToBuy = 2 - resources[offer.name]!!
                if (amountWillingToBuy <= 0) return 0 // we already have enough.
                val amountToBuyInOffer = min(amountWillingToBuy, offer.amount)

                val canUseForBuildings = civInfo.cities
                        .any { city -> city.cityConstructions.getBuildableBuildings().any { it.getResourceRequirements().containsKey(offer.name) } }
                val canUseForUnits = civInfo.cities
                        .any { city -> city.cityConstructions.getConstructableUnits().any { it.getResourceRequirements().containsKey(offer.name) } }
                if (!canUseForBuildings && !canUseForUnits) return 0

                return 50 * amountToBuyInOffer
            }

            TradeType.Technology -> // Currently unused
                return (sqrt(civInfo.gameInfo.ruleSet.technologies[offer.name]!!.cost.toDouble())
                        * civInfo.gameInfo.speed.scienceCostModifier).toInt() * 20
            TradeType.Introduction -> return introductionValue(civInfo.gameInfo.ruleSet)
            TradeType.WarDeclaration -> {
                val civToDeclareWarOn = civInfo.gameInfo.getCivilization(offer.name)
                val threatToThem = Automation.threatAssessment(civInfo, civToDeclareWarOn)

                if (!civInfo.isAtWarWith(civToDeclareWarOn)) return 0 // why should we pay you to go fight someone...?
                else when (threatToThem) {
                    ThreatLevel.VeryLow -> return 0
                    ThreatLevel.Low -> return 0
                    ThreatLevel.Medium -> return 100
                    ThreatLevel.High -> return 500
                    ThreatLevel.VeryHigh -> return 1000
                }
            }
            TradeType.City -> {
                val city = tradePartner.cities.firstOrNull { it.id == offer.name }
                    ?: throw Exception("Got an offer for city id "+offer.name+" which does't seem to exist for this civ!")
                val stats = city.cityStats.currentCityStats
                val surrounded: Int = surroundedByOurCities(city, civInfo)
                if (civInfo.getHappiness() + city.cityStats.happinessList.values.sum() < 0)
                    return 0 // we can't really afford to go into negative happiness because of buying a city
                val sumOfStats = stats.culture + stats.gold + stats.science + stats.production + stats.happiness + stats.food + surrounded
                return sumOfStats.toInt() * 100
            }
            TradeType.Agreement -> {
                if (offer.name == Constants.openBorders) return 100
                throw Exception("Invalid agreement type!")
            }
        }
    }
    private fun surroundedByOurCities(city: CityInfo, civInfo: CivilizationInfo): Int {
        val borderingCivs: Set<String> = city.getNeighbouringCivs()
        if (borderingCivs.size == 1 && borderingCivs.contains(civInfo.civName)) {
            return 10 * civInfo.getEraNumber() // if the city is surrounded only by trading civ
        }
        if (borderingCivs.contains(civInfo.civName))
            return 2 * civInfo.getEraNumber() // if the city has a border with trading civ
        return 0
    }

    fun evaluateSellCost(offer: TradeOffer, civInfo: CivilizationInfo, tradePartner: CivilizationInfo): Int {
        when (offer.type) {
            TradeType.Gold -> return offer.amount
            TradeType.Gold_Per_Turn -> return offer.amount * offer.duration
            TradeType.Treaty -> {
                return when (offer.name) {
                    // Since it will be evaluated twice, once when they evaluate our offer and once when they evaluate theirs
                    Constants.peaceTreaty -> evaluatePeaceCostForThem(civInfo, tradePartner)
                    Constants.researchAgreement -> -offer.amount
                    else -> 1000
                }
            }
            TradeType.Luxury_Resource -> {
                return when {
                    civInfo.getCivResourcesByName()[offer.name]!! > 1 -> 250 // fair price
                    civInfo.hasUnique(UniqueType.RetainHappinessFromLuxury) -> // If we retain 50% happiness, value at 375
                        750 - (civInfo.getMatchingUniques(UniqueType.RetainHappinessFromLuxury)
                            .first().params[0].toPercent() * 250).toInt()
                    else -> 500 // you want to take away our last lux of this type?!
                }
            }
            TradeType.Strategic_Resource -> {
                if (civInfo.gameInfo.spaceResources.contains(offer.name) &&
                    (civInfo.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts) ||
                            tradePartner.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts))
                )
                    return 10000 // We'd rather win the game, thanks

                if (!civInfo.isAtWar()) return 50 * offer.amount

                val canUseForUnits = civInfo.gameInfo.ruleSet.units.values
                    .any { it.getResourceRequirements().containsKey(offer.name)
                            && it.isBuildable(civInfo) }
                if (!canUseForUnits) return 50 * offer.amount

                val amountLeft = civInfo.getCivResourcesByName()[offer.name]
                    ?: throw Exception("Got a strategic resource offer but the the resource doesn't exist in this ruleset!")

                // Each strategic resource starts costing 100 more when we ass the 5 resources baseline
                // That is to say, if I have 4 and you take one away, that's 200
                // take away the third, that's 300, 2nd 400, 1st 500

                // So if he had 5 left, and we want to buy 2, then we want to buy his 5th and 4th last resources,
                // So we'll calculate how much he'll sell his 4th for (200) and his 5th for (100)
                var totalCost = 0

                // I know it's confusing, you're welcome to change to a more understandable way of counting if you can think of one...
                for (numberOfResource in (amountLeft - offer.amount + 1)..amountLeft) {
                    totalCost += if (numberOfResource > 5) 100
                                else (6 - numberOfResource) * 100
                }
                return totalCost
            }
            TradeType.Technology -> return sqrt(civInfo.gameInfo.ruleSet.technologies[offer.name]!!.cost.toDouble()).toInt() * 20
            TradeType.Introduction -> return introductionValue(civInfo.gameInfo.ruleSet)
            TradeType.WarDeclaration -> {
                val civToDeclareWarOn = civInfo.gameInfo.getCivilization(offer.name)

                return when (Automation.threatAssessment(civInfo, civToDeclareWarOn)) {
                    ThreatLevel.VeryLow -> 100
                    ThreatLevel.Low -> 250
                    ThreatLevel.Medium -> 500
                    ThreatLevel.High -> 1000
                    ThreatLevel.VeryHigh -> 10000 // no way boyo
                }
            }

            TradeType.City -> {
                val city = civInfo.cities.firstOrNull { it.id == offer.name }
                    ?: throw Exception("Got an offer to sell city id " + offer.name + " which does't seem to exist for this civ!")

                val capitalcity = civInfo.getCapital()!!
                val distanceCost = distanceCityTradeModifier(civInfo, capitalcity, city)
                val stats = city.cityStats.currentCityStats
                val sumOfStats =
                    stats.culture + stats.gold + stats.science + stats.production + stats.happiness + stats.food - distanceCost
                return sumOfStats.toInt() * 100
            }
            TradeType.Agreement -> {
                if (offer.name == Constants.openBorders) {
                    return when (civInfo.getDiplomacyManager(tradePartner).relationshipLevel()) {
                        RelationshipLevel.Unforgivable -> 10000
                        RelationshipLevel.Enemy -> 2000
                        RelationshipLevel.Competitor -> 500
                        RelationshipLevel.Neutral, RelationshipLevel.Afraid -> 200
                        RelationshipLevel.Favorable, RelationshipLevel.Friend, RelationshipLevel.Ally -> 100
                    }
                }
                throw Exception("Invalid agreement type!")
            }
        }
    }

    fun distanceCityTradeModifier(civInfo: CivilizationInfo, capitalcity: CityInfo, city: CityInfo): Int{
        val distanceBetweenCities = capitalcity.getCenterTile().aerialDistanceTo(city.getCenterTile())

        if (distanceBetweenCities < 500) return 0
        return min(50,  (500 - distanceBetweenCities) * civInfo.getEraNumber())
    }

    fun evaluatePeaceCostForThem(ourCivilization: CivilizationInfo, otherCivilization: CivilizationInfo): Int {
        val ourCombatStrength = ourCivilization.getStatForRanking(RankingType.Force)
        val theirCombatStrength = otherCivilization.getStatForRanking(RankingType.Force)
        if (ourCombatStrength * 1.5f >= theirCombatStrength && theirCombatStrength * 1.5f >= ourCombatStrength)
            return 0 // we're roughly equal, there's no huge power imbalance
        if (ourCombatStrength > theirCombatStrength) {
            val absoluteAdvantage = ourCombatStrength - theirCombatStrength
            val percentageAdvantage = absoluteAdvantage / theirCombatStrength.toFloat()
            return (absoluteAdvantage * percentageAdvantage).toInt() * 10
        } else {
            val absoluteAdvantage = theirCombatStrength - ourCombatStrength
            val percentageAdvantage = absoluteAdvantage / ourCombatStrength.toFloat()
            return -(absoluteAdvantage * percentageAdvantage).toInt() * 10
        }
    }

    private fun introductionValue(ruleSet: Ruleset): Int {
        val unique = ruleSet.modOptions.getMatchingUniques(ModOptionsConstants.tradeCivIntroductions).firstOrNull()
            ?: return 0
        return unique.params[0].toInt()
    }
}
