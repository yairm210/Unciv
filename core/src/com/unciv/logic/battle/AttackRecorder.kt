package com.unciv.logic.battle

import com.unciv.logic.GameInfo
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
class AttackRecorder internal constructor(attacker: ICombatant, targetTile: Tile) {
    private var gameInfo: GameInfo? = attacker.getCivInfo().gameInfo
    private var event: AttackEvent? = null

    private class ParticipantState(var combatant: ICombatant, val record: AttackParticipant) {
        var explicitOutcome: AttackParticipantOutcome? = null
    }

    private val participants = LinkedHashMap<String, ParticipantState>()

    init {
        require(targetTile.tileMap.gameInfo === gameInfo) { "An attack cannot target another game instance" }
        val attack = AttackEvent(attacker, targetTile)
        event = attack
        participants[attacker.getId()] = ParticipantState(attacker, attack.attacker!!)
    }

    /** Validate once at the recorder boundary, before a unit mutation can take place. */
    private fun validate(gameInfo: GameInfo): AttackEvent {
        val attack = currentEvent()
        require(this.gameInfo === gameInfo) { "An attack cannot record effects from another game instance" }
        return attack
    }

    /** Preserve the intended target even when the attack deals no damage. */
    internal fun snapshotTarget(combatant: ICombatant) {
        val attack = validate(combatant.getCivInfo().gameInfo)
        getOrCreateParticipantState(combatant, attack)
    }

    /** Capture identity and initial health before healing or another direct effect changes a unit. */
    internal fun markUnitAffected(combatant: ICombatant) {
        val attack = validate(combatant.getCivInfo().gameInfo)
        getOrCreateParticipantState(combatant, attack)
    }

    /** Capture and withdrawal must be reported explicitly when ordinary survival cannot describe them. */
    internal fun recordOutcome(combatant: ICombatant, outcome: AttackParticipantOutcome?) {
        val attack = validate(combatant.getCivInfo().gameInfo)
        require(outcome == AttackParticipantOutcome.Captured || outcome == AttackParticipantOutcome.Withdrew)
        getOrCreateParticipantState(combatant, attack)?.explicitOutcome = outcome
    }

    /** Replacement can destroy an old unit object; survival is resolved by stable ID at finish. */
    internal fun recordDestruction(combatant: ICombatant) {
        markUnitAffected(combatant)
    }

    /** Report clamped HP loss before applying it; healing never subtracts from accumulated damage. */
    internal fun recordDamage(combatantId: String, actualDamage: Int) {
        currentEvent()
        if (actualDamage <= 0) return
        val participant = requireNotNull(participants[combatantId]) {
            "Register the participant before reporting damage: $combatantId"
        }
        participant.record.damageReceived += actualDamage
    }

    private fun getOrCreateParticipantState(combatant: ICombatant, attack: AttackEvent): ParticipantState {
        val id = combatant.getId()
        return participants.getOrPut(id) {
            val record = AttackParticipant(combatant)
            attack.targets.add(record)
            ParticipantState(combatant, record)
        }
    }

    private fun currentEvent(): AttackEvent = checkNotNull(event) { "This attack has already finished" }

    /** Finalize exactly once. Storage and any later delivery are the caller's responsibility. */
    internal fun finish(resolution: AttackResolution?): AttackEvent {
        require(resolution != null) { "Use finishIncomplete for a failed attack" }
        return finishRecording(resolution)
    }

    /** Preserve partial history on an explicit exception path; combat mutations are not rolled back. */
    internal fun finishIncomplete(): AttackEvent = finishRecording(null)

    private fun finishRecording(resolution: AttackResolution?): AttackEvent {
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
        val combatant = state.combatant
        val record = state.record
        val currentCombatant = getCurrentCombatant(combatant)
        
        record.outcome = state.explicitOutcome ?: when {
            currentCombatant == null -> AttackParticipantOutcome.Destroyed
            currentCombatant.getCivInfo().civID != record.civID -> AttackParticipantOutcome.Captured
            else -> AttackParticipantOutcome.Survived
        }
        record.healthAfter = currentCombatant?.getHealth() ?: combatant.getHealth()
    }

    private fun getCurrentCombatant(combatant: ICombatant): ICombatant? = when (combatant) {
        is MapUnitCombatant -> 
            gameInfo!!.civilizations
            .firstNotNullOfOrNull { it.units.getUnitById(combatant.unit.id) }
            ?.let { MapUnitCombatant(it) }
        
        is CityCombatant -> combatant // TODO: Resolve the current city from game state.
        
        else -> error("Unknown combatant type: $combatant")
    }
}
