@file:Suppress("ConvertArgumentToSet")  // Flags all assignedQuests.removeAll(List) - not worth it

package com.unciv.logic.civilization.managers

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.DiplomacyAction
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.Notification  // for Kdoc
import com.unciv.logic.civilization.NotificationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.Proximity
import com.unciv.logic.civilization.diplomacy.CityStatePersonality
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.QuestName
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toPercent
import com.unciv.utils.randomWeighted
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import kotlin.random.Random

class QuestManager : IsPartOfGameInfoSerialization {

    companion object {
        const val UNSET = -1

        const val GLOBAL_QUEST_FIRST_POSSIBLE_TURN = 30
        const val INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN = 30

        const val GLOBAL_QUEST_FIRST_POSSIBLE_TURN_RAND = 20
        const val INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN_RAND = 20

        const val GLOBAL_QUEST_MIN_TURNS_BETWEEN = 40
        const val INDIVIDUAL_QUEST_MIN_TURNS_BETWEEN = 20

        const val GLOBAL_QUEST_RAND_TURNS_BETWEEN = 25
        const val INDIVIDUAL_QUEST_RAND_TURNS_BETWEEN = 25

        const val GLOBAL_QUEST_MAX_ACTIVE = 1
        const val INDIVIDUAL_QUEST_MAX_ACTIVE = 2
    }

    /** Civilization object holding and dispatching quests */
    @Transient
    private lateinit var civ: Civilization

    /** Readability helper to access the Ruleset through [civ] */
    private val ruleset get() = civ.gameInfo.ruleset

    /** List of active quests, both global and individual ones*/
    private var assignedQuests: ArrayList<AssignedQuest> = ArrayList()

    /**  Number of turns left before starting new global quest */
    private var globalQuestCountdown: Int = UNSET

    /** Number of turns left before this city state can start a new individual quest.
     * Key is major civ name, value is turns to quest */
    private var individualQuestCountdown: HashMap<String, Int> = HashMap()

    /** Target number of units to kill for this war, for war with major pseudo-quest */
    private var unitsToKillForCiv: HashMap<String, Int> = HashMap()

    /** For this attacker, number of units killed by each civ */
    private var unitsKilledFromCiv: HashMap<String, HashMap<String, Int>> = HashMap()

    /** Returns true if [civ] have active quests for [challenger] */
    @Readonly fun haveQuestsFor(challenger: Civilization): Boolean = getAssignedQuestsFor(challenger.civName).any()

    /** Access all assigned Quests for [civName] */
    @Readonly
    fun getAssignedQuestsFor(civName: String) =
        assignedQuests.asSequence().filter { it.assignee == civName }

    /** Access all assigned Quests of "type" [questName] */
    // Note if we decide to cache an index of these (such as `assignedQuests.groupBy { it.questNameInstance }`), this accessor would simplify the transition
    @Readonly
    private fun getAssignedQuestsOfName(questName: QuestName) =
        assignedQuests.asSequence().filter { it.questNameInstance == questName }

    /** Returns true if [civ] has asked anyone to conquer [target] */
    @Readonly fun wantsDead(target: String): Boolean = getAssignedQuestsOfName(QuestName.ConquerCityState).any { it.data1 == target }

    /** Returns the influence multiplier for [donor] from a Investment quest that [civ] might have (assumes only one) */
    @Readonly
    fun getInvestmentMultiplier(donor: String): Float {
        val investmentQuest = getAssignedQuestsOfName(QuestName.Invest).firstOrNull { it.assignee == donor }
            ?: return 1f
        return investmentQuest.data1.toPercent()
    }

    fun clone(): QuestManager {
        val toReturn = QuestManager()
        toReturn.globalQuestCountdown = globalQuestCountdown
        toReturn.individualQuestCountdown.putAll(individualQuestCountdown)
        toReturn.assignedQuests.addAll(assignedQuests)
        toReturn.unitsToKillForCiv.putAll(unitsToKillForCiv)
        for ((attacker, unitsKilled) in unitsKilledFromCiv) {
            toReturn.unitsKilledFromCiv[attacker] = HashMap(unitsKilled)
        }
        return toReturn
    }

    fun setTransients(civ: Civilization) {
        this.civ = civ
        for (quest in assignedQuests)
            quest.setTransients(civ.gameInfo)
    }

    fun endTurn() {

        if (civ.isDefeated()) {
            assignedQuests.clear()
            individualQuestCountdown.clear()
            globalQuestCountdown = UNSET
            return
        }

        if (civ.cities.isEmpty()) return // don't assign quests until we have a city

        seedGlobalQuestCountdown()
        seedIndividualQuestsCountdowns()

        decrementQuestCountdowns()

        handleGlobalQuests()
        handleIndividualQuests()

        tryStartNewGlobalQuest()
        tryStartNewIndividualQuests()

        tryBarbarianInvasion()
        tryEndWarWithMajorQuests()
    }

    private fun decrementQuestCountdowns() {
        if (globalQuestCountdown > 0)
            globalQuestCountdown -= 1

        for (entry in individualQuestCountdown)
            if (entry.value > 0)
                entry.setValue(entry.value - 1)
    }

