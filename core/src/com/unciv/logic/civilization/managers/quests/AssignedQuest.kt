package com.unciv.logic.civilization.managers.quests

import com.badlogic.gdx.math.Vector2
import com.unciv.GUI
import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.toHexCoord
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.QuestName
import com.unciv.models.translations.fillPlaceholders
import com.unciv.ui.components.fonts.Fonts
import yairm210.purity.annotations.Readonly

class AssignedQuest : IsPartOfGameInfoSerialization {

    val questName: String
    val assignedOnTurn: Int
    val data1: String
    val data2: String

    constructor(
        gameInfo: GameInfo,
        quest: Quest,
        assigner: Civilization,
        assignee: Civilization,
        assignedOnTurn: Int = 0,
        data1: String = "",
        data2: String = ""
    ) {
        this.gameInfo = gameInfo
        this.quest = quest
        this.questName = quest.name
        this.assignedOnTurn = assignedOnTurn
        this.data1 = data1
        this.data2 = data2
        this.assigner = assigner.civID
        this.assignerCiv = assigner
        this.assignee = assignee.civID
        this.assigneeCiv = assignee
    }

    @Suppress("unused") // Used in deserialization
    constructor(): this("", "", "", 0, "", "")
    @Suppress("unused")
    constructor(
        questName: String = "",
        assigner: String = "",
        assignee: String = "",
        assignedOnTurn: Int = 0,
        data1: String = "",
        data2: String = ""
    ) {
        this.questName = questName
        this.assignedOnTurn = assignedOnTurn
        this.data1 = data1
        this.data2 = data2
        this.assigner = assigner
        this.assignee = assignee
    }

    val assigner: String

    @Transient
    lateinit var assignerCiv: Civilization
        private set

    val assignee: String

    @Transient
    lateinit var assigneeCiv: Civilization
        private set

    @Transient
    private lateinit var gameInfo: GameInfo

    @Transient
    lateinit var quest: Quest
        private set

    val questNameInstance get() = quest.questNameInstance

    fun clone() = AssignedQuest(questName, assigner, assignee, assignedOnTurn, data1, data2)

    internal fun setTransients(gameInfo: GameInfo, quest: Quest? = null) {
        this.gameInfo = gameInfo
        if (quest != null) {
            this.quest = quest
        }
        else if (!::quest.isInitialized) {
            this.quest = gameInfo.ruleset.quests[questName]!!
        }
        if (!::assignerCiv.isInitialized)
            assignerCiv = gameInfo.getCivilization(assigner)
        if (!::assigneeCiv.isInitialized)
            assigneeCiv = gameInfo.getCivilization(assignee)
    }

    @Readonly
    fun isIndividual(): Boolean = !isGlobal()
    @Readonly
    fun isGlobal(): Boolean = quest.isGlobal()
    @Suppress("MemberVisibilityCanBePrivate")
    @Readonly
    fun doesExpire(): Boolean = quest.duration > 0
    @Readonly
    fun isExpired(): Boolean = doesExpire() && getRemainingTurns() == 0
    @Suppress("MemberVisibilityCanBePrivate")
    @Readonly
    fun getDuration(): Int = (gameInfo.speed.modifier * quest.duration).toInt()
    @Readonly
    fun getRemainingTurns(): Int = (assignedOnTurn + getDuration() - gameInfo.turns).coerceAtLeast(0)
    @Readonly
    fun getInfluence() = quest.influence

    @Readonly
    fun getDescription(): String = quest.description.fillPlaceholders(data1)

    fun onClickAction() {
        when (questNameInstance) {
            QuestName.ClearBarbarianCamp -> {
                GUI.resetToWorldScreen()
                GUI.getMap().setCenterPosition(Vector2(data1.toFloat(), data2.toFloat()).toHexCoord(), selectUnit = false)
            }
            QuestName.Route -> {
                GUI.resetToWorldScreen()
                GUI.getMap().setCenterPosition(assignerCiv.getCapital()!!.location.toHexCoord(), selectUnit = false)
            }
            QuestName.BullyCityState, QuestName.ConquerCityState -> {
                // In case they were destroyed after issuing the quest
                val targetCs = gameInfo.getAliveCityStates().firstOrNull { it.civID == data1 }
                if (targetCs != null) {
                    // Did they even settle their first city?
                    val capital = targetCs.getCapital()
                    if (capital != null) {
                        GUI.resetToWorldScreen()
                        GUI.getMap().setCenterPosition(capital.location.toHexCoord(), selectUnit = false)
                    }
                }
            }
            else -> Unit
        }
    }

    /** Debug visualization only */
    override fun toString() = if (!::quest.isInitialized) "(uninitialized)"
        else "$questName(${quest.type}, assigner=$assigner, assignee=$assignee, d2=$data1, d1=$data2, ${Fonts.turn}$assignedOnTurn)"
}
