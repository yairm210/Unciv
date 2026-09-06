package com.unciv.logic.battle

import com.unciv.logic.GameInfo
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile

/**
 * Builds a detached history record for combat between units. The caller explicitly [finish]es
 * the record; recording never stores history or publishes notifications as a side effect.
 * Identity and visibility are captured before effects; final HP and outcomes are written once.
 * Callers report direct effects explicitly. This object does not execute or intercept unit mutations.
 *
 * Never store this temporary object in game state, cached GameContexts, Views or deferred actions.
 * Its operations are engine-only; raw records must not be exposed through user-facing APIs.
 */
class AttackRecorder internal constructor(attacker: MapUnitCombatant, targetTile: Tile) {
    private var gameInfo: GameInfo? = attacker.getCivInfo().gameInfo
    private var event: AttackEvent? = null

    private class ParticipantState(var unit: MapUnit, val record: AttackParticipant) {
        var explicitOutcome: AttackParticipantOutcome? = null
    }

    private val participants = LinkedHashMap<Int, ParticipantState>()

    init {
        require(targetTile.tileMap.gameInfo === gameInfo) { "An attack cannot target another game instance" }
        val attack = AttackEvent(attacker, targetTile)
        event = attack
        participants[attacker.unit.id] = ParticipantState(attacker.unit, attack.attacker!!)
    }

    /** Validate once at the recorder boundary, before a unit mutation can take place. */
    private fun validate(gameInfo: GameInfo): AttackEvent {
        val attack = currentEvent()
        require(this.gameInfo === gameInfo) { "An attack cannot record effects from another game instance" }
        return attack
    }

    /** Preserve the intended target even when the attack deals no damage. */
    internal fun snapshotTarget(combatant: MapUnitCombatant) {
        val attack = validate(combatant.getCivInfo().gameInfo)
        getOrCreateParticipantState(combatant.unit, attack)
    }

    /** Capture identity and initial health before healing or another direct effect changes a unit. */
    internal fun markUnitAffected(unit: MapUnit) {
        val attack = validate(unit.civ.gameInfo)
        getOrCreateParticipantState(unit, attack)
    }

    /** Capture and withdrawal must be reported explicitly when ordinary survival cannot describe them. */
    internal fun recordOutcome(combatant: MapUnitCombatant, outcome: AttackParticipantOutcome) {
        val attack = validate(combatant.getCivInfo().gameInfo)
        require(outcome == AttackParticipantOutcome.Captured || outcome == AttackParticipantOutcome.Withdrew)
        getOrCreateParticipantState(combatant.unit, attack).explicitOutcome = outcome
    }

    /** Replacement can destroy an old unit object; survival is resolved by stable ID at finish. */
    internal fun recordDestruction(unit: MapUnit) {
        markUnitAffected(unit)
    }

    /** Report clamped HP loss before applying it; healing never subtracts from accumulated damage. */
    internal fun recordDamage(unit: MapUnit, actualDamage: Int) {
        val attack = validate(unit.civ.gameInfo)
        if (actualDamage <= 0) return
        getOrCreateParticipantState(unit, attack).record.damageReceived += actualDamage
    }

    private fun getOrCreateParticipantState(unit: MapUnit, attack: AttackEvent): ParticipantState =
        participants.getOrPut(unit.id) {
            val record = AttackParticipant(MapUnitCombatant(unit))
            attack.targets.add(record)
            ParticipantState(unit, record)
        }.also { it.unit = unit }

    private fun currentEvent(): AttackEvent = checkNotNull(event) { "This attack has already finished" }

    /** Finalize exactly once. Storage and any later delivery are the caller's responsibility. */
    internal fun finish(resolution: AttackResolution): AttackEvent {
        require(resolution != AttackResolution.Pending) { "Use finishIncomplete for a failed attack" }
        return finishRecording(resolution)
    }

    /** Preserve partial history on an explicit exception path; combat mutations are not rolled back. */
    internal fun finishIncomplete(): AttackEvent = finishRecording(AttackResolution.Pending)

    private fun finishRecording(resolution: AttackResolution): AttackEvent {
        val attack = currentEvent()
        try {
            for (state in participants.values) finishParticipant(state)
            attack.resolution = resolution
            return attack
        } finally {
            participants.clear()
            event = null
            gameInfo = null
        }
    }

    private fun finishParticipant(state: ParticipantState) {
        val unit = state.unit
        val record = state.record
        val survivingUnit = record.unitId?.let { unit.civ.units.getUnitById(it) }
        val captured = unit.civ.civID != record.civId && survivingUnit != null
        record.outcome = state.explicitOutcome ?: when {
            captured -> AttackParticipantOutcome.Captured
            survivingUnit == null -> AttackParticipantOutcome.Destroyed
            else -> AttackParticipantOutcome.Survived
        }
        record.healthAfter = survivingUnit?.health ?: unit.health
    }
}
