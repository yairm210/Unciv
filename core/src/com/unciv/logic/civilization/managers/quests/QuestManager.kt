package com.unciv.logic.civilization.managers.quests

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.DiplomacyAction
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.Notification  // for Kdoc
import com.unciv.logic.civilization.NotificationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.Proximity
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.managers.quests.QuestTargetHelpers.getRandom
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.QuestName
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toPercent
import com.unciv.utils.randomWeighted
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import kotlin.collections.iterator

@Suppress("ConvertArgumentToSet")  // Flags all assignedQuests.removeAll(List) - not worth it
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

    ////////// Serialized fields //////////

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

    ////////// Transients, clone, etc //////////

    /** Civilization object holding and dispatching quests */
    @Transient
    private lateinit var civ: Civilization

    /** Readability helper to access the Ruleset through [civ] */
    private val ruleset get() = civ.gameInfo.ruleset

    fun clone(): QuestManager {
        val toReturn = QuestManager()
        toReturn.globalQuestCountdown = globalQuestCountdown
        toReturn.individualQuestCountdown.putAll(individualQuestCountdown)
        toReturn.assignedQuests.addAll(assignedQuests.map { it.clone() })
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

    /** Debug visualization only */
    override fun toString() = if (!::civ.isInitialized) "(uninitialized)"
        else "${civ.civID}:$assignedQuests"

    ////////// Public //////////

    /** Returns true if [civ] have active quests for [challenger] */
    @Readonly fun haveQuestsFor(challenger: Civilization): Boolean = getAssignedQuestsFor(challenger).any()

    /** Access all assigned Quests for [civName] */
    @Suppress("unused")
    @Readonly fun getAssignedQuestsFor(civName: String) =
        assignedQuests.asSequence().filter { it.assignee == civName }

    /** Access all assigned Quests for [civ] */
    @Readonly fun getAssignedQuestsFor(civ: Civilization) =
        assignedQuests.asSequence().filter { it.assigneeCiv == civ }
    /** Returns true if [civ] has asked anyone to conquer [target] */
    @Readonly fun wantsDead(target: String): Boolean = getAssignedQuestsOfName(QuestName.ConquerCityState).any { it.data1 == target }

    /** Returns the influence multiplier for [donor] from an Investment quest that [civ] might have (assumes only one) */
    @Readonly
    fun getInvestmentMultiplier(donor: Civilization): Float {
        val investmentQuest = getAssignedQuestsOfName(QuestName.Invest).firstOrNull { it.assigneeCiv == donor }
            ?: return 1f
        return investmentQuest.data1.toPercent()
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

    fun handleIndividualQuests() {
        assignedQuests.removeAll { it.isIndividual() && handleIndividualQuest(it) }
    }

    fun handleObsoleteGlobalQuests() {
        val iterator = assignedQuests.iterator()
        for (assignedQuest in iterator) {
            if (!isObsolete(assignedQuest)) continue
            notifyExpired(assignedQuest)
            iterator.remove()
        }
    }

    ////////// Internal Implementation //////////

    private fun Civilization.addQuestNotification(text: String, actions: Iterable<NotificationAction>) =
        addNotification(text, actions, NotificationCategory.Diplomacy, civ.civName, NotificationIcon.Quest)
    private fun Civilization.addDiplomacyNotification(text: String) =
        addQuestNotification(text, listOf(DiplomacyAction(civ)))
    private fun Civilization.addQuestNotification(text: String, location: HexCoord? = civ.getCapital()!!.location) =
        // Note: that LocationAction pseudo-constructor is able to filter out null location(s), no need for `if`
        addQuestNotification(text, LocationAction(location).asIterable())

    /** Access all assigned Quests of "type" [questName] */
    // Note if we decide to cache an index of these (such as `assignedQuests.groupBy { it.questNameInstance }`), this accessor would simplify the transition
    @Readonly
    private fun getAssignedQuestsOfName(questName: QuestName) =
        assignedQuests.asSequence().filter { it.questNameInstance == questName }

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

        val rng = civ.state.stateBasedRandom("QuestManager.seedGlobalQuestCooldown")
        val countdown =
                if (civ.gameInfo.turns == GLOBAL_QUEST_FIRST_POSSIBLE_TURN)
                    rng.nextInt(GLOBAL_QUEST_FIRST_POSSIBLE_TURN_RAND)
                else
                    GLOBAL_QUEST_MIN_TURNS_BETWEEN + rng.nextInt(GLOBAL_QUEST_RAND_TURNS_BETWEEN)

        globalQuestCountdown = (countdown * civ.gameInfo.speed.modifier).toInt()
    }

    private fun seedIndividualQuestsCountdowns() {
        if (civ.gameInfo.turns < INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN)
            return

        @Suppress("DEPRECATION") // At the time of writing this. the ReplaceWith is the deprecated function itself
        val majorCivs = civ.gameInfo.getAliveMajorCivs()
        for (majorCiv in majorCivs)
            if (!individualQuestCountdown.containsKey(majorCiv.civID) || individualQuestCountdown[majorCiv.civID] == UNSET)
                seedIndividualQuestsCountdown(majorCiv)
    }

    private fun seedIndividualQuestsCountdown(challenger: Civilization) {
        val rng = civ.state.copy(otherCiv = challenger).stateBasedRandom("QuestManager.seedIndividualQuestCooldown")
        val countdown: Int =
                if (civ.gameInfo.turns == INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN)
                    rng.nextInt(INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN_RAND)
                else
                    INDIVIDUAL_QUEST_MIN_TURNS_BETWEEN + rng.nextInt(
                        INDIVIDUAL_QUEST_RAND_TURNS_BETWEEN
                    )

        individualQuestCountdown[challenger.civID] = (countdown * civ.gameInfo.speed.modifier).toInt()
    }

    // Readability helper - No asSequence(): call frequency * data size is small
    @Readonly private fun getQuests(predicate: (Quest) -> Boolean) = ruleset.quests.values.filter(predicate)

    private fun tryStartNewGlobalQuest() {
        if (globalQuestCountdown != 0)
            return
        if (assignedQuests.count { it.isGlobal() } >= GLOBAL_QUEST_MAX_ACTIVE)
            return

        @Suppress("DEPRECATION") // We want a Sequence
        val majorCivs = civ.getKnownCivs().filter { it.isMajorCiv() && !it.isAtWarWith(civ) } // A Sequence - fine because the count below can be different for each Quest
        @Readonly fun Quest.isAssignable() = majorCivs.count { civ -> isQuestValid(this, civ) } >= minimumCivs
        val assignableQuests = getQuests {
            it.isGlobal() && it.isAssignable()
        }

        if (assignableQuests.isNotEmpty()) {
            val quest = assignableQuests.randomWeighted(civ.getRandom()) { getQuestWeight(it.name) }
            @Suppress("DEPRECATION") // At the time of writing this. the ReplaceWith is the deprecated function itself
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

            if (getAssignedQuestsFor(challenger).count { it.isIndividual() } >= INDIVIDUAL_QUEST_MAX_ACTIVE)
                continue

            val assignableQuests = getQuests { it.isIndividual() && isQuestValid(it, challenger) }

            if (assignableQuests.isNotEmpty()) {
                val quest = assignableQuests.randomWeighted(civ.getRandom(challenger)) { getQuestWeight(it.name) }
                val assignees = arrayListOf(challenger)

                assignNewQuest(quest, assignees)
            }
        }
    }

    private fun tryBarbarianInvasion() {
        if ((civ.getTurnsTillCallForBarbHelp() == null || civ.getTurnsTillCallForBarbHelp() == 0)
            && civ.cityStateFunctions.getNumThreateningBarbarians() >= 2) {

            civ.forEachKnownCiv {
                if (it.isMajorCiv()
                    && it.isAlive()
                    && !it.isAtWarWith(civ)
                    && it.getProximity(civ) <= Proximity.Far
                ) return@forEachKnownCiv
                it.addNotification(
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
            !canAssignAQuestTo(it.assigneeCiv) }
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

    /** If quest is complete, it gives the influence reward to the player.
     *  Returns true if the quest can be removed (is either complete, obsolete or expired) */
    private fun handleIndividualQuest(assignedQuest: AssignedQuest): Boolean {
        val assignee = assignedQuest.assigneeCiv

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
            val (data1, data2) = quest.questNameInstance.getAssignedQuestData(civ, quest, assignee)
            val newQuest = AssignedQuest(
                gameInfo = civ.gameInfo,
                quest = quest,
                assigner = civ,
                assignee = assignee,
                assignedOnTurn = turn,
                data1 = data1,
                data2 = data2
            )
            val notificationActions = quest.questNameInstance.getNotificationActions(civ, newQuest)

            assignedQuests.add(newQuest)
            if (quest.isIndividual())
                individualQuestCountdown[assignee.civID] = UNSET

            assignee.addQuestNotification("[${civ.civName}] assigned you a new quest: [${quest.name}].", notificationActions)
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
    private fun isQuestValid(quest: Quest, challenger: Civilization) = when {
        !canAssignAQuestTo(challenger) ->
            false
        getAssignedQuestsOfName(quest.questNameInstance).any { it.assigneeCiv == challenger } ->
            false
        quest.isIndividual() && civ.getDiplomacyManager(challenger)!!.hasFlag(DiplomacyFlags.Bullied) ->
            false
        else -> quest.questNameInstance.isValid(civ, challenger)
    }

    /** Returns true if the [assignedQuest] is successfully completed */
    @Readonly
    private fun isComplete(assignedQuest: AssignedQuest) =
        assignedQuest.questNameInstance.isComplete(civ, assignedQuest)

    /** Returns true if the [assignedQuest] request cannot be fulfilled anymore */
    @Readonly
    private fun isObsolete(assignedQuest: AssignedQuest) =
        assignedQuest.questNameInstance.isObsolete(civ, assignedQuest)

    /** Increments [assignedQuest.assignee][AssignedQuest.assignee] influence on [civ] and adds a [Notification] */
    private fun giveReward(assignedQuest: AssignedQuest) {
        val rewardInfluence = assignedQuest.getInfluence()
        val assignee = assignedQuest.assigneeCiv

        civ.getDiplomacyManager(assignee)!!.addInfluence(rewardInfluence)
        if (rewardInfluence > 0)
            assignee.addQuestNotification("[${civ.civName}] rewarded you with [${rewardInfluence.toInt()}] influence for completing the [${assignedQuest.questName}] quest.")

        // We may have received bonuses from city-state friend-ness or ally-ness
        for (city in civ.cities)
            city.cityStats.update()
    }

    /** Notifies the assignee of [assignedQuest] that the quest is now obsolete or expired.
     *  Optionally displays the [winners] of global quests. */
    private fun notifyExpired(assignedQuest: AssignedQuest, winners: List<AssignedQuest> = emptyList()) {
        val assignee = assignedQuest.assigneeCiv
        if (winners.isEmpty()) {
            assignee.addQuestNotification("[${civ.civName}] no longer needs your help with the [${assignedQuest.questName}] quest.")
        } else {
            assignee.addQuestNotification("The [${assignedQuest.questName}] quest for [${civ.civName}] has ended. It was won by [${winners.joinToString { "{${it.assigneeCiv.civName}}" }}].")
        }
    }

    /** Returns the score for the [assignedQuest] */
    @Readonly
    private fun getScoreForQuest(assignedQuest: AssignedQuest): Int {
        val assignee = assignedQuest.assigneeCiv

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
    private inner class WinnersAndLosers(questName: QuestName) {
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
        require(inquiringAssignedQuest.assignerCiv == civ)
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

        val listOfLeadersAsTranslatedString = evaluation.winners.joinToString(separator = ", ") { it.assigneeCiv.civName.tr() }
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
    fun barbarianCampCleared(civInfo: Civilization, location: HexCoord) {
        val matchingQuests = getAssignedQuestsOfName(QuestName.ClearBarbarianCamp)
                .filter { it.data1.toInt() == location.x && it.data2.toInt() == location.y }

        val winningQuest = matchingQuests.firstOrNull { it.assigneeCiv == civInfo }
        if (winningQuest != null)
            giveReward(winningQuest)

        assignedQuests.removeAll(matchingQuests)
    }

    /**
     * Gets notified the city state [cityState] was just conquered by [attacker].
     */
    fun cityStateConquered(cityState: Civilization, attacker: Civilization) {
        val matchingQuests = getAssignedQuestsOfName(QuestName.ConquerCityState)
            .filter { it.data1 == cityState.civID && it.assigneeCiv == attacker }

        for (quest in matchingQuests)
            giveReward(quest)

        assignedQuests.removeAll(matchingQuests)
    }

    /**
     * Gets notified the city state [cityState] was just bullied by [bully].
     */
    fun cityStateBullied(cityState: Civilization, bully: Civilization) {
        val matchingQuests = getAssignedQuestsOfName(QuestName.BullyCityState)
            .filter { it.data1 == cityState.civID && it.assigneeCiv == bully}

        for (quest in matchingQuests)
            giveReward(quest)

        assignedQuests.removeAll(matchingQuests)

        // What idiots haha oh wait that's us
        if (civ != cityState) return

        // Revoke most quest types from the bully
        val revokedQuests = getAssignedQuestsFor(bully)
            .filter { it.isIndividual() || it.questNameInstance == QuestName.Invest }
            .toList()
        assignedQuests.removeAll(revokedQuests)
        if (revokedQuests.isEmpty()) return
        bully.addDiplomacyNotification("[${civ.civName}] cancelled the quests they had given you because you demanded tribute from them.")
    }

    /** Gets notified when we are attacked, for war with major pseudo-quest */
    fun wasAttackedBy(attacker: Civilization) {
        // Set target number units to kill
        val totalMilitaryUnits = attacker.units.getCivUnits().count { !it.isCivilian() }
        val unitsToKill = (totalMilitaryUnits / 4).coerceAtMost(3)
        unitsToKillForCiv[attacker.civID] = unitsToKill

        // Ask for assistance
        val location = civ.getCapital(firstCityIfNoCapital = true)?.location
        civ.forEachKnownCiv { thirdCiv ->
            if (!thirdCiv.isMajorCiv() || thirdCiv.isDefeated() || thirdCiv.isAtWarWith(civ))
                return@forEachKnownCiv
            notifyAskForAssistance(thirdCiv, attacker.civID, unitsToKill, location?.toHexCoord())
        }
    }

    private fun notifyAskForAssistance(assignee: Civilization, attackerName: String, unitsToKill: Int, location: HexCoord?) {
        if (attackerName == assignee.civID) return  // No "Hey Bob help us against Bob"
        val message = "[${civ.civName}] is being attacked by [$attackerName]!" +
            // Space relevant in template!
            " Kill [$unitsToKill] of the attacker's military units and they will be immensely grateful."
        assignee.addQuestNotification(message, location)
    }

    /** Gets notified when [killed]'s military unit was killed by [killer], for war with major pseudo-quest */
    fun militaryUnitKilledBy(killer: Civilization, killed: Civilization) {
        if (!isWarWithMajorActive(killed)) return

        // No credit if we're at war or haven't met
        if (!civ.knows(killer) || civ.isAtWarWith(killer))  return

        // Make the map if we haven't already
        val unitsKilledFromCivEntry = unitsKilledFromCiv.getOrPut(killed.civID) { HashMap() }

        // Update kill count
        val updatedKillCount = 1 + (unitsKilledFromCivEntry[killer.civID] ?: 0)
        unitsKilledFromCivEntry[killer.civID] = updatedKillCount

        // Quest complete?
        if (updatedKillCount >= unitsToKillForCiv[killed.civID]!!) {
            killer.addDiplomacyNotification("[${civ.civName}] is deeply grateful for your assistance in the war against [${killed.civName}]!")
            civ.getDiplomacyManager(killer)!!.addInfluence(100f) // yikes
            endWarWithMajorQuest(killed)
        }
    }

    /** Called when a major civ meets the city-state for the first time. Mainly for war with major pseudo-quest. */
    fun justMet(otherCiv: Civilization) {
        if (unitsToKillForCiv.isEmpty()) return
        val location = civ.getCapital(firstCityIfNoCapital = true)?.location
        for ((attackerName, unitsToKill) in unitsToKillForCiv)
            notifyAskForAssistance(otherCiv, attackerName, unitsToKill, location?.toHexCoord())
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
        civ.forEachKnownCiv { thirdCiv ->
            if (thirdCiv.isDefeated() || thirdCiv == attacker || thirdCiv.isAtWarWith(civ))
                return@forEachKnownCiv
            if (unitsKilledSoFar(attacker, thirdCiv) >= unitsToKill(attacker)) // Don't show the notification to the one who won the quest
                return@forEachKnownCiv
            thirdCiv.addDiplomacyNotification("[${civ.civName}] no longer needs your assistance against [${attacker.civName}].")
        }
        unitsToKillForCiv.remove(attacker.civID)
        unitsKilledFromCiv.remove(attacker.civID)
    }

    @Readonly fun isWarWithMajorActive(target: Civilization): Boolean = unitsToKillForCiv.containsKey(target.civID)

    @Readonly fun unitsToKill(target: Civilization): Int = unitsToKillForCiv[target.civID] ?: 0

    @Readonly
    fun unitsKilledSoFar(target: Civilization, viewingCiv: Civilization): Int {
        val killMap = unitsKilledFromCiv[target.civID] ?: return 0
        return killMap[viewingCiv.civID] ?: 0
    }

    /**
     * Gets notified when given gold by [donorCiv].
     */
    fun receivedGoldGift(donorCiv: Civilization) {
        val matchingQuests = getAssignedQuestsOfName(QuestName.GiveGold)
            .filter { it.assigneeCiv == donorCiv }

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
}
