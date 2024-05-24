package com.unciv.logic.automation.civilization

import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.automation.civilization.MotivationToAttackAutomation.hasAtLeastMotivationToAttack
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeRequest
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.screens.victoryscreen.RankingType

object DiplomacyAutomation {

    internal fun offerDeclarationOfFriendship(civInfo: Civilization) {
        val civsThatWeCanDeclareFriendshipWith = civInfo.getKnownCivs()
            .filter { civInfo.diplomacyFunctions.canSignDeclarationOfFriendshipWith(it)
                && !civInfo.getDiplomacyManager(it).hasFlag(DiplomacyFlags.DeclinedDeclarationOfFriendship)}
            .sortedByDescending { it.getDiplomacyManager(civInfo).relationshipLevel() }.toList()
        for (otherCiv in civsThatWeCanDeclareFriendshipWith) {
            // Default setting is 2, this will be changed according to different civ.
            if ((1..10).random() <= 2 && wantsToSignDeclarationOfFrienship(civInfo, otherCiv)) {
                otherCiv.popupAlerts.add(PopupAlert(AlertType.DeclarationOfFriendship, civInfo.civName))
            }
        }
    }

    internal fun wantsToSignDeclarationOfFrienship(civInfo: Civilization, otherCiv: Civilization): Boolean {
        val diploManager = civInfo.getDiplomacyManager(otherCiv)
        if (diploManager.hasFlag(DiplomacyFlags.DeclinedDeclarationOfFriendship)) return false
        // Shortcut, if it is below favorable then don't consider it
        if (diploManager.isRelationshipLevelLT(RelationshipLevel.Favorable)) return false

        val numOfFriends = civInfo.diplomacy.count { it.value.hasFlag(DiplomacyFlags.DeclarationOfFriendship) }
        val otherCivNumberOfFriends = otherCiv.diplomacy.count { it.value.hasFlag(DiplomacyFlags.DeclarationOfFriendship) }
        val knownCivs = civInfo.getKnownCivs().count { it.isMajorCiv() && it.isAlive() }
        val allCivs = civInfo.gameInfo.civilizations.count { it.isMajorCiv() } - 1 // Don't include us
        val deadCivs = civInfo.gameInfo.civilizations.count { it.isMajorCiv() && !it.isAlive() }
        val allAliveCivs = allCivs - deadCivs

        // Motivation should be constant as the number of civs changes
        var motivation = diploManager.opinionOfOtherCiv().toInt() - 40

        // If the other civ is stronger than we are compelled to be nice to them
        // If they are too weak, then thier friendship doesn't mean much to us
        motivation += when (Automation.threatAssessment(civInfo,otherCiv)) {
            ThreatLevel.VeryHigh -> 10
            ThreatLevel.High -> 5
            ThreatLevel.VeryLow -> -5
            else -> 0
        }

        // Try to ally with a fourth of the civs in play
        val civsToAllyWith = 0.25f * allAliveCivs
        if (numOfFriends < civsToAllyWith) {
            // Goes from 10 to 0 once the civ gets 1/4 of all alive civs as friends
            motivation += (10 - 10 * (numOfFriends / civsToAllyWith)).toInt()
        } else {
            // Goes from 0 to -120 as the civ gets more friends, offset by civsToAllyWith
            motivation -= (120f * (numOfFriends - civsToAllyWith) / (knownCivs - civsToAllyWith)).toInt()
        }

        // The more friends they have the less we should want to sign friendship (To promote teams)
        motivation -= otherCivNumberOfFriends * 10

        // Goes from 0 to -50 as more civs die
        // this is meant to prevent the game from stalemating when a group of friends
        // conquers all oposition
        motivation -= deadCivs / allCivs * 50

        // Become more desperate as we have more wars
        motivation += civInfo.diplomacy.values.count { it.otherCiv().isMajorCiv() && it.diplomaticStatus == DiplomaticStatus.War } * 10

        // Wait to declare frienships until more civs
        // Goes from -30 to 0 when we know 75% of allCivs
        val civsToKnow = 0.75f * allAliveCivs
        motivation -= ((civsToKnow - knownCivs) / civsToKnow * 30f).toInt().coerceAtLeast(0)

        motivation -= hasAtLeastMotivationToAttack(civInfo, otherCiv, motivation / 2) * 2

        return motivation > 0
    }

