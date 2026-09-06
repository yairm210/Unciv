package com.unciv.logic.notifications

import com.unciv.logic.battle.AttackParticipantKind
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.view.ObservedCombatant

/** Text choices only: identities have already been filtered by AttackEventsView. */
internal object CombatNotificationText {
    fun attackerDescription(attacker: ObservedCombatant): String {
        if (attacker.isOwn) return ownedDescription(attacker, "Our ")
        return when {
            attacker.kind == AttackParticipantKind.City && attacker.name == null -> "An enemy city"
            attacker.kind == AttackParticipantKind.City -> "Enemy city [${attacker.name}]"
            attacker.name == null -> "An enemy unit"
            else -> "An enemy [${attacker.name}]"
        }
    }

    fun ownedDescription(participant: ObservedCombatant, leadingText: String = "our "): String =
        when {
            participant.kind == AttackParticipantKind.City -> "[${participant.name}]"
            !participant.instanceName.isNullOrEmpty() -> "[${participant.instanceName}]"
            else -> "$leadingText[${participant.name}]"
        }

    fun icon(participant: ObservedCombatant): String =
        if (participant.kind == AttackParticipantKind.City) NotificationIcon.City
        else participant.name ?: NotificationIcon.War
}
