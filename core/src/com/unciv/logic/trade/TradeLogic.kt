package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.CityStateFunctions
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType

class TradeLogic(val ourCivilization: Civilization, val otherCivilization: Civilization) {

    /** Contains everything we could offer the other player, whether we've actually offered it or not */
    val ourAvailableOffers = getAvailableOffers(ourCivilization, otherCivilization)
    val theirAvailableOffers = getAvailableOffers(otherCivilization, ourCivilization)
    val currentTrade = Trade()

    private fun getAvailableOffers(civInfo: Civilization, otherCivilization: Civilization): TradeOffersList {
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

        if (civInfo.diplomacyFunctions.canSignResearchAgreementNoCostWith(otherCivilization))
            offers.add(TradeOffer(Constants.researchAgreement, TradeType.Treaty, civInfo.diplomacyFunctions.getResearchAgreementCost(otherCivilization)))

        if (civInfo.diplomacyFunctions.canSignDefensivePactWith(otherCivilization))
            offers.add(TradeOffer(Constants.defensivePact, TradeType.Treaty))

        for (entry in civInfo.getCivResourcesWithOriginsForTrade()
            .filterNot { it.resource.resourceType == ResourceType.Bonus }
            .filter { it.origin == Constants.tradable }
        ) {
            val resourceTradeType = if (entry.resource.resourceType == ResourceType.Luxury) TradeType.Luxury_Resource
            else TradeType.Strategic_Resource
            offers.add(TradeOffer(entry.resource.name, resourceTradeType, entry.amount))
        }

        offers.add(TradeOffer("Gold", TradeType.Gold, civInfo.gold))
        offers.add(TradeOffer("Gold per turn", TradeType.Gold_Per_Turn, civInfo.stats.statsForNextTurn.gold.toInt()))

        if (!civInfo.isOneCityChallenger() && !otherCivilization.isOneCityChallenger()
                && !civInfo.isCityState() && !otherCivilization.isCityState()) {
            for (city in civInfo.cities.filterNot { it.isCapital() || it.isInResistance() })
                offers.add(TradeOffer(city.id, TradeType.City))
        }

        val otherCivsWeKnow = civInfo.getKnownCivs()
            .filter { it.civName != otherCivilization.civName && it.isMajorCiv() && !it.isDefeated() }

        if (civInfo.gameInfo.ruleset.modOptions.hasUnique(UniqueType.TradeCivIntroductions)) {
            val civsWeKnowAndTheyDont = otherCivsWeKnow
                .filter { !otherCivilization.diplomacy.containsKey(it.civName) && !it.isDefeated() }
            for (thirdCiv in civsWeKnowAndTheyDont) {
                offers.add(TradeOffer(thirdCiv.civName, TradeType.Introduction))
            }
        }

        if (!civInfo.isCityState() && !otherCivilization.isCityState()
                && !civInfo.gameInfo.ruleset.modOptions.hasUnique(UniqueType.DiplomaticRelationshipsCannotChange)) {

            val civsWeBothKnow = civInfo.getDiplomacyManager(otherCivilization).getCommonKnownCivs()

            val civsWeArentAtWarWith = civsWeBothKnow
                    .filter { civInfo.getDiplomacyManager(it).canDeclareWar() }

            for (thirdCiv in civsWeArentAtWarWith) {
                offers.add(TradeOffer(thirdCiv.civName, TradeType.WarDeclaration))
            }

            val civsWeAreAtWarWith = civsWeBothKnow
                .filter { civInfo.getDiplomacyManager(it).diplomaticStatus == DiplomaticStatus.War }

            for (thirdCiv in civsWeAreAtWarWith) {
                offers.add(TradeOffer(thirdCiv.civName, TradeType.PeaceDeclaration))
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
        fun transferTrade(from: Civilization, to: Civilization, offer: TradeOffer) {
            when (offer.type) {
                TradeType.Gold -> {
                    to.addGold(offer.amount)
                    from.addGold(-offer.amount)
                }
                TradeType.Technology -> {
                    to.tech.addTechnology(offer.name)
                }
                TradeType.City -> {
                    val city = from.cities.first { it.id == offer.name }
                    city.moveToCiv(to)
                    city.getCenterTile().getUnits().toList()
                        .forEach { it.movement.teleportToClosestMoveableTile() }
                    for (tile in city.getTiles()) {
                        for (unit in tile.getUnits().toList()) {
                            if (!unit.civ.diplomacyFunctions.canPassThroughTiles(to) && !unit.cache.canEnterForeignTerrain)
                                unit.movement.teleportToClosestMoveableTile()
                        }
                    }
                    to.cache.updateOurTiles()
                    from.cache.updateOurTiles()

                    // suggest an option to liberate the city
                    if (to.isHuman()
                            && city.foundingCiv != ""
                            && from.civName != city.foundingCiv // can't liberate if the city actually belongs to those guys
                            && to.civName != city.foundingCiv
                    )  // can't liberate if it's our city
                        to.popupAlerts.add(PopupAlert(AlertType.CityTraded, city.id))
                }
                TradeType.Treaty -> {
                    // Note: Treaties are not transfered from both sides due to notifications and double signing
                    if (offer.name == Constants.peaceTreaty) to.getDiplomacyManager(from).makePeace()
                    if (offer.name == Constants.researchAgreement) {
                        to.addGold(-offer.amount)
                        from.addGold(-offer.amount)
                        to.getDiplomacyManager(from)
                            .setFlag(DiplomacyFlags.ResearchAgreement, offer.duration)
                        from.getDiplomacyManager(to)
                            .setFlag(DiplomacyFlags.ResearchAgreement, offer.duration)
                    }
                    if (offer.name == Constants.defensivePact) to.getDiplomacyManager(from).signDefensivePact(offer.duration)
                }
                TradeType.Introduction -> to.diplomacyFunctions.makeCivilizationsMeet(to.gameInfo.getCivilization(offer.name))
                TradeType.WarDeclaration -> {
                    val nameOfCivToDeclareWarOn = offer.name
                    from.getDiplomacyManager(nameOfCivToDeclareWarOn).declareWar()
                }
                TradeType.PeaceDeclaration -> {
                    val nameOfCivToDeclarePeaceOn = offer.name
                    from.getDiplomacyManager(nameOfCivToDeclarePeaceOn).makePeace()
                    //let's get the civilization to declare peace on
                    val civToDeclarePeaceOn = from.gameInfo.civilizations.first() { it.civName == nameOfCivToDeclarePeaceOn}
                    // let's create a "puppet" peaceTreaty to propagate turnsToPeaceTreaty duration-related effects
                    val peaceTrade = Trade()
                    peaceTrade.ourOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty, 1, com.unciv.UncivGame.Current.gameInfo!!.speed))
                    // let's add peaceTreaties to both diplomacyManagers
                    from.getDiplomacyManager(civToDeclarePeaceOn).apply {
                        trades.add(peaceTrade)
                    }
                    civToDeclarePeaceOn.getDiplomacyManager(from).apply {
                        trades.add(peaceTrade.reverse())
                    }
                }
                else -> {}
            }
        }

        if (currentTrade.ourOffers.isEmpty()) { // Must evaluate before moving, or else cities have already moved and we get an exception
            val goldValueOfTrade = TradeEvaluation().getTradeAcceptability(currentTrade, ourCivilization, otherCivilization)
            val diplomaticValueOfTrade = CityStateFunctions(ourCivilization).influenceGainedByGift(otherCivilization, goldValueOfTrade) / 10
            ourCivilization.getDiplomacyManager(otherCivilization).addModifier(DiplomaticModifiers.GaveUsGifts, diplomaticValueOfTrade.toFloat())
        }

        // Transfer of cities needs to happen before peace treaty, to avoid our units teleporting out of areas that soon will be ours
        for (offer in currentTrade.theirOffers.filterNot { it.type == TradeType.Treaty })
            transferTrade(otherCivilization, ourCivilization, offer)
        for (offer in currentTrade.ourOffers.filterNot { it.type == TradeType.Treaty })
            transferTrade(ourCivilization, otherCivilization, offer)

        // Transfter of treaties should only be done from one side to avoid double signing and notifying
        for (offer in currentTrade.theirOffers.filter { it.type == TradeType.Treaty })
            transferTrade(otherCivilization, ourCivilization, offer)

        ourCivilization.cache.updateCivResources()
        ourCivilization.updateStatsForNextTurn()

        otherCivilization.cache.updateCivResources()
        otherCivilization.updateStatsForNextTurn()
    }
}
