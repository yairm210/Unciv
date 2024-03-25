package com.unciv.logic.civilization.diplomacy

import com.unciv.Constants
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.DiplomacyAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PopupAlert
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType

object DeclareWar {

    /** Declares war with the other civ in this diplomacy manager.
     * Handles all war effects and diplomatic changes with other civs and such.
     *
     * @param indirectCityStateAttack Influence with city states should only be set to -60
     * when they are attacked directly, not when their ally is attacked.
     * When @indirectCityStateAttack is set to true, we thus don't reset the influence with this city state.
     * Should only ever be set to true for calls originating from within this function.
     */
    internal fun declareWar(diplomacyManager: DiplomacyManager, indirectCityStateAttack: Boolean = false) {
        val civInfo = diplomacyManager.civInfo
        val otherCiv = diplomacyManager.otherCiv()
        val otherCivDiplomacy = diplomacyManager.otherCivDiplomacy()

        if (otherCiv.isCityState() && !indirectCityStateAttack) {
            otherCivDiplomacy.setInfluence(-60f)
            civInfo.numMinorCivsAttacked += 1
            otherCiv.cityStateFunctions.cityStateAttacked(civInfo)

            // You attacked your own ally, you're a right bastard
            if (otherCiv.getAllyCiv() == civInfo.civName) {
                otherCiv.cityStateFunctions.updateAllyCivForCityState()
                otherCivDiplomacy.setInfluence(-120f)
                for (knownCiv in civInfo.getKnownCivs()) {
                    knownCiv.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.BetrayedDeclarationOfFriendship, -10f)
                }
            }
        }

        onWarDeclared(diplomacyManager, true)
        onWarDeclared(otherCivDiplomacy, false)

        otherCiv.addNotification("[${civInfo.civName}] has declared war on us!",
            NotificationCategory.Diplomacy, NotificationIcon.War, civInfo.civName)
        otherCiv.popupAlerts.add(PopupAlert(AlertType.WarDeclaration, civInfo.civName))

        diplomacyManager.getCommonKnownCivsWithSpectators().forEach {
            it.addNotification("[${civInfo.civName}] has declared war on [${diplomacyManager.otherCivName}]!",
                NotificationCategory.Diplomacy, civInfo.civName, NotificationIcon.War, diplomacyManager.otherCivName)
        }

        otherCivDiplomacy.setModifier(DiplomaticModifiers.DeclaredWarOnUs, -20f)
        otherCivDiplomacy.removeModifier(DiplomaticModifiers.ReturnedCapturedUnits)

