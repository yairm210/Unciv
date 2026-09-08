package com.unciv.logic.notifications

import com.unciv.logic.battle.AttackParticipantOutcome
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.view.ObservedAttackResult

/** Formats a recipient's permitted combat result without consulting game state. */
internal object AttackResultNotifications {
    fun create(report: ObservedAttackResult): Notification? {
        val (actionIcon, actionText) = when (report.outcome) {
            AttackParticipantOutcome.Destroyed, AttackParticipantOutcome.DefensesReduced ->
                NotificationIcon.Death to "has destroyed"
            AttackParticipantOutcome.Captured -> NotificationIcon.War to "has captured"
            AttackParticipantOutcome.Raided -> NotificationIcon.War to "has raided"
            AttackParticipantOutcome.Pending, AttackParticipantOutcome.Withdrew -> return null
            else -> if (report.attackerDefeatedByDefender)
                NotificationIcon.War to "was destroyed while attacking"
            else NotificationIcon.War to "has attacked"
        }
        val attackerText = CombatNotificationText.attackerDescription(report.attacker)
        val attackerDamageText = if (report.retaliationDamage > 0) " ([-${report.retaliationDamage}] HP)" else ""
        val ownedTarget = CombatNotificationText.ownedDescription(report.target)
        val targetText = if (report.outcome == AttackParticipantOutcome.DefensesReduced)
            "the defence of $ownedTarget"
        else ownedTarget
        val targetDamageText = if (report.damageReceived > 0 || report.showZeroDamage)
            " ([-${report.damageReceived}] HP)"
        else ""
        return Notification(
            "$attackerText$attackerDamageText $actionText $targetText$targetDamageText",
            arrayOf(CombatNotificationText.icon(report.attacker), actionIcon, CombatNotificationText.icon(report.target)),
            report.locations.map { LocationAction(it) },
            NotificationCategory.War
        )
    }
}
