package com.unciv.logic.civilization.managers.quests

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.diplomacy.CityStateFunctions
import com.unciv.logic.civilization.diplomacy.CityStatePersonality
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.managers.quests.QuestTargetHelpers.getBarbarianEncampmentForQuest
import com.unciv.logic.civilization.managers.quests.QuestTargetHelpers.getCityStateTarget
import com.unciv.logic.civilization.managers.quests.QuestTargetHelpers.getCivilizationToFindForQuest
import com.unciv.logic.civilization.managers.quests.QuestTargetHelpers.getGreatPersonForQuest
import com.unciv.logic.civilization.managers.quests.QuestTargetHelpers.getMostRecentBully
import com.unciv.logic.civilization.managers.quests.QuestTargetHelpers.getNaturalWonderToFindForQuest
import com.unciv.logic.civilization.managers.quests.QuestTargetHelpers.getResourceForQuest
import com.unciv.logic.civilization.managers.quests.QuestTargetHelpers.getWonderToBuildForQuest
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.QuestName
import com.unciv.models.translations.getPlaceholderParameters
import yairm210.purity.annotations.Readonly

/**
 *  Container for [IQuestImplementation] implementations per [QuestName].
 *
 *  These are instantiated and delegated to by each [QuestName] entry. There should always be exactly
 *  one class here per enum entry there. The aim is to have [QuestName] in models, while this is logic.
 *
 *  Convention: Every member that returns `false` for `isComplete` because completion is handled
 *  elsewhere MUST comment how completion happens, in Kdoc format, with working links.
 *
 *  TODO Eliminate `when` blocks for [ContestCulture][QuestName.ContestCulture],
 *       [ContestFaith][QuestName.ContestFaith] and [QuestName.ContestTech] with additional methods?
 */
sealed class QuestImplementation : IQuestImplementation {
    @Readonly
    protected fun AssignedQuest.isData1CivDefeated(civ: Civilization) =
        civ.gameInfo.getCivilizationOrNull(data1)?.isDefeated() != false
    @Readonly
    protected fun AssignedQuest.getTile(civ: Civilization) =
        civ.gameInfo.tileMap[data1.toInt(), data2.toInt()]
    @Readonly
    protected fun AssignedQuest.getPosition() =
        HexCoord(data1.toInt(), data2.toInt())

