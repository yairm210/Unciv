package com.unciv.logic.trade

import com.unciv.logic.automation.Automation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.DiplomaticStatus
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.ui.utils.tr
import kotlin.math.sqrt

class TradeLogic(val ourCivilization:CivilizationInfo, val otherCivilization: CivilizationInfo){

    val ourAvailableOffers = getAvailableOffers(ourCivilization,otherCivilization)
    val theirAvailableOffers = getAvailableOffers(otherCivilization,ourCivilization)
    val currentTrade = Trade()

    fun getAvailableOffers(civInfo: CivilizationInfo, otherCivilization: CivilizationInfo): TradeOffersList {
        val offers = TradeOffersList()
        if(civInfo.isAtWarWith(otherCivilization))
            offers.add(TradeOffer("Peace Treaty", TradeType.Treaty, 20, 0))
        for(entry in civInfo.getCivResources().filterNot { it.key.resourceType == ResourceType.Bonus }) {
            val resourceTradeType = if(entry.key.resourceType== ResourceType.Luxury) TradeType.Luxury_Resource
            else TradeType.Strategic_Resource
            offers.add(TradeOffer(entry.key.name, resourceTradeType, 30, entry.value))
        }
        for(entry in civInfo.tech.techsResearched
                .filterNot { otherCivilization.tech.isResearched(it) }
                .filter { otherCivilization.tech.canBeResearched(it) }){
            offers.add(TradeOffer(entry, TradeType.Technology, 0, 1))
        }
        offers.add(TradeOffer("Gold".tr(), TradeType.Gold, 0, civInfo.gold))
        offers.add(TradeOffer("Gold per turn".tr(), TradeType.Gold_Per_Turn, 30, civInfo.getStatsForNextTurn().gold.toInt()))
        for(city in civInfo.cities.filterNot { it.isCapital() })
            offers.add(TradeOffer(city.name, TradeType.City, 0, 1))

        val civsWeKnowAndTheyDont = civInfo.diplomacy.values.map { it.otherCiv() }
                .filter { !otherCivilization.diplomacy.containsKey(it.civName) && it != otherCivilization }
        for(thirdCiv in civsWeKnowAndTheyDont){
            offers.add(TradeOffer("Introduction to " + thirdCiv.civName, TradeType.Introduction, 0,1))
        }

        return offers
    }


    fun isTradeAcceptable(): Boolean {
        val sumOfTheirOffers = currentTrade.theirOffers.filter { it.type!= TradeType.Treaty } // since treaties should only be evaluated once for 2 sides
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
                    var value = 50*offer.amount // they'll buy at 50 each only, and that's so they can trade it away
                    if(!theirAvailableOffers.any { it.name==offer.name })
                        value+=250 // only if they're lacking will they buy the first one at 300
                    return value
                }

            }
            TradeType.Technology -> return sqrt(GameBasics.Technologies[offer.name]!!.cost.toDouble()).toInt()*10
            TradeType.Strategic_Resource -> return 50 * offer.amount
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
        // Dunno what this is?
            else -> return 1000
        }
    }


    fun evaluatePeaceCostForThem(): Int {
        val ourCombatStrength = Automation().evaluteCombatStrength(ourCivilization)
        val theirCombatStrength = Automation().evaluteCombatStrength(otherCivilization)
        if(ourCombatStrength==theirCombatStrength) return 0
        if(ourCombatStrength==0) return 1000
        if(theirCombatStrength==0) return -1000 // Chumps got no cities or units
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
        fun transfer(us: CivilizationInfo, them: CivilizationInfo, trade: Trade) {
            for (offer in trade.theirOffers) {
                if (offer.type == TradeType.Gold) {
                    us.gold += offer.amount
                    them.gold -= offer.amount
                }
                if (offer.type == TradeType.Technology) {
                    us.tech.techsResearched.add(offer.name)
                }
                if(offer.type== TradeType.City){
                    val city = them.cities.first { it.name==offer.name }
                    city.moveToCiv(us)
                    city.getCenterTile().getUnits().forEach { it.movementAlgs().teleportToClosestMoveableTile() }
                }
                if(offer.type== TradeType.Treaty){
                    if(offer.name=="Peace Treaty"){
                        us.diplomacy[them.civName]!!.diplomaticStatus= DiplomaticStatus.Peace
                        for(unit in us.getCivUnits().filter { it.getTile().getOwner()==them })
                            unit.movementAlgs().teleportToClosestMoveableTile()
                    }
                }
                if(offer.type==TradeType.Introduction)
                    us.meetCivilization(us.gameInfo.civilizations
                            .first { it.civName==offer.name.split(" ")[2] })
            }
        }

        transfer(ourCivilization,otherCivilization,currentTrade)
        transfer(otherCivilization,ourCivilization,currentTrade.reverse())
    }
}