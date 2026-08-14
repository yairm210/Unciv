package com.unciv.logic.battle

import com.unciv.logic.map.tile.Tile

data class AttackableTile(
    val tileToAttackFrom: Tile,
    val tileToAttack: Tile,
    val movementLeftAfterMovingToAttackTile: Float,
    val combatant: ICombatant?,
    /** True when this specific attack uses ranged combat rules. Hybrid melee/ranged units set this per target. */
    val isRangedAttack: Boolean = false
)
