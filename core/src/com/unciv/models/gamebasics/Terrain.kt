package com.unciv.models.gamebasics

import com.unciv.models.stats.NamedStats

class Terrain : NamedStats(), ICivilopedia {
    override val description: String
        get() = this.clone().toString()
    @JvmField var type: TerrainType? = null // BaseTerrain or TerrainFeature

    @JvmField var overrideStats = false

    /***
     * If true, other terrain layers can come over this one. For mountains, lakes etc. this is false
     */

    @JvmField var canHaveOverlay = true

    /***
     * If true, nothing can be built here - not even resource improvements
     */
    @JvmField var unbuildable = false

    /***
     * For terrain features
     */
    @JvmField var occursOn: Collection<String>? = null

    @JvmField var movementCost = 1

}



