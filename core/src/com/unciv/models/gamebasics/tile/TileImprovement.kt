package com.unciv.models.gamebasics.tile

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ICivilopedia
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats
import java.util.*
import com.unciv.models.gamebasics.tr

class TileImprovement : NamedStats(), ICivilopedia {

    var terrainsCanBeBuiltOn: Collection<String> = ArrayList()
    var techRequired: String? = null

    var improvingTech: String? = null
    var improvingTechStats: Stats? = null

    private val turnsToBuild: Int = 0 // This is the base cost.
    fun getTurnsToBuild(civInfo: CivilizationInfo): Int {
        var realTurnsToBuild = turnsToBuild.toFloat()
        if (civInfo.getBuildingUniques().contains("Worker construction increased 25%"))
            realTurnsToBuild *= 0.75f
        if (civInfo.policies.isAdopted("Citizenship"))
            realTurnsToBuild *= 0.75f
        return Math.round(realTurnsToBuild)
    }

    override val description: String
        get() {
            val stringBuilder = StringBuilder()
            if (!this.clone().toString().isEmpty()) stringBuilder.appendln(this.clone().toString())
            if (!terrainsCanBeBuiltOn.isEmpty()) stringBuilder.appendln("Can be built on ".tr() + terrainsCanBeBuiltOn.joinToString(", "))

            val statsToResourceNames = HashMap<String, ArrayList<String>>()
            for (tr: TileResource in GameBasics.TileResources.values.filter { it.improvement == name }) {
                val statsString = tr.improvementStats.toString()
                if (!statsToResourceNames.containsKey(statsString))
                    statsToResourceNames[statsString] = ArrayList()
                statsToResourceNames[statsString]!!.add(tr.name)
            }
            statsToResourceNames.forEach {
                stringBuilder.appendln(it.key + " for ".tr() + it.value.joinToString(", "))
            }

            if (techRequired != null) stringBuilder.appendln("Tech required: ".tr()+"$techRequired".tr())

            return stringBuilder.toString()
        }
}

