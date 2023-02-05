package com.unciv.logic.automation.unit

import com.unciv.logic.battle.ICombatant
import com.unciv.logic.map.tile.Tile

class AttackableTile(val tileToAttackFrom: Tile, val tileToAttack: Tile,
                     val movementLeftAfterMovingToAttackTile:Float,
                     /** This is only for debug purposes */ val combatant:ICombatant)
