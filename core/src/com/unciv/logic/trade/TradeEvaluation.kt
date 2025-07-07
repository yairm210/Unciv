package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.logic.automation.civilization.DiplomacyAutomation
import com.unciv.logic.automation.civilization.MotivationToAttackAutomation
import com.unciv.logic.automation.civilization.DeclareWarPlanEvaluator
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
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
        // If a civ doesn't has ever owned an original capital, which means it has not settle the first city yet, 
        // it shouldn't be forbidden to trade with other civs owing to cities.size == 0.
        if ((offerer.hasEverOwnedOriginalCapital && trade.ourOffers.count { it.type == TradeOfferType.City } == offerer.cities.size)
            || (tradePartner.hasEverOwnedOriginalCapital && trade.theirOffers.count { it.type == TradeOfferType.City } == tradePartner.cities.size)) {
            return false
        }

        for (offer in trade.ourOffers)
            if (!isOfferValid(offer, offerer, tradePartner)) {
                return false
            }            

        for (offer in trade.theirOffers)
            if (!isOfferValid(offer, tradePartner, offerer)) {
                return false
            }
        return true
    }

    private fun isOfferValid(tradeOffer: TradeOffer, offerer: Civilization, tradePartner: Civilization): Boolean {

        fun hasResource(tradeOffer: TradeOffer): Boolean {
            val resourcesByName = offerer.getCivResourcesByName()
            return resourcesByName.containsKey(tradeOffer.name) && resourcesByName[tradeOffer.name]!! >= tradeOffer.amount
        }

        return when (tradeOffer.type) {
            // if they go a little negative it's okay, but don't allowing going overboard (promising same gold to many)
            TradeOfferType.Gold -> tradeOffer.amount * 0.9f < offerer.gold
            TradeOfferType.Gold_Per_Turn -> tradeOffer.amount * 0.9f < offerer.stats.statsForNextTurn.gold
            TradeOfferType.Treaty -> {
                // Current automation should prevent these from being offered anyway, 
                //   these are a safeguard against future automation changes 
                when (tradeOffer.name) {
                    Constants.peaceTreaty -> offerer.isAtWarWith(tradePartner)
                    Constants.researchAgreement -> !offerer.getDiplomacyManager(tradePartner)!!.hasFlag(DiplomacyFlags.ResearchAgreement)
                    Constants.defensivePact -> !offerer.getDiplomacyManager(tradePartner)!!.hasFlag(DiplomacyFlags.DefensivePact)
                    else -> true // potentional future treaties
                }
            }
            TradeOfferType.Agreement -> true
            TradeOfferType.Luxury_Resource -> hasResource(tradeOffer)
            TradeOfferType.Strategic_Resource -> hasResource(tradeOffer)
            TradeOfferType.Stockpiled_Resource -> hasResource(tradeOffer)
            TradeOfferType.Technology -> true
            TradeOfferType.Introduction -> !tradePartner.knows(tradeOffer.name) // You can't introduce them to someone they already know!
            TradeOfferType.WarDeclaration -> offerer.getDiplomacyManager(tradeOffer.name)!!.canDeclareWar()
            // TODO: Implement trade offer validation for TurnManager -> startTurn  -> line 91
            TradeOfferType.PeaceProposal -> true
            TradeOfferType.City -> offerer.cities.any { it.id == tradeOffer.name }
        }
    }

    fun isTradeAcceptable(trade: Trade, evaluator: Civilization, tradePartner: Civilization): Boolean {
        return getTradeAcceptability(trade, evaluator, tradePartner, true) >= 0
    }

    fun getTradeAcceptability(trade: Trade, evaluator: Civilization, tradePartner: Civilization, includeDiplomaticGifts:Boolean = false): Int {
        val citiesAskedToSurrender = trade.ourOffers.count { it.type == TradeOfferType.City }
        val maxCitiesToSurrender = ceil(evaluator.cities.size.toFloat() / 5).toInt()
        if (citiesAskedToSurrender > maxCitiesToSurrender) {
            return Int.MIN_VALUE
        }

        val sumOfTheirOffers = trade.theirOffers.asSequence()
                .filter { it.type != TradeOfferType.Treaty } // since treaties should only be evaluated once for 2 sides
                .map { evaluateBuyCostWithInflation(it, evaluator, tradePartner, trade) }.sum()

        var sumOfOurOffers = trade.ourOffers.sumOf { evaluateSellCostWithInflation(it, evaluator, tradePartner, trade) }

        val relationshipLevel = evaluator.getDiplomacyManager(tradePartner)!!.relationshipIgnoreAfraid()
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
        val diplomaticGifts: Int = if (includeDiplomaticGifts) evaluator.getDiplomacyManager(tradePartner)!!.getGoldGifts() else 0
        return sumOfTheirOffers - sumOfOurOffers + diplomaticGifts
    }

    fun evaluateBuyCostWithInflation(offer: TradeOffer, civInfo: Civilization, tradePartner: Civilization, trade: Trade): Int {
        if (offer.type != TradeOfferType.Gold && offer.type != TradeOfferType.Gold_Per_Turn)
            return (evaluateBuyCost(offer, civInfo, tradePartner, trade) / getGoldInflation(civInfo)).toInt()
        return evaluateBuyCost(offer, civInfo, tradePartner, trade)
    }

    /**
     * How much their offer is worth to us in gold
     */
    private fun evaluateBuyCost(offer: TradeOffer, civInfo: Civilization, tradePartner: Civilization, trade: Trade): Int {
        when (offer.type) {
            TradeOfferType.Gold -> return offer.amount
            // GPT loses value for each 'future' turn, meaning: gold now is more valuable than gold in the future
            // Empire-wide production tends to grow at roughly 2% per turn (quick speed), so let's take that as a base line
            // Formula could be more sophisticated by taking into account game speed and estimated chance of the gpt-giver cancelling the trade after X amount of turns
            TradeOfferType.Gold_Per_Turn -> return (1..offer.duration).sumOf { offer.amount * 0.98.pow(it) }.toInt()
            TradeOfferType.Treaty -> {
                return when (offer.name) {
                    // Since it will be evaluated twice, once when they evaluate our offer and once when they evaluate theirs
                    Constants.peaceTreaty -> evaluatePeaceCostForThem(civInfo, tradePartner)
                    Constants.defensivePact -> 0
                    Constants.researchAgreement -> -offer.amount
                    else -> 1000
                }
            }

            TradeOfferType.Luxury_Resource -> {
                if (civInfo.getDiplomacyManager(tradePartner)!!.hasFlag(DiplomacyFlags.ResourceTradesCutShort))
                    return 0 // We don't trust you for resources
                
                val lowestExplicitBuyCost = civInfo.gameInfo.ruleset.tileResources[offer.name]!!
                    .getMatchingUniques(UniqueType.AiWillBuyAt, StateForConditionals(civInfo))
                    .minOfOrNull { it.params[0].toInt() }

                if (lowestExplicitBuyCost != null) return lowestExplicitBuyCost

                val weLoveTheKingPotential = civInfo.cities.count { it.demandedResource == offer.name } * 50
                return if(!civInfo.hasResource(offer.name)) { // we can't trade on resources, so we are only interested in 1 copy for ourselves
                        weLoveTheKingPotential + when { // We're a lot more interested in luxury if low on happiness (AI is never low on happiness though)
                            civInfo.getHappiness() < 0 -> 450
                            civInfo.getHappiness() < 10 -> 350
                            else -> 300 // Higher than corresponding sell cost since a trade is mutually beneficial!
                        }
                    } else 0
            }

            TradeOfferType.Strategic_Resource -> {
                if (civInfo.getDiplomacyManager(tradePartner)!!.hasFlag(DiplomacyFlags.ResourceTradesCutShort))
                    return 0 // We don't trust you for resources

                val amountWillingToBuy = 2 - civInfo.getResourceAmount(offer.name)
                if (amountWillingToBuy <= 0) return 0 // we already have enough.
                val amountToBuyInOffer = min(amountWillingToBuy, offer.amount)


                val lowestExplicitBuyCost = civInfo.gameInfo.ruleset.tileResources[offer.name]!!
                    .getMatchingUniques(UniqueType.AiWillBuyAt, StateForConditionals(civInfo))
                    .minOfOrNull { it.params[0].toInt() }
                if (lowestExplicitBuyCost != null) return lowestExplicitBuyCost

                val canUseForBuildings = civInfo.cities
                        .any { city -> city.cityConstructions.getBuildableBuildings().any {
                            it.getResourceRequirementsPerTurn(city.state).containsKey(offer.name) } }
                val canUseForUnits = civInfo.cities
                        .any { city -> city.cityConstructions.getConstructableUnits().any {
                            it.getResourceRequirementsPerTurn(civInfo.state).containsKey(offer.name) } }
                if (!canUseForBuildings && !canUseForUnits) return 0

                return 50 * amountToBuyInOffer
            }
            
            TradeOfferType.Stockpiled_Resource -> {
                val resource = civInfo.gameInfo.ruleset.tileResources[offer.name] ?: return 0
                val lowestBuyCost = resource.getMatchingUniques(UniqueType.AiWillBuyAt, StateForConditionals(civInfo))
                    .minOfOrNull { it.params[0].toInt() }
                return lowestBuyCost ?: 0
            }

            TradeOfferType.Technology -> // Currently unused
                return (sqrt(civInfo.gameInfo.ruleset.technologies[offer.name]!!.cost.toDouble())
                        * civInfo.gameInfo.speed.scienceCostModifier).toInt() * 20
            TradeOfferType.Introduction -> return introductionValue(civInfo.gameInfo.ruleset)
            TradeOfferType.WarDeclaration -> {
                val civToDeclareWarOn = civInfo.gameInfo.getCivilization(offer.name)
                if (trade.theirOffers.any { it.type == TradeOfferType.WarDeclaration && it.name == offer.name }
                        && trade.ourOffers.any {it.type == TradeOfferType.WarDeclaration && it.name == offer.name}) {
                    // Team war is handled in the selling method
                    return 0
                } else if (civInfo.isAtWarWith(civToDeclareWarOn)) {
                    // We shouldn't require them to pay us to join our war (no negative values)
                    return (20 * DeclareWarPlanEvaluator.evaluateJoinOurWarPlan(civInfo, civToDeclareWarOn, tradePartner, null)).toInt().coerceAtLeast(0)
                } else {
                    // Why should we pay you to go fight someone else?
                    return 0
                }
            }
            TradeOfferType.PeaceProposal -> {
                // How much their offer is worth to us in gold
                // We are pretending offerred civ is us
                val eval = evaluatePeaceCostForThem(tradePartner.getDiplomacyManager(offer.name)!!.civInfo, tradePartner)
                return eval
            }
            TradeOfferType.City -> {
                val city = tradePartner.cities.firstOrNull { it.id == offer.name }
                    ?: throw Exception("Got an offer for city id "+offer.name+" which does't seem to exist for this civ!")
                val surrounded: Int = surroundedByOurCities(city, civInfo)
                if (civInfo.getHappiness() + city.cityStats.happinessList.values.sum() < 0)
                    return 0 // we can't really afford to go into negative happiness because of buying a city
                val sumOfPop = city.population.population
                val sumOfBuildings = city.cityConstructions.getBuiltBuildings().count()
                return (sumOfPop * 4 + sumOfBuildings + 4 + surrounded) * 100
            }
            TradeOfferType.Agreement -> {
                if (offer.name == Constants.openBorders) return 100
                throw Exception("Invalid agreement type!")
            }
        }
    }

    private fun surroundedByOurCities(city: City, civInfo: Civilization): Int {
        val borderingCivs: Set<String> = getNeighbouringCivs(city)
        if (borderingCivs.contains(civInfo.civName))
            return 3 // if the city has a border with trading civ
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


    fun evaluateSellCostWithInflation(offer: TradeOffer, civInfo: Civilization, tradePartner: Civilization, trade: Trade): Int {
        if (offer.type != TradeOfferType.Gold && offer.type != TradeOfferType.Gold_Per_Turn)
            return (evaluateSellCost(offer, civInfo, tradePartner, trade) / getGoldInflation(civInfo)).toInt()
        return evaluateSellCost(offer, civInfo, tradePartner, trade)
    }

    /**
     * How much our offer is worth to us in gold
     */
    private fun evaluateSellCost(offer: TradeOffer, civInfo: Civilization, tradePartner: Civilization, trade: Trade): Int {
        when (offer.type) {
            TradeOfferType.Gold -> return offer.amount
            TradeOfferType.Gold_Per_Turn -> return offer.amount * offer.duration
            TradeOfferType.Treaty -> {
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
            TradeOfferType.Luxury_Resource -> {
                val lowestExplicitSellCost = civInfo.gameInfo.ruleset.tileResources[offer.name]!!
                    .getMatchingUniques(UniqueType.AiWillSellAt, StateForConditionals(civInfo))
                    .minOfOrNull { it.params[0].toInt() }

                if (lowestExplicitSellCost != null) return lowestExplicitSellCost
                
                return when {
                    civInfo.getResourceAmount(offer.name) > 1 -> 250 // fair price
                    civInfo.hasUnique(UniqueType.RetainHappinessFromLuxury) -> // If we retain 100% happiness, value it as a duplicate lux
                        600 - (civInfo.getMatchingUniques(UniqueType.RetainHappinessFromLuxury)
                            .first().params[0].toFloat() * 3.5f).toInt()
                    else -> 600 // you want to take away our last lux of this type?!
                }
            }
            TradeOfferType.Strategic_Resource -> {
                if (civInfo.gameInfo.spaceResources.contains(offer.name) &&
                    (civInfo.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts) ||
                            tradePartner.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts))
                )
                    return Int.MAX_VALUE // We'd rather win the game, thanks
                
                val lowestExplicitSellCost = civInfo.gameInfo.ruleset.tileResources[offer.name]!!
                    .getMatchingUniques(UniqueType.AiWillSellAt, StateForConditionals(civInfo))
                    .minOfOrNull { it.params[0].toInt() }
                
                if (lowestExplicitSellCost != null) return lowestExplicitSellCost

                if (!civInfo.isAtWar()) return 50 * offer.amount

                val canUseForUnits = civInfo.gameInfo.ruleset.units.values
                    .any { it.getResourceRequirementsPerTurn(civInfo.state).containsKey(offer.name)
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

            TradeOfferType.Stockpiled_Resource -> {
                val resource = civInfo.gameInfo.ruleset.tileResources[offer.name] ?: return 0
                val lowestSellCost = resource.getMatchingUniques(UniqueType.AiWillSellAt, StateForConditionals(civInfo))
                    .minOfOrNull { it.params[0].toInt() }
                return lowestSellCost ?: Int.MAX_VALUE
            }
            
            TradeOfferType.Technology -> return sqrt(civInfo.gameInfo.ruleset.technologies[offer.name]!!.cost.toDouble()).toInt() * 20
            TradeOfferType.Introduction -> return introductionValue(civInfo.gameInfo.ruleset)
            TradeOfferType.WarDeclaration -> {
                val civToDeclareWarOn = civInfo.gameInfo.getCivilization(offer.name)
                if (trade.theirOffers.any { it.type == TradeOfferType.WarDeclaration && it.name == offer.name }
                        && trade.ourOffers.any {it.type == TradeOfferType.WarDeclaration && it.name == offer.name}) {
                    // Only accept if the war will benefit us, or if they pay us enough
                    // We shouldn't want to pay them for us to declare war (no negative values)
                    return (-20 * DeclareWarPlanEvaluator.evaluateTeamWarPlan(civInfo, civToDeclareWarOn, tradePartner, null)).toInt().coerceAtLeast(0)
                } else if (tradePartner.isAtWarWith(civToDeclareWarOn)) {
                    // We might want them to pay us to join them in war (no negative values)
                    return (-20 * DeclareWarPlanEvaluator.evaluateJoinWarPlan(civInfo, civToDeclareWarOn, tradePartner, null)).toInt().coerceAtLeast(0)
                } else {
                    // We might want them to pay us to declare war (no negative values)
                    return (-25 * DeclareWarPlanEvaluator.evaluateDeclareWarPlan(civInfo, civToDeclareWarOn, null)).toInt().coerceAtLeast(0)
                }
            }
            TradeOfferType.PeaceProposal -> {
                // How much our offer is worth to us in gold
                // We are pretending offerred civ is them
                val eval = evaluatePeaceCostForThem(civInfo, civInfo.getDiplomacyManager(offer.name)!!.civInfo)
                return eval
            }
            TradeOfferType.City -> {
                val city = civInfo.cities.firstOrNull { it.id == offer.name }
                    ?: throw Exception("Got an offer to sell city id " + offer.name + " which does't seem to exist for this civ!")

                val distanceBonus = distanceCityTradeModifier(civInfo, city)
                val sumOfPop = city.population.population
                val sumOfBuildings = city.cityConstructions.getBuiltBuildings().count()
                return ((sumOfPop * 4 + sumOfBuildings * 1 + 4 + distanceBonus) * 100).coerceAtLeast(1000)
            }
            TradeOfferType.Agreement -> {
                if (offer.name == Constants.openBorders) {
                    return when (civInfo.getDiplomacyManager(tradePartner)!!.relationshipIgnoreAfraid()) {
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
    fun getGoldInflation(civInfo: Civilization): Double {
        val modifier = 1000.0
        val goldPerTurn = civInfo.stats.statsForNextTurn.gold.toDouble()
        // To visualise the function, plug this into a 2d graphing calculator \frac{1000}{x^{1.2}+1.66*1000}
        // Goes from 1 at GPT = 0 to .923 at GPT = 100, .577 at GPT = 1000 and 0.415 at GPT = 10000
        // The current value of gold will never go below 40%, or the .4f that it is set to (being roughly the efficiency ratio between purchasing and upgrading units)
        // So this does not scale off to infinity
        return modifier/ (goldPerTurn.coerceAtLeast(1.0).pow(1.2) + (1.66 * modifier)) + .4
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

    fun evaluatePeaceCostForThem(ourCiv: Civilization, otherCiv: Civilization): Int {
        val ourCombatStrength = ourCiv.getStatForRanking(RankingType.Force)
        val theirCombatStrength = otherCiv.getStatForRanking(RankingType.Force)
        if (ourCombatStrength * 1.5f >= theirCombatStrength && theirCombatStrength * 1.5f >= ourCombatStrength)
            return 0 // we're roughly equal, there's no huge power imbalance
        if (ourCombatStrength > theirCombatStrength) {
            if (MotivationToAttackAutomation.hasAtLeastMotivationToAttack(ourCiv, otherCiv, 0f) <= 0) return 0
            val absoluteAdvantage = ourCombatStrength - theirCombatStrength
            val percentageAdvantage = absoluteAdvantage / theirCombatStrength.toFloat()
            // We don't add the same constraint here. We should not make peace easily if we're
            // heavily advantaged.
            val totalAdvantage = (absoluteAdvantage * percentageAdvantage).toInt()
            if(totalAdvantage < 0) //May be a negative number if strength disparity is such that it leads to integer overflow
                return 10000    //in that rare case, the AI would accept peace against a defeated foe.
            return (totalAdvantage / (getGoldInflation(otherCiv) * 2)).toInt()
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
            return -(absoluteAdvantage * percentageAdvantage / (getGoldInflation(ourCiv) * 2)).coerceAtMost(10000.0).toInt()
        }
    }

    private fun introductionValue(ruleSet: Ruleset): Int {
        val unique = ruleSet.modOptions.getMatchingUniques(UniqueType.TradeCivIntroductions).firstOrNull()
            ?: return 0
        return unique.params[0].toInt()
    }
}