    private fun seedGlobalQuestCountdown() {
        if (civ.gameInfo.turns < GLOBAL_QUEST_FIRST_POSSIBLE_TURN)
            return

        if (globalQuestCountdown != UNSET)
            return

        val countdown =
                if (civ.gameInfo.turns == GLOBAL_QUEST_FIRST_POSSIBLE_TURN)
                    Random.nextInt(GLOBAL_QUEST_FIRST_POSSIBLE_TURN_RAND)
                else
                    GLOBAL_QUEST_MIN_TURNS_BETWEEN + Random.nextInt(GLOBAL_QUEST_RAND_TURNS_BETWEEN)

        globalQuestCountdown = (countdown * civ.gameInfo.speed.modifier).toInt()
    }

    private fun seedIndividualQuestsCountdowns() {
        if (civ.gameInfo.turns < INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN)
            return

        val majorCivs = civ.gameInfo.getAliveMajorCivs()
        for (majorCiv in majorCivs)
            if (!individualQuestCountdown.containsKey(majorCiv.civName) || individualQuestCountdown[majorCiv.civName] == UNSET)
                seedIndividualQuestsCountdown(majorCiv)
    }

    private fun seedIndividualQuestsCountdown(challenger: Civilization) {
        val countdown: Int =
                if (civ.gameInfo.turns == INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN)
                    Random.nextInt(INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN_RAND)
                else
                    INDIVIDUAL_QUEST_MIN_TURNS_BETWEEN + Random.nextInt(
                        INDIVIDUAL_QUEST_RAND_TURNS_BETWEEN
                    )

        individualQuestCountdown[challenger.civName] = (countdown * civ.gameInfo.speed.modifier).toInt()
    }

    // Readability helper - No asSequence(): call frequency * data size is small
    @Readonly private fun getQuests(predicate: (Quest) -> Boolean) = ruleset.quests.values.filter(predicate)

    private fun tryStartNewGlobalQuest() {
        if (globalQuestCountdown != 0)
            return
        if (assignedQuests.count { it.isGlobal() } >= GLOBAL_QUEST_MAX_ACTIVE)
            return

        val majorCivs = civ.getKnownCivs().filter { it.isMajorCiv() && !it.isAtWarWith(civ) } // A Sequence - fine because the count below can be different for each Quest
        @Readonly fun Quest.isAssignable() = majorCivs.count { civ -> isQuestValid(this, civ) } >= minimumCivs
        val assignableQuests = getQuests {
            it.isGlobal() && it.isAssignable()
        }

        if (assignableQuests.isNotEmpty()) {
            val quest = assignableQuests.randomWeighted { getQuestWeight(it.name) }
            val assignees = civ.gameInfo.getAliveMajorCivs().filter { !it.isAtWarWith(civ) && isQuestValid(quest, it) }

            assignNewQuest(quest, assignees)
            globalQuestCountdown = UNSET
        }
    }

    private fun tryStartNewIndividualQuests() {
        for ((challengerName, countdown) in individualQuestCountdown) {
            val challenger = civ.gameInfo.getCivilization(challengerName)

            if (countdown != 0)
                continue

            if (getAssignedQuestsFor(challenger.civName).count { it.isIndividual() } >= INDIVIDUAL_QUEST_MAX_ACTIVE)
                continue

            val assignableQuests = getQuests { it.isIndividual() && isQuestValid(it, challenger) }

            if (assignableQuests.isNotEmpty()) {
                val quest = assignableQuests.randomWeighted { getQuestWeight(it.name) }
                val assignees = arrayListOf(challenger)

                assignNewQuest(quest, assignees)
            }
        }
    }

    private fun tryBarbarianInvasion() {
        if ((civ.getTurnsTillCallForBarbHelp() == null || civ.getTurnsTillCallForBarbHelp() == 0)
            && civ.cityStateFunctions.getNumThreateningBarbarians() >= 2) {

            for (otherCiv in civ.getKnownCivs().filter {
                    it.isMajorCiv()
                    && it.isAlive()
                    && !it.isAtWarWith(civ)
                    && it.getProximity(civ) <= Proximity.Far
            }) {
                otherCiv.addNotification(
                    "[${civ.civName}] is being invaded by Barbarians! Destroy Barbarians near their territory to earn Influence.",
                    civ.getCapital()!!.location,
                    NotificationCategory.Diplomacy, civ.civName,
                    NotificationIcon.War
                )
            }
            civ.addFlag(CivFlags.TurnsTillCallForBarbHelp.name, 30)
        }
    }

    private fun handleGlobalQuests() {
        // Remove any participants that are no longer valid because of being dead or at war with the CS
        assignedQuests.removeAll { it.isGlobal() &&
            !canAssignAQuestTo(civ.gameInfo.getCivilization(it.assignee)) }
        val globalQuestsExpired = assignedQuests.filter { it.isGlobal() && it.isExpired() }.map { it.questNameInstance }.distinct()
        for (globalQuestName in globalQuestsExpired)
            handleGlobalQuest(globalQuestName)
    }

    private fun handleGlobalQuest(questName: QuestName) {
        val winnersAndLosers = WinnersAndLosers(questName)
        winnersAndLosers.winners.forEach { giveReward(it) }
        winnersAndLosers.losers.forEach { notifyExpired(it, winnersAndLosers.winners) }

        assignedQuests.removeAll { it.questNameInstance == questName }  // removing winners then losers would leave those with score 0
    }

