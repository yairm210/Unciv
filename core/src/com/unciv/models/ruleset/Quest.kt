package com.unciv.models.ruleset

import com.unciv.models.stats.INamed

enum class QuestType {
    Individual,
    Global
}

/** [Quest] class holds all functionality relative to a quest */
class Quest : INamed {

    /** Unique identifier name of the quest, it is also shown */
    override var name: String = ""

    /** Descrption of the quest shown to players */
    var description: String = ""

    /** [QuestType]: it is either Individual or Global */
    var type: QuestType = QuestType.Individual

    /** Influence reward gained on quest completion */
    var influece: Float = 40f

    /** Maximum number of turns to complete the quest, 0 if there's no turn limit */
    var duration: Int = 0

    /**Minimum number of [CivInfo] needed to start the quest. It is meaningful only for [QuestType.Global]
     * quests [type]. */
    var minimumCivs: Int = 1

    /** Checks if [this] is a Global quest */
    fun isGlobal(): Boolean = type == QuestType.Global
    fun isIndividual(): Boolean = !isGlobal()
}