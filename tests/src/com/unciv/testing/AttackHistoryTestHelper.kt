package com.unciv.testing

import com.unciv.logic.GameInfo
import com.unciv.logic.battle.AttackEvent
import com.unciv.logic.battle.AttackKind
import com.unciv.logic.battle.AttackParticipant
import com.unciv.logic.battle.AttackParticipantOutcome
import com.unciv.logic.battle.AttackRecorder
import com.unciv.logic.battle.AttackResolution
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Privileged fixtures for engine assertions and deliberately constructed visibility scenarios.
 * These stay in the test source set: production callers must use AttackEventsView instead.
 * Reflection preserves live-record assertions without adding a production history accessor.
 */
internal val GameInfo.attackEventsForTesting: MutableList<AttackEvent>
    @Suppress("UNCHECKED_CAST")
    get() = attackEventsField.get(this) as MutableList<AttackEvent>

private val attackEventsField = GameInfo::class.java.getDeclaredField("attackEvents").apply {
    isAccessible = true
}

/** Stores a deliberately mutable fixture across the separate tests module boundary. */
internal fun GameInfo.recordAttackForTesting(attacker: ICombatant, targetTile: Tile): AttackEvent {
    val event = AttackEvent(attacker, targetTile)
    storeAttackMethod.invokeForTesting(this, event)
    return event
}

internal fun GameInfo.storeAttackForTesting(event: AttackEvent) {
    storeAttackMethod.invokeForTesting(this, event)
}

private val storeAttackMethod = GameInfo::class.java.declaredMethods.single {
    (it.name == "storeAttack" || it.name.startsWith("storeAttack\$")) &&
        it.parameterTypes.contentEquals(arrayOf(AttackEvent::class.java))
}.apply { isAccessible = true }

/** Exercises engine construction without making a raw-record factory public. */
internal fun newAttackRecorderForTesting(
    attacker: ICombatant,
    targetTile: Tile,
    kind: AttackKind = AttackKind.Combat
): AttackRecorder = try {
    AttackRecorder::class.java
        .getDeclaredConstructor(ICombatant::class.java, Tile::class.java, AttackKind::class.java)
        .apply { isAccessible = true }
        .newInstance(attacker, targetTile, kind)
} catch (exception: InvocationTargetException) {
    throw exception.targetException
}

internal fun AttackRecorder.snapshotTargetForTesting(combatant: ICombatant, retainIfUnaffected: Boolean = true) {
    snapshotTargetMethod.invokeForTesting(this, combatant, retainIfUnaffected)
}

internal fun AttackRecorder.markUnitAffectedForTesting(unit: MapUnit) {
    markUnitAffectedMethod.invokeForTesting(this, unit)
}

internal fun AttackRecorder.snapshotForTesting(): AttackEvent =
    snapshotMethod.invokeForTesting(this) as AttackEvent

internal fun AttackRecorder.beginInterceptionForTesting(interceptor: MapUnitCombatant): Int =
    beginInterceptionMethod.invokeForTesting(this, interceptor) as Int

internal fun AttackRecorder.recordInterceptionForTesting(
    index: Int,
    intercepted: Boolean,
    damageToAttacker: Int = 0,
    damageToInterceptor: Int = 0
) {
    recordInterceptionMethod.invokeForTesting(this, index, intercepted, damageToAttacker, damageToInterceptor, null, null)
}

internal fun AttackRecorder.interceptorSnapshotForTesting(index: Int): AttackParticipant =
    interceptorSnapshotMethod.invokeForTesting(this, index) as AttackParticipant

internal fun AttackRecorder.damageReceivedForTesting(combatant: ICombatant): Int =
    damageReceivedMethod.invokeForTesting(this, combatant) as Int

internal fun AttackRecorder.retainAllTargetsForTesting() {
    retainAllTargetsMethod.invokeForTesting(this)
}

internal fun AttackRecorder.finishForTesting(resolution: AttackResolution = AttackResolution.Completed): AttackEvent =
    finishMethod.invokeForTesting(this, resolution) as AttackEvent

internal fun AttackRecorder.finishIncompleteForTesting(): AttackEvent =
    finishIncompleteMethod.invokeForTesting(this) as AttackEvent

private val finishMethod = AttackRecorder::class.java.declaredMethods.single {
    (it.name == "finish" || it.name.startsWith("finish\$")) &&
        it.parameterTypes.contentEquals(arrayOf(AttackResolution::class.java))
}.apply { isAccessible = true }

private val finishIncompleteMethod = AttackRecorder::class.java.declaredMethods.single {
    (it.name == "finishIncomplete" || it.name.startsWith("finishIncomplete\$")) &&
        it.parameterTypes.isEmpty()
}.apply { isAccessible = true }

private val snapshotTargetMethod = recorderMethod("snapshotTarget", ICombatant::class.java, Boolean::class.javaPrimitiveType!!)
private val markUnitAffectedMethod = recorderMethod("markUnitAffected", MapUnit::class.java)
private val snapshotMethod = recorderMethod("snapshot")
private val beginInterceptionMethod = recorderMethod("beginInterception", MapUnitCombatant::class.java)
private val recordInterceptionMethod = recorderMethod("recordInterception", Int::class.javaPrimitiveType!!,
    Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
    AttackParticipantOutcome::class.java, AttackParticipantOutcome::class.java)
private val interceptorSnapshotMethod = recorderMethod("interceptorSnapshot", Int::class.javaPrimitiveType!!)
private val damageReceivedMethod = recorderMethod("damageReceived", ICombatant::class.java)
private val retainAllTargetsMethod = recorderMethod("retainAllTargets")

private fun recorderMethod(name: String, vararg parameters: Class<*>): Method =
    AttackRecorder::class.java.declaredMethods.single {
        (it.name == name || it.name.startsWith("$name\$")) && it.parameterTypes.contentEquals(parameters)
    }.apply { isAccessible = true }

private fun Method.invokeForTesting(receiver: Any, vararg arguments: Any?): Any? =
    try {
        invoke(receiver, *arguments)
    } catch (exception: InvocationTargetException) {
        throw exception.targetException
    }
