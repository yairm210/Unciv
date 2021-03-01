package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.QuestName
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.fillPlaceholders
import com.unciv.ui.utils.randomWeighted
import kotlin.math.max
import kotlin.random.Random

class QuestManager {

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

    /** Returns [true] if [civInfo] have active quests for [challenger] */
    fun haveQuestsFor(challenger: CivilizationInfo): Boolean = assignedQuests.any { it.assignee == challenger.civName }

    fun clone(): QuestManager {
        val toReturn = QuestManager()
        toReturn.globalQuestCountdown = globalQuestCountdown
        toReturn.individualQuestCountdown.putAll(individualQuestCountdown)
        toReturn.assignedQuests.addAll(assignedQuests)
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

        globalQuestCountdown = (countdown * civInfo.gameInfo.gameParameters.gameSpeed.modifier).toInt()
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

        individualQuestCountdown[challenger.civName] = (countdown * civInfo.gameInfo.gameParameters.gameSpeed.modifier).toInt()
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

    private fun handleGlobalQuests() {
        val globalQuestsExpired = assignedQuests.filter { it.isGlobal() && it.isExpired() }.map { it.questName }.distinct()
        for (globalQuestName in globalQuestsExpired)
            handleGlobalQuest(globalQuestName)
    }

    private fun handleGlobalQuest(questName: String) {
        val quests = assignedQuests.filter { it.questName == questName }
        if (quests.isEmpty())
            return

        val topScore = quests.map { getScoreForQuest(it) }.max()!!

        for (quest in quests) {
            if (getScoreForQuest(quest) >= topScore)
                giveReward(quest)
        }

        assignedQuests.removeAll(quests)
    }

    private fun handleIndividualQuests() {
        val toRemove = ArrayList<AssignedQuest>()

        for (assignedQuest in assignedQuests.filter { it.isIndividual() }) {
            val shouldRemove = handleIndividualQuest(assignedQuest)
            if (shouldRemove)
                toRemove.add(assignedQuest)
        }

        assignedQuests.removeAll(toRemove)
    }

    /** If quest is complete, it gives the influence reward to the player.
     *  Returns [true] if the quest can be removed (is either complete, obsolete or expired) */
    private fun handleIndividualQuest(assignedQuest: AssignedQuest): Boolean {
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)

        // One of the civs is defeated, or they started a war: remove quest
        if (!canAssignAQuestTo(assignee))
            return true

        if (isComplete(assignedQuest)) {
            giveReward(assignedQuest)
            return true
        }

        if (isObsolete(assignedQuest))
            return true

        if (assignedQuest.isExpired())
            return true

        return false
    }

