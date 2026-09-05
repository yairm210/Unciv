package com.unciv.logic.civilization.diplomacy

import com.unciv.UncivGame
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.map.mapunit.movement.UnitMovement
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import yairm210.purity.annotations.Readonly
import kotlin.math.max

class DiplomacyFunctions(val civInfo: Civilization) {

    /** A sorted Sequence of all other civs we know (excluding barbarians and spectators) */
    fun getKnownCivsSorted(includeCityStates: Boolean = true, includeDefeated: Boolean = false) =
        civInfo.gameInfo.getCivsSorted(includeCityStates, includeDefeated) {
            it != civInfo && civInfo.knows(it)
        }


    fun makeCivilizationsMeet(otherCiv: Civilization, warOnContact: Boolean = false) {
        meetCiv(otherCiv, warOnContact)
        otherCiv.diplomacyFunctions.meetCiv(civInfo, warOnContact)
    }

    private fun meetCiv(otherCiv: Civilization, warOnContact: Boolean = false) {
        civInfo.diplomacy[otherCiv.civID] = DiplomacyManager(civInfo, otherCiv)
            .apply { diplomaticStatus = DiplomaticStatus.Peace }

        if (!otherCiv.isSpectator())
            otherCiv.popupAlerts.add(PopupAlert(AlertType.FirstContact, civInfo.civID))

        if (civInfo.isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Meet another civilization")


        if (civInfo.isCityState && otherCiv.isMajorCiv()) {
            if (warOnContact || otherCiv.isMinorCivAggressor())
                return // No gift if they are bad people, or we are just about to be at war

            val cityStateLocation = civInfo.getCapital()?.location
            val isFirstMajorCivToMeet = civInfo.diplomacy.count { it.value.otherCiv.isMajorCiv() } == 1

            fun addNotification(text: String, icon: String) {
                if (cityStateLocation != null)
                    otherCiv.addNotification(text, cityStateLocation, NotificationCategory.Diplomacy, icon)
                else
                    otherCiv.addNotification(text, NotificationCategory.Diplomacy, icon)
            }

            val gift = Stats(gold = 15f)
            if (isFirstMajorCivToMeet)
                gift.timesInPlace(2f)
            val meetingText =
                if (isFirstMajorCivToMeet)
                    "[${civInfo.civName}] welcomes us as the first great empire they have encountered and presents us with a gift of [${gift.toStringForNotifications()}]"
                else
                    "In honor of our meeting, [${civInfo.civName}] presents us with a gift of [${gift.toStringForNotifications()}]"
            addNotification(meetingText, NotificationIcon.Gold)
            otherCiv.addStats(gift)
            
            civInfo.forEachMatchingUnique(UniqueType.CityStateReligiousMeetingGift) {
                val religiousGift = it.stats.clone()
                if (isFirstMajorCivToMeet)
                    religiousGift.timesInPlace(2f)
                val religiousMeetingText = "[${civInfo.civName}] also shares their " +
                    if (isFirstMajorCivToMeet) "treasured religious idols, awarding us [${religiousGift.toStringForNotifications()}]"
                    else "knowledge of religious rituals, awarding us [${religiousGift.toStringForNotifications()}]"
                addNotification(religiousMeetingText, NotificationIcon.Faith)
                otherCiv.addStats(religiousGift)
            }

            if (civInfo.cities.isNotEmpty())
                civInfo.getCapital()?.getCenterTile()?.setExplored(otherCiv, true)

            civInfo.questManager.justMet(otherCiv) // Include them in war with major pseudo-quest
        }
    }

    @Readonly
    fun isAtWarWith(otherCiv: Civilization): Boolean {
        return when {
            otherCiv == civInfo -> false
            otherCiv.isBarbarian || civInfo.isBarbarian -> true
            else -> {
                val diplomacyManager = civInfo.getDiplomacyManager(otherCiv)
                    ?: return false // not encountered yet
                return diplomacyManager.diplomaticStatus == DiplomaticStatus.War
            }
        }
    }

    /**
     * If denounciation happened this turn from either side, establishing embassy again is possible only from next turn.
     */
    @Readonly
    private fun isDenouncedThisTurn(diploManager: DiplomacyManager): Boolean {
        return diploManager.getFlag(DiplomacyFlags.Denunciation) == 30
            || diploManager.otherCivDiplomacy().getFlag(DiplomacyFlags.Denunciation) == 30
    }

    /**
     * Basic check if we can trade embassies, does not check all prerequisities
     * Use [canOfferEmbassyTo] and [canEstablishEmbassyWith] instead
     */
    @Readonly
    private fun canTradeEmbassies(): Boolean {
        return civInfo.isMajorCiv() && civInfo.hasUnique(UniqueType.EnablesEmbassies)
    }

    /**
     * Test if we can offer our embassy to [otherCiv]
     */
    @Readonly
    fun canOfferEmbassyTo(otherCiv: Civilization): Boolean {
        if (!canTradeEmbassies() || !otherCiv.isMajorCiv() || civInfo.getCapital() == null)
            return false

        val theirDiploManager = otherCiv.getDiplomacyManager(civInfo)!!
        return !civInfo.isAtWarWith(otherCiv) && !isDenouncedThisTurn(theirDiploManager)
            && !theirDiploManager.hasModifier(DiplomaticModifiers.EstablishedEmbassy)
            && !theirDiploManager.hasModifier(DiplomaticModifiers.SharedEmbassies)
    }

    /**
     * Test if we can establish embassy in [otherCiv] capital
     */
    @Readonly
    fun canEstablishEmbassyWith(otherCiv: Civilization): Boolean {
        if (!canTradeEmbassies() || !otherCiv.isMajorCiv() || otherCiv.getCapital() == null)
            return false

        val ourDiploManager = civInfo.getDiplomacyManager(otherCiv)!!
        return !civInfo.isAtWarWith(otherCiv) && !isDenouncedThisTurn(ourDiploManager)
            && !ourDiploManager.hasModifier(DiplomaticModifiers.EstablishedEmbassy)
            && !ourDiploManager.hasModifier(DiplomaticModifiers.SharedEmbassies)
    }

    @Readonly
    fun meetsEmbassyRequirementFor(otherCiv: Civilization): Boolean {
        return !civInfo.hasUnique(UniqueType.RequiresEmbassiesForDiplomacy) ||
            civInfo.getDiplomacyManager(otherCiv)!!.hasModifier(DiplomaticModifiers.SharedEmbassies)
    }

    /**
     * Remove mutual embassies from both civs
     */
    fun removeEmbassies(otherCiv: Civilization) {
        val ourDiploManager = civInfo.getDiplomacyManager(otherCiv)!!
        ourDiploManager.removeModifier(DiplomaticModifiers.EstablishedEmbassy)
        ourDiploManager.removeModifier(DiplomaticModifiers.ReceivedEmbassy)
        ourDiploManager.removeModifier(DiplomaticModifiers.SharedEmbassies)

        val theirDiploManager = ourDiploManager.otherCivDiplomacy()
        theirDiploManager.removeModifier(DiplomaticModifiers.EstablishedEmbassy)
        theirDiploManager.removeModifier(DiplomaticModifiers.ReceivedEmbassy)
        theirDiploManager.removeModifier(DiplomaticModifiers.SharedEmbassies)
    }

    @Readonly
    fun canSignDeclarationOfFriendshipWith(otherCiv: Civilization): Boolean {
        return otherCiv.isMajorCiv() && !otherCiv.isAtWarWith(civInfo)
            && !civInfo.getDiplomacyManager(otherCiv)!!.hasFlag(DiplomacyFlags.Denunciation)
            && !civInfo.getDiplomacyManager(otherCiv)!!.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
    }

    @Readonly
    fun canSignResearchAgreement(): Boolean {
        if (!civInfo.isMajorCiv()) return false
        if (!civInfo.hasUnique(UniqueType.EnablesResearchAgreements)) return false
        if (civInfo.tech.allTechsAreResearched()) return false
        return true
    }

    @Readonly
    fun canSignResearchAgreementNoCostWith (otherCiv: Civilization): Boolean {
        val ourDiploManager = civInfo.getDiplomacyManager(otherCiv)!!
        return canSignResearchAgreement()
            && otherCiv.diplomacyFunctions.canSignResearchAgreement()
            && meetsEmbassyRequirementFor(otherCiv)
            && ourDiploManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
            && !ourDiploManager.hasFlag(DiplomacyFlags.ResearchAgreement)
            && !ourDiploManager.otherCivDiplomacy().hasFlag(DiplomacyFlags.ResearchAgreement)
    }

    @Readonly
    fun canSignResearchAgreementWith(otherCiv: Civilization): Boolean {
        val cost = getResearchAgreementCost(otherCiv)
        return canSignResearchAgreementNoCostWith(otherCiv)
            && civInfo.gold >= cost && otherCiv.gold >= cost
    }

    @Readonly
    fun getResearchAgreementCost(otherCiv: Civilization): Int {
        // https://forums.civfanatics.com/resources/research-agreements-bnw.25568/
        return ( max(civInfo.getEra().researchAgreementCost, otherCiv.getEra().researchAgreementCost)
                    * civInfo.gameInfo.speed.goldCostModifier
            ).toInt()
    }

    @Readonly
    fun canSignDefensivePact(): Boolean {
        if (!civInfo.isMajorCiv()) return false
        if (!civInfo.hasUnique(UniqueType.EnablesDefensivePacts)) return false
        return true
    }

    @Readonly
    fun canSignDefensivePactWith(otherCiv: Civilization): Boolean {
        val ourDiplomacyManager = civInfo.getDiplomacyManager(otherCiv)!!
        return canSignDefensivePact()
            && otherCiv.diplomacyFunctions.canSignDefensivePact()
            && meetsEmbassyRequirementFor(otherCiv)
            && ourDiplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
            && !ourDiplomacyManager.hasFlag(DiplomacyFlags.DefensivePact)
            && !ourDiplomacyManager.otherCivDiplomacy().hasFlag(DiplomacyFlags.DefensivePact)
            && ourDiplomacyManager.diplomaticStatus != DiplomaticStatus.DefensivePact
    }

    /**
     * @returns whether units of this civilization can pass through the tiles owned by [otherCiv],
     * considering only civ-wide filters.
     * Use [Tile.canCivPassThrough] to check whether units of a civilization can pass through
     * a specific tile, considering only civ-wide filters.
     * Use [UnitMovement.canPassThrough] to check whether a specific unit can pass through
     * a specific tile.
     */
    @Readonly
    fun canPassThroughTiles(otherCiv: Civilization): Boolean {
        if (otherCiv == civInfo) return true
        if (otherCiv.isBarbarian) return true
        if (civInfo.isBarbarian && civInfo.gameInfo.turns >= civInfo.gameInfo.getDifficulty().turnBarbariansCanEnterPlayerTiles)
            return true
        val diplomacyManager = civInfo.getDiplomacyManager(otherCiv)
        if (diplomacyManager != null && (diplomacyManager.hasOpenBorders || diplomacyManager.diplomaticStatus == DiplomaticStatus.War))
            return true
        // Players can always pass through city-state tiles
        if (!civInfo.isAIOrAutoPlaying() && otherCiv.isCityState) return true
        return false
    }
}
