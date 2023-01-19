package com.unciv.logic.automation.unit

import com.unciv.logic.map.tile.TileInfo

class AttackableTile(val tileToAttackFrom: TileInfo, val tileToAttack: TileInfo,
                     val movementLeftAfterMovingToAttackTile:Float)
