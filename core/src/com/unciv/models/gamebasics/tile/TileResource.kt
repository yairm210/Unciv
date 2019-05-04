package com.unciv.models.gamebasics.tile

import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats
import com.unciv.models.gamebasics.tr
import java.util.ArrayList

class TileResource : NamedStats(), ICivilopedia {
    override val description: String
        get(){
            val stringBuilder = StringBuilder()
            stringBuilder.appendln(this.clone().toString())
            val terrainsCanBeBuiltOnString:ArrayList<String> = arrayListOf()
            for (i in terrainsCanBeFoundOn) {
                terrainsCanBeBuiltOnString.add(i.tr())
            }
            stringBuilder.appendln("Can be found on ".tr() + terrainsCanBeBuiltOnString.joinToString(", "))
            stringBuilder.appendln()
            stringBuilder.appendln("Improved by ".tr()+"$improvement".tr())
            stringBuilder.appendln("Bonus stats for improvement: ".tr()+"$improvementStats".tr())
            return stringBuilder.toString()
        }

    @JvmField var resourceType: ResourceType = ResourceType.Bonus
    @JvmField var terrainsCanBeFoundOn: List<String> = listOf()
    @JvmField var improvement: String? = null
    @JvmField var improvementStats: Stats? = null

    /**
     * The building that improves this resource, if any. E.G.: Granary for wheat, Stable for cattle.
     */
    @JvmField var building: String? = null
    @JvmField var revealedBy: String? = null

    fun getBuilding(): Building? {
        return if (building == null) null else GameBasics.Buildings[building!!]
    }
}

