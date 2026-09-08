package com.unciv.logic.notifications

import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.view.ObservedInterception
import com.unciv.view.ObservedUnopposedAirSweep

/** Formats detached, permitted reports supplied by AttackEventsView. */
internal object AirInterceptionNotifications {
    fun create(report: ObservedInterception): Notification {
        val ourName = report.ourUnit.name ?: "Unknown unit"
        val enemyName = report.enemyUnit.name ?: "Unknown unit"
        val ourDamage = report.ourDamage
        val enemyDamage = report.enemyDamage
        val text = if (report.isAttacking) when {
            report.ourDestroyed && report.enemyDestroyed ->
                "Our [$ourName] ([-$ourDamage] HP) and an intercepting [$enemyName] ([-$enemyDamage] HP) were both destroyed"
            report.ourDestroyed ->
                "Our [$ourName] ([-$ourDamage] HP) was destroyed by an intercepting [$enemyName] ([-$enemyDamage] HP)"
            report.enemyDestroyed ->
                "Our [$ourName] ([-$ourDamage] HP) destroyed an intercepting [$enemyName] ([-$enemyDamage] HP)"
            else ->
                "Our [$ourName] ([-$ourDamage] HP) was attacked by an intercepting [$enemyName] ([-$enemyDamage] HP)"
        } else when {
            report.ourDestroyed && report.enemyDestroyed ->
                "Our [$ourName] ([-$ourDamage] HP) and an enemy [$enemyName] ([-$enemyDamage] HP) were both destroyed during interception"
            report.enemyDestroyed ->
                "Our [$ourName] ([-$ourDamage] HP) intercepted and destroyed an enemy [$enemyName] ([-$enemyDamage] HP)"
            report.ourDestroyed ->
                "Our [$ourName] ([-$ourDamage] HP) intercepted and was destroyed by an enemy [$enemyName] ([-$enemyDamage] HP)"
            else ->
                "Our [$ourName] ([-$ourDamage] HP) intercepted and attacked an enemy [$enemyName] ([-$enemyDamage] HP)"
        }
        return Notification(
            text,
            listOfNotNull(report.ourUnit.name, NotificationIcon.War, report.enemyUnit.name).toTypedArray(),
            report.locations.map { LocationAction(it) },
            NotificationCategory.War
        )
    }

    fun create(report: ObservedUnopposedAirSweep): Notification = Notification(
        "Nothing tried to intercept our [${report.unitType}]",
        arrayOf(report.unitType), emptyList(), NotificationCategory.War
    )
}
