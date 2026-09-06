package com.unciv.logic.notifications

import com.unciv.logic.civilization.CivilopediaAction
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.view.ObservedNuclearDetonation

/** Formats only the launch identity and explosion location permitted by the View. */
internal object NuclearAttackNotifications {
    fun create(report: ObservedNuclearDetonation): Notification {
        val civilizationName = report.attackingCivilizationName
        val text = when {
            civilizationName == null -> "A(n) [${report.weaponType}] has been detonated by [an unknown civilization]!"
            report.hitOurTerritory -> "A(n) [${report.weaponType}] from [$civilizationName] has exploded in our territory!"
            else -> "A(n) [${report.weaponType}] has been detonated by [$civilizationName]!"
        }
        val icons = if (civilizationName != null)
            arrayOf(civilizationName, NotificationIcon.War, report.weaponType)
        else arrayOf(NotificationIcon.War, report.weaponType)
        val actions = listOfNotNull(
            report.location?.let { LocationAction(it) },
            CivilopediaAction("Units/" + report.weaponType)
        )
        return Notification(text, icons, actions, NotificationCategory.War)
    }
}
