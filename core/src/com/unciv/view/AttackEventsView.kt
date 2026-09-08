package com.unciv.view

import com.unciv.logic.battle.AttackEvent
import com.unciv.logic.battle.AttackKind
import com.unciv.logic.battle.AttackParticipant
import com.unciv.logic.battle.AttackParticipantKind
import com.unciv.logic.battle.AttackParticipantOutcome
import com.unciv.logic.battle.AttackResolution
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
import java.util.Collections

/**
 * Read-only attack history bound to one GameView's perspective.
 *
 * Deliberately does not extend View: another View must not be able to unwrap the private history.
 * Construction is confined to GameInfo by AttackEventsViewBoundaryTest. Engine serialization and
 * cloning initialize the backing collection before this View is created; expiry mutates it in place.
 */
class AttackEventsView internal constructor(
    private val events: List<AttackEvent>,
    private val viewer: Civilization,
    val spectatorMode: Boolean
) {
    /**
     * A detached snapshot of the endpoints observed when each attack happened.
     * Selection matches only known endpoints or participants identified at attack time.
     * Spectator mode alone never grants knowledge belonging to an unrestricted spectator.
     */
    @Readonly
    fun getObservedAttacks(selectedUnit: MapUnitView? = null): List<ObservedAttack> {
        val selectedPosition = selectedUnit?.getTile()?.position()
        val selectedUnitId = selectedUnit?.unit?.id
        return immutableCopy(events.mapNotNull { attack ->
            val source = attack.source.takeIf { viewer.isSpectator() || viewer.civID in attack.knowsSource }
            val target = attack.target.takeIf { viewer.isSpectator() || viewer.civID in attack.knowsTarget }
            if (source == null && target == null) return@mapNotNull null
            if (selectedUnitId != null && selectedPosition != source && selectedPosition != target
                && !isKnownParticipant(attack.attacker, selectedUnitId)
                && attack.targets.none { isKnownParticipant(it, selectedUnitId) }
                && attack.interceptions.none { isKnownParticipant(it.interceptor, selectedUnitId) }) return@mapNotNull null
            ObservedAttack(attack.turn, source, target)
        })
    }

    /** Reports contain only facts this recipient can receive, without exposing stored records. */
    @Readonly
    fun getCombatReports(): List<ObservedCombatReport> {
        @LocalState val reports = ArrayList<ObservedCombatReport>()
        for (attack in events) {
            if (attack.resolution == AttackResolution.Pending) continue
            val attacker = attack.attacker ?: continue
            if (attack.kind == AttackKind.AirSweep && attack.resolution == AttackResolution.Completed
                && attack.interceptions.isEmpty() && attacker.civId == viewer.civID)
                reports.add(ObservedUnopposedAirSweep(attack.turn, attacker.name))
            reports.addAll(interceptionReports(attack, attacker))
            withdrawalReport(attack, attacker)?.let { reports.add(it) }
            if (attack.resolution != AttackResolution.Completed || attack.kind == AttackKind.AirSweep) continue
            nuclearReport(attack, attacker)?.let { reports.add(it) }
            reports.addAll(resultReports(attack, attacker))
            improvementDestructionReport(attack, attacker)?.let { reports.add(it) }
        }
        return immutableCopy(reports)
    }

    /** Capture notices use an ephemeral snapshot taken before ownership or placement changes. */
    @Readonly
    fun getCaptureReports(): List<ObservedAttackResult> = immutableCopy(events.flatMap { attack ->
        val attacker = attack.attacker
        if (attack.resolution != AttackResolution.Completed || attacker == null) return@flatMap emptyList()
        attack.targets.mapNotNull { target ->
            if (!target.captureAttempted || target.civId != viewer.civID
                || target.outcome !in listOf(AttackParticipantOutcome.Captured, AttackParticipantOutcome.Destroyed))
                return@mapNotNull null
            ObservedAttackResult(
                attack.turn,
                observe(attacker, cityIdentified = viewer.civID in attack.knowsSource),
                observe(target), target.outcome,
                damageReceived = 0, retaliationDamage = 0,
                attackerDefeatedByDefender = false, showZeroDamage = false,
                locations = immutableCopy(listOf(target.position))
            )
        }
    })

    /**
     * Name information for a trigger involving our own unit, including during attack execution.
     * A supplied ID cannot select an enemy participant or disclose unrelated collateral victims.
     */
    @Readonly
    fun getCombatantForTrigger(ownUnitId: Int): ObservedCombatant? {
        fun isOurUnit(participant: AttackParticipant?): Boolean = participant != null
            && participant.kind == AttackParticipantKind.Unit && participant.unitId == ownUnitId
            && participant.civId == viewer.civID
        for (index in events.lastIndex downTo 0) {
            val attack = events[index]
            val attacker = attack.attacker ?: continue
            if (isOurUnit(attacker)) {
                val opponent = when (attack.kind) {
                    AttackKind.Combat -> attack.targets.firstOrNull()
                    AttackKind.AirSweep -> attack.interceptions.firstOrNull { it.intercepted }?.interceptor
                    // An area attack does not identify the units hidden inside its blast radius.
                    AttackKind.Nuclear -> null
                }
                return opponent?.let { observe(it) }
            }
            if (attack.targets.any(::isOurUnit)
                || attack.interceptions.any { it.intercepted && isOurUnit(it.interceptor) })
                return observe(attacker, cityIdentified = viewer.civID in attack.knowsSource)
        }
        return null
    }

    @Readonly
    private fun withdrawalReport(attack: AttackEvent, attacker: AttackParticipant): ObservedWithdrawal? {
        if (attack.kind != AttackKind.Combat || attack.resolution != AttackResolution.Withdrawn) return null
        val destination = attack.withdrawalDestination ?: return null
        val target = attack.targets.firstOrNull() ?: return null
        if (target.outcome != AttackParticipantOutcome.Withdrew) return null
        val isDefending = target.civId == viewer.civID
        if (!isDefending && attacker.civId != viewer.civID) return null
        val locations = if (isDefending)
            listOf(destination, knownSource(attack)).filterNotNull()
        else listOf(destination.takeIf { viewer.civID in attack.withdrawalKnownBy } ?: attack.target, attack.source)
        return ObservedWithdrawal(
            attack.turn,
            observe(attacker, cityIdentified = viewer.civID in attack.knowsSource),
            observe(target), immutableCopy(locations.distinct())
        )
    }

    @Readonly
    private fun improvementDestructionReport(attack: AttackEvent, attacker: AttackParticipant): ObservedImprovementDestruction? {
        if (attack.kind != AttackKind.Combat || attack.resolution != AttackResolution.Completed) return null
        val improvementName = attack.destroyedImprovement ?: return null
        val target = attack.targets.firstOrNull() ?: return null
        if (target.civId != viewer.civID) return null
        return ObservedImprovementDestruction(
            attack.turn,
            observe(attacker, cityIdentified = viewer.civID in attack.knowsSource),
            improvementName,
            immutableCopy(listOf(target.position, knownSource(attack)).filterNotNull().distinct())
        )
    }

    @Readonly
    private fun resultReports(attack: AttackEvent, attacker: AttackParticipant): List<ObservedAttackResult> =
        attack.targets.mapIndexedNotNull { index, target ->
            if (target.civId != viewer.civID) return@mapIndexedNotNull null
            if (attack.kind == AttackKind.Combat && (target.captureAttempted
                    || target.outcome == AttackParticipantOutcome.Captured && target.kind == AttackParticipantKind.Unit))
                return@mapIndexedNotNull null
            if (target.outcome == AttackParticipantOutcome.Pending || target.outcome == AttackParticipantOutcome.Withdrew)
                return@mapIndexedNotNull null
            val primaryTarget = attack.kind == AttackKind.Combat && index == 0
            val interceptionDamage = if (attack.kind == AttackKind.Nuclear)
                attack.interceptions.filter { sameParticipant(it.interceptor, target) }.sumOf { it.damageToInterceptor }
            else 0
            val damage = (target.damageReceived - interceptionDamage).coerceAtLeast(0)
            if (!primaryTarget && damage == 0 && target.outcome == AttackParticipantOutcome.Survived)
                return@mapIndexedNotNull null
            ObservedAttackResult(
                attack.turn,
                observe(attacker, cityIdentified = viewer.civID in attack.knowsSource),
                observe(target), target.outcome, damage,
                retaliationDamage = if (primaryTarget) attack.defenderRetaliationDamage else 0,
                attackerDefeatedByDefender = primaryTarget && attack.attackerDefeatedByDefender,
                showZeroDamage = primaryTarget,
                locations = immutableCopy(listOf(target.position, knownSource(attack)).filterNotNull().distinct())
            )
        }

    @Readonly
    private fun interceptionReports(attack: AttackEvent, attacker: AttackParticipant): List<ObservedInterception> =
        attack.interceptions.mapNotNull { interception ->
            if (!interception.intercepted) return@mapNotNull null
            val interceptor = interception.interceptor ?: return@mapNotNull null
            val attackerOutcome = interception.attackerOutcome ?: return@mapNotNull null
            val interceptorOutcome = interception.interceptorOutcome ?: return@mapNotNull null
            val isAttacking = attacker.civId == viewer.civID
            if (!isAttacking && interceptor.civId != viewer.civID) return@mapNotNull null
            // A later nuclear explosion must not retroactively expose its target during interception.
            val phaseKnowsTarget = interception.knowsTarget
                ?: if (attack.kind == AttackKind.Nuclear) emptySet() else attack.knowsTarget
            val locations = if (isAttacking) listOf(
                attack.target, attack.source, interceptor.position.takeIf { viewer.civID in interceptor.knownBy }
            ).filterNotNull() else listOf(
                attack.target.takeIf { viewer.civID in phaseKnowsTarget }, interceptor.position, knownSource(attack)
            ).filterNotNull()
            ObservedInterception(
                attack.turn,
                ourUnit = observe(if (isAttacking) attacker else interceptor),
                enemyUnit = observe(if (isAttacking) interceptor else attacker),
                ourDamage = if (isAttacking) interception.damageToAttacker else interception.damageToInterceptor,
                enemyDamage = if (isAttacking) interception.damageToInterceptor else interception.damageToAttacker,
                ourDestroyed = (if (isAttacking) attackerOutcome else interceptorOutcome) == AttackParticipantOutcome.Destroyed,
                enemyDestroyed = (if (isAttacking) interceptorOutcome else attackerOutcome) == AttackParticipantOutcome.Destroyed,
                isAttacking = isAttacking,
                locations = immutableCopy(locations.distinct())
            )
        }

    @Readonly
    private fun nuclearReport(attack: AttackEvent, attacker: AttackParticipant): ObservedNuclearDetonation? {
        if (attack.kind != AttackKind.Nuclear || attacker.civId == viewer.civID) return null
        val hitOurTerritory = viewer.civID in attack.nuclearTerritoryCivIds
        if (!viewer.isAlive() && !hitOurTerritory && attack.targets.none { it.civId == viewer.civID }) return null
        val attackingCivName = if (viewer.civID in attack.knowsSource)
            viewer.gameInfo.civilizations.firstOrNull { it.civID == attack.attackingCivId }?.civName
        else null
        return ObservedNuclearDetonation(attack.turn, attacker.name, attackingCivName, hitOurTerritory,
            attack.target.takeIf { viewer.civID in attack.knowsTarget })
    }

    @Readonly
    private fun observe(participant: AttackParticipant): ObservedCombatant =
        observe(participant, cityIdentified = viewer.civID in participant.knownBy)

    @Readonly
    private fun observe(participant: AttackParticipant, cityIdentified: Boolean): ObservedCombatant {
        val isOwn = participant.civId == viewer.civID
        val name = participant.name.takeIf { participant.kind != AttackParticipantKind.City || isOwn || cityIdentified }
        return ObservedCombatant(participant.kind, name, participant.instanceName.takeIf { isOwn }, isOwn)
    }

    @Readonly
    private fun knownSource(attack: AttackEvent): HexCoord? = attack.source.takeIf { viewer.civID in attack.knowsSource }

    @Readonly
    private fun sameParticipant(first: AttackParticipant?, second: AttackParticipant): Boolean =
        first != null && first.kind == second.kind && when (first.kind) {
            AttackParticipantKind.Unit -> first.unitId != null && first.unitId == second.unitId
            AttackParticipantKind.City -> first.cityId != null && first.cityId == second.cityId
        }

    @Readonly
    private fun <T> immutableCopy(items: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(items))

    @Readonly
    private fun isKnownParticipant(participant: AttackParticipant?, unitId: Int): Boolean =
        participant != null && participant.unitId == unitId
            && (viewer.isSpectator() || viewer.civID in participant.knownBy)
}