    fun handleIndividualQuests() {
        assignedQuests.removeAll { it.isIndividual() && handleIndividualQuest(it) }
    }

    /** If quest is complete, it gives the influence reward to the player.
     *  Returns true if the quest can be removed (is either complete, obsolete or expired) */
    private fun handleIndividualQuest(assignedQuest: AssignedQuest): Boolean {
        val assignee = civ.gameInfo.getCivilization(assignedQuest.assignee)

        // One of the civs is defeated, or they started a war: remove quest
        if (!canAssignAQuestTo(assignee))
            return true

        if (isComplete(assignedQuest)) {
            giveReward(assignedQuest)
            return true
        }

        if (isObsolete(assignedQuest)) {
            notifyExpired(assignedQuest)
            return true
        }

        if (assignedQuest.isExpired()) {
            notifyExpired(assignedQuest)
            return true
        }

        return false
    }

    private fun assignNewQuest(quest: Quest, assignees: Iterable<Civilization>) {

        val turn = civ.gameInfo.turns

        for (assignee in assignees) {

            var data1 = ""
            var data2 = ""
            var notificationActions: List<NotificationAction> = listOf(DiplomacyAction(civ))

            when (quest.questNameInstance) {
                QuestName.ClearBarbarianCamp -> {
                    val camp = getBarbarianEncampmentForQuest()!!
                    data1 = camp.position.x.toInt().toString()
                    data2 = camp.position.y.toInt().toString()
                    notificationActions = listOf(LocationAction(camp.position), notificationActions.first())
                }
                QuestName.ConnectResource -> data1 = getResourceForQuest(assignee)!!.name
                QuestName.ConstructWonder -> data1 = getWonderToBuildForQuest(assignee)!!.name
                QuestName.GreatPerson -> data1 = getGreatPersonForQuest(assignee)!!.name
                QuestName.FindPlayer -> data1 = getCivilizationToFindForQuest(assignee)!!.civName
                QuestName.FindNaturalWonder -> data1 = getNaturalWonderToFindForQuest(assignee)!!
                QuestName.ConquerCityState -> data1 = getCityStateTarget(assignee)!!.civName
                QuestName.BullyCityState -> data1 = getCityStateTarget(assignee)!!.civName
                QuestName.PledgeToProtect -> data1 = getMostRecentBully()!!
                QuestName.GiveGold -> data1 = getMostRecentBully()!!
                QuestName.DenounceCiv -> data1 = getMostRecentBully()!!
                QuestName.SpreadReligion -> {
                    val playerReligion = civ.gameInfo.religions.values
                        .first { it.foundingCiv == assignee && it.isMajorReligion() }  // isQuestValid must have ensured this won't throw
                    data1 = playerReligion.getReligionDisplayName() // For display
                    data2 = playerReligion.name // To check completion
                }
                QuestName.ContestCulture -> data1 = assignee.totalCultureForContests.toString()
                QuestName.ContestFaith -> data1 = assignee.totalFaithForContests.toString()
                QuestName.ContestTech -> data1 = assignee.tech.getNumberOfTechsResearched().toString()
                QuestName.Invest -> data1 = quest.description.getPlaceholderParameters().first()
                else -> Unit
            }

            val newQuest = AssignedQuest(
                    questName = quest.name,
                    assigner = civ.civName,
                    assignee = assignee.civName,
                    assignedOnTurn = turn,
                    data1 = data1,
                    data2 = data2
            )
            newQuest.setTransients(civ.gameInfo, quest)

            assignedQuests.add(newQuest)
            if (quest.isIndividual())
                individualQuestCountdown[assignee.civName] = UNSET

            assignee.addNotification("[${civ.civName}] assigned you a new quest: [${quest.name}].",
                notificationActions,
                NotificationCategory.Diplomacy, civ.civName, "OtherIcons/Quest")
        }
    }

    /** Returns true if [civ] can assign a quest to [challenger] */
    @Readonly
    private fun canAssignAQuestTo(challenger: Civilization): Boolean {
        return !challenger.isDefeated() && challenger.isMajorCiv() &&
                civ.knows(challenger) && !civ.isAtWarWith(challenger)
    }

    /** Returns true if the [quest] can be assigned to [challenger] */
    @Readonly
    private fun isQuestValid(quest: Quest, challenger: Civilization): Boolean {
        if (!canAssignAQuestTo(challenger))
            return false
        if (getAssignedQuestsOfName(quest.questNameInstance).any { it.assignee == challenger.civName })
            return false
        if (quest.isIndividual() && civ.getDiplomacyManager(challenger)!!.hasFlag(DiplomacyFlags.Bullied))
            return false

        return when (quest.questNameInstance) {
            QuestName.ClearBarbarianCamp -> getBarbarianEncampmentForQuest() != null
            QuestName.Route -> isRouteQuestValid(challenger)
            QuestName.ConnectResource -> getResourceForQuest(challenger) != null
            QuestName.ConstructWonder -> getWonderToBuildForQuest(challenger) != null
            QuestName.GreatPerson -> getGreatPersonForQuest(challenger) != null
            QuestName.FindPlayer -> getCivilizationToFindForQuest(challenger) != null
            QuestName.FindNaturalWonder -> getNaturalWonderToFindForQuest(challenger) != null
            QuestName.PledgeToProtect -> getMostRecentBully() != null && challenger !in civ.cityStateFunctions.getProtectorCivs()
            QuestName.GiveGold -> getMostRecentBully() != null
            QuestName.DenounceCiv -> isDenounceCivQuestValid(challenger, getMostRecentBully())
            QuestName.SpreadReligion -> {
                val playerReligion = civ.gameInfo.religions.values.firstOrNull { it.foundingCiv == challenger && it.isMajorReligion() }?.name
                playerReligion != null && civ.getCapital()!!.religion.getMajorityReligion()?.name != playerReligion
            }
            QuestName.ConquerCityState -> getCityStateTarget(challenger) != null && civ.cityStatePersonality != CityStatePersonality.Friendly
            QuestName.BullyCityState -> getCityStateTarget(challenger) != null
            QuestName.ContestFaith -> civ.gameInfo.isReligionEnabled()
            else -> true
        }
    }

