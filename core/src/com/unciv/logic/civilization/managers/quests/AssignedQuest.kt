package com.unciv.logic.civilization.managers.quests

import com.unciv.GUI
import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.QuestName
import com.unciv.models.translations.fillPlaceholders
import com.unciv.ui.components.fonts.Fonts
import yairm210.purity.annotations.Readonly

class AssignedQuest(
    val questName: String = "",
    val assigner: String = "",
    val assignee: String = "",
    val assignedOnTurn: Int = 0,
    val data1: String = "",
    val data2: String = "",
) : IsPartOfGameInfoSerialization {
    @Suppress("unused") // Used in deserialization
    constructor(): this("")

    constructor(
        gameInfo: GameInfo,
        quest: Quest,
        assigner: Civilization,
        assignee: Civilization,
        assignedOnTurn: Int = 0,
        data1: String = "",
        data2: String = ""
    ) : this(quest.name, assigner.civID, assignee.civID, assignedOnTurn, data1, data2) {
        this.gameInfo = gameInfo
        this.quest = quest
        this.assignerCiv = assigner
        this.assigneeCiv = assignee
    }

    @Transient
    lateinit var assignerCiv: Civilization
        private set

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

    @Readonly fun isIndividual(): Boolean = !isGlobal()
    @Readonly fun isGlobal(): Boolean = quest.isGlobal()
    @Suppress("MemberVisibilityCanBePrivate")
    @Readonly fun doesExpire(): Boolean = quest.duration > 0
    @Readonly fun isExpired(): Boolean = doesExpire() && getRemainingTurns() == 0
    @Suppress("MemberVisibilityCanBePrivate")
    @Readonly fun getDuration(): Int = (gameInfo.speed.modifier * quest.duration).toInt()
    @Readonly fun getRemainingTurns(): Int = (assignedOnTurn + getDuration() - gameInfo.turns).coerceAtLeast(0)
    @Readonly fun getInfluence() = quest.influence

    @Readonly fun getDescription(): String = quest.description.fillPlaceholders(data1)

    fun onClickAction() {
        when (questNameInstance) {
            QuestName.ClearBarbarianCamp -> {
                GUI.resetToWorldScreen()
                GUI.getMap().setCenterPosition(HexCoord(data1.toInt(), data2.toInt()), selectUnit = false)
            }
            QuestName.Route -> {
                GUI.resetToWorldScreen()
                GUI.getMap().setCenterPosition(assignerCiv.getCapital()!!.location.toHexCoord(), selectUnit = false)
            }
            QuestName.BullyCityState, QuestName.ConquerCityState -> {
                // In case they were destroyed after issuing the quest
                @Suppress("DEPRECATION")
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
        else "$questName(${quest.type}, assigner=$assigner, assignee=$assignee, d1=$data1, d2=$data2, ${Fonts.turn}$assignedOnTurn)"
}
