package com.unciv.testing

import com.unciv.logic.battle.AttackEvent
import com.unciv.logic.battle.AttackParticipantOutcome
import com.unciv.logic.battle.AttackRecorder
import com.unciv.logic.battle.AttackResolution
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Test-only access to the recorder's engine API across the separate Kotlin test module. */
internal fun newAttackRecorderForTesting(attacker: MapUnitCombatant, target: Tile): AttackRecorder =
    unwrapReflectionFailure {
        AttackRecorder::class.java.getDeclaredConstructor(MapUnitCombatant::class.java, Tile::class.java)
            .apply { isAccessible = true }.newInstance(attacker, target)
    }

internal fun AttackRecorder.snapshotTargetForTesting(target: MapUnitCombatant) {
    snapshotTargetMethod.invokeForTesting(this, target)
}

internal fun AttackRecorder.recordOutcomeForTesting(target: MapUnitCombatant, outcome: AttackParticipantOutcome) {
    recordOutcomeMethod.invokeForTesting(this, target, outcome)
}

internal fun AttackRecorder.recordDamageForTesting(unit: MapUnit, actualDamage: Int) {
    recordDamageMethod.invokeForTesting(this, unit, actualDamage)
}

internal fun AttackRecorder.markUnitAffectedForTesting(unit: MapUnit) {
    markUnitAffectedMethod.invokeForTesting(this, unit)
}

internal fun AttackRecorder.recordDestructionForTesting(unit: MapUnit) {
    recordDestructionMethod.invokeForTesting(this, unit)
}

internal fun AttackRecorder.finishForTesting(resolution: AttackResolution = AttackResolution.Completed): AttackEvent =
    finishMethod.invokeForTesting(this, resolution) as AttackEvent

internal fun AttackRecorder.finishIncompleteForTesting(): AttackEvent =
    finishIncompleteMethod.invokeForTesting(this) as AttackEvent

private val snapshotTargetMethod = recorderMethod("snapshotTarget", MapUnitCombatant::class.java)
private val recordOutcomeMethod = recorderMethod("recordOutcome", MapUnitCombatant::class.java, AttackParticipantOutcome::class.java)
private val recordDamageMethod = recorderMethod("recordDamage", MapUnit::class.java, Int::class.javaPrimitiveType!!)
private val markUnitAffectedMethod = recorderMethod("markUnitAffected", MapUnit::class.java)
private val recordDestructionMethod = recorderMethod("recordDestruction", MapUnit::class.java)
private val finishMethod = recorderMethod("finish", AttackResolution::class.java)
private val finishIncompleteMethod = recorderMethod("finishIncomplete")

private fun recorderMethod(name: String, vararg parameters: Class<*>): Method =
    AttackRecorder::class.java.declaredMethods.single {
        (it.name == name || it.name.startsWith("$name\$")) && it.parameterTypes.contentEquals(parameters)
    }.apply { isAccessible = true }

private fun Method.invokeForTesting(receiver: Any, vararg arguments: Any?): Any? =
    unwrapReflectionFailure { invoke(receiver, *arguments) }

private inline fun <T> unwrapReflectionFailure(action: () -> T): T =
    try {
        action()
    } catch (exception: InvocationTargetException) {
        throw exception.targetException
    }
