package com.unciv.view

import com.unciv.logic.battle.AttackParticipantKind
import com.unciv.logic.battle.AttackParticipantOutcome
import com.unciv.logic.map.HexCoord

/** Only permitted identity fields. An unknown city has a null name; enemy custom names are omitted. */
data class ObservedCombatant(
    val kind: AttackParticipantKind,
    val name: String?,
    val instanceName: String?,
    val isOwn: Boolean
)

/** Detached reports bound to a single recipient, suitable for notifications or a future combat log. */
sealed interface ObservedCombatReport {
    val turn: Int
}

data class ObservedAttackResult(
    override val turn: Int,
    val attacker: ObservedCombatant,
    val target: ObservedCombatant,
    val outcome: AttackParticipantOutcome,
    val damageReceived: Int,
    val retaliationDamage: Int,
    val attackerDefeatedByDefender: Boolean,
    val showZeroDamage: Boolean,
    val locations: List<HexCoord>
) : ObservedCombatReport

data class ObservedInterception(
    override val turn: Int,
    val ourUnit: ObservedCombatant,
    val enemyUnit: ObservedCombatant,
    val ourDamage: Int,
    val enemyDamage: Int,
    val ourDestroyed: Boolean,
    val enemyDestroyed: Boolean,
    val isAttacking: Boolean,
    val locations: List<HexCoord>
) : ObservedCombatReport

data class ObservedNuclearDetonation(
    override val turn: Int,
    val weaponType: String,
    val attackingCivilizationName: String?,
    val hitOurTerritory: Boolean,
    val location: HexCoord?
) : ObservedCombatReport

data class ObservedUnopposedAirSweep(
    override val turn: Int,
    val unitType: String
) : ObservedCombatReport

data class ObservedWithdrawal(
    override val turn: Int,
    val attacker: ObservedCombatant,
    val target: ObservedCombatant,
    val locations: List<HexCoord>
) : ObservedCombatReport

data class ObservedImprovementDestruction(
    override val turn: Int,
    val attacker: ObservedCombatant,
    val improvementName: String,
    val locations: List<HexCoord>
) : ObservedCombatReport
