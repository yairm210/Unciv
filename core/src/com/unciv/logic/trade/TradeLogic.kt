package com.unciv.logic.trade

import com.unciv.Constants
import com.unciv.logic.city.managers.SpyFleeReason
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DeclareWarReason
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.WarType
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType

class TradeLogic(val ourCivilization: Civilization, val otherCivilization: Civilization) {

    /** Contains everything we could offer the other player, whether we've actually offered it or not */
    val ourAvailableOffers = getAvailableOffers(ourCivilization, otherCivilization)
    val theirAvailableOffers = getAvailableOffers(otherCivilization, ourCivilization)
    val currentTrade = Trade()

    private fun getAvailableOffers(civInfo: Civilization, otherCivilization: Civilization): TradeOffersList {
        val offers = TradeOffersList()
        if (civInfo.isCityState && otherCivilization.isCityState) return offers
        if (civInfo.isAtWarWith(otherCivilization))
            offers.add(TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = civInfo.gameInfo.speed))

        if (!otherCivilization.getDiplomacyManager(civInfo)!!.hasOpenBorders
                && !otherCivilization.isCityState
                && civInfo.hasUnique(UniqueType.EnablesOpenBorders)
                && otherCivilization.hasUnique(UniqueType.EnablesOpenBorders)) {
            offers.add(TradeOffer(Constants.openBorders, TradeOfferType.Agreement, speed = civInfo.gameInfo.speed))
        }

        if (civInfo.diplomacyFunctions.canSignResearchAgreementNoCostWith(otherCivilization))
            offers.add(TradeOffer(Constants.researchAgreement, TradeOfferType.Treaty,
                civInfo.diplomacyFunctions.getResearchAgreementCost(otherCivilization), civInfo.gameInfo.speed))

        if (civInfo.diplomacyFunctions.canSignDefensivePactWith(otherCivilization))
            offers.add(TradeOffer(Constants.defensivePact, TradeOfferType.Treaty, speed = civInfo.gameInfo.speed))

        for (entry in civInfo.getPerTurnResourcesWithOriginsForTrade()
            .filterNot { it.resource.resourceType == ResourceType.Bonus }
            .filter { it.origin == Constants.tradable }
        ) {
            val resourceTradeOfferType = if (entry.resource.resourceType == ResourceType.Luxury) TradeOfferType.Luxury_Resource
            else TradeOfferType.Strategic_Resource
            offers.add(TradeOffer(entry.resource.name, resourceTradeOfferType, entry.amount, speed = civInfo.gameInfo.speed))
        }
        
        for (entry in civInfo.getStockpiledResourcesForTrade()){
            offers.add(TradeOffer(entry.resource.name, TradeOfferType.Stockpiled_Resource, entry.amount, speed = civInfo.gameInfo.speed))
        }

        offers.add(TradeOffer("Gold", TradeOfferType.Gold, civInfo.gold, speed = civInfo.gameInfo.speed))
        offers.add(TradeOffer("Gold per turn", TradeOfferType.Gold_Per_Turn, civInfo.stats.statsForNextTurn.gold.toInt(), civInfo.gameInfo.speed))

        if (!civInfo.isOneCityChallenger() && !otherCivilization.isOneCityChallenger()
                && !civInfo.isCityState && !otherCivilization.isCityState
        ) {
            for (city in civInfo.cities.filterNot { it.isCapital() || it.isInResistance() })
                offers.add(TradeOffer(city.id, TradeOfferType.City, speed = civInfo.gameInfo.speed))
        }

        val otherCivsWeKnow = civInfo.getKnownCivs()
            .filter { it.civName != otherCivilization.civName && it.isMajorCiv() && !it.isDefeated() }

        if (civInfo.gameInfo.ruleset.modOptions.hasUnique(UniqueType.TradeCivIntroductions)) {
            val civsWeKnowAndTheyDont = otherCivsWeKnow
                .filter { !otherCivilization.diplomacy.containsKey(it.civName) && !it.isDefeated() }
            for (thirdCiv in civsWeKnowAndTheyDont) {
                offers.add(TradeOffer(thirdCiv.civName, TradeOfferType.Introduction, speed = civInfo.gameInfo.speed))
            }
        }

        if (!civInfo.isCityState && !otherCivilization.isCityState
                && !civInfo.gameInfo.ruleset.modOptions.hasUnique(UniqueType.DiplomaticRelationshipsCannotChange)) {
            val civsWeBothKnow = otherCivsWeKnow
                    .filter { otherCivilization.diplomacy.containsKey(it.civName) }
            val civsWeArentAtWarWith = civsWeBothKnow
                    .filter { civInfo.getDiplomacyManager(it)!!.canDeclareWar() }
            for (thirdCiv in civsWeArentAtWarWith) {
                offers.add(TradeOffer(thirdCiv.civName, TradeOfferType.WarDeclaration, speed = civInfo.gameInfo.speed))
            }
        }

        if (!civInfo.isCityState && !otherCivilization.isCityState) {
            val thirdCivsAtWarWeKnow = otherCivilization.getKnownCivs()
                .filter { it.civName != otherCivilization.civName && !it.isDefeated() && it.isAtWarWith(civInfo) }

            for (thirdCiv in thirdCivsAtWarWeKnow) {
                // Setting amount to 0 makes TradeOffer.isTradable() return false and also disables the button in trade window
                val amount = if (TradeEvaluation().isPeaceProposalEnabled(thirdCiv, civInfo)) 1 else 0
                offers.add(TradeOffer(thirdCiv.civName, TradeOfferType.PeaceProposal, amount, civInfo.gameInfo.speed))
            }
        }
        
