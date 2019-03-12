package com.unciv.logic.trade

import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tr
import kotlin.math.min
import kotlin.math.sqrt

class TradeLogic(val ourCivilization:CivilizationInfo, val otherCivilization: CivilizationInfo){

    val ourAvailableOffers = getAvailableOffers(ourCivilization,otherCivilization)
    val theirAvailableOffers = getAvailableOffers(otherCivilization,ourCivilization)
    val currentTrade = Trade()

    fun getAvailableOffers(civInfo: CivilizationInfo, otherCivilization: CivilizationInfo): TradeOffersList {
        val offers = TradeOffersList()
        if(civInfo.isAtWarWith(otherCivilization))
            offers.add(TradeOffer("Peace Treaty", TradeType.Treaty, 20))
        for(entry in civInfo.getCivResources().filterNot { it.key.resourceType == ResourceType.Bonus }) {
            val resourceTradeType = if(entry.key.resourceType== ResourceType.Luxury) TradeType.Luxury_Resource
            else TradeType.Strategic_Resource
            offers.add(TradeOffer(entry.key.name, resourceTradeType, 30, entry.value))
        }
        for(entry in civInfo.tech.techsResearched
                .filterNot { otherCivilization.tech.isResearched(it) }
                .filter { otherCivilization.tech.canBeResearched(it) }){
            offers.add(TradeOffer(entry, TradeType.Technology, 0))
        }
        offers.add(TradeOffer("Gold".tr(), TradeType.Gold, 0, civInfo.gold))
        offers.add(TradeOffer("Gold per turn".tr(), TradeType.Gold_Per_Turn, 30, civInfo.getStatsForNextTurn().gold.toInt()))
        for(city in civInfo.cities.filterNot { it.isCapital() })
            offers.add(TradeOffer(city.name, TradeType.City, 0))

        val otherCivsWeKnow = civInfo.diplomacy.values.map { it.otherCiv() }
                .filter { it != otherCivilization && !it.isBarbarianCivilization() && !it.isDefeated() }
        val civsWeKnowAndTheyDont = otherCivsWeKnow
                .filter { !otherCivilization.diplomacy.containsKey(it.civName) && !it.isDefeated() }
        for(thirdCiv in civsWeKnowAndTheyDont){
            offers.add(TradeOffer("Introduction to " + thirdCiv.civName, TradeType.Introduction, 0))
        }

        val civsWeBothKnow = otherCivsWeKnow
                .filter { otherCivilization.diplomacy.containsKey(it.civName) }
        val civsWeArentAtWarWith = civsWeBothKnow
                .filter { civInfo.diplomacy[it.civName]!!.diplomaticStatus== DiplomaticStatus.Peace }
        for(thirdCiv in civsWeArentAtWarWith){
            offers.add(TradeOffer("Declare war on "+thirdCiv.civName,TradeType.WarDeclaration,0))
        }

        return offers
    }

    fun isTradeAcceptable(): Boolean {
        val sumOfTheirOffers = currentTrade.theirOffers.asSequence()
                .filter { it.type!= TradeType.Treaty } // since treaties should only be evaluated once for 2 sides
                .map { evaluateOffer(it,false) }.sum()
        val sumOfOurOffers = currentTrade.ourOffers.map { evaluateOffer(it,true)}.sum()
        return sumOfOurOffers >= sumOfTheirOffers
    }

