package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.QuestName
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.tr
import com.unciv.ui.utils.extensions.randomWeighted
import com.unciv.ui.utils.extensions.toPercent
import kotlin.math.max
import kotlin.random.Random

@Suppress("NON_EXHAUSTIVE_WHEN")  // Many when uses in here are much clearer this way
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
    lateinit var civInfo: CivilizationInfo

    /** List of active quests, both global and individual ones*/
    var assignedQuests: ArrayList<AssignedQuest> = ArrayList()

    /**  Number of turns left before starting new global quest */
    private var globalQuestCountdown: Int = UNSET

    /** Number of turns left before this city state can start a new individual quest */
    private var individualQuestCountdown: HashMap<String, Int> = HashMap()

    /** Target number of units to kill for this war, for war with major pseudo-quest */
    private var unitsToKillForCiv: HashMap<String, Int> = HashMap()

    /** For this attacker, number of units killed by each civ */
    private var unitsKilledFromCiv: HashMap<String, HashMap<String, Int>> = HashMap()

    /** Returns true if [civInfo] have active quests for [challenger] */
    fun haveQuestsFor(challenger: CivilizationInfo): Boolean = assignedQuests.any { it.assignee == challenger.civName }

    /** Returns true if [civInfo] has asked anyone to conquer [target] */
    fun wantsDead(target: String): Boolean = assignedQuests.any { it.questName == QuestName.ConquerCityState.value && it.data1 == target }

    /** Returns the influence multiplier for [donor] from a Investment quest that [civInfo] might have (assumes only one) */
    fun getInvestmentMultiplier(donor: String): Float {
        val investmentQuest = assignedQuests.firstOrNull { it.questName == QuestName.Invest.value && it.assignee == donor }
        return if (investmentQuest == null)
            1f
        else
            investmentQuest.data1.toPercent()
    }

    fun clone(): QuestManager {
        val toReturn = QuestManager()
        toReturn.globalQuestCountdown = globalQuestCountdown
        toReturn.individualQuestCountdown.putAll(individualQuestCountdown)
        toReturn.assignedQuests.addAll(assignedQuests)
        toReturn.unitsToKillForCiv.putAll(unitsToKillForCiv)
        for ((attacker, unitsKilled) in unitsKilledFromCiv) {
            toReturn.unitsKilledFromCiv[attacker] = HashMap()
            toReturn.unitsKilledFromCiv[attacker]!!.putAll(unitsKilled)
        }
        return toReturn
    }

    fun setTransients() {
        for (quest in assignedQuests)
            quest.gameInfo = civInfo.gameInfo
    }

    fun endTurn() {

        if (civInfo.isDefeated()) {
            assignedQuests.clear()
            individualQuestCountdown.clear()
            globalQuestCountdown = UNSET
            return
        }

        if (civInfo.cities.none()) return // don't assign quests until we have a city

        seedGlobalQuestCountdown()
        seedIndividualQuestsCountdown()

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
        if (civInfo.gameInfo.turns < GLOBAL_QUEST_FIRST_POSSIBLE_TURN)
            return

        if (globalQuestCountdown != UNSET)
            return

        val countdown =
                if (civInfo.gameInfo.turns == GLOBAL_QUEST_FIRST_POSSIBLE_TURN)
                    Random.nextInt(GLOBAL_QUEST_FIRST_POSSIBLE_TURN_RAND)
                else
                    GLOBAL_QUEST_MIN_TURNS_BETWEEN + Random.nextInt(GLOBAL_QUEST_RAND_TURNS_BETWEEN)

        globalQuestCountdown = (countdown * civInfo.gameInfo.speed.modifier).toInt()
    }

    private fun seedIndividualQuestsCountdown() {
        if (civInfo.gameInfo.turns < INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN)
            return

        val majorCivs = civInfo.gameInfo.getAliveMajorCivs()
        for (majorCiv in majorCivs)
            if (!individualQuestCountdown.containsKey(majorCiv.civName) || individualQuestCountdown[majorCiv.civName] == UNSET)
                seedIndividualQuestsCountdown(majorCiv)
    }

    private fun seedIndividualQuestsCountdown(challenger: CivilizationInfo) {
        val countdown: Int =
                if (civInfo.gameInfo.turns == INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN)
                    Random.nextInt(INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN_RAND)
                else
                    INDIVIDUAL_QUEST_MIN_TURNS_BETWEEN + Random.nextInt(INDIVIDUAL_QUEST_RAND_TURNS_BETWEEN)

        individualQuestCountdown[challenger.civName] = (countdown * civInfo.gameInfo.speed.modifier).toInt()
    }

    private fun tryStartNewGlobalQuest() {
        if (globalQuestCountdown != 0)
            return
        if (assignedQuests.count { it.isGlobal() } >= GLOBAL_QUEST_MAX_ACTIVE)
            return

        val globalQuests = civInfo.gameInfo.ruleSet.quests.values.filter { it.isGlobal() }
        val majorCivs = civInfo.getKnownCivs().filter { it.isMajorCiv() && !it.isAtWarWith(civInfo) }

        val assignableQuests = ArrayList<Quest>()
        for (quest in globalQuests) {
            val numberValidMajorCivs = majorCivs.count { civ -> isQuestValid(quest, civ) }
            if (numberValidMajorCivs >= quest.minimumCivs)
                assignableQuests.add(quest)
        }
        val weights = assignableQuests.map { getQuestWeight(it.name) }

        if (assignableQuests.isNotEmpty()) {
            val quest = assignableQuests.randomWeighted(weights)
            val assignees = civInfo.gameInfo.getAliveMajorCivs().filter { !it.isAtWarWith(civInfo) && isQuestValid(quest, it) }

            assignNewQuest(quest, assignees)
            globalQuestCountdown = UNSET
        }
    }

    private fun tryStartNewIndividualQuests() {
        for ((challengerName, countdown) in individualQuestCountdown) {
            val challenger = civInfo.gameInfo.getCivilization(challengerName)

            if (countdown != 0)
                return

            if (assignedQuests.count { it.assignee == challenger.civName && it.isIndividual() } >= INDIVIDUAL_QUEST_MAX_ACTIVE)
                return

            val assignableQuests = civInfo.gameInfo.ruleSet.quests.values.filter { it.isIndividual() && isQuestValid(it, challenger) }
            val weights = assignableQuests.map { getQuestWeight(it.name) }

            if (assignableQuests.isNotEmpty()) {
                val quest = assignableQuests.randomWeighted(weights)
                val assignees = arrayListOf(challenger)

                assignNewQuest(quest, assignees)
                break
            }
        }
    }

    private fun tryBarbarianInvasion() {
        if ((civInfo.getTurnsTillCallForBarbHelp() == null || civInfo.getTurnsTillCallForBarbHelp() == 0)
            && civInfo.cityStateFunctions.getNumThreateningBarbarians() >= 2) {

            for (otherCiv in civInfo.getKnownCivs().filter {
                    it.isMajorCiv()
                    && it.isAlive()
                    && !it.isAtWarWith(civInfo)
                    && it.getProximity(civInfo) <= Proximity.Far }) {
                otherCiv.addNotification("[${civInfo.civName}] is being invaded by Barbarians! Destroy Barbarians near their territory to earn Influence.",
                    civInfo.getCapital()!!.location, civInfo.civName, NotificationIcon.War)
            }
            civInfo.addFlag(CivFlags.TurnsTillCallForBarbHelp.name, 30)
        }
    }

    private fun handleGlobalQuests() {
        // Remove any participants that are no longer valid because of being dead or at war with the CS
        assignedQuests.removeAll { it.isGlobal() &&
            !canAssignAQuestTo(civInfo.gameInfo.getCivilization(it.assignee)) }
        val globalQuestsExpired = assignedQuests.filter { it.isGlobal() && it.isExpired() }.map { it.questName }.distinct()
        for (globalQuestName in globalQuestsExpired)
            handleGlobalQuest(globalQuestName)
    }

    private fun handleGlobalQuest(questName: String) {
        val quests = assignedQuests.filter { it.questName == questName }
        if (quests.isEmpty())
            return

        val topScore = quests.maxOf { getScoreForQuest(it) }
        val winners = quests.filter { getScoreForQuest(it) == topScore }
        winners.forEach { giveReward(it) }
        for (loser in quests.filterNot { it in winners })
            notifyExpired(loser, winners)

        assignedQuests.removeAll(quests)
    }

    private fun handleIndividualQuests() {
        assignedQuests.removeAll { it.isIndividual() && handleIndividualQuest(it) }
    }

    /** If quest is complete, it gives the influence reward to the player.
     *  Returns true if the quest can be removed (is either complete, obsolete or expired) */
    private fun handleIndividualQuest(assignedQuest: AssignedQuest): Boolean {
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)

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

    private fun assignNewQuest(quest: Quest, assignees: Iterable<CivilizationInfo>) {

        val turn = civInfo.gameInfo.turns

        for (assignee in assignees) {

            val playerReligion = civInfo.gameInfo.religions.values.firstOrNull { it.foundingCivName == assignee.civName && it.isMajorReligion() }

            var data1 = ""
            var data2 = ""

            when (quest.name) {
                QuestName.ClearBarbarianCamp.value -> {
                    val camp = getBarbarianEncampmentForQuest()!!
                    data1 = camp.position.x.toInt().toString()
                    data2 = camp.position.y.toInt().toString()
                }
                QuestName.ConnectResource.value -> data1 = getResourceForQuest(assignee)!!.name
                QuestName.ConstructWonder.value -> data1 = getWonderToBuildForQuest(assignee)!!.name
                QuestName.GreatPerson.value -> data1 = getGreatPersonForQuest(assignee)!!.name
                QuestName.FindPlayer.value -> data1 = getCivilizationToFindForQuest(assignee)!!.civName
                QuestName.FindNaturalWonder.value -> data1 = getNaturalWonderToFindForQuest(assignee)!!
                QuestName.ConquerCityState.value -> data1 = getCityStateTarget(assignee)!!.civName
                QuestName.BullyCityState.value -> data1 = getCityStateTarget(assignee)!!.civName
                QuestName.PledgeToProtect.value -> data1 = getMostRecentBully()!!
                QuestName.GiveGold.value -> data1 = getMostRecentBully()!!
                QuestName.DenounceCiv.value -> data1 = getMostRecentBully()!!
                QuestName.SpreadReligion.value -> { data1 = playerReligion!!.getReligionDisplayName() // For display
                                                    data2 = playerReligion.name } // To check completion
                QuestName.ContestCulture.value -> data1 = assignee.totalCultureForContests.toString()
                QuestName.ContestFaith.value -> data1 = assignee.totalFaithForContests.toString()
                QuestName.ContestTech.value -> data1 = assignee.tech.getNumberOfTechsResearched().toString()
                QuestName.Invest.value -> data1 = quest.description.getPlaceholderParameters().first()
            }

            val newQuest = AssignedQuest(
                    questName = quest.name,
                    assigner = civInfo.civName,
                    assignee = assignee.civName,
                    assignedOnTurn = turn,
                    data1 = data1,
                    data2 = data2
            )
            newQuest.gameInfo = civInfo.gameInfo

            assignedQuests.add(newQuest)
            assignee.addNotification("[${civInfo.civName}] assigned you a new quest: [${quest.name}].",
                    DiplomacyAction(civInfo.civName), civInfo.civName, "OtherIcons/Quest")

            if (quest.isIndividual())
                individualQuestCountdown[assignee.civName] = UNSET
        }
    }

    /** Returns true if [civInfo] can assign a quest to [challenger] */
    private fun canAssignAQuestTo(challenger: CivilizationInfo): Boolean {
        return !challenger.isDefeated() && challenger.isMajorCiv() &&
                civInfo.knows(challenger) && !civInfo.isAtWarWith(challenger)
    }

    /** Returns true if the [quest] can be assigned to [challenger] */
    private fun isQuestValid(quest: Quest, challenger: CivilizationInfo): Boolean {
        if (!canAssignAQuestTo(challenger))
            return false
        if (assignedQuests.any { it.assignee == challenger.civName && it.questName == quest.name })
            return false
        if (quest.isIndividual() && civInfo.getDiplomacyManager(challenger).hasFlag(DiplomacyFlags.Bullied))
            return false

        val mostRecentBully = getMostRecentBully()
        val playerReligion = civInfo.gameInfo.religions.values.firstOrNull { it.foundingCivName == challenger.civName && it.isMajorReligion() }?.name

        return when (quest.name) {
            QuestName.ClearBarbarianCamp.value -> getBarbarianEncampmentForQuest() != null
            QuestName.Route.value -> !challenger.cities.none()
                    && !challenger.isCapitalConnectedToCity(civInfo.getCapital()!!)
                    // Need to have a city within 7 tiles on the same continent
                    && challenger.cities.any { it.getCenterTile().aerialDistanceTo(civInfo.getCapital()!!.getCenterTile()) <= 7
                        && it.getCenterTile().getContinent() == civInfo.getCapital()!!.getCenterTile().getContinent() }
            QuestName.ConnectResource.value -> getResourceForQuest(challenger) != null
            QuestName.ConstructWonder.value -> getWonderToBuildForQuest(challenger) != null
            QuestName.GreatPerson.value -> getGreatPersonForQuest(challenger) != null
            QuestName.FindPlayer.value -> getCivilizationToFindForQuest(challenger) != null
            QuestName.FindNaturalWonder.value -> getNaturalWonderToFindForQuest(challenger) != null
            QuestName.PledgeToProtect.value -> mostRecentBully != null && challenger !in civInfo.getProtectorCivs()
            QuestName.GiveGold.value -> mostRecentBully != null
            QuestName.DenounceCiv.value -> mostRecentBully != null && challenger.knows(mostRecentBully)
                                            && !challenger.getDiplomacyManager(mostRecentBully).hasFlag(DiplomacyFlags.Denunciation)
                                            && challenger.getDiplomacyManager(mostRecentBully).diplomaticStatus != DiplomaticStatus.War
                                            && !( challenger.playerType == PlayerType.Human && civInfo.gameInfo.getCivilization(mostRecentBully).playerType == PlayerType.Human)
            QuestName.SpreadReligion.value -> playerReligion != null && civInfo.getCapital()!!.religion.getMajorityReligion()?.name != playerReligion
            QuestName.ConquerCityState.value -> getCityStateTarget(challenger) != null && civInfo.cityStatePersonality != CityStatePersonality.Friendly
            QuestName.BullyCityState.value -> getCityStateTarget(challenger) != null
            QuestName.ContestFaith.value -> civInfo.gameInfo.isReligionEnabled()
            else -> true
        }
    }

    /** Returns true if the [assignedQuest] is successfully completed */
    private fun isComplete(assignedQuest: AssignedQuest): Boolean {
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)
        return when (assignedQuest.questName) {
            QuestName.Route.value -> assignee.isCapitalConnectedToCity(civInfo.getCapital()!!)
            QuestName.ConnectResource.value -> assignee.detailedCivResources.map { it.resource }.contains(civInfo.gameInfo.ruleSet.tileResources[assignedQuest.data1])
            QuestName.ConstructWonder.value -> assignee.cities.any { it.cityConstructions.isBuilt(assignedQuest.data1) }
            QuestName.GreatPerson.value -> assignee.getCivGreatPeople().any { it.baseUnit.getReplacedUnit(civInfo.gameInfo.ruleSet).name == assignedQuest.data1 }
            QuestName.FindPlayer.value -> assignee.hasMetCivTerritory(civInfo.gameInfo.getCivilization(assignedQuest.data1))
            QuestName.FindNaturalWonder.value -> assignee.naturalWonders.contains(assignedQuest.data1)
            QuestName.PledgeToProtect.value -> assignee in civInfo.getProtectorCivs()
            QuestName.DenounceCiv.value -> assignee.getDiplomacyManager(assignedQuest.data1).hasFlag(DiplomacyFlags.Denunciation)
            QuestName.SpreadReligion.value -> civInfo.getCapital()!!.religion.getMajorityReligion() == civInfo.gameInfo.religions[assignedQuest.data2]
            else -> false
        }
    }

    /** Returns true if the [assignedQuest] request cannot be fulfilled anymore */
    private fun isObsolete(assignedQuest: AssignedQuest): Boolean {
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)
        return when (assignedQuest.questName) {
            QuestName.ClearBarbarianCamp.value -> civInfo.gameInfo.tileMap[assignedQuest.data1.toInt(), assignedQuest.data2.toInt()].improvement != Constants.barbarianEncampment
            QuestName.ConstructWonder.value -> civInfo.gameInfo.getCities().any { it.civInfo != assignee && it.cityConstructions.isBuilt(assignedQuest.data1) }
            QuestName.FindPlayer.value -> civInfo.gameInfo.getCivilization(assignedQuest.data1).isDefeated()
            QuestName.ConquerCityState.value ->  civInfo.gameInfo.getCivilization(assignedQuest.data1).isDefeated()
            QuestName.BullyCityState.value ->  civInfo.gameInfo.getCivilization(assignedQuest.data1).isDefeated()
            QuestName.DenounceCiv.value ->  civInfo.gameInfo.getCivilization(assignedQuest.data1).isDefeated()
            else -> false
        }
    }

    /** Increments [assignedQuest.assignee][AssignedQuest.assignee] influence on [civInfo] and adds a [Notification] */
    private fun giveReward(assignedQuest: AssignedQuest) {
        val rewardInfluence = civInfo.gameInfo.ruleSet.quests[assignedQuest.questName]!!.influence
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)

        civInfo.getDiplomacyManager(assignedQuest.assignee).addInfluence(rewardInfluence)
        if (rewardInfluence > 0)
            assignee.addNotification(
                "[${civInfo.civName}] rewarded you with [${rewardInfluence.toInt()}] influence for completing the [${assignedQuest.questName}] quest.",
                civInfo.getCapital()!!.location, civInfo.civName, "OtherIcons/Quest"
            )

        // We may have received bonuses from city-state friend-ness or ally-ness
        for (city in civInfo.cities)
            city.cityStats.update()
    }

    /** Notifies the assignee of [assignedQuest] that the quest is now obsolete or expired.
     *  Optionally displays the [winners] of global quests. */
    private fun notifyExpired(assignedQuest: AssignedQuest, winners: List<AssignedQuest> = emptyList()) {
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)
        if (winners.isEmpty()) {
            assignee.addNotification(
                    "[${civInfo.civName}] no longer needs your help with the [${assignedQuest.questName}] quest.",
                    civInfo.getCapital()!!.location, civInfo.civName, "OtherIcons/Quest")
        } else {
            assignee.addNotification(
                    "The [${assignedQuest.questName}] quest for [${civInfo.civName}] has ended. It was won by [${winners.joinToString { it.assignee.tr() }}].",
                    civInfo.getCapital()!!.location, civInfo.civName, "OtherIcons/Quest")
        }
    }

    /** Returns the score for the [assignedQuest] */
    private fun getScoreForQuest(assignedQuest: AssignedQuest): Int {
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)
        return when (assignedQuest.questName) {
            QuestName.ContestCulture.value -> assignee.totalCultureForContests - assignedQuest.data1.toInt()
            QuestName.ContestFaith.value -> assignee.totalFaithForContests - assignedQuest.data1.toInt()
            QuestName.ContestTech.value -> assignee.tech.getNumberOfTechsResearched() - assignedQuest.data1.toInt()
            else -> 0
        }
    }

    /** Returns a string with the leading civ and their score for [questName] */
    fun getLeaderStringForQuest(questName: String): String {
        val leadingQuest = assignedQuests.filter { it.questName == questName }.maxByOrNull { getScoreForQuest(it) }
            ?: return ""

        return when (questName){
            QuestName.ContestCulture.value -> "Current leader is [${leadingQuest.assignee}] with [${getScoreForQuest(leadingQuest)}] [Culture] generated."
            QuestName.ContestFaith.value -> "Current leader is [${leadingQuest.assignee}] with [${getScoreForQuest(leadingQuest)}] [Faith] generated."
            QuestName.ContestTech.value -> "Current leader is [${leadingQuest.assignee}] with [${getScoreForQuest(leadingQuest)}] Technologies discovered."
            else -> ""
        }
    }

    /**
     * Gets notified a barbarian camp in [location] has been cleared by [civInfo].
     * Since [QuestName.ClearBarbarianCamp] is a global quest, it could have been assigned to
     * multiple civilizations, so after this notification all matching quests are removed.
     */
    fun barbarianCampCleared(civInfo: CivilizationInfo, location: Vector2) {
        val matchingQuests = assignedQuests.asSequence()
                .filter { it.questName == QuestName.ClearBarbarianCamp.value }
                .filter { it.data1.toInt() == location.x.toInt() && it.data2.toInt() == location.y.toInt() }

        val winningQuest = matchingQuests.filter { it.assignee == civInfo.civName }.firstOrNull()
        if (winningQuest != null)
            giveReward(winningQuest)

        assignedQuests.removeAll(matchingQuests)
    }

    /**
     * Gets notified the city state [cityState] was just conquered by [attacker].
     */
    fun cityStateConquered(cityState: CivilizationInfo, attacker: CivilizationInfo) {
        val matchingQuests = assignedQuests.asSequence()
            .filter { it.questName == QuestName.ConquerCityState.value }
            .filter { it.data1 == cityState.civName && it.assignee == attacker.civName }

        for (quest in matchingQuests)
            giveReward(quest)

        assignedQuests.removeAll(matchingQuests)
    }

    /**
     * Gets notified the city state [cityState] was just bullied by [bully].
     */
    fun cityStateBullied(cityState: CivilizationInfo, bully: CivilizationInfo) {
        val matchingQuests = assignedQuests.asSequence()
            .filter { it.questName == QuestName.BullyCityState.value }
            .filter { it.data1 == cityState.civName && it.assignee == bully.civName}

        for (quest in matchingQuests)
            giveReward(quest)

        assignedQuests.removeAll(matchingQuests)

        // What idiots haha oh wait that's us
        if (civInfo == cityState) {
            // Revoke most quest types from the bully
            val revokedQuests = assignedQuests.asSequence()
                .filter { it.assignee == bully.civName && (it.isIndividual() || it.questName == QuestName.Invest.value) }
            assignedQuests.removeAll(revokedQuests)
            if (revokedQuests.count() > 0)
                bully.addNotification("[${civInfo.civName}] cancelled the quests they had given you because you demanded tribute from them.",
                    DiplomacyAction(civInfo.civName), civInfo.civName, "OtherIcons/Quest")
        }
    }

    /** Gets notified when we are attacked, for war with major pseudo-quest */
    fun wasAttackedBy(attacker: CivilizationInfo) {
        // Set target number units to kill
        val totalMilitaryUnits = attacker.getCivUnits().count { !it.isCivilian() }
        val unitsToKill = max(3, totalMilitaryUnits / 4)
        unitsToKillForCiv[attacker.civName] = unitsToKill


        val location = if (civInfo.cities.isEmpty() || civInfo.getCapital() == null) null
            else civInfo.getCapital()!!.location

        // Ask for assistance
        for (thirdCiv in civInfo.getKnownCivs().filter { it.isAlive() && !it.isAtWarWith(civInfo) && it.isMajorCiv() }) {
            if (location != null)
                thirdCiv.addNotification("[${civInfo.civName}] is being attacked by [${attacker.civName}]! Kill [$unitsToKill] of the attacker's military units and they will be immensely grateful.",
                    location, civInfo.civName, "OtherIcons/Quest")
            else thirdCiv.addNotification("[${civInfo.civName}] is being attacked by [${attacker.civName}]! Kill [$unitsToKill] of the attacker's military units and they will be immensely grateful.",
                civInfo.civName, "OtherIcons/Quest")
        }
    }

    /** Gets notified when [killed]'s military unit was killed by [killer], for war with major pseudo-quest */
    fun militaryUnitKilledBy(killer: CivilizationInfo, killed: CivilizationInfo) {
        if (!warWithMajorActive(killed)) return

        // No credit if we're at war or haven't met
        if (!civInfo.knows(killer) || civInfo.isAtWarWith(killer))  return

        // Make the map if we haven't already
        if (unitsKilledFromCiv[killed.civName] == null)
            unitsKilledFromCiv[killed.civName] = HashMap()

        // Update kill count
        val updatedKillCount = 1 + (unitsKilledFromCiv[killed.civName]!![killer.civName] ?: 0)
        unitsKilledFromCiv[killed.civName]!![killer.civName] = updatedKillCount

        // Quest complete?
        if (updatedKillCount >= unitsToKillForCiv[killed.civName]!!) {
            killer.addNotification("[${civInfo.civName}] is deeply grateful for your assistance in the war against [${killed.civName}]!",
                DiplomacyAction(civInfo.civName), civInfo.civName, "OtherIcons/Quest")
            civInfo.getDiplomacyManager(killer).addInfluence(100f) // yikes
            endWarWithMajorQuest(killed)
        }
    }

    /** Called when a major civ meets the city-state for the first time. Mainly for war with major pseudo-quest. */
    fun justMet(otherCiv: CivilizationInfo) {
        val location = if (civInfo.cities.isEmpty() || civInfo.getCapital() == null) null
            else civInfo.getCapital()!!.location

        for ((attackerName, unitsToKill) in unitsToKillForCiv) {
            if (location != null)
                otherCiv.addNotification("[${civInfo.civName}] is being attacked by [$attackerName]! Kill [$unitsToKill] of the attacker's military units and they will be immensely grateful.",
                    location, civInfo.civName, "OtherIcons/Quest")
            else otherCiv.addNotification("[${civInfo.civName}] is being attacked by [$attackerName]! Kill [$unitsToKill] of the attacker's military units and they will be immensely grateful.",
                civInfo.civName, "OtherIcons/Quest")
        }
    }

    /** Ends War with Major pseudo-quests that aren't relevant any longer */
    private fun tryEndWarWithMajorQuests() {
        for (attacker in unitsToKillForCiv.keys.map { civInfo.gameInfo.getCivilization(it) }) {
            if (civInfo.isDefeated()
                || attacker.isDefeated()
                || !civInfo.isAtWarWith(attacker)) {
                    endWarWithMajorQuest(attacker)
            }
        }
    }

    private fun endWarWithMajorQuest(attacker: CivilizationInfo) {
        for (thirdCiv in civInfo.getKnownCivs().filterNot { it.isDefeated() || it == attacker || it.isAtWarWith(civInfo) }) {
            if (unitsKilledSoFar(attacker, thirdCiv) >= unitsToKill(attacker)) // Don't show the notification to the one who won the quest
                continue
            thirdCiv.addNotification("[${civInfo.civName}] no longer needs your assistance against [${attacker.civName}].",
                DiplomacyAction(civInfo.civName), civInfo.civName, "OtherIcons/Quest")
        }
        unitsToKillForCiv.remove(attacker.civName)
        unitsKilledFromCiv.remove(attacker.civName)
    }

    fun warWithMajorActive(target: CivilizationInfo): Boolean {
        return unitsToKillForCiv.containsKey(target.civName)
    }

    fun unitsToKill(target: CivilizationInfo): Int {
        return unitsToKillForCiv[target.civName] ?: 0
    }

    fun unitsKilledSoFar(target: CivilizationInfo, viewingCiv: CivilizationInfo): Int {
        val killMap = unitsKilledFromCiv[target.civName] ?: return 0
        return killMap[viewingCiv.civName] ?: 0
    }

    /**
     * Gets notified when given gold by [donorCiv].
     */
    fun receivedGoldGift(donorCiv: CivilizationInfo) {
        val matchingQuests = assignedQuests.asSequence()
            .filter { it.questName == QuestName.GiveGold.value }
            .filter { it.assignee == donorCiv.civName}

        for (quest in matchingQuests)
            giveReward(quest)

        assignedQuests.removeAll(matchingQuests)
    }

    /**
     * Returns the weight of the [questName], depends on city state trait and personality
     */
    private fun getQuestWeight(questName: String): Float {
        var weight = 1f
        val trait = civInfo.cityStateType
        val personality = civInfo.cityStatePersonality
        when (questName) {
            QuestName.Route.value -> {
                when (personality) {
                    CityStatePersonality.Friendly -> weight *= 2f
                    CityStatePersonality.Hostile -> weight *= .2f
                    else -> {}
                }
                when (trait) {
                    CityStateType.Maritime -> weight *= 1.2f
                    CityStateType.Mercantile -> weight *= 1.5f
                    else -> {}
                }
            }
            QuestName.ConnectResource.value -> {
                when (trait) {
                    CityStateType.Maritime -> weight *= 2f
                    CityStateType.Mercantile -> weight *= 3f
                    else -> {}
                }
            }
            QuestName.ConstructWonder.value -> {
                if (trait == CityStateType.Cultured)
                    weight *= 3f
            }
            QuestName.GreatPerson.value -> {
                if (trait == CityStateType.Cultured)
                    weight *= 3f
            }
            QuestName.ConquerCityState.value -> {
                if (trait == CityStateType.Militaristic)
                    weight *= 2f
                when (personality) {
                    CityStatePersonality.Hostile -> weight *= 2f
                    CityStatePersonality.Neutral -> weight *= .4f
                    else -> {}
                }
            }
            QuestName.FindPlayer.value -> {
                when (trait) {
                    CityStateType.Maritime -> weight *= 3f
                    CityStateType.Mercantile -> weight *= 2f
                    else -> {}
                }
            }
            QuestName.FindNaturalWonder.value -> {
                if (trait == CityStateType.Militaristic)
                    weight *= .5f
                if (personality == CityStatePersonality.Hostile)
                    weight *= .3f
            }
            QuestName.ClearBarbarianCamp.value -> {
                weight *= 3f
                if (trait == CityStateType.Militaristic)
                    weight *= 3f
            }
            QuestName.GiveGold.value -> {
                weight *= when (trait) {
                    CityStateType.Militaristic -> 2f
                    CityStateType.Mercantile -> 3.5f
                    else -> 3f
                }
            }
            QuestName.PledgeToProtect.value -> {
                weight *= when (trait) {
                    CityStateType.Militaristic -> 2f
                    CityStateType.Cultured -> 3.5f
                    else -> 3f
                }
            }
            QuestName.BullyCityState.value -> {
                when (personality) {
                    CityStatePersonality.Hostile -> weight *= 2f
                    CityStatePersonality.Irrational -> weight *= 1.5f
                    CityStatePersonality.Friendly -> weight *= .3f
                    CityStatePersonality.Neutral -> {}
                }
            }
            QuestName.DenounceCiv.value -> {
                weight *= when (trait) {
                    CityStateType.Religious -> 2.5f
                    CityStateType.Maritime -> 2f
                    else -> 1.5f
                }
            }
            QuestName.SpreadReligion.value -> {
                if (trait == CityStateType.Religious)
                    weight *= 3f
            }
            QuestName.ContestCulture.value -> {
                if (trait == CityStateType.Cultured)
                    weight *= 2f
            }
            QuestName.ContestFaith.value -> {
                weight *= when (trait) {
                    CityStateType.Religious -> 2f
                    else -> .5f
                }
            }
            QuestName.ContestTech.value -> {
                if (trait == CityStateType.Religious)
                    weight *= .5f
            }
            QuestName.Invest.value -> {
                if (trait == CityStateType.Mercantile)
                    weight *= 1.5f
            }
        }
        return weight
    }

    //region get-quest-target
    /**
     * Returns a random [TileInfo] containing a Barbarian encampment within 8 tiles of [civInfo]
     * to be destroyed
     */
    private fun getBarbarianEncampmentForQuest(): TileInfo? {
        val encampments = civInfo.getCapital()!!.getCenterTile().getTilesInDistance(8)
                .filter { it.improvement == Constants.barbarianEncampment }.toList()

        if (encampments.isNotEmpty())
            return encampments.random()

        return null
    }

    /**
     * Returns a random resource to be connected to the [challenger]'s trade route as a quest.
     * The resource must be a [ResourceType.Luxury] or [ResourceType.Strategic], must not be owned
     * by the [civInfo] and the [challenger], and must be viewable by the [challenger];
     * if none exists, it returns null.
     */
    private fun getResourceForQuest(challenger: CivilizationInfo): TileResource? {
        val ownedByCityStateResources = civInfo.detailedCivResources.map { it.resource }
        val ownedByMajorResources = challenger.detailedCivResources.map { it.resource }

        val resourcesOnMap = civInfo.gameInfo.tileMap.values.asSequence().mapNotNull { it.resource }.distinct()
        val viewableResourcesForChallenger = resourcesOnMap.map { civInfo.gameInfo.ruleSet.tileResources[it]!! }
                .filter { it.revealedBy == null || challenger.tech.isResearched(it.revealedBy!!) }

        val notOwnedResources = viewableResourcesForChallenger.filter {
            it.resourceType != ResourceType.Bonus &&
                    !ownedByCityStateResources.contains(it) &&
                    !ownedByMajorResources.contains(it)
        }.toList()

        if (notOwnedResources.isNotEmpty())
            return notOwnedResources.random()

        return null
    }

    private fun getWonderToBuildForQuest(challenger: CivilizationInfo): Building? {
        val startingEra = civInfo.gameInfo.ruleSet.eras[civInfo.gameInfo.gameParameters.startingEra]!!
        val wonders = civInfo.gameInfo.ruleSet.buildings.values
                .filter { building ->
                            // Buildable wonder
                            building.isWonder
                            && (building.requiredTech == null || challenger.tech.isResearched(building.requiredTech!!))
                            && civInfo.gameInfo.getCities().none { it.cityConstructions.isBuilt(building.name) }
                            // Can't be disabled
                            && building.name !in startingEra.startingObsoleteWonders
                            && (civInfo.gameInfo.isReligionEnabled() || !building.hasUnique(UniqueType.HiddenWithoutReligion))
                            // Can't be more than 25% built anywhere
                            && civInfo.gameInfo.getCities().none {
                        it.cityConstructions.getWorkDone(building.name) * 3 > it.cityConstructions.getRemainingWork(building.name) }
                            // Can't be a unique wonder
                            && building.uniqueTo == null
                }

        if (wonders.isNotEmpty())
            return wonders.random()

        return null
    }

    /**
     * Returns a random Natural Wonder not yet discovered by [challenger].
     */
    private fun getNaturalWonderToFindForQuest(challenger: CivilizationInfo): String? {
        val naturalWondersToFind = civInfo.gameInfo.tileMap.naturalWonders.subtract(challenger.naturalWonders)

        if (naturalWondersToFind.isNotEmpty())
            return naturalWondersToFind.random()

        return null
    }

    /**
     * Returns a Great Person [BaseUnit] that is not owned by both the [challenger] and the [civInfo]
     */
    private fun getGreatPersonForQuest(challenger: CivilizationInfo): BaseUnit? {
        val ruleSet = civInfo.gameInfo.ruleSet

        val challengerGreatPeople = challenger.getCivGreatPeople().map { it.baseUnit.getReplacedUnit(ruleSet) }
        val cityStateGreatPeople = civInfo.getCivGreatPeople().map { it.baseUnit.getReplacedUnit(ruleSet) }

        val greatPeople = challenger.getGreatPeople()
                .map { it.getReplacedUnit(ruleSet) }
                .distinct()
                .filterNot { challengerGreatPeople.contains(it)
                        || cityStateGreatPeople.contains(it)
                        || (it.hasUnique(UniqueType.HiddenWithoutReligion) && !civInfo.gameInfo.isReligionEnabled()) }
                .toList()

        if (greatPeople.isNotEmpty())
            return greatPeople.random()

        return null
    }

    /**
     * Returns a random [CivilizationInfo] (major) that [challenger] has met, but whose territory he
     * cannot see; if none exists, it returns null.
     */
    private fun getCivilizationToFindForQuest(challenger: CivilizationInfo): CivilizationInfo? {
        val civilizationsToFind = challenger.getKnownCivs()
                .filter { it.isAlive() && it.isMajorCiv() && !challenger.hasMetCivTerritory(it) }

        if (civilizationsToFind.isNotEmpty())
            return civilizationsToFind.random()

        return null
    }

    /**
     * Returns a city-state [CivilizationInfo] that [civInfo] wants to target for hostile quests
     */
    private fun getCityStateTarget(challenger: CivilizationInfo): CivilizationInfo? {
        val closestProximity = civInfo.gameInfo.getAliveCityStates()
            .mapNotNull { civInfo.proximity[it.civName] }.filter { it != Proximity.None }.minByOrNull { it.ordinal }

        if (closestProximity == null || closestProximity == Proximity.Distant) // None close enough
            return null

        val validTargets = civInfo.getKnownCivs().filter { it.isCityState() && challenger.knows(it)
                && civInfo.proximity[it.civName] == closestProximity }

        return validTargets.randomOrNull()
    }

    /** Returns a [CivilizationInfo] of the civ that most recently bullied [civInfo].
     *  Note: forgets after 20 turns has passed! */
    private fun getMostRecentBully(): String? {
        val bullies = civInfo.diplomacy.values.filter { it.hasFlag(DiplomacyFlags.Bullied)}
        return bullies.maxByOrNull { it.getFlag(DiplomacyFlags.Bullied) }?.otherCivName
    }
    //endregion
}