    internal fun offerOpenBorders(civInfo: Civilization) {
        if (!civInfo.hasUnique(UniqueType.EnablesOpenBorders)) return
        val civsThatWeCanOpenBordersWith = civInfo.getKnownCivs()
            .filter { it.isMajorCiv() && !civInfo.isAtWarWith(it)
                && it.hasUnique(UniqueType.EnablesOpenBorders)
                && !civInfo.getDiplomacyManager(it).hasOpenBorders
                && !civInfo.getDiplomacyManager(it).hasFlag(DiplomacyFlags.DeclinedOpenBorders)
                && !isTradeBeingOffered(civInfo, it, Constants.openBorders)
            }

            .sortedByDescending { it.getDiplomacyManager(civInfo).relationshipLevel() }.toList()
        for (otherCiv in civsThatWeCanOpenBordersWith) {
            // Default setting is 3, this will be changed according to different civ.
            if ((1..10).random() < 7) continue
            if (wantsToOpenBorders(civInfo, otherCiv)) {
                val tradeLogic = TradeLogic(civInfo, otherCiv)
                tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.openBorders, TradeType.Agreement))
                tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.openBorders, TradeType.Agreement))

                otherCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
            } else {
                // Remember this for a few turns to save computation power
                civInfo.getDiplomacyManager(otherCiv).setFlag(DiplomacyFlags.DeclinedOpenBorders, 5)
            }
        }
    }

    fun wantsToOpenBorders(civInfo: Civilization, otherCiv: Civilization): Boolean {
        val diploManager = civInfo.getDiplomacyManager(otherCiv)
        if (diploManager.hasFlag(DiplomacyFlags.DeclinedOpenBorders)) return false
        if (diploManager.isRelationshipLevelLT(RelationshipLevel.Favorable)) return false
        // Don't accept if they are at war with our friends, they might use our land to attack them
        if (civInfo.diplomacy.values.any { it.isRelationshipLevelGE(RelationshipLevel.Friend) && it.otherCiv().isAtWarWith(otherCiv)})
            return false
        if (hasAtLeastMotivationToAttack(civInfo, otherCiv, (diploManager.opinionOfOtherCiv()/ 2 - 10).toInt()) >= 0)
            return false
        return true
    }

    internal fun offerResearchAgreement(civInfo: Civilization) {
        if (!civInfo.diplomacyFunctions.canSignResearchAgreement()) return // don't waste your time

        val canSignResearchAgreementCiv = civInfo.getKnownCivs()
            .filter {
                civInfo.diplomacyFunctions.canSignResearchAgreementsWith(it)
                    && !civInfo.getDiplomacyManager(it).hasFlag(DiplomacyFlags.DeclinedResearchAgreement)
                    && !isTradeBeingOffered(civInfo, it, Constants.researchAgreement)
            }
            .sortedByDescending { it.stats.statsForNextTurn.science }

        for (otherCiv in canSignResearchAgreementCiv) {
            // Default setting is 5, this will be changed according to different civ.
            if ((1..10).random() <= 5) continue
            val tradeLogic = TradeLogic(civInfo, otherCiv)
            val cost = civInfo.diplomacyFunctions.getResearchAgreementCost(otherCiv)
            tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.researchAgreement, TradeType.Treaty, cost))
            tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.researchAgreement, TradeType.Treaty, cost))

            otherCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
        }
    }

    internal fun offerDefensivePact(civInfo: Civilization) {
        if (!civInfo.diplomacyFunctions.canSignDefensivePact()) return // don't waste your time

        val canSignDefensivePactCiv = civInfo.getKnownCivs()
            .filter {
                civInfo.diplomacyFunctions.canSignDefensivePactWith(it)
                    && !civInfo.getDiplomacyManager(it).hasFlag(DiplomacyFlags.DeclinedDefensivePact)
                    && civInfo.getDiplomacyManager(it).relationshipIgnoreAfraid() == RelationshipLevel.Ally
                    && !isTradeBeingOffered(civInfo, it, Constants.defensivePact)
            }

        for (otherCiv in canSignDefensivePactCiv) {
            // Default setting is 3, this will be changed according to different civ.
            if ((1..10).random() <= 7) continue
            if (wantsToSignDefensivePact(civInfo, otherCiv)) {
                //todo: Add more in depth evaluation here
                val tradeLogic = TradeLogic(civInfo, otherCiv)
                tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.defensivePact, TradeType.Treaty))
                tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.defensivePact, TradeType.Treaty))

                otherCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
            } else {
                // Remember this for a few turns to save computation power
                civInfo.getDiplomacyManager(otherCiv).setFlag(DiplomacyFlags.DeclinedDefensivePact, 5)
            }
        }
    }

    fun wantsToSignDefensivePact(civInfo: Civilization, otherCiv: Civilization): Boolean {
        val diploManager = civInfo.getDiplomacyManager(otherCiv)
        if (diploManager.hasFlag(DiplomacyFlags.DeclinedDefensivePact)) return false
        if (diploManager.isRelationshipLevelLT(RelationshipLevel.Ally)) return false
        val commonknownCivs = diploManager.getCommonKnownCivs()
        // If they have bad relations with any of our friends, don't consider it
        for (thirdCiv in commonknownCivs) {
            if (civInfo.getDiplomacyManager(thirdCiv).isRelationshipLevelGE(RelationshipLevel.Friend)
                && thirdCiv.getDiplomacyManager(otherCiv).isRelationshipLevelLT(RelationshipLevel.Favorable))
                return false
        }
        // If they have bad relations with any of thier friends, don't consider it
        for (thirdCiv in commonknownCivs) {
            if (otherCiv.getDiplomacyManager(thirdCiv).isRelationshipLevelGE(RelationshipLevel.Friend)
                && thirdCiv.getDiplomacyManager(civInfo).isRelationshipLevelLT(RelationshipLevel.Neutral))
                return false
        }

        val defensivePacts = civInfo.diplomacy.count { it.value.hasFlag(DiplomacyFlags.DefensivePact) }
        val otherCivNonOverlappingDefensivePacts = otherCiv.diplomacy.values.count { it.hasFlag(DiplomacyFlags.DefensivePact)
            && (!it.otherCiv().knows(civInfo) || !it.otherCiv().getDiplomacyManager(civInfo).hasFlag(DiplomacyFlags.DefensivePact)) }
        val allCivs = civInfo.gameInfo.civilizations.count { it.isMajorCiv() } - 1 // Don't include us
        val deadCivs = civInfo.gameInfo.civilizations.count { it.isMajorCiv() && !it.isAlive() }
        val allAliveCivs = allCivs - deadCivs

        // We have to already be at RelationshipLevel.Ally, so we must have 80 oppinion of them
        var motivation = diploManager.opinionOfOtherCiv().toInt() - 80

        // If they are stronger than us, then we value it a lot more
        // If they are weaker than us, then we don't value it
        motivation += when (Automation.threatAssessment(civInfo,otherCiv)) {
            ThreatLevel.VeryHigh -> 10
            ThreatLevel.High -> 5
            ThreatLevel.Low -> -5
            ThreatLevel.VeryLow -> -10
            else -> 0
        }

        // If they have a defensive pact with another civ then we would get drawn into thier battles as well
        motivation -= 15 * otherCivNonOverlappingDefensivePacts

        // Becomre more desperate as we have more wars
        motivation += civInfo.diplomacy.values.count { it.otherCiv().isMajorCiv() && it.diplomaticStatus == DiplomaticStatus.War } * 5

        // Try to have a defensive pact with 1/5 of all civs
        val civsToAllyWith = 0.20f * allAliveCivs
        // Goes from 0 to -50 as the civ gets more allies, offset by civsToAllyWith
        motivation -= (50f * (defensivePacts - civsToAllyWith) / (allAliveCivs - civsToAllyWith)).coerceAtMost(0f).toInt()

        return motivation > 0
    }

    internal fun declareWar(civInfo: Civilization) {
        if (civInfo.wantsToFocusOn(Victory.Focus.Culture) &&
            civInfo.getPersonality().isNeutralPersonality)
            return
        if (civInfo.cities.isEmpty() || civInfo.diplomacy.isEmpty()) return
        if (civInfo.isAtWar() || civInfo.getHappiness() <= 0) return

        val ourMilitaryUnits = civInfo.units.getCivUnits().filter { !it.isCivilian() }.count()
        if (ourMilitaryUnits < civInfo.cities.size) return
        if (ourMilitaryUnits < 4) return  // to stop AI declaring war at the beginning of games when everyone isn't set up well enough
        // For mods we can't check the number of cities, so we will check the population instead.
        if (civInfo.cities.sumOf { it.population.population } < 12) return // FAR too early for that what are you thinking!

        //evaluate war
        val enemyCivs = civInfo.getKnownCivs()
            .filterNot {
                it == civInfo || it.cities.isEmpty() || !civInfo.getDiplomacyManager(it).canDeclareWar()
                    || it.cities.none { city -> civInfo.hasExplored(city.getCenterTile()) }
            }
        // If the AI declares war on a civ without knowing the location of any cities, it'll just keep amassing an army and not sending it anywhere,
        //   and end up at a massive disadvantage

        if (enemyCivs.none()) return

        val minMotivationToAttack = 20
        // Attack the highest score enemy that we are willing to fight.
        // This is to help prevent civs from ganging up on smaller civs
        // and directs them to fight their competitors instead.
        val civWithBestMotivationToAttack = enemyCivs
            .filter { hasAtLeastMotivationToAttack(civInfo, it, minMotivationToAttack) >= 20 }
            .maxByOrNull { it.getStatForRanking(RankingType.Score) }

        if (civWithBestMotivationToAttack != null)
            civInfo.getDiplomacyManager(civWithBestMotivationToAttack).declareWar()
    }


    internal fun offerPeaceTreaty(civInfo: Civilization) {
        if (!civInfo.isAtWar() || civInfo.cities.isEmpty() || civInfo.diplomacy.isEmpty()) return

        val enemiesCiv = civInfo.diplomacy.filter { it.value.diplomaticStatus == DiplomaticStatus.War }
            .map { it.value.otherCiv() }
            .filterNot { it == civInfo || it.isBarbarian() || it.cities.isEmpty() }
            .filter { !civInfo.getDiplomacyManager(it).hasFlag(DiplomacyFlags.DeclinedPeace) }
            // Don't allow AIs to offer peace to city states allied with their enemies
            .filterNot { it.isCityState() && it.getAllyCiv() != null && civInfo.isAtWarWith(civInfo.gameInfo.getCivilization(it.getAllyCiv()!!)) }
            // ignore civs that we have already offered peace this turn as a counteroffer to another civ's peace offer
            .filter { it.tradeRequests.none { tradeRequest -> tradeRequest.requestingCiv == civInfo.civName && tradeRequest.trade.isPeaceTreaty() } }

        for (enemy in enemiesCiv) {
            if(hasAtLeastMotivationToAttack(civInfo, enemy, 10) >= 10) {
                // We can still fight. Refuse peace.
                continue
            }

            // pay for peace
            val tradeLogic = TradeLogic(civInfo, enemy)

            tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty))
            tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty))

            var moneyWeNeedToPay = -TradeEvaluation().evaluatePeaceCostForThem(civInfo, enemy)

            if (civInfo.gold > 0 && moneyWeNeedToPay > 0) {
                if (moneyWeNeedToPay > civInfo.gold) {
                    moneyWeNeedToPay = civInfo.gold  // As much as possible
                }
                tradeLogic.currentTrade.ourOffers.add(
                    TradeOffer("Gold".tr(), TradeType.Gold, moneyWeNeedToPay)
                )
            }

            enemy.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
        }
    }

    private fun isTradeBeingOffered(civInfo: Civilization, otherCiv: Civilization, offerName:String): Boolean {
        return civInfo.tradeRequests.filter { request -> request.requestingCiv == otherCiv.civName }
            .any { trade -> trade.trade.ourOffers.any { offer -> offer.name == offerName }}
            || civInfo.tradeRequests.filter { request -> request.requestingCiv == otherCiv.civName }
            .any { trade -> trade.trade.theirOffers.any { offer -> offer.name == offerName }}
    }
}