    private fun assignNewQuest(quest: Quest, assignees: Iterable<CivilizationInfo>) {

        val turn = civInfo.gameInfo.turns

        for (assignee in assignees) {

            var data1 = ""
            var data2 = ""

            when (quest.name) {
                QuestName.ClearBarbarianCamp.value -> {
                    val camp = getBarbarianEncampmentForQuest(assignee)!!
                    data1 = camp.position.x.toInt().toString()
                    data2 = camp.position.y.toInt().toString()
                }
                QuestName.ConnectResource.value -> data1 = getResourceForQuest(assignee)!!.name
                QuestName.ConstructWonder.value -> data1 = getWonderToBuildForQuest(assignee)!!.name
                QuestName.GreatPerson.value -> data1 = getGreatPersonForQuest(assignee)!!.name
                QuestName.FindPlayer.value -> data1 = getCivilizationToFindForQuest(assignee)!!.civName
                QuestName.FindNaturalWonder.value -> data1 = getNaturalWonderToFindForQuest(assignee)!!
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
            assignee.addNotification("[${civInfo.civName}] assigned you a new quest: [${quest.name}].", Color.GOLD, DiplomacyAction(civInfo.civName))

            if (quest.isIndividual())
                individualQuestCountdown[assignee.civName] = UNSET
        }
    }

    /** Returns [true] if [civInfo] can assign a quest to [challenger] */
    private fun canAssignAQuestTo(challenger: CivilizationInfo): Boolean {
        return !challenger.isDefeated() && challenger.isMajorCiv() &&
                civInfo.knows(challenger) && !civInfo.isAtWarWith(challenger)
    }

    /** Returns [true] if the [quest] can be assigned to [challenger] */
    private fun isQuestValid(quest: Quest, challenger: CivilizationInfo): Boolean {
        if (!canAssignAQuestTo(challenger))
            return false
        if (assignedQuests.any { it.assignee == challenger.civName && it.questName == quest.name })
            return false

        return when (quest.name) {
            QuestName.ClearBarbarianCamp.value -> getBarbarianEncampmentForQuest(challenger) != null
            QuestName.Route.value -> civInfo.hasEverBeenFriendWith(challenger) && challenger.cities.any()
                    && !civInfo.isCapitalConnectedToCity(challenger.getCapital())
            QuestName.ConnectResource.value -> civInfo.hasEverBeenFriendWith(challenger) && getResourceForQuest(challenger) != null
            QuestName.ConstructWonder.value -> civInfo.hasEverBeenFriendWith(challenger) && getWonderToBuildForQuest(challenger) != null
            QuestName.GreatPerson.value -> civInfo.hasEverBeenFriendWith(challenger) && getGreatPersonForQuest(challenger) != null
            QuestName.FindPlayer.value -> civInfo.hasEverBeenFriendWith(challenger) && getCivilizationToFindForQuest(challenger) != null
            QuestName.FindNaturalWonder.value -> civInfo.hasEverBeenFriendWith(challenger) && getNaturalWonderToFindForQuest(challenger) != null
            else -> true
        }
    }

    /** Returns [true] if the [assignedQuest] is successfully completed */
    private fun isComplete(assignedQuest: AssignedQuest): Boolean {
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)
        return when (assignedQuest.questName) {
            QuestName.Route.value -> civInfo.isCapitalConnectedToCity(assignee.getCapital())
            QuestName.ConnectResource.value -> assignee.detailedCivResources.map { it.resource }.contains(civInfo.gameInfo.ruleSet.tileResources[assignedQuest.data1])
            QuestName.ConstructWonder.value -> assignee.cities.any { it.cityConstructions.isBuilt(assignedQuest.data1) }
            QuestName.GreatPerson.value -> assignee.getCivGreatPeople().any { it.baseUnit.getReplacedUnit(civInfo.gameInfo.ruleSet).name == assignedQuest.data1 }
            QuestName.FindPlayer.value -> assignee.hasMetCivTerritory(civInfo.gameInfo.getCivilization(assignedQuest.data1))
            QuestName.FindNaturalWonder.value -> assignee.naturalWonders.contains(assignedQuest.data1)
            else -> false
        }
    }

