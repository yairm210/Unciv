package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.translations.tr

class TradeLogic(val ourCivilization:CivilizationInfo, val otherCivilization: CivilizationInfo){

    /** Contains everything we could offer the other player, whether we've actually offered it or not */
    val ourAvailableOffers = getAvailableOffers(ourCivilization,otherCivilization)
    val theirAvailableOffers = getAvailableOffers(otherCivilization,ourCivilization)
    val currentTrade = Trade()

    fun getAvailableOffers(civInfo: CivilizationInfo, otherCivilization: CivilizationInfo): TradeOffersList {
        val offers = TradeOffersList()
        if (civInfo.isCityState() && otherCivilization.isCityState()) return offers
        if (civInfo.isAtWarWith(otherCivilization))
            offers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty))

        if(!otherCivilization.getDiplomacyManager(civInfo).hasOpenBorders
                && !otherCivilization.isCityState()
                && civInfo.tech.getTechUniques().contains("Enables Open Borders agreements")
                && otherCivilization.tech.getTechUniques().contains("Enables Open Borders agreements")) {
            val relationshipLevel = otherCivilization.getDiplomacyManager(civInfo).relationshipLevel()

            offers.add(TradeOffer(Constants.openBorders, TradeType.Agreement))
        }

        for(entry in civInfo.getCivResources()
                .filterNot { it.resource.resourceType == ResourceType.Bonus }) {
            val resourceTradeType = if(entry.resource.resourceType== ResourceType.Luxury) TradeType.Luxury_Resource
            else TradeType.Strategic_Resource
            offers.add(TradeOffer(entry.resource.name, resourceTradeType, entry.amount))
        }

        offers.add(TradeOffer("Gold".tr(), TradeType.Gold, civInfo.gold))
        offers.add(TradeOffer("Gold per turn".tr(), TradeType.Gold_Per_Turn, civInfo.statsForNextTurn.gold.toInt()))

        if (!civInfo.isOneCityChallenger() && !otherCivilization.isOneCityChallenger()
                && !civInfo.isCityState() && !otherCivilization.isCityState()) {
            for (city in civInfo.cities.filterNot { it.isCapital() || it.isInResistance() })
                offers.add(TradeOffer(city.id, TradeType.City))
        }

        val otherCivsWeKnow = civInfo.getKnownCivs()
                .filter { it.civName != otherCivilization.civName && it.isMajorCiv() && !it.isDefeated() }
        val civsWeKnowAndTheyDont = otherCivsWeKnow
                .filter { !otherCivilization.diplomacy.containsKey(it.civName) && !it.isDefeated() }

        for (thirdCiv in civsWeKnowAndTheyDont) {
            offers.add(TradeOffer(thirdCiv.civName, TradeType.Introduction))
        }

        if (!civInfo.isCityState() && !otherCivilization.isCityState()) {
            val civsWeBothKnow = otherCivsWeKnow
                    .filter { otherCivilization.diplomacy.containsKey(it.civName) }
            val civsWeArentAtWarWith = civsWeBothKnow
                    .filter { civInfo.getDiplomacyManager(it).canDeclareWar() }
            for (thirdCiv in civsWeArentAtWarWith) {
                offers.add(TradeOffer(thirdCiv.civName, TradeType.WarDeclaration))
            }
        }

        return offers
    }

    fun acceptTrade() {
        ourCivilization.getDiplomacyManager(otherCivilization).apply {
            trades.add(currentTrade)
            updateHasOpenBorders()
        }
        otherCivilization.getDiplomacyManager(ourCivilization).apply {
            trades.add(currentTrade.reverse())
            updateHasOpenBorders()
        }

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
                if (offer.type == TradeType.City) {
                    val city = from.cities.first { it.id == offer.name }
                    city.moveToCiv(to)
                    city.getCenterTile().getUnits().toList().forEach { it.movement.teleportToClosestMoveableTile() }
                    to.updateViewableTiles()
                    from.updateViewableTiles()
                }
                if (offer.type == TradeType.Treaty) {
                    if (offer.name == Constants.peaceTreaty) to.getDiplomacyManager(from).makePeace()
                    if (offer.name == Constants.researchAgreement) {
                        to.gold -= offer.amount
                        to.getDiplomacyManager(from).setFlag(DiplomacyFlags.ResearchAgreement, offer.duration)
                    }
                }
                if (offer.type == TradeType.Introduction)
                    to.meetCivilization(to.gameInfo.getCivilization(offer.name))

                if (offer.type == TradeType.WarDeclaration) {
                    val nameOfCivToDeclareWarOn = offer.name
                    from.getDiplomacyManager(nameOfCivToDeclareWarOn).declareWar()
                }
            }
            to.updateStatsForNextTurn()
            to.updateDetailedCivResources()
        }

        transferTrade(ourCivilization, otherCivilization, currentTrade)
        transferTrade(otherCivilization, ourCivilization, currentTrade.reverse())
    }
}

