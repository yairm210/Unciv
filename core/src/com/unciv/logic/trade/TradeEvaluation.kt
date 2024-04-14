package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.automation.civilization.DiplomacyAutomation
import com.unciv.logic.automation.civilization.MotivationToAttackAutomation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.screens.victoryscreen.RankingType
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class TradeEvaluation {

    fun isTradeValid(trade: Trade, offerer: Civilization, tradePartner: Civilization): Boolean {

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

    private fun isOfferValid(tradeOffer: TradeOffer, offerer: Civilization, tradePartner: Civilization): Boolean {

        fun hasResource(tradeOffer: TradeOffer): Boolean {
            val resourcesByName = offerer.getCivResourcesByName()
            return resourcesByName.containsKey(tradeOffer.name) && resourcesByName[tradeOffer.name]!! >= tradeOffer.amount
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
            TradeType.PeaceNegotiation -> true
            TradeType.City -> offerer.cities.any { it.id == tradeOffer.name }
        }
    }

    fun isTradeAcceptable(trade: Trade, evaluator: Civilization, tradePartner: Civilization): Boolean {
        return getTradeAcceptability(trade, evaluator, tradePartner) >= 0
    }

    fun getTradeAcceptability(trade: Trade, evaluator: Civilization, tradePartner: Civilization): Int {
        val citiesAskedToSurrender = trade.ourOffers.count { it.type == TradeType.City }
        val maxCitiesToSurrender = ceil(evaluator.cities.size.toFloat() / 5).toInt()
        if (citiesAskedToSurrender > maxCitiesToSurrender) {
            return Int.MIN_VALUE
        }

        val sumOfTheirOffers = trade.theirOffers.asSequence()
                .filter { it.type != TradeType.Treaty } // since treaties should only be evaluated once for 2 sides
                .map { evaluateBuyCostWithInflation(it, evaluator, tradePartner) }.sum()

        var sumOfOurOffers = trade.ourOffers.sumOf { evaluateSellCostWithInflation(it, evaluator, tradePartner) }

        val relationshipLevel = evaluator.getDiplomacyManager(tradePartner).relationshipIgnoreAfraid()
        // If we're making a peace treaty, don't try to up the bargain for people you don't like.
        // Leads to spartan behaviour where you demand more, the more you hate the enemy...unhelpful
        if (trade.ourOffers.none { it.name == Constants.peaceTreaty || it.name == Constants.researchAgreement}) {
            if (relationshipLevel == RelationshipLevel.Enemy) sumOfOurOffers = (sumOfOurOffers * 1.5).toInt()
            else if (relationshipLevel == RelationshipLevel.Unforgivable) sumOfOurOffers *= 2
        }
        if (trade.ourOffers.firstOrNull { it.name == Constants.defensivePact } != null) {
            if (relationshipLevel == RelationshipLevel.Ally) {
                //todo: Add more in depth evaluation here
            } else {
                return Int.MIN_VALUE
            }
        }

        return sumOfTheirOffers - sumOfOurOffers
    }

    fun evaluateBuyCostWithInflation(offer: TradeOffer, civInfo: Civilization, tradePartner: Civilization): Int {
        if (offer.type != TradeType.Gold && offer.type != TradeType.Gold_Per_Turn)
            return (evaluateBuyCost(offer, civInfo, tradePartner) / getGoldInflation(civInfo)).toInt()
        return evaluateBuyCost(offer, civInfo, tradePartner)
    }

    private fun evaluateBuyCost(offer: TradeOffer, civInfo: Civilization, tradePartner: Civilization): Int {
        when (offer.type) {
            TradeType.Gold -> return offer.amount
            // GPT loses 1% of value for each 'future' turn, meaning: gold now is more valuable than gold in the future
            TradeType.Gold_Per_Turn -> return (1..offer.duration).sumOf { offer.amount * 0.99.pow(it) }.toInt()
            TradeType.Treaty -> {
                return when (offer.name) {
                    // Since it will be evaluated twice, once when they evaluate our offer and once when they evaluate theirs
                    Constants.peaceTreaty -> evaluatePeaceCostForThem(civInfo, tradePartner)
                    Constants.defensivePact -> 0
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
                val amountWillingToBuy = 2 - civInfo.getResourceAmount(offer.name)
                if (amountWillingToBuy <= 0) return 0 // we already have enough.
                val amountToBuyInOffer = min(amountWillingToBuy, offer.amount)

                val canUseForBuildings = civInfo.cities
                        .any { city -> city.cityConstructions.getBuildableBuildings().any {
                            it.getResourceRequirementsPerTurn(StateForConditionals(civInfo, city)).containsKey(offer.name) } }
                val canUseForUnits = civInfo.cities
                        .any { city -> city.cityConstructions.getConstructableUnits().any {
                            it.getResourceRequirementsPerTurn(StateForConditionals(civInfo)).containsKey(offer.name) } }
                if (!canUseForBuildings && !canUseForUnits) return 0

                return 50 * amountToBuyInOffer
            }

            TradeType.Technology -> // Currently unused
                return (sqrt(civInfo.gameInfo.ruleset.technologies[offer.name]!!.cost.toDouble())
                        * civInfo.gameInfo.speed.scienceCostModifier).toInt() * 20
            TradeType.Introduction -> return introductionValue(civInfo.gameInfo.ruleset)
            TradeType.WarDeclaration -> {
                val civToDeclareWarOn = civInfo.gameInfo.getCivilization(offer.name)

                return if (!civInfo.isAtWarWith(civToDeclareWarOn)) 0 // why should we pay you to go fight someone...?
                else when (Automation.threatAssessment(civInfo, civToDeclareWarOn)) {
                    ThreatLevel.VeryLow -> 0
                    ThreatLevel.Low -> 0
                    ThreatLevel.Medium -> 100
                    ThreatLevel.High -> 500
                    ThreatLevel.VeryHigh -> 1000
                }
            }

            TradeType.PeaceNegotiation -> {
                val civToMakePeaceWith = civInfo.gameInfo.getCivilization(offer.name)
                // following Boolean condition check is the same that triggers the Peace Offer automation in Diplomacy Automation
                val alreadyWantsPeaceFlag = (MotivationToAttackAutomation.hasAtLeastMotivationToAttack(civInfo, civToMakePeaceWith, 10)  >= 10)
                val costRequiredByEnemy = evaluatePeaceCostForThem(civInfo, civToMakePeaceWith)
                val costRequiredByCiv = evaluatePeaceCostForThem(civToMakePeaceWith, civInfo)
                val gapToPay = if (alreadyWantsPeaceFlag && (civInfo.gold < costRequiredByEnemy))
                                    costRequiredByEnemy - civInfo.gold
                                else if (alreadyWantsPeaceFlag)
                                        0
                                     else if (civToMakePeaceWith.gold < costRequiredByCiv)
                                        costRequiredByCiv - civToMakePeaceWith.gold
                                     else
                                         0
                return if (alreadyWantsPeaceFlag) {
                    // just to cover next turn eventual inflation and diplomatic expenses
                    (gapToPay + ((gapToPay) * 0.01)).toInt()
                } else {
                    when (Automation.threatAssessment(civInfo, tradePartner)) {
                        ThreatLevel.VeryLow -> 1000 + gapToPay
                        ThreatLevel.Low -> 500 + gapToPay
                        ThreatLevel.Medium -> 250 + gapToPay
                        ThreatLevel.High -> 100 + gapToPay
                        ThreatLevel.VeryHigh -> 50 + gapToPay
                    }
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

    private fun surroundedByOurCities(city: City, civInfo: Civilization): Int {
        val borderingCivs: Set<String> = getNeighbouringCivs(city)
        if (borderingCivs.size == 1 && borderingCivs.contains(civInfo.civName)) {
            return 10 * civInfo.getEraNumber() // if the city is surrounded only by trading civ
        }
        if (borderingCivs.contains(civInfo.civName))
            return 2 * civInfo.getEraNumber() // if the city has a border with trading civ
        return 0
    }

    private fun getNeighbouringCivs(city: City): Set<String> {
        val tilesList: HashSet<Tile> = city.getTiles().toHashSet()
        val cityPositionList: ArrayList<Tile> = arrayListOf()

        for (tiles in tilesList)
            for (tile in tiles.neighbors)
                if (!tilesList.contains(tile))
                    cityPositionList.add(tile)

        return cityPositionList
            .asSequence()
            .mapNotNull { it.getOwner()?.civName }
            .toSet()
    }


    fun evaluateSellCostWithInflation(offer: TradeOffer, civInfo: Civilization, tradePartner: Civilization): Int {
        if (offer.type != TradeType.Gold && offer.type != TradeType.Gold_Per_Turn)
            return (evaluateSellCost(offer, civInfo, tradePartner) / getGoldInflation(civInfo)).toInt()
        return evaluateSellCost(offer, civInfo, tradePartner)
    }

    private fun evaluateSellCost(offer: TradeOffer, civInfo: Civilization, tradePartner: Civilization): Int {
        when (offer.type) {
            TradeType.Gold -> return offer.amount
            TradeType.Gold_Per_Turn -> return offer.amount * offer.duration
            TradeType.Treaty -> {
                return when (offer.name) {
                    // Since it will be evaluated twice, once when they evaluate our offer and once when they evaluate theirs
                    Constants.peaceTreaty -> evaluatePeaceCostForThem(civInfo, tradePartner)
                    Constants.defensivePact -> if (DiplomacyAutomation.wantsToSignDefensivePact(civInfo, tradePartner)) 0
                        else 100000
                    Constants.researchAgreement -> -offer.amount
                    else -> 1000
                    //Todo:AddDefensiveTreatyHere
                }
            }
            TradeType.Luxury_Resource -> {
                return when {
                    civInfo.getResourceAmount(offer.name) > 1 -> 250 // fair price
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

                val canUseForUnits = civInfo.gameInfo.ruleset.units.values
                    .any { it.getResourceRequirementsPerTurn(StateForConditionals(civInfo)).containsKey(offer.name)
                            && it.isBuildable(civInfo) }
                if (!canUseForUnits) return 50 * offer.amount

                val amountLeft = civInfo.getResourceAmount(offer.name)

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
            TradeType.Technology -> return sqrt(civInfo.gameInfo.ruleset.technologies[offer.name]!!.cost.toDouble()).toInt() * 20
            TradeType.Introduction -> return introductionValue(civInfo.gameInfo.ruleset)
            TradeType.WarDeclaration -> {
                val civToDeclareWarOn = civInfo.gameInfo.getCivilization(offer.name)

                return when (Automation.threatAssessment(civInfo, civToDeclareWarOn)) {
                    ThreatLevel.VeryLow -> 100
                    ThreatLevel.Low -> 250
                    ThreatLevel.Medium -> 500
                    ThreatLevel.High -> 1000
                    ThreatLevel.VeryHigh -> 10000 // declaring war to a super power is NO WAY
                }
            }

            TradeType.PeaceNegotiation -> {
                val civToMakePeaceWith = civInfo.gameInfo.getCivilization(offer.name)
                return when (Automation.threatAssessment(civInfo, civToMakePeaceWith)) {
                    ThreatLevel.VeryLow -> 10000
                    ThreatLevel.Low -> 1000
                    ThreatLevel.Medium -> 500
                    ThreatLevel.High -> 250
                    ThreatLevel.VeryHigh -> 100
                }

            }

            TradeType.City -> {
                val city = civInfo.cities.firstOrNull { it.id == offer.name }
                    ?: throw Exception("Got an offer to sell city id " + offer.name + " which does't seem to exist for this civ!")

                val distanceBonus = distanceCityTradeModifier(civInfo, city)
                val stats = city.cityStats.currentCityStats
                val sumOfStats =
                    stats.culture + stats.gold + stats.science + stats.production + stats.happiness + stats.food + distanceBonus
                return (sumOfStats.toInt() * 100).coerceAtLeast(1000)
            }
            TradeType.Agreement -> {
                if (offer.name == Constants.openBorders) {
                    return when (civInfo.getDiplomacyManager(tradePartner).relationshipIgnoreAfraid()) {
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

    /**
     * This returns how much one gold is worth now in comparison to starting out the game
     * Gold is worth less as the civilization has a higher income
     */
    private fun getGoldInflation(civInfo: Civilization): Double {
        val modifier = 1000.0
        val goldPerTurn = civInfo.stats.statsForNextTurn.gold.toDouble()
        // To visualise the function, plug this into a 2d graphing calculator \frac{1000}{x^{1.2}+1.11*1000}
        // Goes from 1 at GPT = 0 to .834 at GPT = 100, .296 at GPT = 1000 and 0.116 at GPT = 10000
        // The current value of gold will never go below 10% or the .1f that it is set to
        // So this does not scale off to infinity
        return modifier / (goldPerTurn.pow(1.2).coerceAtLeast(0.0) + (1.11f * modifier)) + .1f
    }

    /** This code returns a positive value if the city is significantly far away from the capital
     * and given how this method is used this ends up making such cities more expensive. That's how
     * I found it. I'm not sure it makes sense. One might also find arguments why cities closer to
     * the capital are more expensive. */
    private fun distanceCityTradeModifier(civInfo: Civilization, city: City): Int{
        val distanceToCapital = civInfo.getCapital()!!.getCenterTile().aerialDistanceTo(city.getCenterTile())

        if (distanceToCapital < 500) return 0
        return (distanceToCapital - 500) * civInfo.getEraNumber()
    }

    fun evaluatePeaceCostForThem(ourCivilization: Civilization, otherCivilization: Civilization): Int {
        val ourCombatStrength = ourCivilization.getStatForRanking(RankingType.Force)
        val theirCombatStrength = otherCivilization.getStatForRanking(RankingType.Force)
        if (ourCombatStrength * 1.5f >= theirCombatStrength && theirCombatStrength * 1.5f >= ourCombatStrength)
            return 0 // we're roughly equal, there's no huge power imbalance
        if (ourCombatStrength > theirCombatStrength) {
            val absoluteAdvantage = ourCombatStrength - theirCombatStrength
            val percentageAdvantage = absoluteAdvantage / theirCombatStrength.toFloat()
            // We don't add the same constraint here. We should not make peace easily if we're
            // heavily advantaged.
            val totalAdvantage = (absoluteAdvantage * percentageAdvantage).toInt() * 10
            if(totalAdvantage < 0) //May be a negative number if strength disparity is such that it leads to integer overflow
                return 10000    //in that rare case, the AI would accept peace against a defeated foe.
            return totalAdvantage
        } else {
            // This results in huge values for large power imbalances. However, we should not give
            // up everything just because there is a big power imbalance. There's a better chance to
            // recover if you don't give away all your cities for example.
            //
            // Example A (this would probably give us away everything):
            // absoluteAdvantage = 10000 - 100 = 9500
            // percentageAdvantage = 9500 / 100 = 95
            // return -(9500 * 95) * 10 = -9025000
            //
            // Example B (this is borderline)
            // absoluteAdvantage = 10000 - 2500 = 7500
            // percentageAdvantage = 7500 / 2500 = 3
            // return -(7500 * 3) * 10 = -225000
            //
            // Example C (this is fine):
            // absoluteAdvantage = 10000 - 5000 = 5000
            // percentageAdvantage = 5000 / 5000 = 1
            // return -(5000 * 1) * 10 = -50000
            //
            // Hence we cap the max cost at 100k which equals about 2 or 3 cities in the mid game
            // (stats ~30 each)
            val absoluteAdvantage = theirCombatStrength - ourCombatStrength
            val percentageAdvantage = absoluteAdvantage / ourCombatStrength.toFloat()
            return -min((absoluteAdvantage * percentageAdvantage).toInt() * 10, 100000)
        }
    }

    private fun introductionValue(ruleSet: Ruleset): Int {
        val unique = ruleSet.modOptions.getMatchingUniques(UniqueType.TradeCivIntroductions).firstOrNull()
            ?: return 0
        return unique.params[0].toInt()
    }
}