    class None : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) = false
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) = false
    }

    class Route : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization): Boolean {
            if (challenger.cities.isEmpty()) return false
            if (challenger.isCapitalConnectedToCity(civ.getCapital()!!)) return false
            val capital = civ.getCapital() ?: return false
            val capitalTile = capital.getCenterTile()
            return challenger.cities.any {
                it.getCenterTile().getContinent() == capitalTile.getContinent() &&
                    it.getCenterTile().aerialDistanceTo(capitalTile) <= 7
            }
        }
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) =
            assignedQuest.assigneeCiv.isCapitalConnectedToCity(civ.getCapital()!!)
    }

    class ClearBarbarianCamp : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.getBarbarianEncampmentForQuest(challenger) != null
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getBarbarianEncampmentForQuest(assignee)!!.position
                .run { x.toString() to y.toString() }
        override fun getNotificationActions(civ: Civilization, assignedQuest: AssignedQuest) =
            listOf(LocationAction(assignedQuest.getPosition())) +
            super.getNotificationActions(civ, assignedQuest)
        /** Completion happens when [MapUnit.clearEncampment] calls [QuestManager.barbarianCampCleared]: */
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) = false
        override fun isObsolete(civ: Civilization, assignedQuest: AssignedQuest) =
            !assignedQuest.getTile(civ).isBarbarianEncampment()
    }

    class ConstructWonder : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.getWonderToBuildForQuest(challenger) != null
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getWonderToBuildForQuest(assignee)!!.name to ""
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) =
            assignedQuest.assigneeCiv.cities
                .any { it.cityConstructions.isBuilt(assignedQuest.data1) }
        @Suppress("DEPRECATION")
        override fun isObsolete(civ: Civilization, assignedQuest: AssignedQuest) =
            civ.gameInfo.getCities().any { it.civ != assignedQuest.assigneeCiv && it.cityConstructions.isBuilt(assignedQuest.data1) }
    }

    class ConnectResource : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.getResourceForQuest(challenger) != null
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getResourceForQuest(assignee)!!.name to ""
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest): Boolean {
            val resource = civ.gameInfo.ruleset.tileResources[assignedQuest.data1]
            return assignedQuest.assigneeCiv.detailedCivResources
                .any { it.resource == resource }
        }
    }

    class GreatPerson : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.getGreatPersonForQuest(challenger) != null
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getGreatPersonForQuest(assignee)!!.name to ""
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest): Boolean {
            val ruleset = civ.gameInfo.ruleset
            return assignedQuest.assigneeCiv.units.getCivGreatPeople()
                .any { it.baseUnit.getReplacedUnit(ruleset).name == assignedQuest.data1 }
        }
    }

    class ConquerCityState : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.getCityStateTarget(challenger) != null && civ.cityStatePersonality != CityStatePersonality.Friendly
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getCityStateTarget(assignee)!!.civID to ""
        /** Completed by [CityStateFunctions.cityStateDestroyed] calling [QuestManager.cityStateConquered] */
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) = false
        override fun isObsolete(civ: Civilization, assignedQuest: AssignedQuest) =
            assignedQuest.isData1CivDefeated(civ)
    }

    class FindPlayer : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.getCivilizationToFindForQuest(challenger) != null
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getCivilizationToFindForQuest(assignee)!!.civID to ""
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest): Boolean {
            val civToFind = civ.gameInfo.getCivilizationOrNull(assignedQuest.data1) ?: return false
            return assignedQuest.assigneeCiv.hasMetCivTerritory(civToFind)
        }
        override fun isObsolete(civ: Civilization, assignedQuest: AssignedQuest) =
            assignedQuest.isData1CivDefeated(civ)
    }

    class FindNaturalWonder : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.getNaturalWonderToFindForQuest(challenger) != null
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getNaturalWonderToFindForQuest(assignee)!! to ""
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) =
            assignedQuest.assigneeCiv.naturalWonders.contains(assignedQuest.data1)
    }

    class GiveGold : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.getMostRecentBully() != null
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getMostRecentBully()!! to ""
        /** Completed by [CityStateFunctions.receiveGoldGift] calling [QuestManager.receivedGoldGift] */
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) = false
    }

    class PledgeToProtect : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.getMostRecentBully() != null && challenger !in civ.cityStateFunctions.getProtectorCivs()
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getMostRecentBully()!! to ""
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) =
            assignedQuest.assigneeCiv in civ.cityStateFunctions.getProtectorCivs()
    }

    class ContestCulture : QuestImplementation() {
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            assignee.totalCultureForContests.toString() to ""
        /** Completed by [QuestManager.handleGlobalQuest] after it expires via duration */
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) = false
    }

    class ContestFaith : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.gameInfo.isReligionEnabled()
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            assignee.totalFaithForContests.toString() to ""
        /** Completed by [QuestManager.handleGlobalQuest] after it expires via duration */
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) = false
    }

    class ContestTech : QuestImplementation() {
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            assignee.tech.getNumberOfTechsResearched().toString() to ""
        /** Completed by [QuestManager.handleGlobalQuest] after it expires via duration */
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) = false
    }

    class Invest : QuestImplementation() {
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            quest.description.getPlaceholderParameters().first() to ""
        /** Never completed, just modifies gift effect and times out */
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) = false
    }

    class BullyCityState : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization) =
            civ.getCityStateTarget(challenger) != null
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getCityStateTarget(assignee)!!.civID to ""
        /** Completed by [CityStateFunctions.cityStateBullied] calling [QuestManager.cityStateBullied] */
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) = false
        override fun isObsolete(civ: Civilization, assignedQuest: AssignedQuest) =
            assignedQuest.isData1CivDefeated(civ)
    }

    class DenounceCiv : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization): Boolean {
            val mostRecentBully = civ.getMostRecentBully() ?: return false
            val challengerDiplomacy = challenger.getDiplomacyManager(mostRecentBully) ?: return false
            return !challengerDiplomacy.hasFlag(DiplomacyFlags.Denunciation)
                && challengerDiplomacy.diplomaticStatus != DiplomaticStatus.War
                && !( challenger.playerType == PlayerType.Human
                && civ.gameInfo.getCivilization(mostRecentBully).playerType == PlayerType.Human)
        }
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) =
            civ.getMostRecentBully()!! to ""
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) =
            assignedQuest.assigneeCiv.getDiplomacyManager(assignedQuest.data1)
                ?.hasFlag(DiplomacyFlags.Denunciation) == true
        override fun isObsolete(civ: Civilization, assignedQuest: AssignedQuest) =
            assignedQuest.isData1CivDefeated(civ)
    }

    class SpreadReligion : QuestImplementation() {
        override fun isValid(civ: Civilization, challenger: Civilization): Boolean {
            val playerReligion = civ.gameInfo.religions.values
                .firstOrNull { it.foundingCiv == challenger && it.isMajorReligion() }
                ?: return false
            return civ.getCapital()!!.religion.getMajorityReligion()?.name != playerReligion.name
        }
        override fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization): Pair<String, String> {
            val playerReligion = civ.gameInfo.religions.values
                .first { it.foundingCiv == assignee && it.isMajorReligion() }  // isQuestValid must have ensured this won't throw
            val data1 = playerReligion.getReligionDisplayName() // For display
            val data2 = playerReligion.name // To check completion
            return data1 to data2
        }
        override fun isComplete(civ: Civilization, assignedQuest: AssignedQuest) =
            civ.getCapital()!!.religion.getMajorityReligion() == civ.gameInfo.religions[assignedQuest.data2]
    }
}