    @Readonly
    private fun isRouteQuestValid(challenger: Civilization): Boolean {
        if (challenger.cities.isEmpty()) return false
        if (challenger.isCapitalConnectedToCity(civ.getCapital()!!)) return false
        val capital = civ.getCapital() ?: return false
        val capitalTile = capital.getCenterTile()
        return challenger.cities.any {
            it.getCenterTile().getContinent() == capitalTile.getContinent() &&
            it.getCenterTile().aerialDistanceTo(capitalTile) <= 7
        }
    }

    @Readonly
    private fun isDenounceCivQuestValid(challenger: Civilization, mostRecentBully: String?): Boolean {
        return mostRecentBully != null
            && challenger.knows(mostRecentBully)
            && !challenger.getDiplomacyManager(mostRecentBully)!!.hasFlag(DiplomacyFlags.Denunciation)
            && challenger.getDiplomacyManager(mostRecentBully)!!.diplomaticStatus != DiplomaticStatus.War
            && !( challenger.playerType == PlayerType.Human
            && civ.gameInfo.getCivilization(mostRecentBully).playerType == PlayerType.Human)
    }

    /** Returns true if the [assignedQuest] is successfully completed */
    @Readonly
    private fun isComplete(assignedQuest: AssignedQuest): Boolean {
        val assignee = civ.gameInfo.getCivilization(assignedQuest.assignee)
        return when (assignedQuest.questNameInstance) {
            QuestName.Route -> assignee.isCapitalConnectedToCity(civ.getCapital()!!)
            QuestName.ConnectResource -> assignee.detailedCivResources.map { it.resource }.contains(ruleset.tileResources[assignedQuest.data1])
            QuestName.ConstructWonder -> assignee.cities.any { it.cityConstructions.isBuilt(assignedQuest.data1) }
            QuestName.GreatPerson -> assignee.units.getCivGreatPeople().any { it.baseUnit.getReplacedUnit(ruleset).name == assignedQuest.data1 }
            QuestName.FindPlayer -> assignee.hasMetCivTerritory(civ.gameInfo.getCivilization(assignedQuest.data1))
            QuestName.FindNaturalWonder -> assignee.naturalWonders.contains(assignedQuest.data1)
            QuestName.PledgeToProtect -> assignee in civ.cityStateFunctions.getProtectorCivs()
            QuestName.DenounceCiv -> assignee.getDiplomacyManager(assignedQuest.data1)!!.hasFlag(DiplomacyFlags.Denunciation)
            QuestName.SpreadReligion -> civ.getCapital()!!.religion.getMajorityReligion() == civ.gameInfo.religions[assignedQuest.data2]
            else -> false
        }
    }

    /** Returns true if the [assignedQuest] request cannot be fulfilled anymore */
    @Readonly
    private fun isObsolete(assignedQuest: AssignedQuest): Boolean {
        val assignee = civ.gameInfo.getCivilization(assignedQuest.assignee)
        return when (assignedQuest.questNameInstance) {
            QuestName.ClearBarbarianCamp -> civ.gameInfo.tileMap[assignedQuest.data1.toInt(), assignedQuest.data2.toInt()].improvement != Constants.barbarianEncampment
            QuestName.ConstructWonder -> civ.gameInfo.getCities().any { it.civ != assignee && it.cityConstructions.isBuilt(assignedQuest.data1) }
            QuestName.FindPlayer -> civ.gameInfo.getCivilization(assignedQuest.data1).isDefeated()
            QuestName.ConquerCityState ->  civ.gameInfo.getCivilization(assignedQuest.data1).isDefeated()
            QuestName.BullyCityState ->  civ.gameInfo.getCivilization(assignedQuest.data1).isDefeated()
            QuestName.DenounceCiv ->  civ.gameInfo.getCivilization(assignedQuest.data1).isDefeated()
            else -> false
        }
    }

    /** Increments [assignedQuest.assignee][AssignedQuest.assignee] influence on [civ] and adds a [Notification] */
    private fun giveReward(assignedQuest: AssignedQuest) {
        val rewardInfluence = assignedQuest.getInfluence()
        val assignee = civ.gameInfo.getCivilization(assignedQuest.assignee)

        civ.getDiplomacyManager(assignedQuest.assignee)!!.addInfluence(rewardInfluence)
        if (rewardInfluence > 0)
            assignee.addNotification(
                "[${civ.civName}] rewarded you with [${rewardInfluence.toInt()}] influence for completing the [${assignedQuest.questName}] quest.",
                civ.getCapital()!!.location, NotificationCategory.Diplomacy, civ.civName, "OtherIcons/Quest"
            )

        // We may have received bonuses from city-state friend-ness or ally-ness
        for (city in civ.cities)
            city.cityStats.update()
    }

