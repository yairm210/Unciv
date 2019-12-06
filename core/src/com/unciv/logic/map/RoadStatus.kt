package com.unciv.logic.map

import com.unciv.logic.map.RoadStatus.Railroad
import com.unciv.logic.map.RoadStatus.Road
import com.unciv.models.gamebasics.Ruleset

/**
 * You can use RoadStatus.name to identify [Road] and [Railroad]
 * in string-based identification, as done in [improvement].
 */
enum class RoadStatus {

    None,
    Road,
    Railroad;

    /** returns null for [None] */
    fun improvement(ruleset: Ruleset) = ruleset.TileImprovements[this.name]

}
