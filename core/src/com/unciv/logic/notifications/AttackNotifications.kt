package com.unciv.logic.notifications

import com.unciv.logic.civilization.Notification
import com.unciv.view.AttackEventsView
import com.unciv.view.ObservedAttackResult
import com.unciv.view.ObservedImprovementDestruction
import com.unciv.view.ObservedInterception
import com.unciv.view.ObservedNuclearDetonation
import com.unciv.view.ObservedUnopposedAirSweep
import com.unciv.view.ObservedWithdrawal

/** Formats only the facts permitted by the recipient's View. No publisher can read raw attack records. */
internal object AttackNotifications {
    fun create(view: AttackEventsView): List<Notification> = view.getCombatReports().mapNotNull { report ->
        when (report) {
            is ObservedAttackResult -> AttackResultNotifications.create(report)
            is ObservedInterception -> AirInterceptionNotifications.create(report)
            is ObservedNuclearDetonation -> NuclearAttackNotifications.create(report)
            is ObservedUnopposedAirSweep -> AirInterceptionNotifications.create(report)
            is ObservedWithdrawal -> CombatEffectNotifications.create(report)
            is ObservedImprovementDestruction -> CombatEffectNotifications.create(report)
        }
    }

    /** Captures can also happen during movement, without a stored combat event. */
    fun createCapture(view: AttackEventsView): List<Notification> =
        view.getCaptureReports().mapNotNull(AttackResultNotifications::create)
}