    /** Notifies the assignee of [assignedQuest] that the quest is now obsolete or expired.
     *  Optionally displays the [winners] of global quests. */
    private fun notifyExpired(assignedQuest: AssignedQuest, winners: List<AssignedQuest> = emptyList()) {
        val assignee = civ.gameInfo.getCivilization(assignedQuest.assignee)
        if (winners.isEmpty()) {
            assignee.addNotification(
                    "[${civ.civName}] no longer needs your help with the [${assignedQuest.questName}] quest.",
                    civ.getCapital()!!.location,
                NotificationCategory.Diplomacy, civ.civName, "OtherIcons/Quest")
        } else {
            assignee.addNotification(
                    "The [${assignedQuest.questName}] quest for [${civ.civName}] has ended. It was won by [${winners.joinToString { "{${it.assignee}}" }}].",
                    civ.getCapital()!!.location,
                NotificationCategory.Diplomacy, civ.civName, "OtherIcons/Quest")
        }
    }

    /** Returns the score for the [assignedQuest] */
    @Readonly
    private fun getScoreForQuest(assignedQuest: AssignedQuest): Int {
        val assignee = civ.gameInfo.getCivilization(assignedQuest.assignee)

        return when (assignedQuest.questNameInstance) {
            //quest total = civ total - the value at the time the quest started (which was stored in assignedQuest.data1)
            QuestName.ContestCulture -> assignee.totalCultureForContests - assignedQuest.data1.toInt()
            QuestName.ContestFaith -> assignee.totalFaithForContests - assignedQuest.data1.toInt()
            QuestName.ContestTech -> assignee.tech.getNumberOfTechsResearched() - assignedQuest.data1.toInt()
            else -> 0
        }
    }

    /** Evaluate a contest-type quest:
     *
     *  - Determines [winner(s)][winners] (as AssignedQuest instances, which name their assignee): Those whose score is the [maximum score][maxScore], possibly tied.
     *    and [losers]: all other [assignedQuests] matching parameter `questName`.
     *  - Called by the UI via [getScoreStringForGlobalQuest] before a Contest is resolved to display who currently leads,
     *    and by [handleGlobalQuest] to distribute rewards and notifications.
     *  @param questName filters [assignedQuests] by their [QuestName][AssignedQuest.questNameInstance]
     */
    inner class WinnersAndLosers(questName: QuestName) {
        val winners = mutableListOf<AssignedQuest>()
        val losers = mutableListOf<AssignedQuest>()
        var maxScore: Int = -1
            private set

        init {
            require(ruleset.quests[questName.value]!!.isGlobal())

            for (quest in getAssignedQuestsOfName(questName)) {
                val qScore = getScoreForQuest(quest)
                when {
                    qScore <= 0 -> Unit // no civ is a winner if their score is 0
                    qScore < maxScore ->
                        losers.add(quest)
                    qScore == maxScore ->
                        winners.add(quest)
                    else -> { // qScore > maxScore
                        losers.addAll(winners)
                        winners.clear()
                        winners.add(quest)
                        maxScore = qScore
                    }
                }
            }
        }
    }

    /** Returns a string to show "competition" status:
     *  - Show leading civ(s) (more than one only if tied for first place) with best score.
     *  - The assignee civ of the given [inquiringAssignedQuest] is shown for comparison if it is not among the leaders.
     *
     *  Assumes the result will be passed to [String.tr] - but parts are pretranslated to avoid nested brackets.
     *  Tied leaders are separated by ", " - translators cannot influence this, sorry.
     *  @param inquiringAssignedQuest Determines ["type"][AssignedQuest.questNameInstance] to find all competitors in [assignedQuests] and [viewing civ][AssignedQuest.assignee].
     */
    @Readonly
    fun getScoreStringForGlobalQuest(inquiringAssignedQuest: AssignedQuest): String {
        require(inquiringAssignedQuest.assigner == civ.civName)
        require(inquiringAssignedQuest.isGlobal())

        val scoreDescriptor = when (inquiringAssignedQuest.questNameInstance) {
            QuestName.ContestCulture -> "Culture"
            QuestName.ContestFaith -> "Faith"
            QuestName.ContestTech -> "Technologies"
            else -> return "" //This handles global quests which aren't a competition, like invest
        }

        // Get list of leaders with leading score (the losers aren't used here)
        val evaluation = WinnersAndLosers(inquiringAssignedQuest.questNameInstance)
        if (evaluation.winners.isEmpty())   //Only show leaders if there are some
            return ""

        val listOfLeadersAsTranslatedString = evaluation.winners.joinToString(separator = ", ") { it.assignee.tr() }
        @Pure fun getScoreString(name: String, score: Int) = "[$name] with [$score] [$scoreDescriptor]".tr()
        val leadersString = getScoreString(listOfLeadersAsTranslatedString, evaluation.maxScore)

        if (inquiringAssignedQuest in evaluation.winners)
            return "Current leader(s): [$leadersString]"

        val yourScoreString = getScoreString(inquiringAssignedQuest.assignee, getScoreForQuest(inquiringAssignedQuest))
        return "Current leader(s): [$leadersString], you: [$yourScoreString]"
    }