    fun evaluateOffer(offer: TradeOffer, otherCivIsRecieving:Boolean): Int {
        when(offer.type) {
            TradeType.Gold -> return offer.amount
            TradeType.Gold_Per_Turn -> return offer.amount*offer.duration
            TradeType.Luxury_Resource -> {
                if(!otherCivIsRecieving){ // they're giving us
                    var value = 300*offer.amount
                    if(!theirAvailableOffers.any { it.name==offer.name }) // We want to take away their last luxury or give them one they don't have
                        value += 400
                    return value
                }
                else{
                    val civsWhoWillTradeUsForTheLux = ourCivilization.diplomacy.values.map { it.civInfo }
                            .filter { it!= otherCivilization }
                            .filter { !it.hasResource(offer.name) } //they don't have
                    val ourResourceNames = ourCivilization.getCivResources().map { it.key.name }
                    val civsWithLuxToTrade = civsWhoWillTradeUsForTheLux.filter {
                        it.getCivResources().any {
                            it.value > 1 && it.key.resourceType == ResourceType.Luxury //they have a lux we don't and will be willing to trade it
                                    && !ourResourceNames.contains(it.key.name)
                        }
                    }

                    var value = 50*min(offer.amount,civsWithLuxToTrade.size) // they'll buy at 50 each only, and that's so they can trade it away
                    if(!theirAvailableOffers.any { it.name==offer.name })
                        value+=200 // only if they're lacking will they buy the first one at 240 (Civ V standard, see https://www.reddit.com/r/civ/comments/1go7i9/luxury_and_strategic_resource_pricing/ & others)
                    return value
                }
            }
            TradeType.Technology -> return sqrt(GameBasics.Technologies[offer.name]!!.cost.toDouble()).toInt()*20
            TradeType.Strategic_Resource -> {
                if(otherCivIsRecieving) {
                    val resources = ourCivilization.getCivResourcesByName()
                    if (resources[offer.name]!! >= 2) return 0 // we already have enough.
                    val canUseForBuildings = ourCivilization.cities
                            .any { city-> city.cityConstructions.getBuildableBuildings().any { it.requiredResource==offer.name } }
                    val canUseForUnits = ourCivilization.cities
                            .any { city-> city.cityConstructions.getConstructableUnits().any { it.requiredResource==offer.name } }
                    if(!canUseForBuildings && !canUseForUnits) return 0
                    return  50 * offer.amount
                }
                else return 50 * offer.amount
            }
            TradeType.City -> {
                val civ = if(otherCivIsRecieving) ourCivilization else otherCivilization
                val city = civ.cities.first { it.name==offer.name }
                val stats = city.cityStats.currentCityStats
                val sumOfStats = stats.culture+stats.gold+stats.science+stats.production+stats.happiness+stats.food
                return sumOfStats.toInt() * 100
            }
            TradeType.Treaty -> {
                if(offer.name=="Peace Treaty")
                    return evaluatePeaceCostForThem() // Since it will be evaluated twice, once when they evaluate our offer and once when they evaluate theirs
                else return 1000
            }
            TradeType.Introduction -> return 250
            TradeType.WarDeclaration -> {
                val nameOfCivToDeclareWarOn = offer.name.split(' ').last()
                val civToDeclareWarOn = ourCivilization.gameInfo.getCivilization(nameOfCivToDeclareWarOn)
                val threatToThem = Automation().threatAssessment(otherCivilization,civToDeclareWarOn)

                if(!otherCivIsRecieving) { // we're getting this from them, that is, they're declaring war
                    when (threatToThem) {
                        ThreatLevel.VeryLow -> return 100
                        ThreatLevel.Low -> return 250
                        ThreatLevel.Medium -> return 500
                        ThreatLevel.High -> return 1000
                        ThreatLevel.VeryHigh -> return 10000 // no way
                    }
                }
                else{
                    if(otherCivilization.diplomacy[nameOfCivToDeclareWarOn]!!.diplomaticStatus== DiplomaticStatus.War){
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

            }
        // Dunno what this is?
            else -> return 1000
        }
    }

    fun evaluatePeaceCostForThem(): Int {
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

    fun acceptTrade() {

        ourCivilization.diplomacy[otherCivilization.civName]!!.trades.add(currentTrade)
        otherCivilization.diplomacy[ourCivilization.civName]!!.trades.add(currentTrade.reverse())

        // instant transfers
        fun transferTrade(to: CivilizationInfo, from: CivilizationInfo, trade: Trade) {
            for (offer in trade.theirOffers) {
                if (offer.type == TradeType.Gold) {
                    to.gold += offer.amount
                    from.gold -= offer.amount
                }
                if (offer.type == TradeType.Technology) {
                    to.tech.addTechnology(offer.name)
                }
                if(offer.type== TradeType.City){
                    val city = from.cities.first { it.name==offer.name }
                    city.moveToCiv(to)
                    city.getCenterTile().getUnits().forEach { it.movementAlgs().teleportToClosestMoveableTile() }
                    to.updateViewableTiles()
                    from.updateViewableTiles()
                }
                if(offer.type== TradeType.Treaty){
                    if(offer.name=="Peace Treaty"){
                        to.diplomacy[from.civName]!!.diplomaticStatus= DiplomaticStatus.Peace
                        for(unit in to.getCivUnits().filter { it.getTile().getOwner()==from })
                            unit.movementAlgs().teleportToClosestMoveableTile()
                    }
                }
                if(offer.type==TradeType.Introduction)
                    to.meetCivilization(to.gameInfo.getCivilization(offer.name.split(" ")[2]))

                if(offer.type==TradeType.WarDeclaration){
                    val nameOfCivToDeclareWarOn = offer.name.split(' ').last()
                    from.diplomacy[nameOfCivToDeclareWarOn]!!.declareWar()
                }
            }
        }

        transferTrade(ourCivilization,otherCivilization,currentTrade)
        transferTrade(otherCivilization,ourCivilization,currentTrade.reverse())
    }
}
