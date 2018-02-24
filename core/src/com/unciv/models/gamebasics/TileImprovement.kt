package com.unciv.models.gamebasics

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.stats.FullStats
import com.unciv.models.stats.NamedStats

import java.util.ArrayList
import java.util.HashMap

class TileImprovement : NamedStats(), ICivilopedia {

    @JvmField var terrainsCanBeBuiltOn: Collection<String> = ArrayList()
    @JvmField var techRequired: String? = null

    @JvmField var improvingTech: String? = null
    @JvmField var improvingTechStats: FullStats? = null

    private val turnsToBuild: Int = 0 // This is the base cost.
    fun getTurnsToBuild(civInfo: CivilizationInfo): Int {
        var realTurnsToBuild = turnsToBuild.toFloat()
        if (civInfo.buildingUniques.contains("WorkerConstruction"))
            realTurnsToBuild *= 0.75f
        if (civInfo.policies.isAdopted("Citizenship"))
            realTurnsToBuild *= 0.75f
        return Math.round(realTurnsToBuild)
    }

    override val description: String
        get() {
            val stringBuilder = StringBuilder()
            if (!this.clone().toString().isEmpty()) stringBuilder.appendln(this.clone().toString())
            if (!terrainsCanBeBuiltOn.isEmpty()) stringBuilder.appendln("Can be built on " + terrainsCanBeBuiltOn.joinToString(", "))

            val statsToResourceNames = HashMap<String, ArrayList<String>>()
            for (tr: TileResource in GameBasics.TileResources.values.filter { it.improvement == name }) {
                val statsString = tr.improvementStats.toString()
                if (!statsToResourceNames.containsKey(statsString))
                    statsToResourceNames[statsString] = ArrayList()
                statsToResourceNames[statsString]!!.add(tr.name)
            }
            statsToResourceNames.forEach {
                stringBuilder.appendln(it.key + " for " + it.value.joinToString(", "))
            }

            if (techRequired != null) stringBuilder.appendln("Tech required: " + techRequired)

            return stringBuilder.toString()
        }
}