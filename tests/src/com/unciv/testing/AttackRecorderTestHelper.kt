package com.unciv.testing

import com.unciv.logic.battle.AttackEvent
import com.unciv.logic.battle.AttackParticipantOutcome
import com.unciv.logic.battle.AttackRecorder
import com.unciv.logic.battle.AttackResolution
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Test-only access to the recorder's engine API across the separate Kotlin test module. */
internal fun newAttackRecorderForTesting(attacker: ICombatant, target: Tile): AttackRecorder =
    unwrapReflectionFailure {
        AttackRecorder::class.java.getDeclaredConstructor(ICombatant::class.java, Tile::class.java)
            .apply { isAccessible = true }.newInstance(attacker, target)
    }

internal fun AttackRecorder.snapshotTargetForTesting(target: ICombatant) {
    snapshotTargetMethod.invokeForTesting(this, target)
}

internal fun AttackRecorder.recordOutcomeForTesting(target: ICombatant, outcome: AttackParticipantOutcome) {
    recordOutcomeMethod.invokeForTesting(this, target, outcome)
}

internal fun AttackRecorder.recordDamageForTesting(unit: MapUnit, actualDamage: Int) {
    if (actualDamage > 0) markUnitAffectedForTesting(unit)
    recordDamageMethod.invokeForTesting(this, unit.id.toString(), actualDamage)
}

internal fun AttackRecorder.markUnitAffectedForTesting(unit: MapUnit) {
    markUnitAffectedMethod.invokeForTesting(this, MapUnitCombatant(unit))
}

internal fun AttackRecorder.recordDestructionForTesting(unit: MapUnit) {
    recordDestructionMethod.invokeForTesting(this, MapUnitCombatant(unit))
}

internal fun AttackRecorder.finishForTesting(resolution: AttackResolution = AttackResolution.Completed): AttackEvent =
    finishMethod.invokeForTesting(this, resolution) as AttackEvent

private val snapshotTargetMethod = recorderMethod("snapshotTarget", ICombatant::class.java)
private val recordOutcomeMethod = recorderMethod("recordOutcome", ICombatant::class.java, AttackParticipantOutcome::class.java)
private val recordDamageMethod = recorderMethod("recordDamage", String::class.java, Int::class.javaPrimitiveType!!)
private val markUnitAffectedMethod = recorderMethod("markUnitAffected", ICombatant::class.java)
private val recordDestructionMethod = recorderMethod("recordDestruction", ICombatant::class.java)
private val finishMethod = recorderMethod("finish", AttackResolution::class.java)

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