        for (thirdCiv in civInfo.getKnownCivs()) {
            if (thirdCiv.isAtWarWith(otherCiv)) {
                if (thirdCiv.isCityState()) thirdCiv.getDiplomacyManager(civInfo).addInfluence(10f)
                else thirdCiv.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.SharedEnemy, 5f)
            } else thirdCiv.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.WarMongerer, -5f)
        }

        breakTreaties(diplomacyManager)

        if (otherCiv.isMajorCiv())
            for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponDeclaringWar))
                UniqueTriggerActivation.triggerUnique(unique, civInfo)
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
                val diploManager = knownCiv.getDiplomacyManager(diplomacyManager.civInfo)
                if (betrayedFriendship) {
                    val amount = if (knownCiv == otherCiv) -40f else -20f
                    diploManager.addModifier(DiplomaticModifiers.BetrayedDeclarationOfFriendship, amount)
                }
                if (betrayedDefensivePact) {
                    //Note: this stacks with Declaration of Friendship
                    val amount = if (knownCiv == otherCiv) -20f else -10f
                    diploManager.addModifier(DiplomaticModifiers.BetrayedDefensivePact, amount)
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
    }



    /** Everything that happens to both sides equally when war is declared by one side on the other */
    private fun onWarDeclared(diplomacyManager: DiplomacyManager, isOffensiveWar: Boolean) {
        // Cancel all trades.
        for (trade in diplomacyManager.trades)
            for (offer in trade.theirOffers.filter { it.duration > 0 && it.name != Constants.defensivePact})
                diplomacyManager.civInfo.addNotification("[${offer.name}] from [${diplomacyManager.otherCivName}] has ended",
                    DiplomacyAction(diplomacyManager.otherCivName, true),
                    NotificationCategory.Trade, diplomacyManager.otherCivName, NotificationIcon.Trade)
        diplomacyManager.trades.clear()
        diplomacyManager.civInfo.tradeRequests.removeAll { it.requestingCiv == diplomacyManager.otherCivName }

        val civAtWarWith = diplomacyManager.otherCiv()

        // If we attacked, then we need to end all of our defensive pacts acording to Civ 5
        if (isOffensiveWar) {
            removeDefensivePacts(diplomacyManager)
        }
        diplomacyManager.diplomaticStatus = DiplomaticStatus.War

        if (diplomacyManager.civInfo.isMajorCiv()) {
            if (!isOffensiveWar && !civAtWarWith.isCityState()) callInDefensivePactAllies(diplomacyManager)
            callInCityStateAllies(diplomacyManager)
        }

        if (diplomacyManager.civInfo.isCityState() &&
            diplomacyManager.civInfo.cityStateFunctions.getProtectorCivs().contains(civAtWarWith)) {
            diplomacyManager.civInfo.cityStateFunctions.removeProtectorCiv(civAtWarWith, forced = true)
        }

        diplomacyManager.updateHasOpenBorders()

        diplomacyManager.removeModifier(DiplomaticModifiers.YearsOfPeace)
        diplomacyManager.setFlag(DiplomacyFlags.DeclinedPeace, 10)/// AI won't propose peace for 10 turns
        diplomacyManager.setFlag(DiplomacyFlags.DeclaredWar, 10) // AI won't agree to trade for 10 turns
        diplomacyManager.removeFlag(DiplomacyFlags.BorderConflict)
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
            for (civ in diplomacyManager.getCommonKnownCivsWithSpectators()) {
                civ.addNotification("[${diplomacyManager.civInfo.civName}] canceled their Defensive Pact with [${thirdPartyDiploManager.otherCivName}]!",
                    NotificationCategory.Diplomacy, diplomacyManager.civInfo.civName, NotificationIcon.Diplomacy, thirdPartyDiploManager.otherCivName)
            }
            diplomacyManager.civInfo.addNotification("We have canceled our Defensive Pact with [${thirdPartyDiploManager.otherCivName}]!",
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
            civAtWarWith.getDiplomacyManager(ally).declareWar(true)
            // Notify the aggressor
            civAtWarWith.addNotification("[${ally.civName}] has joined the defensive war with [${diplomacyManager.civInfo.civName}]!",
                NotificationCategory.Diplomacy, ally.civName, NotificationIcon.Diplomacy, diplomacyManager.civInfo.civName)
        }
    }

    private fun callInCityStateAllies(diplomacyManager: DiplomacyManager) {
        val civAtWarWith = diplomacyManager.otherCiv()
        for (thirdCiv in diplomacyManager.civInfo.getKnownCivs()
            .filter { it.isCityState() && it.getAllyCiv() == diplomacyManager.civInfo.civName }) {

            if (thirdCiv.knows(civAtWarWith) && !thirdCiv.isAtWarWith(civAtWarWith))
                thirdCiv.getDiplomacyManager(civAtWarWith).declareWar(true)
            else if (!thirdCiv.knows(civAtWarWith)) {
                // Our city state ally has not met them yet, so they have to meet first
                thirdCiv.diplomacyFunctions.makeCivilizationsMeet(civAtWarWith, warOnContact = true)
                thirdCiv.getDiplomacyManager(civAtWarWith).declareWar(true)
            }
        }
    }
}