    /**
     * Gets notified a barbarian camp in [location] has been cleared by [civInfo].
     * Since [QuestName.ClearBarbarianCamp] is a global quest, it could have been assigned to
     * multiple civilizations, so after this notification all matching quests are removed.
     */
    fun barbarianCampCleared(civInfo: Civilization, location: Vector2) {
        val matchingQuests = getAssignedQuestsOfName(QuestName.ClearBarbarianCamp)
                .filter { it.data1.toInt() == location.x.toInt() && it.data2.toInt() == location.y.toInt() }

        val winningQuest = matchingQuests.filter { it.assignee == civInfo.civName }.firstOrNull()
        if (winningQuest != null)
            giveReward(winningQuest)

        assignedQuests.removeAll(matchingQuests)
    }

    /**
     * Gets notified the city state [cityState] was just conquered by [attacker].
     */
    fun cityStateConquered(cityState: Civilization, attacker: Civilization) {
        val matchingQuests = getAssignedQuestsOfName(QuestName.ConquerCityState)
            .filter { it.data1 == cityState.civName && it.assignee == attacker.civName }

        for (quest in matchingQuests)
            giveReward(quest)

        assignedQuests.removeAll(matchingQuests)
    }

    /**
     * Gets notified the city state [cityState] was just bullied by [bully].
     */
    fun cityStateBullied(cityState: Civilization, bully: Civilization) {
        val matchingQuests = getAssignedQuestsOfName(QuestName.BullyCityState)
            .filter { it.data1 == cityState.civName && it.assignee == bully.civName}

        for (quest in matchingQuests)
            giveReward(quest)

        assignedQuests.removeAll(matchingQuests)

        // What idiots haha oh wait that's us
        if (civ != cityState) return

        // Revoke most quest types from the bully
        val revokedQuests = getAssignedQuestsFor(bully.civName)
            .filter { it.isIndividual() || it.questNameInstance == QuestName.Invest }
            .toList()
        assignedQuests.removeAll(revokedQuests)
        if (revokedQuests.isEmpty()) return
        bully.addNotification("[${civ.civName}] cancelled the quests they had given you because you demanded tribute from them.",
            DiplomacyAction(civ),
            NotificationCategory.Diplomacy, civ.civName, "OtherIcons/Quest")
    }

    /** Gets notified when we are attacked, for war with major pseudo-quest */
    fun wasAttackedBy(attacker: Civilization) {
        // Set target number units to kill
        val totalMilitaryUnits = attacker.units.getCivUnits().count { !it.isCivilian() }
        val unitsToKill = (totalMilitaryUnits / 4).coerceAtMost(3)
        unitsToKillForCiv[attacker.civName] = unitsToKill

        // Ask for assistance
        val location = civ.getCapital(firstCityIfNoCapital = true)?.location
        for (thirdCiv in civ.getKnownCivs()) {
            if (!thirdCiv.isMajorCiv() || thirdCiv.isDefeated() || thirdCiv.isAtWarWith(civ))
                continue
            notifyAskForAssistance(thirdCiv, attacker.civName, unitsToKill, location)
        }
    }

    private fun notifyAskForAssistance(assignee: Civilization, attackerName: String, unitsToKill: Int, location: Vector2?) {
        if (attackerName == assignee.civName) return  // No "Hey Bob help us against Bob"
        val message = "[${civ.civName}] is being attacked by [$attackerName]!" +
            // Space relevant in template!
            " Kill [$unitsToKill] of the attacker's military units and they will be immensely grateful."
        // Note: that LocationAction pseudo-constructor is able to filter out null location(s), no need for `if`
        assignee.addNotification(message, LocationAction(location), NotificationCategory.Diplomacy, civ.civName, "OtherIcons/Quest")
    }

    /** Gets notified when [killed]'s military unit was killed by [killer], for war with major pseudo-quest */
    fun militaryUnitKilledBy(killer: Civilization, killed: Civilization) {
        if (!isWarWithMajorActive(killed)) return

        // No credit if we're at war or haven't met
        if (!civ.knows(killer) || civ.isAtWarWith(killer))  return

        // Make the map if we haven't already
        val unitsKilledFromCivEntry = unitsKilledFromCiv.getOrPut(killed.civName) { HashMap() }

        // Update kill count
        val updatedKillCount = 1 + (unitsKilledFromCivEntry[killer.civName] ?: 0)
        unitsKilledFromCivEntry[killer.civName] = updatedKillCount

        // Quest complete?
        if (updatedKillCount >= unitsToKillForCiv[killed.civName]!!) {
            killer.addNotification("[${civ.civName}] is deeply grateful for your assistance in the war against [${killed.civName}]!",
                DiplomacyAction(civ), NotificationCategory.Diplomacy, civ.civName, "OtherIcons/Quest")
            civ.getDiplomacyManager(killer)!!.addInfluence(100f) // yikes
            endWarWithMajorQuest(killed)
        }
    }

