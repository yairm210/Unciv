package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType

class TradeLogic(val ourCivilization:CivilizationInfo, val otherCivilization: CivilizationInfo) {

    /** Contains everything we could offer the other player, whether we've actually offered it or not */
    val ourAvailableOffers = getAvailableOffers(ourCivilization, otherCivilization)
    val theirAvailableOffers = getAvailableOffers(otherCivilization, ourCivilization)
    val currentTrade = Trade()

    private fun getAvailableOffers(civInfo: CivilizationInfo, otherCivilization: CivilizationInfo): TradeOffersList {
        val offers = TradeOffersList()
        if (civInfo.isCityState() && otherCivilization.isCityState()) return offers
        if (civInfo.isAtWarWith(otherCivilization))
            offers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty))

        if (!otherCivilization.getDiplomacyManager(civInfo).hasOpenBorders
                && !otherCivilization.isCityState()
                && civInfo.hasUnique(UniqueType.EnablesOpenBorders)
                && otherCivilization.hasUnique(UniqueType.EnablesOpenBorders)) {
            offers.add(TradeOffer(Constants.openBorders, TradeType.Agreement))
        }

        for (entry in civInfo.getCivResourcesWithOriginsForTrade()
            .filterNot { it.resource.resourceType == ResourceType.Bonus }
            .filter { it.origin == Constants.tradable }
        ) {
            val resourceTradeType = if (entry.resource.resourceType == ResourceType.Luxury) TradeType.Luxury_Resource
            else TradeType.Strategic_Resource
            offers.add(TradeOffer(entry.resource.name, resourceTradeType, entry.amount))
        }

        offers.add(TradeOffer("Gold", TradeType.Gold, civInfo.gold))
        offers.add(TradeOffer("Gold per turn", TradeType.Gold_Per_Turn, civInfo.statsForNextTurn.gold.toInt()))

        if (!civInfo.isOneCityChallenger() && !otherCivilization.isOneCityChallenger()
                && !civInfo.isCityState() && !otherCivilization.isCityState()) {
            for (city in civInfo.cities.filterNot { it.isCapital() || it.isInResistance() })
                offers.add(TradeOffer(city.id, TradeType.City))
        }

        val otherCivsWeKnow = civInfo.getKnownCivs()
            .filter { it.civName != otherCivilization.civName && it.isMajorCiv() && !it.isDefeated() }

        if (civInfo.gameInfo.ruleSet.modOptions.hasUnique(ModOptionsConstants.tradeCivIntroductions)) {
            val civsWeKnowAndTheyDont = otherCivsWeKnow
                .filter { !otherCivilization.diplomacy.containsKey(it.civName) && !it.isDefeated() }
            for (thirdCiv in civsWeKnowAndTheyDont) {
                offers.add(TradeOffer(thirdCiv.civName, TradeType.Introduction))
            }
        }

        if (!civInfo.isCityState() && !otherCivilization.isCityState()
                && !civInfo.gameInfo.ruleSet.modOptions.hasUnique(ModOptionsConstants.diplomaticRelationshipsCannotChange)) {
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
                    to.addGold(offer.amount)
                    from.addGold(-offer.amount)
                }
                if (offer.type == TradeType.Technology) {
                    to.tech.addTechnology(offer.name)
                }
                if (offer.type == TradeType.City) {
                    val city = from.cities.first { it.id == offer.name }
                    city.moveToCiv(to)
                    city.getCenterTile().getUnits().toList().forEach { it.movement.teleportToClosestMoveableTile() }
                    for (tile in city.getTiles()) {
                        for (unit in tile.getUnits().toList()) {
                            if (!unit.civInfo.canPassThroughTiles(to) && !unit.canEnterForeignTerrain) 
                                unit.movement.teleportToClosestMoveableTile()
                        }
                    }
                    to.updateViewableTiles()
                    from.updateViewableTiles()

                    // suggest an option to liberate the city
                    if (to.isPlayerCivilization()
                        && city.foundingCiv != ""
                        && from.civName != city.foundingCiv // can't liberate if the city actually belongs to those guys
                        && to.civName != city.foundingCiv)  // can't liberate if it's our city
                      to.popupAlerts.add(PopupAlert(AlertType.CityTraded, city.id))
                }
                if (offer.type == TradeType.Treaty) {
                    if (offer.name == Constants.peaceTreaty) to.getDiplomacyManager(from).makePeace()
                    if (offer.name == Constants.researchAgreement) {
                        to.addGold(-offer.amount)
                        to.getDiplomacyManager(from).setFlag(DiplomacyFlags.ResearchAgreement, offer.duration)
                    }
                }
                if (offer.type == TradeType.Introduction)
                    to.makeCivilizationsMeet(to.gameInfo.getCivilization(offer.name))
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