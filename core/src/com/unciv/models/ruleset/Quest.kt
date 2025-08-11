package com.unciv.models.ruleset

import com.unciv.logic.civilization.Civilization
import com.unciv.models.stats.INamed
import yairm210.purity.annotations.Readonly

enum class QuestName(val value: String) {
    Route("Route"),
    ClearBarbarianCamp("Clear Barbarian Camp"),
    ConstructWonder("Construct Wonder"),
    ConnectResource("Connect Resource"),
    GreatPerson("Acquire Great Person"),
    ConquerCityState("Conquer City State"),
    FindPlayer("Find Player"),
    FindNaturalWonder("Find Natural Wonder"),
    GiveGold("Give Gold"),
    PledgeToProtect("Pledge to Protect"),
    ContestCulture("Contest Culture"),
    ContestFaith("Contest Faith"),
    ContestTech("Contest Technologies"),
    Invest("Invest"),
    BullyCityState("Bully City State"),
    DenounceCiv("Denounce Civilization"),
    SpreadReligion("Spread Religion"),
    None("")
    ;
    companion object {
        fun find(value: String) = entries.firstOrNull { it.value == value } ?: None
    }
}

enum class QuestType {
    Individual,
    Global
}

/** [Quest] class holds all functionality relative to a quest */
// Notes: This is **not** `IsPartOfGameInfoSerialization`, only Ruleset.
// Saves contain [QuestManager]s instead, which contain lists of [AssignedQuest] instances.
// These are matched to this Quest **by name**.
class Quest : INamed {

    /** Unique identifier name of the quest, it is also shown.
     *  Must match a [QuestName.value] for the Quest to have any functionality. */
    override var name: String = ""

    val questNameInstance by lazy { QuestName.find(name) }  // lazy only ensures evaluation happens after deserialization, all will be 'triggered'

    /** Description of the quest shown to players */
    var description: String = ""

    /** [QuestType]: it is either Individual or Global */
    var type: QuestType = QuestType.Individual

    /** Influence reward gained on quest completion */
    var influence: Float = 40f

    /** Maximum number of turns to complete the quest, 0 if there's no turn limit */
    var duration: Int = 0

    /** Minimum number of [Civilization]s needed to start the quest. It is meaningful only for [QuestType.Global]
     *  quests [type]. */
    var minimumCivs: Int = 1

    /** Certain city states are more likely to give certain quests
     * This is based on both personality and city-state type
     * Both are mapped here as 'how much to multiply the weight of this quest for this kind of city-state' */
    var weightForCityStateType = HashMap<String, Float>()

    /** Checks if `this` is a Global quest */
    @Readonly fun isGlobal(): Boolean = type == QuestType.Global
    @Readonly fun isIndividual(): Boolean = !isGlobal()
}
