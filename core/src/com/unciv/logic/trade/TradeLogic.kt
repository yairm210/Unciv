package com.unciv.logic.trade

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tr

class TradeLogic(val ourCivilization:CivilizationInfo, val otherCivilization: CivilizationInfo){

    /** Contains everything we could offer the other player, whether we've actually offered it or not */
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

