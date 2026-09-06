package com.unciv.view

import com.unciv.logic.map.HexCoord

/** An attack as witnessed by one civilization. Unknown endpoints reveal no coordinates. */
data class ObservedAttack(val turn: Int, val source: HexCoord?, val target: HexCoord?)
