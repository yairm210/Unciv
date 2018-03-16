package com.unciv.models.gamebasics

import com.unciv.models.stats.NamedStats

class Terrain : NamedStats(), ICivilopedia {
    override val description: String
        get() = this.clone().toString()
    lateinit var type: TerrainType

    var overrideStats = false

    /***
     * If true, other terrain layers can come over this one. For mountains, lakes etc. this is false
     */

    var canHaveOverlay = true

    /***
     * If true, nothing can be built here - not even resource improvements
     */
    var unbuildable = false

    /***
     * For terrain features
     */
    var occursOn: Collection<String>? = null

    /**
     * RGB color of base terrain
     */
    var RGB: List<Int>? = null

    var movementCost = 1

}