        return offers
    }

    fun acceptTrade(applyGifts: Boolean = true) {
        val ourDiploManager = ourCivilization.getDiplomacyManager(otherCivilization)!!
        val theirDiploManger = otherCivilization.getDiplomacyManager(ourCivilization)!!

        ourDiploManager.apply {
            trades.add(currentTrade)
            updateHasOpenBorders()
        }
        theirDiploManger.apply {
            trades.add(currentTrade.reverse())
            updateHasOpenBorders()
        }

        // instant transfers
        fun transferTrade(from: Civilization, to: Civilization, offer: TradeOffer) {
            when (offer.type) {
                TradeOfferType.Gold -> {
                    to.addGold(offer.amount)
                    from.addGold(-offer.amount)
                }
                TradeOfferType.Technology -> {
                    to.tech.addTechnology(offer.name)
                }
                TradeOfferType.City -> {
                    val city = from.cities.first { it.id == offer.name }
                    
                    city.espionage.removeAllPresentSpies(SpyFleeReason.CityBought)
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
                TradeOfferType.Treaty -> {
                    // Note: Treaties are not transfered from both sides due to notifications and double signing
                    if (offer.name == Constants.peaceTreaty) to.getDiplomacyManager(from)!!.makePeace()
                    if (offer.name == Constants.researchAgreement) {
                        to.addGold(-offer.amount)
                        from.addGold(-offer.amount)
                        to.getDiplomacyManager(from)!!
                            .setFlag(DiplomacyFlags.ResearchAgreement, offer.duration)
                        from.getDiplomacyManager(to)!!
                            .setFlag(DiplomacyFlags.ResearchAgreement, offer.duration)
                    }
                    if (offer.name == Constants.defensivePact) to.getDiplomacyManager(from)!!.signDefensivePact(offer.duration)
                }
                TradeOfferType.Introduction -> to.diplomacyFunctions.makeCivilizationsMeet(to.gameInfo.getCivilization(offer.name))
                TradeOfferType.WarDeclaration -> {
                    val nameOfCivToDeclareWarOn = offer.name
                    val warType = if (currentTrade.theirOffers.any { it.type == TradeOfferType.WarDeclaration && it.name == nameOfCivToDeclareWarOn }
                            && currentTrade.ourOffers.any {it.type == TradeOfferType.WarDeclaration && it.name == nameOfCivToDeclareWarOn})
                        WarType.TeamWar
                    else WarType.JoinWar

                    from.getDiplomacyManager(nameOfCivToDeclareWarOn)!!.declareWar(DeclareWarReason(warType, to))
                }
                TradeOfferType.PeaceProposal -> {
                    // Convert PeaceProposal to peaceTreaty and apply to warring civs
                    val trade = Trade()
                    val peaceOffer = TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, duration = offer.duration)
                    trade.ourOffers.add(peaceOffer)
                    trade.theirOffers.add(peaceOffer)
                    
                    val thirdCiv = from.gameInfo.getCivilization(offer.name)
                    val tradePartnerDiplo = from.getDiplomacyManager(thirdCiv)!!
                    tradePartnerDiplo.apply {
                        trades.add(trade)
                        updateHasOpenBorders()
                    }

                    thirdCiv.getDiplomacyManager(from)!!.apply {
                        trades.add(trade)
                        updateHasOpenBorders()
                    }

                    tradePartnerDiplo.makePeace()
                }
                else -> {}
            }
        }

        // We shouldn't evaluate trades if we are doing a peace treaty
        // Their value can be so big it throws the gift system out of wack
        if (applyGifts && !currentTrade.ourOffers.any { it.name == Constants.peaceTreaty }) {
            // Must evaluate before moving, or else cities have already moved and we get an exception
            val ourGoldValueOfTrade = TradeEvaluation().getTradeAcceptability(currentTrade, ourCivilization, otherCivilization, includeDiplomaticGifts = false)
            val theirGoldValueOfTrade = TradeEvaluation().getTradeAcceptability(currentTrade.reverse(), otherCivilization, ourCivilization, includeDiplomaticGifts = false)
            if (ourGoldValueOfTrade > theirGoldValueOfTrade) {
                val isPureGift = currentTrade.ourOffers.isEmpty()
                ourDiploManager.giftGold(ourGoldValueOfTrade - theirGoldValueOfTrade.coerceAtLeast(0), isPureGift)
            } else if (theirGoldValueOfTrade > ourGoldValueOfTrade) {
                val isPureGift = currentTrade.theirOffers.isEmpty()
                theirDiploManger.giftGold(theirGoldValueOfTrade - ourGoldValueOfTrade.coerceAtLeast(0), isPureGift)
            }
        }

        // Transfer of cities needs to happen before peace treaty, to avoid our units teleporting out of areas that soon will be ours
        for (offer in currentTrade.theirOffers.filterNot { it.type == TradeOfferType.Treaty })
            transferTrade(otherCivilization, ourCivilization, offer)
        for (offer in currentTrade.ourOffers.filterNot { it.type == TradeOfferType.Treaty })
            transferTrade(ourCivilization, otherCivilization, offer)

        // Transfter of treaties should only be done from one side to avoid double signing and notifying
        for (offer in currentTrade.theirOffers.filter { it.type == TradeOfferType.Treaty })
            transferTrade(otherCivilization, ourCivilization, offer)

        ourCivilization.cache.updateCivResources()
        ourCivilization.updateStatsForNextTurn()

        otherCivilization.cache.updateCivResources()
        otherCivilization.updateStatsForNextTurn()
    }
}
