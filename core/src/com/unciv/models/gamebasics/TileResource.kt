package com.unciv.models.gamebasics

import com.unciv.models.linq.Linq
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats

class TileResource : NamedStats(), ICivilopedia {
    override val description: String
        get(){
            val stringBuilder = StringBuilder()
            stringBuilder.appendln(this.clone().toString())
            stringBuilder.appendln("Can be found on " + terrainsCanBeFoundOn.joinToString())
            stringBuilder.appendln()
            stringBuilder.appendln("Improved by " + improvement)
            stringBuilder.appendln("Bonus stats for improvement: " + improvementStats)
            return stringBuilder.toString()
        }

    @JvmField var resourceType: ResourceType = ResourceType.Bonus
    @JvmField var terrainsCanBeFoundOn: Linq<String> = Linq()
    @JvmField var improvement: String? = null
    @JvmField var improvementStats: Stats? = null

    /**
     * The building that improves this resource, if any. E.G.: Granary for wheat, Stable for cattle.
     */
    @JvmField var building: String? = null
    @JvmField var revealedBy: String? = null

    fun GetBuilding(): Building? {
        return if (building == null) null else GameBasics.Buildings[building]
    }
}

