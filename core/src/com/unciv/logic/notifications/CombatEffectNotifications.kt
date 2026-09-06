package com.unciv.logic.notifications

import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.view.ObservedImprovementDestruction
import com.unciv.view.ObservedWithdrawal

/** Formats combat effects using only identities and locations permitted by the recipient's View. */
internal object CombatEffectNotifications {
    fun create(report: ObservedWithdrawal): Notification {
        val attackerName = report.attacker.name ?: "Unknown unit"
        val targetName = report.target.name ?: "Unknown unit"
        return Notification(
            "[$targetName] withdrew from a [$attackerName]",
            arrayOf(CombatNotificationText.icon(report.target), NotificationIcon.War, CombatNotificationText.icon(report.attacker)),
            report.locations.map { LocationAction(it) },
            NotificationCategory.War
        )
    }

    fun create(report: ObservedImprovementDestruction): Notification {
        val attackerName = report.attacker.name ?: "Unknown unit"
        return Notification(
            "An enemy [$attackerName] has destroyed our tile improvement [${report.improvementName}]",
            arrayOf(CombatNotificationText.icon(report.attacker), NotificationIcon.War),
            report.locations.map { LocationAction(it) },
            NotificationCategory.War
        )
    }
}
