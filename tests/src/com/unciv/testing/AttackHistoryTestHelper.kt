package com.unciv.testing

import com.unciv.logic.GameInfo
import com.unciv.logic.battle.AttackEvent
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.map.tile.Tile
import java.lang.reflect.InvocationTargetException

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

/** Calls the engine recording operation across the separate tests module boundary. */
internal fun GameInfo.recordAttackForTesting(attacker: ICombatant, targetTile: Tile): AttackEvent =
    try {
        recordAttackMethod.invoke(this, attacker, targetTile) as AttackEvent
    } catch (exception: InvocationTargetException) {
        throw exception.targetException
    }

private val recordAttackMethod = GameInfo::class.java.declaredMethods.single {
    (it.name == "recordAttack" || it.name.startsWith("recordAttack\$")) &&
        it.parameterTypes.contentEquals(arrayOf(ICombatant::class.java, Tile::class.java))
}.apply { isAccessible = true }
