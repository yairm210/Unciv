package com.unciv.logic.battle

import com.unciv.logic.map.tile.Tile

data class AttackableTile(
    val tileToAttackFrom: Tile,
    val tileToAttack: Tile,
    val movementLeftAfterMovingToAttackTile: Float,
    val combatant: ICombatant?
)
