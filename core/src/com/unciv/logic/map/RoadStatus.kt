package com.unciv.logic.map

import com.unciv.models.gamebasics.GameBasics

/**
 * You can use RoadStatus.name to identify [Road] and [Railroad]
 * in string-based identification, as done in [improvement].
 */
enum class RoadStatus {

    None,
    Road,
    Railroad;

    /** returns null for [None] */
    fun improvement() = GameBasics.TileImprovements[this.name]

}
