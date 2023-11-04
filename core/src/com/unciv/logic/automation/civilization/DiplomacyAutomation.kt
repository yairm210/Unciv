package com.unciv.logic.automation.civilization

import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.BFS
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeRequest
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Victory
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
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
        // Shortcut, if it is below favorable then don't consider it
        if (diploManager.isRelationshipLevelLT(RelationshipLevel.Favorable)) return false

        val numOfFriends = civInfo.diplomacy.count { it.value.hasFlag(DiplomacyFlags.DeclarationOfFriendship) }
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
            // Goes form 0 to -120 as the civ gets more friends, offset by civsToAllyWith
            motivation -= (120f * (numOfFriends - civsToAllyWith) / (knownCivs - civsToAllyWith)).toInt()
        }

        // Goes from 0 to -50 as more civs die
        // this is meant to prevent the game from stalemating when a group of friends
        // conquers all oposition
        motivation -= deadCivs / allCivs * 50

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
                && !civInfo.getDiplomacyManager(it).hasFlag(DiplomacyFlags.DeclinedOpenBorders) }
            .sortedByDescending { it.getDiplomacyManager(civInfo).relationshipLevel() }.toList()
        for (otherCiv in civsThatWeCanOpenBordersWith) {
            // Default setting is 3, this will be changed according to different civ.
            if ((1..10).random() <= 3 && wantsToOpenBorders(civInfo, otherCiv)) {
                val tradeLogic = TradeLogic(civInfo, otherCiv)
                tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.openBorders, TradeType.Agreement))
                tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.openBorders, TradeType.Agreement))

                otherCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
            }
        }
    }

    fun wantsToOpenBorders(civInfo: Civilization, otherCiv: Civilization): Boolean {
        if (civInfo.getDiplomacyManager(otherCiv).isRelationshipLevelLT(RelationshipLevel.Favorable)) return false
        // Don't accept if they are at war with our friends, they might use our land to attack them
        if (civInfo.diplomacy.values.any { it.isRelationshipLevelGE(RelationshipLevel.Friend) && it.otherCiv().isAtWarWith(otherCiv)})
            return false
        if (hasAtLeastMotivationToAttack(civInfo, otherCiv, (civInfo.getDiplomacyManager(otherCiv).opinionOfOtherCiv()/ 2 - 10).toInt()) >= 0)
            return false
        return true
    }

    internal fun offerResearchAgreement(civInfo: Civilization) {
        if (!civInfo.diplomacyFunctions.canSignResearchAgreement()) return // don't waste your time

        val canSignResearchAgreementCiv = civInfo.getKnownCivs()
            .filter {
                civInfo.diplomacyFunctions.canSignResearchAgreementsWith(it)
                    && !civInfo.getDiplomacyManager(it).hasFlag(DiplomacyFlags.DeclinedResearchAgreement)
            }
            .sortedByDescending { it.stats.statsForNextTurn.science }

        for (otherCiv in canSignResearchAgreementCiv) {
            // Default setting is 5, this will be changed according to different civ.
            if ((1..10).random() > 5) continue
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
            }

        for (otherCiv in canSignDefensivePactCiv) {
            // Default setting is 3, this will be changed according to different civ.
            if ((1..10).random() <= 3 && wantsToSignDefensivePact(civInfo, otherCiv)) {
                //todo: Add more in depth evaluation here
                val tradeLogic = TradeLogic(civInfo, otherCiv)
                tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.defensivePact, TradeType.Treaty))
                tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.defensivePact, TradeType.Treaty))

                otherCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
            }
        }
    }

    fun wantsToSignDefensivePact(civInfo: Civilization, otherCiv: Civilization): Boolean {
        val diploManager = civInfo.getDiplomacyManager(otherCiv)
        if (diploManager.isRelationshipLevelLT(RelationshipLevel.Ally)) return false
        val commonknownCivs = diploManager.getCommonKnownCivs()
        // If they have bad relations with any of our friends, don't consider it
        for(thirdCiv in commonknownCivs) {
            if (civInfo.getDiplomacyManager(thirdCiv).isRelationshipLevelGE(RelationshipLevel.Friend)
                && thirdCiv.getDiplomacyManager(otherCiv).isRelationshipLevelLT(RelationshipLevel.Favorable))
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
            ThreatLevel.Low -> -15
            ThreatLevel.VeryLow -> -30
            else -> 0
        }

        // If they have a defensive pact with another civ then we would get drawn into thier battles as well
        motivation -= 10 * otherCivNonOverlappingDefensivePacts

        // Try to have a defensive pact with 1/5 of all civs
        val civsToAllyWith = 0.20f * allAliveCivs
        // Goes form 0 to -50 as the civ gets more allies, offset by civsToAllyWith
        motivation -= (50f * (defensivePacts - civsToAllyWith) / (allAliveCivs - civsToAllyWith)).coerceAtMost(0f).toInt()

        return motivation > 0
    }

    internal fun declareWar(civInfo: Civilization) {
        if (civInfo.wantsToFocusOn(Victory.Focus.Culture)) return
        if (civInfo.cities.isEmpty() || civInfo.diplomacy.isEmpty()) return
        if (civInfo.isAtWar() || civInfo.getHappiness() <= 0) return

        val ourMilitaryUnits = civInfo.units.getCivUnits().filter { !it.isCivilian() }.count()
        if (ourMilitaryUnits < civInfo.cities.size) return
        if (ourMilitaryUnits < 4) return  // to stop AI declaring war at the beginning of games when everyone isn't set up well enough\
        if (civInfo.cities.size < 3) return // FAR too early for that what are you thinking!

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
        val civWithBestMotivationToAttack = enemyCivs
            .map { Pair(it, hasAtLeastMotivationToAttack(civInfo, it, minMotivationToAttack)) }
            .maxByOrNull { it.second }!!

        if (civWithBestMotivationToAttack.second >= minMotivationToAttack)
            civInfo.getDiplomacyManager(civWithBestMotivationToAttack.first).declareWar()
    }

    /** Will return the motivation to attack, but might short circuit if the value is guaranteed to
     * be lower than `atLeast`. So any values below `atLeast` should not be used for comparison. */
    private fun hasAtLeastMotivationToAttack(civInfo: Civilization, otherCiv: Civilization, atLeast: Int): Int {
        val closestCities = NextTurnAutomation.getClosestCities(civInfo, otherCiv) ?: return 0
        val baseForce = 30f

        var ourCombatStrength = civInfo.getStatForRanking(RankingType.Force).toFloat() + baseForce
        if (civInfo.getCapital() != null) ourCombatStrength += CityCombatant(civInfo.getCapital()!!).getCityStrength()
        var theirCombatStrength = otherCiv.getStatForRanking(RankingType.Force).toFloat() + baseForce
        if(otherCiv.getCapital() != null) theirCombatStrength += CityCombatant(otherCiv.getCapital()!!).getCityStrength()

        //for city-states, also consider their protectors
        if (otherCiv.isCityState() and otherCiv.cityStateFunctions.getProtectorCivs().isNotEmpty()) {
            theirCombatStrength += otherCiv.cityStateFunctions.getProtectorCivs().filterNot { it == civInfo }
                .sumOf { it.getStatForRanking(RankingType.Force) }
        }

        if (theirCombatStrength > ourCombatStrength) return 0

        val ourCity = closestCities.city1
        val theirCity = closestCities.city2

        if (civInfo.units.getCivUnits().filter { it.isMilitary() }.none {
                val damageReceivedWhenAttacking =
                    BattleDamage.calculateDamageToAttacker(
                        MapUnitCombatant(it),
                        CityCombatant(theirCity)
                    )
                damageReceivedWhenAttacking < 100
            })
            return 0 // You don't have any units that can attack this city without dying, don't declare war.

        fun isTileCanMoveThrough(tile: Tile): Boolean {
            val owner = tile.getOwner()
            return !tile.isImpassible()
                && (owner == otherCiv || owner == null || civInfo.diplomacyFunctions.canPassThroughTiles(owner))
        }

        val modifierMap = HashMap<String, Int>()
        val combatStrengthRatio = ourCombatStrength / theirCombatStrength
        val combatStrengthModifier = when {
            combatStrengthRatio > 3f -> 30
            combatStrengthRatio > 2.5f -> 25
            combatStrengthRatio > 2f -> 20
            combatStrengthRatio > 1.5f -> 10
            else -> 0
        }
        modifierMap["Relative combat strength"] = combatStrengthModifier


        if (closestCities.aerialDistance > 7)
            modifierMap["Far away cities"] = -10

        val diplomacyManager = civInfo.getDiplomacyManager(otherCiv)
        if (diplomacyManager.hasFlag(DiplomacyFlags.ResearchAgreement))
            modifierMap["Research Agreement"] = -5

        if (diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship))
            modifierMap["Declaration of Friendship"] = -10

        val relationshipModifier = when (diplomacyManager.relationshipIgnoreAfraid()) {
            RelationshipLevel.Unforgivable -> 10
            RelationshipLevel.Enemy -> 5
            RelationshipLevel.Ally -> -5 // this is so that ally + DoF is not too unbalanced -
            // still possible for AI to declare war for isolated city
            else -> 0
        }
        modifierMap["Relationship"] = relationshipModifier

        if (diplomacyManager.resourcesFromTrade().any { it.amount > 0 })
            modifierMap["Receiving trade resources"] = -5

        if (theirCity.getTiles().none { tile -> tile.neighbors.any { it.getOwner() == theirCity.civ && it.getCity() != theirCity } })
            modifierMap["Isolated city"] = 15

        if (otherCiv.isCityState()) {
            modifierMap["City-state"] = -20
            if (otherCiv.getAllyCiv() == civInfo.civName)
                modifierMap["Allied City-state"] = -20 // There had better be a DAMN good reason
        }

        for (city in otherCiv.cities) {
            val construction = city.cityConstructions.getCurrentConstruction()
            if (construction is Building && construction.hasUnique(UniqueType.TriggersCulturalVictory))
                modifierMap["About to win"] = 15
            if (construction is BaseUnit && construction.hasUnique(UniqueType.AddInCapital))
                modifierMap["About to win"] = 15
        }

        var motivationSoFar = modifierMap.values.sum()

        // We don't need to execute the expensive BFSs below if we're below the threshold here
        // anyways, since it won't get better from those, only worse.
        if (motivationSoFar < atLeast) {
            return motivationSoFar
        }


        val landPathBFS = BFS(ourCity.getCenterTile()) {
            it.isLand && isTileCanMoveThrough(it)
        }

        landPathBFS.stepUntilDestination(theirCity.getCenterTile())
        if (!landPathBFS.hasReachedTile(theirCity.getCenterTile()))
            motivationSoFar -= -10

        // We don't need to execute the expensive BFSs below if we're below the threshold here
        // anyways, since it won't get better from those, only worse.
        if (motivationSoFar < atLeast) {
            return motivationSoFar
        }

        val reachableEnemyCitiesBfs = BFS(civInfo.getCapital(true)!!.getCenterTile()) { isTileCanMoveThrough(it) }
        reachableEnemyCitiesBfs.stepToEnd()
        val reachableEnemyCities = otherCiv.cities.filter { reachableEnemyCitiesBfs.hasReachedTile(it.getCenterTile()) }
        if (reachableEnemyCities.isEmpty()) return 0 // Can't even reach the enemy city, no point in war.

        return motivationSoFar
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

}
