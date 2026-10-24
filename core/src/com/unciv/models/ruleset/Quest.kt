package com.unciv.models.ruleset

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.quests.QuestImplementation
import com.unciv.logic.civilization.managers.quests.IQuestImplementation
import com.unciv.models.stats.INamed
import yairm210.purity.annotations.Readonly

enum class QuestName(
    val value: String,
    private val implementation: IQuestImplementation
) : IQuestImplementation by implementation {
    None("", QuestImplementation.None()),
    Route("Route", QuestImplementation.Route()),
    ClearBarbarianCamp("Clear Barbarian Camp", QuestImplementation.ClearBarbarianCamp()),
    ConstructWonder("Construct Wonder", QuestImplementation.ConstructWonder()),
    ConnectResource("Connect Resource", QuestImplementation.ConnectResource()),
    GreatPerson("Acquire Great Person", QuestImplementation.GreatPerson()),
    ConquerCityState("Conquer City State", QuestImplementation.ConquerCityState()),
    FindPlayer("Find Player", QuestImplementation.FindPlayer()),
    FindNaturalWonder("Find Natural Wonder", QuestImplementation.FindNaturalWonder()),
    GiveGold("Give Gold", QuestImplementation.GiveGold()),
    PledgeToProtect("Pledge to Protect", QuestImplementation.PledgeToProtect()),
    ContestCulture("Contest Culture", QuestImplementation.ContestCulture()),
    ContestFaith("Contest Faith", QuestImplementation.ContestFaith()),
    ContestTech("Contest Technologies", QuestImplementation.ContestTech()),
    Invest("Invest", QuestImplementation.Invest()),
    BullyCityState("Bully City State", QuestImplementation.BullyCityState()),
    DenounceCiv("Denounce Civilization", QuestImplementation.DenounceCiv()),
    SpreadReligion("Spread Religion", QuestImplementation.SpreadReligion()),
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
