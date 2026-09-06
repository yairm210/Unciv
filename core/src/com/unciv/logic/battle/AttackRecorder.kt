package com.unciv.logic.battle

import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile

/**
 * Builds one attack's history. Combat entry points explicitly [finish] and store the result.
 * Identity and visibility are snapshotted before effects; final HP and outcomes are written once.
 * Direct damage, healing, capture and destruction report here. General movement, placement,
 * upgrades and their downstream effects do not propagate the recorder.
 *
 * Never store this temporary object in game state, cached GameContexts, Views or deferred actions.
 * Its API is engine-only; the type is public so unit mutations can accept an optional recorder.
 */
class AttackRecorder internal constructor(
    attacker: ICombatant,
    targetTile: Tile,
    kind: AttackKind = AttackKind.Combat
) {
    private var gameInfo: GameInfo? = attacker.getCivInfo().gameInfo
    private var event: AttackEvent? = null

    private data class ParticipantKey(val kind: AttackParticipantKind, val id: String)

    /** One live participant may have distinct target and interceptor visibility snapshots. */
    private class ParticipantState(var combatant: ICombatant) {
        val records = ArrayList<AttackParticipant>()
        var target: AttackParticipant? = null
        var retainTargetIfUnaffected = false
        var affected = false
        var damageReceived = 0
        var explicitOutcome: AttackParticipantOutcome? = null
    }

    private val participants = LinkedHashMap<ParticipantKey, ParticipantState>()

    init {
        require(targetTile.tileMap.gameInfo === gameInfo) { "An attack cannot target another game instance" }
        val attack = AttackEvent(attacker, targetTile)
        attack.kind = kind
        event = attack
        val state = ParticipantState(attacker)
        state.records.add(attack.attacker!!)
        participants[key(attacker)] = state
    }

    /** Validate once at the recorder boundary; private helpers use the checked event. */
    private fun validate(gameInfo: GameInfo): AttackEvent {
        val attack = currentEvent()
        require(this.gameInfo === gameInfo) { "An attack cannot record effects from another game instance" }
        return attack
    }

    /** A detached engine snapshot for notifications that need the original observation facts. */
    internal fun snapshot(): AttackEvent = currentEvent().clone()

    /**
     * Preserve a target's identity and visibility before combat changes them, without marking it affected.
     * With [retainIfUnaffected] false, discard this snapshot unless an effect or final outcome involves it.
     */
    internal fun snapshotTarget(combatant: ICombatant, retainIfUnaffected: Boolean = true) {
        val attack = validate(combatant.getCivInfo().gameInfo)
        snapshotTarget(getOrCreateParticipantState(combatant), attack, retainIfUnaffected)
    }

    private fun snapshotTarget(state: ParticipantState, attack: AttackEvent, retainIfUnaffected: Boolean) {
        // An attacker in its own blast area already has its attacker record.
        if (state.records.any { it === attack.attacker }) return
        if (state.target == null) {
            val record = AttackParticipant(state.combatant)
            record.damageReceived = state.damageReceived
            state.records.add(record)
            state.target = record
            attack.targets.add(record)
        }
        state.retainTargetIfUnaffected = state.retainTargetIfUnaffected || retainIfUnaffected
    }

    /** Retain all existing target snapshots when a nuclear blast occurs, even if they remain unaffected. */
    internal fun retainAllTargets() {
        currentEvent()
        for (state in participants.values)
            if (state.target != null) state.retainTargetIfUnaffected = true
    }

    /** Civ V shows the blast center to victims even if they could not see it beforehand. */
    internal fun recordNuclearImpact(affectedCivIds: Set<String>, territoryCivIds: Set<String>) {
        val attack = currentEvent()
        check(attack.kind == AttackKind.Nuclear)
        attack.knowsTarget.addAll(affectedCivIds)
        attack.nuclearTerritoryCivIds.addAll(territoryCivIds)
    }

    /** The defender can report its own retaliation, without learning later hidden enemy effects. */
    internal fun recordDefenderRetaliation(attacker: ICombatant, actualDamage: Int) {
        val attack = validate(attacker.getCivInfo().gameInfo)
        if (attack.kind != AttackKind.Combat) return
        attack.defenderRetaliationDamage += actualDamage.coerceAtLeast(0)
        attack.attackerDefeatedByDefender = attack.attackerDefeatedByDefender ||
            (actualDamage > 0 && attacker.isDefeated())
    }

    /** Register attempts before rolling interception, including misses and zero-damage sweeps. */
    internal fun beginInterception(interceptor: MapUnitCombatant): Int {
        val attack = validate(interceptor.getCivInfo().gameInfo)
        val state = getOrCreateParticipantState(interceptor)
        val interception = AttackInterception(interceptor)
        interception.knowsTarget = HashSet(attack.knowsTarget)
        val record = interception.interceptor!!
        record.damageReceived = state.damageReceived
        state.records.add(record)
        val interceptions = attack.interceptions
        interceptions.add(interception)
        return interceptions.lastIndex
    }

    internal fun interceptorSnapshot(index: Int): AttackParticipant =
        currentEvent().interceptions[index].interceptor!!.clone()

    internal fun recordInterception(
        index: Int,
        intercepted: Boolean,
        damageToAttacker: Int = 0,
        damageToInterceptor: Int = 0,
        attackerOutcome: AttackParticipantOutcome? = null,
        interceptorOutcome: AttackParticipantOutcome? = null
    ) {
        val interception = currentEvent().interceptions[index]
        interception.intercepted = intercepted
        interception.damageToAttacker = damageToAttacker
        interception.damageToInterceptor = damageToInterceptor
        interception.attackerOutcome = attackerOutcome
        interception.interceptorOutcome = interceptorOutcome
    }

    internal fun damageReceived(combatant: ICombatant): Int {
        validate(combatant.getCivInfo().gameInfo)
        return participants[key(combatant)]?.damageReceived ?: 0
    }

    /**
     * Mark a unit as affected before healing or capture changes it, preserving its snapshot if needed.
     * Unlike [snapshotTarget], this retains the participant even when the effect deals no damage.
     */
    internal fun markUnitAffected(unit: MapUnit) {
        val attack = validate(unit.civ.gameInfo)
        markAffected(MapUnitCombatant(unit), attack)
    }

    internal fun recordCapture(unit: MapUnit, outcome: AttackParticipantOutcome) {
        val attack = validate(unit.civ.gameInfo)
        val state = markAffected(MapUnitCombatant(unit), attack)
        state.explicitOutcome = outcome
        for (record in state.records) record.captureAttempted = true
    }

    internal fun recordOutcome(combatant: ICombatant, outcome: AttackParticipantOutcome) {
        val attack = validate(combatant.getCivInfo().gameInfo)
        markAffected(combatant, attack).explicitOutcome = outcome
    }

    /** Observe the retreat when it happens without expanding the original attack endpoints. */
    internal fun recordWithdrawal(defender: MapUnitCombatant) {
        val owner = defender.getCivInfo()
        val attack = validate(owner.gameInfo)
        markAffected(defender, attack).explicitOutcome = AttackParticipantOutcome.Withdrew
        attack.withdrawalDestination = defender.getTile().position
        attack.withdrawalKnownBy = owner.gameInfo.civilizations
            .filter { it == owner || defender.isVisibleTo(it) }
            .mapTo(HashSet()) { it.civID }
    }

    /** Preserve the removed improvement's identity for the original target owner's report. */
    internal fun recordImprovementDestroyed(name: String) {
        currentEvent().destroyedImprovement = name
    }

    /** Replacement can destroy an old unit object; survival is resolved by stable ID at finish. */
    internal fun recordDestruction(unit: MapUnit) {
        markUnitAffected(unit)
    }

    internal fun recordDamage(unit: MapUnit, actualDamage: Int) =
        recordDamage(MapUnitCombatant(unit), actualDamage)

    internal fun recordDamage(city: City, actualDamage: Int) =
        recordDamage(CityCombatant(city), actualDamage)

    private fun recordDamage(combatant: ICombatant, actualDamage: Int) {
        val attack = validate(combatant.getCivInfo().gameInfo)
        if (actualDamage <= 0) return
        val state = markAffected(combatant, attack)
        state.damageReceived += actualDamage
        for (record in state.records) record.damageReceived = state.damageReceived
    }

    private fun key(combatant: ICombatant): ParticipantKey = when (combatant) {
        is MapUnitCombatant -> ParticipantKey(AttackParticipantKind.Unit, combatant.unit.id.toString())
        is CityCombatant -> ParticipantKey(AttackParticipantKind.City, combatant.city.id)
        else -> error("Unsupported attack participant: $combatant")
    }

    private fun getOrCreateParticipantState(combatant: ICombatant): ParticipantState {
        return participants.getOrPut(key(combatant)) { ParticipantState(combatant) }
            .also { it.combatant = combatant }
    }

    private fun markAffected(combatant: ICombatant, attack: AttackEvent): ParticipantState {
        val state = getOrCreateParticipantState(combatant)
        if (state.records.isEmpty()) snapshotTarget(state, attack, retainIfUnaffected = false)
        state.affected = true
        return state
    }

    private fun currentEvent(): AttackEvent = checkNotNull(event) { "This attack has already finished" }

    /** Finalize exactly once; the caller then stores the returned record in its GameInfo. */
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
            val unaffectedTargets = participants.values
                .filter { !it.retainTargetIfUnaffected && !it.affected }
                .mapNotNull { it.target }
                .toSet()
            attack.targets.removeAll(unaffectedTargets)
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
        val original = state.records.firstOrNull() ?: return
        val survivingUnit = if (combatant is MapUnitCombatant)
            original.unitId?.let { combatant.unit.civ.units.getUnitById(it) }
        else null
        val captured = combatant.getCivInfo().civID != original.civId && when (combatant) {
            is MapUnitCombatant -> survivingUnit != null
            is CityCombatant -> combatant.city in combatant.city.civ.cities
            else -> false
        }
        val destroyed = when (combatant) {
            is MapUnitCombatant -> survivingUnit == null
            is CityCombatant -> combatant.city !in combatant.city.civ.cities
            else -> false
        }
        // Reconcile original battlefield casualties after movement, placement or city teardown,
        // without propagating the recorder into those systems or inferring damage from net HP loss.
        state.affected = state.affected || captured || destroyed
            || (combatant is MapUnitCombatant && combatant.unit.isDestroyed)
        val explicitOutcome = state.explicitOutcome.takeIf {
            it == AttackParticipantOutcome.Captured || it == AttackParticipantOutcome.Withdrew
                || it == AttackParticipantOutcome.Raided
        }
        val outcome = explicitOutcome ?: when {
            captured -> AttackParticipantOutcome.Captured
            destroyed -> AttackParticipantOutcome.Destroyed
            combatant is CityCombatant && combatant.isDefeated() -> AttackParticipantOutcome.DefensesReduced
            else -> AttackParticipantOutcome.Survived
        }
        val healthAfter = survivingUnit?.health ?: combatant.getHealth()
        for (record in state.records) {
            record.healthAfter = healthAfter
            record.outcome = outcome
        }
    }
}