    /** Called when a major civ meets the city-state for the first time. Mainly for war with major pseudo-quest. */
    fun justMet(otherCiv: Civilization) {
        if (unitsToKillForCiv.isEmpty()) return
        val location = civ.getCapital(firstCityIfNoCapital = true)?.location
        for ((attackerName, unitsToKill) in unitsToKillForCiv)
            notifyAskForAssistance(otherCiv, attackerName, unitsToKill, location)
    }

    /** Ends War with Major pseudo-quests that aren't relevant any longer */
    private fun tryEndWarWithMajorQuests() {
        for (attacker in unitsToKillForCiv.keys.map { civ.gameInfo.getCivilization(it) }) {
            if (civ.isDefeated()
                || attacker.isDefeated()
                || !civ.isAtWarWith(attacker)) {
                    endWarWithMajorQuest(attacker)
            }
        }
    }

    private fun endWarWithMajorQuest(attacker: Civilization) {
        for (thirdCiv in civ.getKnownCivs().filterNot { it.isDefeated() || it == attacker || it.isAtWarWith(civ) }) {
            if (unitsKilledSoFar(attacker, thirdCiv) >= unitsToKill(attacker)) // Don't show the notification to the one who won the quest
                continue
            thirdCiv.addNotification("[${civ.civName}] no longer needs your assistance against [${attacker.civName}].",
                DiplomacyAction(civ), NotificationCategory.Diplomacy, civ.civName, "OtherIcons/Quest")
        }
        unitsToKillForCiv.remove(attacker.civName)
        unitsKilledFromCiv.remove(attacker.civName)
    }

    @Readonly fun isWarWithMajorActive(target: Civilization): Boolean = unitsToKillForCiv.containsKey(target.civName)

    @Readonly fun unitsToKill(target: Civilization): Int = unitsToKillForCiv[target.civName] ?: 0

    @Readonly
    fun unitsKilledSoFar(target: Civilization, viewingCiv: Civilization): Int {
        val killMap = unitsKilledFromCiv[target.civName] ?: return 0
        return killMap[viewingCiv.civName] ?: 0
    }

    /**
     * Gets notified when given gold by [donorCiv].
     */
    fun receivedGoldGift(donorCiv: Civilization) {
        val matchingQuests = getAssignedQuestsOfName(QuestName.GiveGold)
            .filter { it.assignee == donorCiv.civName }

        for (quest in matchingQuests)
            giveReward(quest)

        assignedQuests.removeAll(matchingQuests)
    }

    /**
     * Returns the weight of the [questName], depends on city state trait and personality
     */
    @Readonly
    private fun getQuestWeight(questName: String): Float {
        var weight = 1f
        val quest = ruleset.quests[questName] ?: return 0f

        val personalityWeight = quest.weightForCityStateType[civ.cityStatePersonality.name]
        if (personalityWeight != null) weight *= personalityWeight

        val traitWeight = quest.weightForCityStateType[civ.cityStateType.name]
        if (traitWeight != null) weight *= traitWeight
        return weight
    }

    //region get-quest-target
    /**
     * Returns a random [Tile] containing a Barbarian encampment within 8 tiles of [civ]
     * to be destroyed
     */
    @Readonly
    private fun getBarbarianEncampmentForQuest(): Tile? {
        val encampments = civ.getCapital()!!.getCenterTile().getTilesInDistance(8)
                .filter { it.improvement == Constants.barbarianEncampment }.toList()

        return encampments.randomOrNull()
    }

    /**
     * Returns a random resource to be connected to the [challenger]'s trade route as a quest.
     * The resource must be a [ResourceType.Luxury] or [ResourceType.Strategic], must not be owned
     * by the [civ] and the [challenger], and must be viewable by the [challenger];
     * if none exists, it returns null.
     */
    @Readonly
    private fun getResourceForQuest(challenger: Civilization): TileResource? {
        val ownedByCityStateResources = civ.detailedCivResources.map { it.resource }
        val ownedByMajorResources = challenger.detailedCivResources.map { it.resource }

        val resourcesOnMap = civ.gameInfo.tileMap.values.asSequence().mapNotNull { it.resource }.distinct()
        val viewableResourcesForChallenger = resourcesOnMap.map { ruleset.tileResources[it]!! }
                .filter { challenger.tech.isRevealed(it) }

        val notOwnedResources = viewableResourcesForChallenger.filter {
            it.resourceType != ResourceType.Bonus &&
                    !ownedByCityStateResources.contains(it) &&
                    !ownedByMajorResources.contains(it)
        }.toList()

        return notOwnedResources.randomOrNull()
    }

    @Readonly
    private fun getWonderToBuildForQuest(challenger: Civilization): Building? {
        @Readonly fun isMoreThanAQuarterDone(city: City, buildingName: String) =
            city.cityConstructions.getWorkDone(buildingName) * 3 > city.cityConstructions.getRemainingWork(buildingName)
        val wonders = ruleset.buildings.values
                .filter { building ->
                    // Buildable wonder
                    building.isWonder
                    && challenger.tech.isResearched(building)
                    // Can't be disabled
                    && !building.isUnavailableBySettings(civ.gameInfo)
                    // Can't be a unique wonder
                    && building.uniqueTo == null
                    // Big loop last: Exists or more than 25% built anywhere
                    && civ.gameInfo.getCities().none { it.cityConstructions.isBuilt(building.name) || isMoreThanAQuarterDone(it, building.name) }
                }

        return wonders.randomOrNull()
    }

