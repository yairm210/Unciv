package com.unciv.logic.civilization.managers.quests

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.DiplomacyAction
import com.unciv.logic.civilization.NotificationAction
import com.unciv.models.ruleset.Quest
import yairm210.purity.annotations.Readonly

interface IQuestImplementation {
    @Readonly
    fun isValid(civ: Civilization, challenger: Civilization): Boolean = true
    @Readonly
    fun getAssignedQuestData(civ: Civilization, quest: Quest, assignee: Civilization) = "" to ""
    @Readonly
    fun getNotificationActions(civ: Civilization, assignedQuest: AssignedQuest): List<NotificationAction> =
        listOf(DiplomacyAction(civ))
    @Readonly
    fun isComplete(civ: Civilization, assignedQuest: AssignedQuest): Boolean
    @Readonly
    fun isObsolete(civ: Civilization, assignedQuest: AssignedQuest): Boolean = false
}
