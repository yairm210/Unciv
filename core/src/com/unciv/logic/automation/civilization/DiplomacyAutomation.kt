package com.unciv.logic.automation.civilization

import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.automation.civilization.MotivationToAttackAutomation.hasAtLeastMotivationToAttack
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeRequest
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.ruleset.nation.PersonalityValue
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.victoryscreen.RankingType
import yairm210.purity.annotations.Readonly
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

object DiplomacyAutomation {

    internal fun offerDeclarationOfFriendship(civInfo: Civilization) {
        val civsThatWeCanDeclareFriendshipWith = civInfo.getKnownCivs()
            .filter {
                civInfo.diplomacyFunctions.canSignDeclarationOfFriendshipWith(it)
                    && !civInfo.getDiplomacyManager(it)!!.hasFlag(DiplomacyFlags.DeclinedDeclarationOfFriendship)
            }
            .sortedByDescending { it.getDiplomacyManager(civInfo)!!.relationshipLevel() }.toList()
        for (otherCiv in civsThatWeCanDeclareFriendshipWith) {
            // Default setting is 2, this will be changed according to different civ.
            if ((1..10).random() <= 2 * civInfo.getPersonality().scaledFocus(PersonalityValue.Diplomacy) 
                && wantsToSignDeclarationOfFrienship(civInfo, otherCiv)) {
                otherCiv.popupAlerts.add(PopupAlert(AlertType.DeclarationOfFriendship, civInfo.civName))
            }
        }
    }

    @Readonly
    internal fun wantsToSignDeclarationOfFrienship(civInfo: Civilization, otherCiv: Civilization): Boolean {
        val diploManager = civInfo.getDiplomacyManager(otherCiv)!!
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
        var motivation = diploManager.opinionOfOtherCiv() - 40f

        // Warmongerers don't make good allies
        if (diploManager.hasModifier(DiplomaticModifiers.WarMongerer)) {
            motivation -= diploManager.getModifier(DiplomaticModifiers.WarMongerer) * civInfo.getPersonality().scaledFocus(PersonalityValue.Diplomacy)
        }

        // If the other civ is stronger than we are compelled to be nice to them
        // If they are too weak, then thier friendship doesn't mean much to us
        motivation += when (Automation.threatAssessment(civInfo, otherCiv)) {
            ThreatLevel.VeryHigh -> 10
            ThreatLevel.High -> 5
            ThreatLevel.VeryLow -> -5
            else -> 0
        }

        // Try to ally with a fourth of the civs in play
        val civsToAllyWith = 0.25f * allAliveCivs * civInfo.getPersonality().scaledFocus(PersonalityValue.Diplomacy)
        if (numOfFriends < civsToAllyWith) {
            // Goes from 10 to 0 once the civ gets 1/4 of all alive civs as friends
            motivation += (10 - 10 * numOfFriends / civsToAllyWith)
        } else {
            // Goes from 0 to -120 as the civ gets more friends, offset by civsToAllyWith
            motivation -= (120f * (numOfFriends - civsToAllyWith) / (knownCivs - civsToAllyWith))
        }

        // The more friends they have the less we should want to sign friendship (To promote teams)
        motivation -= otherCivNumberOfFriends * 10

        // Goes from 0 to -50 as more civs die
        // this is meant to prevent the game from stalemating when a group of friends
        // conquers all oposition
        motivation -= deadCivs / allCivs * 50

        // Become more desperate as we have more wars
        motivation += civInfo.diplomacy.values.count { it.otherCiv.isMajorCiv() && it.diplomaticStatus == DiplomaticStatus.War } * 10

        // Wait to declare frienships until more civs
        // Goes from -30 to 0 when we know 75% of allCivs
        val civsToKnow = 0.75f * allAliveCivs
        motivation -= ((civsToKnow - knownCivs) / civsToKnow * 30f).coerceAtLeast(0f)

        // If they are the only non-friendly civ near us then they are the only civ to attack and expand into
        if (civInfo.threatManager.getNeighboringCivilizations().none {
                it.isMajorCiv() && it != otherCiv
                    && civInfo.getDiplomacyManager(it)!!.isRelationshipLevelLT(RelationshipLevel.Favorable)
            })
            motivation -= 20

        motivation -= hasAtLeastMotivationToAttack(civInfo, otherCiv, motivation / 2f) * 2

        return motivation > 0
    }