    /**
     * Returns a random Natural Wonder not yet discovered by [challenger], or the [civ] dispatching the quest.
     *
     * @param challenger The Civilization that will be receiving the quest.
     */
    @Readonly
    private fun getNaturalWonderToFindForQuest(challenger: Civilization): String? =
        civ.gameInfo.tileMap.naturalWonders
            .subtract(challenger.naturalWonders)
            .subtract(civ.naturalWonders)
            .randomOrNull()

    /**
     * Returns a Great Person [BaseUnit] that is not owned by both the [challenger] and the [civ]
     */
    @Readonly
    private fun getGreatPersonForQuest(challenger: Civilization): BaseUnit? {
        val ruleset = ruleset // omit if the accessor should be converted to a transient field

        val existingGreatPeople =
            // concatenate sequences of existing GP for the challenger (a player) and our `civ` (the quest-giving city-state)
            (challenger.units.getCivGreatPeople() + civ.units.getCivGreatPeople())
            .map { it.baseUnit.getReplacedUnit(ruleset) }.toSet()

        val greatPeople = challenger.greatPeople.getGreatPeople()
                .map { it.getReplacedUnit(ruleset) }
                .distinct()
                // The hidden test is already done by getGreatPeople for the civ-specific units,
                // repeat for the replaced one we'll be asking for
                .filterNot { it in existingGreatPeople || it.isUnavailableBySettings(civ.gameInfo) }
                .toList()

        return greatPeople.randomOrNull()
    }

    /**
     * Returns a random [Civilization] (major) that [challenger] has met, but whose territory he
     * cannot see; if none exists, it returns null.
     */
    @Readonly
    private fun getCivilizationToFindForQuest(challenger: Civilization): Civilization? {
        val civilizationsToFind = challenger.getKnownCivs()
            .filter { it.isAlive() && it.isMajorCiv() && !challenger.hasMetCivTerritory(it) }
            .toList()

        return civilizationsToFind.randomOrNull()
    }

    /**
     * Returns a city-state [Civilization] that [civ] wants to target for hostile quests
     */
    @Readonly
    private fun getCityStateTarget(challenger: Civilization): Civilization? {
        val closestProximity = civ.gameInfo.getAliveCityStates()
            .mapNotNull { civ.proximity[it.civName] }.filter { it != Proximity.None }.minByOrNull { it.ordinal }

        if (closestProximity == null || closestProximity == Proximity.Distant) // None close enough
            return null

        val validTargets = civ.getKnownCivs().filter { it.isCityState && challenger.knows(it)
                && civ.proximity[it.civName] == closestProximity }

        return validTargets.toList().randomOrNull()
    }

    /** Returns a [Civilization] of the civ that most recently bullied [civ].
     *  Note: forgets after 20 turns has passed! */
    @Readonly
    private fun getMostRecentBully(): String? {
        val bullies = civ.diplomacy.values.filter { it.hasFlag(DiplomacyFlags.Bullied) }
        return bullies.maxByOrNull { it.getFlag(DiplomacyFlags.Bullied) }?.otherCivName
    }

    //endregion
}


class AssignedQuest(
    val questName: String = "",
    val assigner: String = "",
    val assignee: String = "",
    val assignedOnTurn: Int = 0,
    val data1: String = "",
    val data2: String = ""
) : IsPartOfGameInfoSerialization {

    @Transient
    private lateinit var gameInfo: GameInfo

    @Transient
    private lateinit var questObject: Quest

    val questNameInstance get() = questObject.questNameInstance

    internal fun setTransients(gameInfo: GameInfo, quest: Quest? = null) {
        this.gameInfo = gameInfo
        questObject = quest ?: gameInfo.ruleset.quests[questName]!!
    }

    @Readonly fun isIndividual(): Boolean = !isGlobal()
    @Readonly fun isGlobal(): Boolean = questObject.isGlobal()
    @Suppress("MemberVisibilityCanBePrivate")
    @Readonly fun doesExpire(): Boolean = questObject.duration > 0
    @Readonly fun isExpired(): Boolean = doesExpire() && getRemainingTurns() == 0
    @Suppress("MemberVisibilityCanBePrivate")
    @Readonly fun getDuration(): Int = (gameInfo.speed.modifier * questObject.duration).toInt()
    @Readonly fun getRemainingTurns(): Int = (assignedOnTurn + getDuration() - gameInfo.turns).coerceAtLeast(0)
    @Readonly fun getInfluence() = questObject.influence

    @Readonly fun getDescription(): String = questObject.description.fillPlaceholders(data1)

    fun onClickAction() {
        when (questNameInstance) {
            QuestName.ClearBarbarianCamp -> {
                GUI.resetToWorldScreen()
                GUI.getMap().setCenterPosition(Vector2(data1.toFloat(), data2.toFloat()), selectUnit = false)
            }
            QuestName.Route -> {
                GUI.resetToWorldScreen()
                GUI.getMap().setCenterPosition(gameInfo.getCivilization(assigner).getCapital()!!.location, selectUnit = false)
            }
            else -> Unit
        }
    }
}