class AssignedQuest(val questName: String = "",
                    val assigner: String = "",
                    val assignee: String = "",
                    val assignedOnTurn: Int = 0,
                    val data1: String = "",
                    val data2: String = "") : IsPartOfGameInfoSerialization {

    @Transient
    lateinit var gameInfo: GameInfo

    fun isIndividual(): Boolean = !isGlobal()
    fun isGlobal(): Boolean = gameInfo.ruleSet.quests[questName]!!.isGlobal()
    @Suppress("MemberVisibilityCanBePrivate")
    fun doesExpire(): Boolean = gameInfo.ruleSet.quests[questName]!!.duration > 0
    fun isExpired(): Boolean = doesExpire() && getRemainingTurns() == 0
    @Suppress("MemberVisibilityCanBePrivate")
    fun getDuration(): Int = (gameInfo.speed.modifier * gameInfo.ruleSet.quests[questName]!!.duration).toInt()
    fun getRemainingTurns(): Int = max(0, (assignedOnTurn + getDuration()) - gameInfo.turns)

    fun getDescription(): String {
        val quest = gameInfo.ruleSet.quests[questName]!!
        return quest.description.fillPlaceholders(data1)
    }

    fun onClickAction() {
        val game = UncivGame.Current

        when (questName) {
            QuestName.ClearBarbarianCamp.value -> {
                game.resetToWorldScreen()
                game.worldScreen!!.mapHolder.setCenterPosition(Vector2(data1.toFloat(), data2.toFloat()), selectUnit = false)
            }
            QuestName.Route.value -> {
                game.resetToWorldScreen()
                game.worldScreen!!.mapHolder.setCenterPosition(gameInfo.getCivilization(assigner).getCapital()!!.location, selectUnit = false)
            }
        }
    }
}