    /**
     * Try establishing embassy in other civs' capitals
     * 
     * @param civInfo Civilization which initiates trade
     */
    internal fun offerToEstablishEmbassy(civInfo: Civilization) {
        if (civInfo.getCapital() == null) return
        val civsThatWeCanEstablishEmbassyWith = civInfo.getKnownCivs().filter {
            civInfo.diplomacyFunctions.canEstablishEmbassyWith(it)
            && !civInfo.getDiplomacyManager(it)!!.hasFlag(DiplomacyFlags.DeclinedEmbassy)
            && !areWeOfferingTrade(civInfo, it, Constants.acceptEmbassy)
        }.sortedByDescending { it.getDiplomacyManager(civInfo)!!.relationshipLevel() }

        for (otherCiv in civsThatWeCanEstablishEmbassyWith) {
            // Default setting is 3
            if ((1..10).random() < 7) continue
            if (wantsToAcceptEmbassy(civInfo, otherCiv)) {
                val tradeLogic = TradeLogic(civInfo, otherCiv)
                val embassyOffer = TradeOffer(Constants.acceptEmbassy, TradeOfferType.Embassy, speed = civInfo.gameInfo.speed)
                tradeLogic.currentTrade.theirOffers.add(embassyOffer)

                // If possible offer mutual embassies (Civ V behavior) so we don't waste gold
                if (otherCiv.diplomacyFunctions.canEstablishEmbassyWith(civInfo)) {
                    tradeLogic.currentTrade.ourOffers.add(embassyOffer)
                }
                else { // Otherwise offer GPT (prefered) or flat gold for embassy in their capital
                    val embassyValue = TradeEvaluation().evaluateBuyCostWithInflation(embassyOffer, civInfo, otherCiv, tradeLogic.currentTrade)
                    val embassyGptValue = embassyValue / civInfo.gameInfo.speed.dealDuration
                    val ourGpt = civInfo.stats.statsForNextTurn.gold.toInt()
                    if (embassyGptValue in 1..ourGpt)
                        tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.goldPerTurn, TradeOfferType.Gold_Per_Turn, embassyGptValue, civInfo.gameInfo.speed))
                    else if (civInfo.gold >= embassyValue && ourGpt >= 0)
                        tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.flatGold, TradeOfferType.Gold, embassyValue, civInfo.gameInfo.speed))
                    // else let them make counter offer
                }
                
                otherCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
            }
            else {
                // Remember this for a few turns to save computation power
                civInfo.getDiplomacyManager(otherCiv)!!.setFlag(DiplomacyFlags.DeclinedEmbassy, 5)
            }
        }        
    }

    internal fun offerOpenBorders(civInfo: Civilization) {
        if (!civInfo.hasUnique(UniqueType.EnablesOpenBorders)) return

        val civsThatWeCanOpenBordersWith = civInfo.getKnownCivs().filter {
            val ourDiploManager = civInfo.getDiplomacyManager(it)!!
            it.isMajorCiv()
                && !civInfo.isAtWarWith(it) 
                && it.hasUnique(UniqueType.EnablesOpenBorders)
                && !ourDiploManager.hasOpenBorders
                && !ourDiploManager.otherCivDiplomacy().hasOpenBorders
                && civInfo.diplomacyFunctions.hasMutualEmbassyWith(it)
                && !ourDiploManager.hasFlag(DiplomacyFlags.DeclinedOpenBorders)
                && !areWeOfferingTrade(civInfo, it, Constants.openBorders)
        }.sortedByDescending { it.getDiplomacyManager(civInfo)!!.relationshipLevel() }

        for (otherCiv in civsThatWeCanOpenBordersWith) {
            // Default setting is 3
            if ((1..10).random() < 7) continue
            if (wantsToOpenBorders(civInfo, otherCiv)) {
                val tradeLogic = TradeLogic(civInfo, otherCiv)
                tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.openBorders, TradeOfferType.Agreement, speed = civInfo.gameInfo.speed))
                tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.openBorders, TradeOfferType.Agreement, speed = civInfo.gameInfo.speed))

                otherCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
            } else {
                // Remember this for a few turns to save computation power
                civInfo.getDiplomacyManager(otherCiv)!!.setFlag(DiplomacyFlags.DeclinedOpenBorders, 5)
            }
        }
    }

    /**
     * Test if [otherCiv] wants to accept our embassy in their capital
     */
    @Readonly
    fun wantsToAcceptEmbassy(civInfo: Civilization, otherCiv: Civilization): Boolean {
        val theirDiploManager = otherCiv.getDiplomacyManager(civInfo)!!
        if (civInfo.getDiplomacyManager(otherCiv)!!.hasFlag(DiplomacyFlags.DeclinedEmbassy)) return false
        if (theirDiploManager.isRelationshipLevelLT(RelationshipLevel.Afraid)) return false

        // Being able to see their capital can give us an advantage later on, especially with espionage enabled
        if (!civInfo.getCapital()!!.getCenterTile().isExplored(otherCiv)) return true

        // Did they not discovered our capital yet?
        if (!otherCiv.getCapital()!!.getCenterTile().isExplored(civInfo)) {
            // If we're afraid of them deny embassy
            if (theirDiploManager.relationshipLevel() == RelationshipLevel.Afraid) return false

            // If they're much stronger than us deny embassy
            val ourCombatStrength = civInfo.getStatForRanking(RankingType.Force)
            val theirCombatStrength = otherCiv.getStatForRanking(RankingType.Force)
            val ourAbsoluteAdvantage = ourCombatStrength - theirCombatStrength
            val percentageAdvantage = ourAbsoluteAdvantage / theirCombatStrength.toFloat()
            if (percentageAdvantage > 0.5) return false
        }

        return true // Relationship is Afraid or greater
    }

    @Readonly
    fun wantsToOpenBorders(civInfo: Civilization, otherCiv: Civilization): Boolean {
        val ourDiploManager = civInfo.getDiplomacyManager(otherCiv)!!
        if (ourDiploManager.hasFlag(DiplomacyFlags.DeclinedOpenBorders)) return false
        if (ourDiploManager.isRelationshipLevelLT(RelationshipLevel.Favorable)) return false
        // Don't accept if they are at war with our friends, they might use our land to attack them
        if (civInfo.diplomacy.values.any { it.isRelationshipLevelGE(RelationshipLevel.Friend) && it.otherCiv.isAtWarWith(otherCiv) })
            return false
        // Being able to see their cities can give us an advantage later on, especially with espionage enabled
        if (otherCiv.cities.count { !it.getCenterTile().isVisible(civInfo) } < otherCiv.cities.count() * .8f)
            return true
        if (hasAtLeastMotivationToAttack(civInfo, otherCiv,
                ourDiploManager.opinionOfOtherCiv() * civInfo.getPersonality().scaledFocus(PersonalityValue.Commerce) / 2) > 0)
            return false
        return true
    }

    internal fun offerResearchAgreement(civInfo: Civilization) {
        if (!civInfo.diplomacyFunctions.canSignResearchAgreement()) return // don't waste your time
        
        val civsThatWeCanSignResearchAgreementWith = civInfo.getKnownCivs().filter {
            civInfo.diplomacyFunctions.canSignResearchAgreementWith(it)
                && !civInfo.getDiplomacyManager(it)!!.hasFlag(DiplomacyFlags.DeclinedResearchAgreement)
                && !areWeOfferingTrade(civInfo, it, Constants.researchAgreement)
        }.sortedByDescending { it.stats.statsForNextTurn.science }

        for (otherCiv in civsThatWeCanSignResearchAgreementWith) {
            // Default setting is 5, this will be changed according to different civ.
            if ((1..10).random() <= 5 * civInfo.getPersonality().scaledFocus(PersonalityValue.Science)) continue
            val tradeLogic = TradeLogic(civInfo, otherCiv)
            val cost = civInfo.diplomacyFunctions.getResearchAgreementCost(otherCiv)
            val tradeOffer = TradeOffer(Constants.researchAgreement, TradeOfferType.Treaty, cost, civInfo.gameInfo.speed)

            tradeLogic.currentTrade.ourOffers.add(tradeOffer)
            tradeLogic.currentTrade.theirOffers.add(tradeOffer)
            otherCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
        }
    }

    internal fun offerDefensivePact(civInfo: Civilization) {
        if (!civInfo.diplomacyFunctions.canSignDefensivePact()) return // don't waste your time

        val civsThatWeCanSignDefensivePactWith = civInfo.getKnownCivs().filter {
            val ourDiploManager = civInfo.getDiplomacyManager(it)!!
            civInfo.diplomacyFunctions.canSignDefensivePactWith(it)
                && !ourDiploManager.hasFlag(DiplomacyFlags.DeclinedDefensivePact)
                && ourDiploManager.opinionOfOtherCiv() < 70f * civInfo.getPersonality().inverseScaledFocus(PersonalityValue.Aggressive)
                && !areWeOfferingTrade(civInfo, it, Constants.defensivePact)
        }

        for (otherCiv in civsThatWeCanSignDefensivePactWith) {
            // Default setting is 3, this will be changed according to different civ.
            if ((1..10).random() <= 7 * civInfo.getPersonality().inverseScaledFocus(PersonalityValue.Loyal)) continue
            if (wantsToSignDefensivePact(civInfo, otherCiv)) {
                //todo: Add more in depth evaluation here
                val tradeLogic = TradeLogic(civInfo, otherCiv)
                val tradeOffer = TradeOffer(Constants.defensivePact, TradeOfferType.Treaty, speed = civInfo.gameInfo.speed)

                tradeLogic.currentTrade.ourOffers.add(tradeOffer)
                tradeLogic.currentTrade.theirOffers.add(tradeOffer)
                otherCiv.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
            } else {
                // Remember this for a few turns to save computation power
                civInfo.getDiplomacyManager(otherCiv)!!.setFlag(DiplomacyFlags.DeclinedDefensivePact, 5)
            }
        }
    }

    @Readonly
    fun wantsToSignDefensivePact(civInfo: Civilization, otherCiv: Civilization): Boolean {
        val ourDiploManager = civInfo.getDiplomacyManager(otherCiv)!!
        if (ourDiploManager.hasFlag(DiplomacyFlags.DeclinedDefensivePact)) return false
        if (ourDiploManager.opinionOfOtherCiv() < 65f * civInfo.getPersonality().inverseScaledFocus(PersonalityValue.Aggressive)) return false
        val commonknownCivs = ourDiploManager.getCommonKnownCivs()

        for (thirdCiv in commonknownCivs) {
            // If they have bad relations with any of our friends, don't consider it
            if (civInfo.getDiplomacyManager(thirdCiv)!!.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
                && thirdCiv.getDiplomacyManager(otherCiv)!!.isRelationshipLevelLT(RelationshipLevel.Favorable))
                return false
            
            // If they have bad relations with any of our friends, don't consider it
            if (otherCiv.getDiplomacyManager(thirdCiv)!!.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
                && thirdCiv.getDiplomacyManager(civInfo)!!.isRelationshipLevelLT(RelationshipLevel.Neutral))
                return false
        }

        val defensivePacts = civInfo.diplomacy.count { it.value.hasFlag(DiplomacyFlags.DefensivePact) }
        val otherCivNonOverlappingDefensivePacts = otherCiv.diplomacy.values.count {
            it.hasFlag(DiplomacyFlags.DefensivePact)
                && it.otherCiv.getDiplomacyManager(civInfo)?.hasFlag(DiplomacyFlags.DefensivePact) != true
        }
        val allCivs = civInfo.gameInfo.civilizations.count { it.isMajorCiv() } - 1 // Don't include us
        val deadCivs = civInfo.gameInfo.civilizations.count { it.isMajorCiv() && !it.isAlive() }
        val allAliveCivs = allCivs - deadCivs

        // We have to already be at RelationshipLevel.Ally, so we must have 80 oppinion of them
        var motivation = ourDiploManager.opinionOfOtherCiv() - 80

        // Warmongerers don't make good allies
        if (ourDiploManager.hasModifier(DiplomaticModifiers.WarMongerer)) {
            motivation -= ourDiploManager.getModifier(DiplomaticModifiers.WarMongerer) * civInfo.getPersonality().scaledFocus(PersonalityValue.Diplomacy)
        }

        // If they are stronger than us, then we value it a lot more
        // If they are weaker than us, then we don't value it
        motivation += when (Automation.threatAssessment(civInfo, otherCiv)) {
            ThreatLevel.VeryHigh -> 10
            ThreatLevel.High -> 5
            ThreatLevel.Low -> -3
            ThreatLevel.VeryLow -> -7
            else -> 0
        }

        // If they have a defensive pact with another civ then we would get drawn into their battles as well
        motivation -= 15 * otherCivNonOverlappingDefensivePacts

        // Becomre more desperate as we have more wars
        motivation += civInfo.diplomacy.values.count { it.otherCiv.isMajorCiv() && it.diplomaticStatus == DiplomaticStatus.War } * 5

        // Try to have a defensive pact with 1/5 of all civs
        val civsToAllyWith = 0.20f * allAliveCivs * civInfo.getPersonality().scaledFocus(PersonalityValue.Diplomacy)
        // Goes from 0 to -40 as the civ gets more allies, offset by civsToAllyWith
        motivation -= (40f * (defensivePacts - civsToAllyWith) / (allAliveCivs - civsToAllyWith)).coerceAtMost(0f)

        return motivation > 0
    }

    internal fun declareWar(civInfo: Civilization) {
        if (civInfo.cities.isEmpty() || civInfo.diplomacy.isEmpty()) return
        if (civInfo.getPersonality()[PersonalityValue.DeclareWar] == 0f) return
        if (civInfo.getHappiness() <= 0) return

        val ourMilitaryUnits = civInfo.units.getCivUnits().filter { !it.isCivilian() }.count()
        if (ourMilitaryUnits < civInfo.cities.size) return
        if (ourMilitaryUnits < 4) return  // to stop AI declaring war at the beginning of games when everyone isn't set up well enough
        // For mods we can't check the number of cities, so we will check the population instead.
        if (civInfo.cities.sumOf { it.population.population } < 12) return // FAR too early for that what are you thinking!

        //evaluate war
        val targetCivs = civInfo.getKnownCivs()
            .filterNot {
                it.isDefeated() || it == civInfo || it.cities.isEmpty() || !civInfo.getDiplomacyManager(it)!!.canDeclareWar()
                    || it.cities.none { city -> civInfo.hasExplored(city.getCenterTile()) }
            }
        // If the AI declares war on a civ without knowing the location of any cities, 
        // it'll just keep amassing an army and not sending it anywhere, and end up at a massive disadvantage.

        if (targetCivs.none()) return

        val targetCivsWithMotivation: List<Pair<Civilization, Float>> = targetCivs
            .map { Pair(it, hasAtLeastMotivationToAttack(civInfo, it, 0f)) }
            .filter { it.second > 0 }.toList()

        DeclareWarTargetAutomation.chooseDeclareWarTarget(civInfo, targetCivsWithMotivation)
    }

    internal fun offerPeaceTreaty(civInfo: Civilization) {
        if (!civInfo.isAtWar() || civInfo.cities.isEmpty() || civInfo.diplomacy.isEmpty()) return

        val enemiesCiv = civInfo.diplomacy.asSequence()
            .filter { it.value.diplomaticStatus == DiplomaticStatus.War }
            .map { it.value.otherCiv }
            .filterNot {
                it == civInfo || it.isBarbarian || it.cities.isEmpty()
                        || it.getDiplomacyManager(civInfo)!!.hasFlag(DiplomacyFlags.DeclaredWar)
                        || civInfo.getDiplomacyManager(it)!!.hasFlag(DiplomacyFlags.DeclaredWar)
            }.filter { !civInfo.getDiplomacyManager(it)!!.hasFlag(DiplomacyFlags.DeclinedPeace) }
            // Don't allow AIs to offer peace to city states allied with their enemies
            .filterNot { it.isCityState && it.allyCiv != null && civInfo.isAtWarWith(it.allyCiv!!) }
            // ignore civs that we have already offered peace this turn as a counteroffer to another civ's peace offer
            .filter { it.tradeRequests.none { tradeRequest -> tradeRequest.requestingCiv == civInfo.civName && tradeRequest.trade.isPeaceTreaty() } }
            .toList()

        for (enemy in enemiesCiv) {
            if (hasAtLeastMotivationToAttack(civInfo, enemy, 10f) >= 10) {
                // We can still fight. Refuse peace.
                continue
            }
            
            if (enemy.cities.any{ (it.health / it.getMaxHealth()) < 0.5f }) // We are just about to take their city!
                continue

            if (civInfo.getStatForRanking(RankingType.Force) - 0.8f * civInfo.threatManager.getCombinedForceOfWarringCivs() > 0) {
                val randomSeed = civInfo.gameInfo.civilizations.indexOf(enemy) + civInfo.getCivsAtWarWith().count() + 123 * civInfo.gameInfo.turns
                if (Random(randomSeed).nextInt(100) > 80) continue
            }

            // pay for peace
            val tradeLogic = TradeLogic(civInfo, enemy)
            val tradeOffer = TradeOffer(Constants.peaceTreaty, TradeOfferType.Treaty, speed = civInfo.gameInfo.speed)

            tradeLogic.currentTrade.ourOffers.add(tradeOffer)
            tradeLogic.currentTrade.theirOffers.add(tradeOffer)

            if (enemy.isMajorCiv()) {
                var moneyWeNeedToPay = -TradeEvaluation().evaluatePeaceCostForThem(civInfo, enemy)

                if (civInfo.gold > 0 && moneyWeNeedToPay > 0) {
                    if (moneyWeNeedToPay > civInfo.gold) {
                        moneyWeNeedToPay = civInfo.gold  // As much as possible
                    }
                    tradeLogic.currentTrade.ourOffers.add(
                        TradeOffer("Gold", TradeOfferType.Gold, moneyWeNeedToPay, civInfo.gameInfo.speed)
                    )
                } else if (moneyWeNeedToPay < -100) {
                    val moneyTheyNeedToPay = abs(moneyWeNeedToPay).coerceAtMost(enemy.gold)
                    if (moneyTheyNeedToPay > 0) {
                        tradeLogic.currentTrade.theirOffers.add(
                            TradeOffer("Gold", TradeOfferType.Gold, moneyTheyNeedToPay, civInfo.gameInfo.speed)
                        )
                    }
                }
            }

            enemy.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
        }
    }

    internal fun askForHelp(civInfo: Civilization) {
        if (!civInfo.isAtWar() || civInfo.cities.isEmpty() || civInfo.diplomacy.isEmpty()) return

        val enemyCivs = civInfo.getCivsAtWarWith().filter { it.isMajorCiv() }
            .sortedByDescending { it.getStatForRanking(RankingType.Force) }
        for (enemyCiv in enemyCivs) {
            val potentialAllies = enemyCiv.threatManager.getNeighboringCivilizations()
                .filter {
                    civInfo.knows(it) && !it.isAtWarWith(enemyCiv)
                    && civInfo.getDiplomacyManager(it)!!.isRelationshipLevelGE(RelationshipLevel.Friend)
                    && !it.getDiplomacyManager(civInfo)!!.hasFlag(DiplomacyFlags.DeclinedJoinWarOffer) }
                .sortedByDescending { it.getStatForRanking(RankingType.Force) }
            val civToAsk = potentialAllies.firstOrNull { 
                DeclareWarPlanEvaluator.evaluateJoinOurWarPlan(civInfo, enemyCiv, it, null) > 0 } ?: continue

            val tradeLogic = TradeLogic(civInfo, civToAsk)
            // TODO: add gold offer here
            tradeLogic.currentTrade.theirOffers.add(TradeOffer(enemyCiv.civName, TradeOfferType.WarDeclaration, speed = civInfo.gameInfo.speed))
            civToAsk.tradeRequests.add(TradeRequest(civInfo.civName, tradeLogic.currentTrade.reverse()))
        }
    }

    @Readonly
    private fun areWeOfferingTrade(civInfo: Civilization, otherCiv: Civilization, offerName: String): Boolean {
        return otherCiv.tradeRequests.filter { request -> request.requestingCiv == civInfo.civName }
            .any { trade -> trade.trade.ourOffers.any { offer -> offer.name == offerName }
                    || trade.trade.theirOffers.any { offer -> offer.name == offerName } }
    }

    internal fun denounce(
        civInfo: Civilization
    ) {
        /*
        this is not an official formula - modify or replace if needed
        
        whether to denounce is determined by how rapidly opinion has declined, the current relationship level, and (to do) personality traits
        
        with the current formula, excluding decounceWillingness, the AI will denounce if opinion drops rapidly from:
            135 to 50
            65 to 0
            0 to -50
            -60 to -100
        
        what "rapidly" means can be adjusted with DiplomacyManager#smoothedOpinionEmaPeriod
        */

        fun requiredOpinionChange(
            diplomacyManager: DiplomacyManager,
            denounceWillingnessModifier: Float = 1f
        ): Float =
            -65 * denounceWillingnessModifier * 1.005f.pow(diplomacyManager.opinionOfOtherCiv())

        fun debugPrint(diplomacy: DiplomacyManager) {
            val opinionChange = diplomacy.opinionOfOtherCiv() - diplomacy.smoothedOpinionOfOtherCiv
            println(
                "-> %s: %d (%d), %d / %d".format(
                    diplomacy.otherCivName,
                    diplomacy.opinionOfOtherCiv().roundToInt(),
                    diplomacy.smoothedOpinionOfOtherCiv.roundToInt(),
                    opinionChange.roundToInt(),
                    requiredOpinionChange(diplomacy).roundToInt()
                )
            )
        }

        // enable to print the civ name followed by a list of their opinions of other civs each turn
        val debug = false
        if (debug) println(civInfo.civName)

        // limit how many civs we can denounce similtaneously
        // TODO: replace this with logic to consider consequences of denouncing others
        val maxActiveDenunciations = 5
        if (civInfo.diplomacy.values.count { it.hasFlag(DiplomacyFlags.Denunciation) } >= maxActiveDenunciations)
            return
        
        civInfo.diplomacy.values.stream()
            // only denounce major civs
            .filter { it.otherCiv.isMajorCiv() }
            .peek { if (debug) debugPrint(it) }
            // not able to denounce if we are at war, signed DoF, or already denounced them
            .filter {
                it.diplomaticStatus != DiplomaticStatus.War
                    && !it.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
                    && !it.hasFlag(DiplomacyFlags.Denunciation)
            }.forEach {
                val denounceWillingnessModifier =
                    1f // TODO: apply denounceWillingness personality trait
                // compare our current opinion with the smoothed opinion
                val opinionChange = it.opinionOfOtherCiv() - it.smoothedOpinionOfOtherCiv
                // denounce if opinion dropped too quickly
                if (opinionChange <= requiredOpinionChange(it, denounceWillingnessModifier))
                    it.denounce()
            }
    }
}
