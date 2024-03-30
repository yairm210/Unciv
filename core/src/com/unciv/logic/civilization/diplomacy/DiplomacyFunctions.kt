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
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
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
        civInfo.diplomacy[otherCiv.civName] = DiplomacyManager(civInfo, otherCiv.civName)
            .apply { diplomaticStatus = DiplomaticStatus.Peace }

        if (!otherCiv.isSpectator())
            otherCiv.popupAlerts.add(PopupAlert(AlertType.FirstContact, civInfo.civName))

        if (civInfo.isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Meet another civilization")


        if (civInfo.isCityState() && otherCiv.isMajorCiv()) {
            if (warOnContact || otherCiv.isMinorCivAggressor()) return // No gift if they are bad people, or we are just about to be at war

            val cityStateLocation = if (civInfo.cities.isEmpty()) null else civInfo.getCapital()!!.location

            val giftAmount = Stats(gold = 15f)
            val faithAmount = Stats(faith = 4f)
            // Later, religious city-states will also gift gold, making this the better implementation
            // For now, it might be overkill though.
            var meetString = "[${civInfo.civName}] has given us [${giftAmount}] as a token of goodwill for meeting us"
            val religionMeetString = "[${civInfo.civName}] has also given us [${faithAmount}]"
            if (civInfo.diplomacy.count { it.value.otherCiv().isMajorCiv() } == 1) {
                giftAmount.timesInPlace(2f)
                meetString = "[${civInfo.civName}] has given us [${giftAmount}] as we are the first major civ to meet them"
            }
            if (cityStateLocation != null)
                otherCiv.addNotification(meetString, cityStateLocation, NotificationCategory.Diplomacy, NotificationIcon.Gold)
            else
                otherCiv.addNotification(meetString, NotificationCategory.Diplomacy, NotificationIcon.Gold)

            if (otherCiv.isCityState() && otherCiv.cityStateFunctions.canProvideStat(Stat.Faith)) {
                otherCiv.addNotification(religionMeetString, NotificationCategory.Diplomacy, NotificationIcon.Faith)

                for ((key, value) in faithAmount)
                    otherCiv.addStat(key, value.toInt())
            }
            for ((key, value) in giftAmount)
                otherCiv.addStat(key, value.toInt())

            if (civInfo.cities.isNotEmpty())
                civInfo.getCapital()?.getCenterTile()?.setExplored(otherCiv, true)

            civInfo.questManager.justMet(otherCiv) // Include them in war with major pseudo-quest
        }
    }


    fun isAtWarWith(otherCiv: Civilization): Boolean {
        return when {
            otherCiv == civInfo -> false
            otherCiv.isBarbarian() || civInfo.isBarbarian() -> true
            else -> {
                val diplomacyManager = civInfo.diplomacy[otherCiv.civName]
                    ?: return false // not encountered yet
                return diplomacyManager.diplomaticStatus == DiplomaticStatus.War
            }
        }
    }

    fun canSignDeclarationOfFriendshipWith(otherCiv: Civilization): Boolean {
        return otherCiv.isMajorCiv() && !otherCiv.isAtWarWith(civInfo)
            && !civInfo.getDiplomacyManager(otherCiv).hasFlag(DiplomacyFlags.Denunciation)
            && !civInfo.getDiplomacyManager(otherCiv).hasFlag(DiplomacyFlags.DeclarationOfFriendship)
    }

    fun canSignResearchAgreement(): Boolean {
        if (!civInfo.isMajorCiv()) return false
        if (!civInfo.hasUnique(UniqueType.EnablesResearchAgreements)) return false
        if (civInfo.gameInfo.ruleset.technologies.values
                    .none { civInfo.tech.canBeResearched(it.name) && !civInfo.tech.isResearched(it.name) }) return false
        return true
    }

    fun canSignResearchAgreementNoCostWith (otherCiv: Civilization): Boolean {
        val diplomacyManager = civInfo.getDiplomacyManager(otherCiv)
        return canSignResearchAgreement() && otherCiv.diplomacyFunctions.canSignResearchAgreement()
            && diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
            && !diplomacyManager.hasFlag(DiplomacyFlags.ResearchAgreement)
            && !diplomacyManager.otherCivDiplomacy().hasFlag(DiplomacyFlags.ResearchAgreement)
    }

    fun canSignResearchAgreementsWith(otherCiv: Civilization): Boolean {
        val cost = getResearchAgreementCost(otherCiv)
        return canSignResearchAgreementNoCostWith(otherCiv)
            && civInfo.gold >= cost && otherCiv.gold >= cost
    }

    fun getResearchAgreementCost(otherCiv: Civilization): Int {
        // https://forums.civfanatics.com/resources/research-agreements-bnw.25568/
        return ( max(civInfo.getEra().researchAgreementCost, otherCiv.getEra().researchAgreementCost)
                    * civInfo.gameInfo.speed.goldCostModifier
            ).toInt()
    }

    fun canSignDefensivePact(): Boolean {
        if (!civInfo.isMajorCiv()) return false
        if (!civInfo.hasUnique(UniqueType.EnablesDefensivePacts)) return false
        return true
    }

    fun canSignDefensivePactWith(otherCiv: Civilization): Boolean {
        val diplomacyManager = civInfo.getDiplomacyManager(otherCiv)
        return canSignDefensivePact() && otherCiv.diplomacyFunctions.canSignDefensivePact()
            && (diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
            || diplomacyManager.otherCivDiplomacy().hasFlag(DiplomacyFlags.DeclarationOfFriendship))
            && !diplomacyManager.hasFlag(DiplomacyFlags.DefensivePact)
            && !diplomacyManager.otherCivDiplomacy().hasFlag(DiplomacyFlags.DefensivePact)
            && diplomacyManager.diplomaticStatus != DiplomaticStatus.DefensivePact
    }


    /**
     * @returns whether units of this civilization can pass through the tiles owned by [otherCiv],
     * considering only civ-wide filters.
     * Use [Tile.canCivPassThrough] to check whether units of a civilization can pass through
     * a specific tile, considering only civ-wide filters.
     * Use [UnitMovement.canPassThrough] to check whether a specific unit can pass through
     * a specific tile.
     */
    fun canPassThroughTiles(otherCiv: Civilization): Boolean {
        if (otherCiv == civInfo) return true
        if (otherCiv.isBarbarian()) return true
        if (civInfo.isBarbarian() && civInfo.gameInfo.turns >= civInfo.gameInfo.difficultyObject.turnBarbariansCanEnterPlayerTiles)
            return true
        val diplomacyManager = civInfo.diplomacy[otherCiv.civName]
        if (diplomacyManager != null && (diplomacyManager.hasOpenBorders || diplomacyManager.diplomaticStatus == DiplomaticStatus.War))
            return true
        // Players can always pass through city-state tiles
        if (!civInfo.isAIOrAutoPlaying() && otherCiv.isCityState()) return true
        return false
    }



}
