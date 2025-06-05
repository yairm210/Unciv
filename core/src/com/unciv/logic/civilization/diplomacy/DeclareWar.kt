package com.unciv.logic.civilization.diplomacy

import com.unciv.Constants
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.DiplomacyAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PopupAlert
import com.unciv.models.ruleset.nation.PersonalityValue
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType

object DeclareWar {

    /** Declares war with the other civ in this diplomacy manager.
     * Handles all war effects and diplomatic changes with other civs and such.
     *
     * @param declareWarReason Changes what sort of effects the war has depending on how it was initiated.
     * If it was a direct attack put [WarType.DirectWar] for the following effects.
     * Influence with city states should only be set to -60
     * when they are attacked directly, not when their ally is attacked.
     * When @indirectCityStateAttack is set to true, we thus don't reset the influence with this city state.
     * Should only ever be set to true for calls originating from within this function.
     */
    internal fun declareWar(diplomacyManager: DiplomacyManager, declareWarReason: DeclareWarReason) {
        val civInfo = diplomacyManager.civInfo
        val otherCiv = diplomacyManager.otherCiv()
        val otherCivDiplomacy = diplomacyManager.otherCivDiplomacy()

        if (otherCiv.isCityState && declareWarReason.warType == WarType.DirectWar)
            handleCityStateDirectAttack(diplomacyManager)

        notifyOfWar(diplomacyManager, declareWarReason)

        onWarDeclared(diplomacyManager, true, declareWarReason.warType)
        onWarDeclared(otherCivDiplomacy, false, declareWarReason.warType)

        changeOpinions(diplomacyManager, declareWarReason)

        breakTreaties(diplomacyManager)

        if (otherCiv.isMajorCiv())
            for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponDeclaringWar))
                UniqueTriggerActivation.triggerUnique(unique, civInfo)
    }

    private fun handleCityStateDirectAttack(diplomacyManager: DiplomacyManager) {
        val civInfo = diplomacyManager.civInfo
        val otherCiv = diplomacyManager.otherCiv()
        val otherCivDiplomacy = diplomacyManager.otherCivDiplomacy()

        otherCivDiplomacy.setInfluence(-60f)
        civInfo.numMinorCivsAttacked += 1
        otherCiv.cityStateFunctions.cityStateAttacked(civInfo)

        // You attacked your own ally, you're a right bastard
        if (otherCiv.getAllyCivName() == civInfo.civName) {
            otherCiv.cityStateFunctions.updateAllyCivForCityState()
            otherCivDiplomacy.setInfluence(-120f)
            for (knownCiv in civInfo.getKnownCivs()) {
                knownCiv.getDiplomacyManager(civInfo)!!.addModifier(DiplomaticModifiers.BetrayedDeclarationOfFriendship, -10f)
            }
        }
    }

    private fun notifyOfWar(diplomacyManager: DiplomacyManager, declareWarReason: DeclareWarReason) {
        val civInfo = diplomacyManager.civInfo
        val otherCiv = diplomacyManager.otherCiv()

        when (declareWarReason.warType) {
            WarType.DirectWar -> {
                otherCiv.popupAlerts.add(PopupAlert(AlertType.WarDeclaration, civInfo.civName))

                otherCiv.addNotification("[${civInfo.civName}] has declared war on us!",
                    NotificationCategory.Diplomacy, otherCiv.civName, NotificationIcon.War, civInfo.civName)

                diplomacyManager.getCommonKnownCivsWithSpectators().forEach {
                    it.addNotification("[${civInfo.civName}] has declared war on [${otherCiv.civName}]!",
                        NotificationCategory.Diplomacy, otherCiv.civName, NotificationIcon.War, civInfo.civName)
                }
            }
            WarType.DefensivePactWar, WarType.CityStateAllianceWar, WarType.JoinWar,
            WarType.ProtectedCityStateWar, WarType.AlliedCityStateWar -> {
                val allyCiv = declareWarReason.allyCiv!!
                otherCiv.popupAlerts.add(PopupAlert(AlertType.WarDeclaration, civInfo.civName))
                val aggressor = if (declareWarReason.warType == WarType.DefensivePactWar) otherCiv else civInfo
                val defender = if (declareWarReason.warType == WarType.DefensivePactWar) civInfo else otherCiv

                defender.addNotification("[${aggressor.civName}] has joined [${allyCiv.civName}] in the war against us!",
                    NotificationCategory.Diplomacy, defender.civName, NotificationIcon.War, allyCiv.civName, aggressor.civName)

                aggressor.addNotification("We have joined [${allyCiv.civName}] in the war against [${defender.civName}]!",
                    NotificationCategory.Diplomacy, defender.civName, NotificationIcon.War, allyCiv.civName, aggressor.civName)

                diplomacyManager.getCommonKnownCivsWithSpectators().filterNot { it == allyCiv }.forEach {
                    it.addNotification("[${aggressor.civName}] has joined [${allyCiv.civName}] in the war against [${defender.civName}]!",
                        NotificationCategory.Diplomacy, defender.civName, NotificationIcon.War, allyCiv.civName, aggressor.civName)
                }

                allyCiv.addNotification("[${aggressor.civName}] has joined us in the war against [${defender.civName}]!",
                        NotificationCategory.Diplomacy, defender.civName, NotificationIcon.War, allyCiv.civName, aggressor.civName)
            }
            WarType.TeamWar -> {
                val allyCiv = declareWarReason.allyCiv!!
                // We only want to send these notifications once, it doesn't matter who sends it though
                if (civInfo.gameInfo.civilizations.indexOf(civInfo) > civInfo.gameInfo.civilizations.indexOf(allyCiv)) return

                otherCiv.popupAlerts.add(PopupAlert(AlertType.WarDeclaration, civInfo.civName))
                otherCiv.popupAlerts.add(PopupAlert(AlertType.WarDeclaration, allyCiv.civName))

                civInfo.addNotification("You and [${allyCiv.civName}] have declared war against [${otherCiv.civName}]!",
                        NotificationCategory.Diplomacy, otherCiv.civName, NotificationIcon.War, allyCiv.civName, civInfo.civName)

                allyCiv.addNotification("You and [${civInfo.civName}] have declared war against [${otherCiv.civName}]!",
                        NotificationCategory.Diplomacy, otherCiv.civName, NotificationIcon.War, civInfo.civName, allyCiv.civName)

                otherCiv.addNotification("[${civInfo.civName}] and [${allyCiv.civName}] have declared war against us!",
                        NotificationCategory.Diplomacy, otherCiv.civName, NotificationIcon.War, allyCiv.civName, civInfo.civName)

                diplomacyManager.getCommonKnownCivsWithSpectators().filterNot { it == allyCiv }.forEach {
                    it.addNotification("[${civInfo.civName}] and [${allyCiv.civName}] have declared war against [${otherCiv.civName}]!",
                            NotificationCategory.Diplomacy, otherCiv.civName, NotificationIcon.War, allyCiv.civName, civInfo.civName)
                }
            }
        }
    }

    /** Everything that happens to both sides equally when war is declared by one side on the other */
    private fun onWarDeclared(diplomacyManager: DiplomacyManager, isOffensiveWar: Boolean, warType: WarType) {
        // Cancel all trades.
        for (trade in diplomacyManager.trades)
            for (offer in trade.theirOffers.filter { it.duration > 0 && it.name != Constants.defensivePact})
                diplomacyManager.civInfo.addNotification("[${offer.name}] from [${diplomacyManager.otherCivName}] has ended",
                    DiplomacyAction(diplomacyManager.otherCivName, true),
                    NotificationCategory.Trade, diplomacyManager.otherCivName, NotificationIcon.Trade)
        diplomacyManager.trades.clear()
        diplomacyManager.civInfo.tradeRequests.removeAll { it.requestingCiv == diplomacyManager.otherCivName }

        // Must come *before* state is "at war" so units know they're not allowed in tiles without open borders anymore
        diplomacyManager.updateHasOpenBorders()

        val civAtWarWith = diplomacyManager.otherCiv()

        // If we attacked, then we need to end all of our defensive pacts acording to Civ 5
        if (isOffensiveWar) {
            removeDefensivePacts(diplomacyManager)
        }
        diplomacyManager.diplomaticStatus = DiplomaticStatus.War
        
        // Defensive pact chains are not allowed now
        if (diplomacyManager.civInfo.isMajorCiv()) {
            if (!isOffensiveWar && warType != WarType.DefensivePactWar && !civAtWarWith.isCityState)
                callInDefensivePactAllies(diplomacyManager)                
            callInCityStateAllies(diplomacyManager)
        }

        if (diplomacyManager.civInfo.isCityState &&
            diplomacyManager.civInfo.cityStateFunctions.getProtectorCivs().contains(civAtWarWith)) {
            diplomacyManager.civInfo.cityStateFunctions.removeProtectorCiv(civAtWarWith, forced = true)
        }

        diplomacyManager.removeModifier(DiplomaticModifiers.YearsOfPeace)
        diplomacyManager.setFlag(DiplomacyFlags.DeclinedPeace, diplomacyManager.civInfo.gameInfo.ruleset.modOptions.constants.minimumWarDuration) // AI won't propose peace for 10 turns
        diplomacyManager.setFlag(DiplomacyFlags.DeclaredWar, diplomacyManager.civInfo.gameInfo.ruleset.modOptions.constants.minimumWarDuration) // AI won't agree to trade for 10 turns
        diplomacyManager.removeFlag(DiplomacyFlags.BorderConflict)
    }

    private fun changeOpinions(diplomacyManager: DiplomacyManager, declareWarReason: DeclareWarReason) {
        val civInfo = diplomacyManager.civInfo
        val otherCiv = diplomacyManager.otherCiv()
        val otherCivDiplomacy = diplomacyManager.otherCivDiplomacy()
        val warType = declareWarReason.warType

        otherCivDiplomacy.setModifier(DiplomaticModifiers.DeclaredWarOnUs, -20f)
        otherCivDiplomacy.removeModifier(DiplomaticModifiers.ReturnedCapturedUnits)

        // Apply warmongering
        if (warType == WarType.DirectWar || warType == WarType.JoinWar || warType == WarType.TeamWar) {
            for (thirdCiv in civInfo.getKnownCivs()) {
                if (!thirdCiv.isAtWarWith(otherCiv)
                    && thirdCiv.getDiplomacyManager(otherCiv)?.isRelationshipLevelGT(RelationshipLevel.Competitor) != false
                    && thirdCiv != declareWarReason.allyCiv) {
                    // We don't want this modify to stack if there is a defensive pact
                    thirdCiv.getDiplomacyManager(civInfo)!!
                        .addModifier(DiplomaticModifiers.WarMongerer, -5f)
                }
            }
        }

        // Apply shared enemy modifiers
        for (thirdCiv in diplomacyManager.getCommonKnownCivs()) {
            if ((thirdCiv.isAtWarWith(otherCiv) || thirdCiv == declareWarReason.allyCiv) && !thirdCiv.isAtWarWith(civInfo)) {
                // Improve our relations
                if (thirdCiv.isCityState) thirdCiv.getDiplomacyManager(civInfo)!!.addInfluence(10f)
                else thirdCiv.getDiplomacyManager(civInfo)!!.addModifier(DiplomaticModifiers.SharedEnemy, 5f * civInfo.getPersonality().modifierFocus(PersonalityValue.Loyal, .3f))
            } else if (thirdCiv.isAtWarWith(civInfo)) {
                // Improve their relations
                if (thirdCiv.isCityState) thirdCiv.getDiplomacyManager(otherCiv)!!.addInfluence(10f)
                else thirdCiv.getDiplomacyManager(otherCiv)!!.addModifier(DiplomaticModifiers.SharedEnemy, 5f * civInfo.getPersonality().modifierFocus(PersonalityValue.Loyal, .3f))
            }
        }
    }

    private fun breakTreaties(diplomacyManager: DiplomacyManager) {
        val otherCiv = diplomacyManager.otherCiv()
        val otherCivDiplomacy = diplomacyManager.otherCivDiplomacy()

        var betrayedFriendship = false
        var betrayedDefensivePact = false
        if (diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)) {
            betrayedFriendship = true
            diplomacyManager.removeFlag(DiplomacyFlags.DeclarationOfFriendship)
            otherCivDiplomacy.removeModifier(DiplomaticModifiers.DeclarationOfFriendship)
        }
        otherCivDiplomacy.removeFlag(DiplomacyFlags.DeclarationOfFriendship)

        if (diplomacyManager.hasFlag(DiplomacyFlags.DefensivePact)) {
            betrayedDefensivePact = true
            diplomacyManager.removeFlag(DiplomacyFlags.DefensivePact)
            otherCivDiplomacy.removeModifier(DiplomaticModifiers.DefensivePact)
        }
        otherCivDiplomacy.removeFlag(DiplomacyFlags.DefensivePact)

        if (betrayedFriendship || betrayedDefensivePact) {
            for (knownCiv in diplomacyManager.civInfo.getKnownCivs()) {
                val diploManager = knownCiv.getDiplomacyManager(diplomacyManager.civInfo)!!
                if (betrayedFriendship) {
                    val amount = if (knownCiv == otherCiv) -40f else -20f
                    diploManager.addModifier(DiplomaticModifiers.BetrayedDeclarationOfFriendship, amount * knownCiv.getPersonality().modifierFocus(PersonalityValue.Loyal, .3f))
                }
                if (betrayedDefensivePact) {
                    //Note: this stacks with Declaration of Friendship
                    val amount = if (knownCiv == otherCiv) -20f else -10f
                    diploManager.addModifier(DiplomaticModifiers.BetrayedDefensivePact, amount * knownCiv.getPersonality().modifierFocus(PersonalityValue.Loyal, .3f))
                }
                diploManager.removeModifier(DiplomaticModifiers.DeclaredFriendshipWithOurAllies) // obviously this guy's declarations of friendship aren't worth much.
                diploManager.removeModifier(DiplomaticModifiers.SignedDefensivePactWithOurAllies)
            }
        }

        if (diplomacyManager.hasFlag(DiplomacyFlags.ResearchAgreement)) {
            diplomacyManager.removeFlag(DiplomacyFlags.ResearchAgreement)
            diplomacyManager.totalOfScienceDuringRA = 0
            otherCivDiplomacy.totalOfScienceDuringRA = 0
        }
        otherCivDiplomacy.removeFlag(DiplomacyFlags.ResearchAgreement)

        // The other civ should keep any gifts we gave them
        // But we should not nesessarily take away their gifts
        otherCivDiplomacy.removeModifier(DiplomaticModifiers.GaveUsGifts)
    }

    /**
     * Removes all defensive Pacts and trades. Notifies other civs.
     * Note: Does not remove the flags and modifiers of the otherCiv if there is a defensive pact.
     * This is so that we can apply more negative modifiers later.
     */
    private fun removeDefensivePacts(diplomacyManager: DiplomacyManager) {
        val civAtWarWith = diplomacyManager.otherCiv()
        for (thirdPartyDiploManager in diplomacyManager.civInfo.diplomacy.values) {
            if (thirdPartyDiploManager.diplomaticStatus != DiplomaticStatus.DefensivePact) continue

            // Cancel the defensive pact functionality
            thirdPartyDiploManager.diplomaticStatus = DiplomaticStatus.Peace
            thirdPartyDiploManager.otherCivDiplomacy().diplomaticStatus = DiplomaticStatus.Peace

            // We already removed the trades and functionality
            // But we don't want to remove the flags yet so we can process BetrayedDefensivePact later
            if (thirdPartyDiploManager.otherCiv() != civAtWarWith) {
                // Trades with defensive pact are now invalid
                val defensivePactOffer = thirdPartyDiploManager.trades
                    .firstOrNull { trade -> trade.ourOffers.any { offer -> offer.name == Constants.defensivePact } }
                thirdPartyDiploManager.trades.remove(defensivePactOffer)
                val theirDefensivePactOffer = thirdPartyDiploManager.otherCivDiplomacy().trades
                    .firstOrNull { trade -> trade.ourOffers.any { offer -> offer.name == Constants.defensivePact } }
                thirdPartyDiploManager.otherCivDiplomacy().trades.remove(theirDefensivePactOffer)

                thirdPartyDiploManager.removeFlag(DiplomacyFlags.DefensivePact)
                thirdPartyDiploManager.otherCivDiplomacy().removeFlag(DiplomacyFlags.DefensivePact)
            }
            for (civ in thirdPartyDiploManager.getCommonKnownCivsWithSpectators()) {
                civ.addNotification("[${diplomacyManager.civInfo.civName}] cancelled their Defensive Pact with [${thirdPartyDiploManager.otherCivName}]!",
                    NotificationCategory.Diplomacy, diplomacyManager.civInfo.civName, NotificationIcon.Diplomacy, thirdPartyDiploManager.otherCivName)
            }

            thirdPartyDiploManager.otherCiv().addNotification("[${diplomacyManager.civInfo.civName}] cancelled their Defensive Pact with us!",
                NotificationCategory.Diplomacy, diplomacyManager.civInfo.civName, NotificationIcon.Diplomacy, thirdPartyDiploManager.otherCivName)

            thirdPartyDiploManager.civInfo.addNotification("We have cancelled our Defensive Pact with [${thirdPartyDiploManager.otherCivName}]!",
                NotificationCategory.Diplomacy, NotificationIcon.Diplomacy, thirdPartyDiploManager.otherCivName)
        }
    }


    /**
     * Goes through each DiplomacyManager with a defensive pact that is not already in the war.
     * The civ that we are calling them in against should no longer have a defensive pact with us.
     */
    private fun callInDefensivePactAllies(diplomacyManager: DiplomacyManager) {
        val civAtWarWith = diplomacyManager.otherCiv()
        for (ourDefensivePact in diplomacyManager.civInfo.diplomacy.values.filter { ourDipManager ->
            ourDipManager.diplomaticStatus == DiplomaticStatus.DefensivePact
                && !ourDipManager.otherCiv().isDefeated()
                && !ourDipManager.otherCiv().isAtWarWith(civAtWarWith)
        }) {
            val ally = ourDefensivePact.otherCiv()
            if (!civAtWarWith.knows(ally)) civAtWarWith.diplomacyFunctions.makeCivilizationsMeet(ally, true)
            // Have the aggressor declare war on the ally.
            civAtWarWith.getDiplomacyManager(ally)!!.declareWar(DeclareWarReason(WarType.DefensivePactWar, diplomacyManager.civInfo))
        }
    }

    private fun callInCityStateAllies(diplomacyManager: DiplomacyManager) {
        val civAtWarWith = diplomacyManager.otherCiv()
        for (thirdCiv in diplomacyManager.civInfo.getKnownCivs()
            .filter { it.isCityState && it.getAllyCivName() == diplomacyManager.civInfo.civName }) {

            if (!thirdCiv.isAtWarWith(civAtWarWith)) {
                if (!thirdCiv.knows(civAtWarWith))
                    // Our city state ally has not met them yet, so they have to meet first
                    thirdCiv.diplomacyFunctions.makeCivilizationsMeet(civAtWarWith, warOnContact = true)
                thirdCiv.getDiplomacyManager(civAtWarWith)!!.declareWar(DeclareWarReason(WarType.CityStateAllianceWar, diplomacyManager.civInfo))
            }
        }
    }
}

enum class WarType {
    /** One civ declared war on the other. */
    DirectWar,
    /** A city state has joined a war through its alliance. */
    CityStateAllianceWar,
    /** A civilization has joined a war through its defensive pact. */
    DefensivePactWar,
    /** A civilization has joined a war through a trade.*/
    JoinWar,
    /** Two civilizations are starting a war through a trade. */
    TeamWar,
    /** Someone attacked our protected city-state */
    ProtectedCityStateWar,
    /** Someone attacked our allied city-state */
    AlliedCityStateWar
}

/**
 * Stores the reason for the war. We might want to add justified wars in the future.
 * @param allyCiv If the given [WarType] is [WarType.CityStateAllianceWar], [WarType.DefensivePactWar] or [WarType.JoinWar]
 * the allyCiv needs to be given.
 */
class DeclareWarReason(val warType: WarType, val allyCiv: Civilization? = null)