    /** Returns [true] if the [assignedQuest] request cannot be fulfilled anymore */
    private fun isObsolete(assignedQuest: AssignedQuest): Boolean {
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)
        return when (assignedQuest.questName) {
            QuestName.ClearBarbarianCamp.value -> civInfo.gameInfo.tileMap[assignedQuest.data1.toInt(), assignedQuest.data2.toInt()].improvement != Constants.barbarianEncampment
            QuestName.ConstructWonder.value -> civInfo.gameInfo.getCities().any { it.civInfo != assignee && it.cityConstructions.isBuilt(assignedQuest.data1) }
            QuestName.FindPlayer.value -> civInfo.gameInfo.getCivilization(assignedQuest.data1).isDefeated()
            else -> false
        }
    }

    /** Increments [assignedQuest.assignee] influence on [civInfo] and adds a [Notification] */
    private fun giveReward(assignedQuest: AssignedQuest) {
        val rewardInfluence = civInfo.gameInfo.ruleSet.quests[assignedQuest.questName]!!.influece
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)

        civInfo.getDiplomacyManager(assignedQuest.assignee).influence += rewardInfluence
        if (rewardInfluence > 0)
            assignee.addNotification("[${civInfo.civName}] rewarded you with [${rewardInfluence.toInt()}] influence for completing the [${assignedQuest.questName}] quest.", civInfo.getCapital().location, Color.GOLD)
    }

    /** Returns the score for the [assignedQuest] */
    private fun getScoreForQuest(assignedQuest: AssignedQuest): Int {
        val assignee = civInfo.gameInfo.getCivilization(assignedQuest.assignee)
        return when (assignedQuest.questName) {
            // Waiting for contest quests
            else -> 0
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
                }
                when (trait) {
                    CityStateType.Maritime -> weight *= 1.2f
                    CityStateType.Mercantile -> weight *= 1.5f
                }
            }
            QuestName.ConnectResource.value -> {
                when (trait) {
                    CityStateType.Maritime -> weight *= 2f
                    CityStateType.Mercantile -> weight *= 3f
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
                }
            }
            QuestName.FindPlayer.value -> {
                when (trait) {
                    CityStateType.Maritime -> weight *= 3f
                    CityStateType.Mercantile -> weight *= 2f
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
        }
        return weight
    }

    //region get-quest-target
    /**
     * Returns a random [TileInfo] containing a Barbarian encampment within 8 tiles of [civInfo]
     * to be destroyed
     */
    private fun getBarbarianEncampmentForQuest(challenger: CivilizationInfo): TileInfo? {
        val encampments = civInfo.getCapital().getCenterTile().getTilesInDistance(8)
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
        val wonders = civInfo.gameInfo.ruleSet.buildings.values
                .filter { building ->
                    building.isWonder &&
                            (building.requiredTech == null || challenger.tech.isResearched(building.requiredTech!!)) &&
                            civInfo.gameInfo.getCities().none { it.cityConstructions.isBuilt(building.name) }
                }

        if (wonders.isNotEmpty())
            return wonders.random()

        return null
    }

    /**
     * Returns a random [NaturalWonder] not yet discovered by [challenger].
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

        val greatPeople = ruleSet.units.values
                .asSequence()
                .filter { it.isGreatPerson() }
                .map { it.getReplacedUnit(ruleSet) }
                .distinct()
                .filter { !challengerGreatPeople.contains(it) && !cityStateGreatPeople.contains(it) }
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
    //endregion
}


class AssignedQuest(val questName: String = "",
                    val assigner: String = "",
                    val assignee: String = "",
                    val assignedOnTurn: Int = 0,
                    val data1: String = "",
                    val data2: String = "") {

    @Transient
    lateinit var gameInfo: GameInfo

    fun isIndividual(): Boolean = !isGlobal()
    fun isGlobal(): Boolean = gameInfo.ruleSet.quests[questName]!!.isGlobal()
    fun doesExpire(): Boolean = gameInfo.ruleSet.quests[questName]!!.duration > 0
    fun isExpired(): Boolean = doesExpire() && getRemainingTurns() == 0
    fun getDuration(): Int = (gameInfo.gameParameters.gameSpeed.modifier * gameInfo.ruleSet.quests[questName]!!.duration).toInt()
    fun getRemainingTurns(): Int = max(0, (assignedOnTurn + getDuration()) - gameInfo.turns)

    fun getDescription(): String {
        val quest = gameInfo.ruleSet.quests[questName]!!
        return quest.description.fillPlaceholders(data1)
    }

    fun onClickAction() {
        val game = UncivGame.Current

        when (questName) {
            QuestName.ClearBarbarianCamp.value -> {
                game.setWorldScreen()
                game.worldScreen.mapHolder.setCenterPosition(Vector2(data1.toFloat(), data2.toFloat()), selectUnit = false)
            }
            QuestName.Route.value -> {
                game.setWorldScreen()
                game.worldScreen.mapHolder.setCenterPosition(gameInfo.getCivilization(assigner).getCapital().location, selectUnit = false)
            }
        }
    }
}
