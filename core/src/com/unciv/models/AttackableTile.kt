package com.unciv.models

import com.unciv.logic.map.TileInfo

class AttackableTile(val tileToAttackFrom: TileInfo, val tileToAttack: TileInfo,
                     val movementLeftAfterMovingToAttackTile:Float)