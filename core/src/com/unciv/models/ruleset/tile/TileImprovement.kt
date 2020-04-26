package com.unciv.models.ruleset.tile

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.tr
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats
import java.util.*
import kotlin.math.roundToInt

class TileImprovement : NamedStats() {

    var terrainsCanBeBuiltOn: Collection<String> = ArrayList()

    // Used only for Camp - but avoid hardcoded comparison and *allow modding*
    // Terrain Features that need not be cleared if the improvement enables a resource
    var resourceTerrainAllow: Collection<String> = ArrayList()

    var techRequired: String? = null

    var improvingTech: String? = null
    var improvingTechStats: Stats? = null
    var uniqueTo:String? = null
    var uniques = ArrayList<String>()

    val turnsToBuild: Int = 0 // This is the base cost.


    fun getTurnsToBuild(civInfo: CivilizationInfo): Int {
        var realTurnsToBuild = turnsToBuild.toFloat() * civInfo.gameInfo.gameParameters.gameSpeed.modifier
        if (civInfo.containsBuildingUnique("Worker construction increased 25%"))
            realTurnsToBuild *= 0.75f
        if (civInfo.policies.isAdopted("Citizenship"))
            realTurnsToBuild *= 0.75f
        return realTurnsToBuild.roundToInt()
    }

    fun getDescription(ruleset: Ruleset, forPickerScreen: Boolean = true): String {
        val stringBuilder = StringBuilder()
        val statsDesc = this.clone().toString()
        if (statsDesc.isNotEmpty()) stringBuilder.appendln(statsDesc)
        if (improvingTech != null && improvingTechStats != null) {
            val improveStatsDesc = improvingTechStats.toString()
            if (improveStatsDesc.isNotEmpty()) stringBuilder.appendln("[$improveStatsDesc] with [${improvingTech!!}]".tr())
        }
        if (uniqueTo!=null && !forPickerScreen) stringBuilder.appendln("Unique to [$uniqueTo]".tr())
        if (!terrainsCanBeBuiltOn.isEmpty()) {
            val terrainsCanBeBuiltOnString: ArrayList<String> = arrayListOf()
            for (i in terrainsCanBeBuiltOn) {
                terrainsCanBeBuiltOnString.add(i.tr())
            }
            stringBuilder.appendln("Can be built on ".tr() + terrainsCanBeBuiltOnString.joinToString(", "))//language can be changed when setting changes.
        }
        val statsToResourceNames = HashMap<String, ArrayList<String>>()
        for (tr: TileResource in ruleset.tileResources.values.filter { it.improvement == name }) {
            val statsString = tr.improvementStats.toString()
            if (!statsToResourceNames.containsKey(statsString))
                statsToResourceNames[statsString] = ArrayList()
            statsToResourceNames[statsString]!!.add(tr.name.tr())
        }
        statsToResourceNames.forEach {
            stringBuilder.appendln(it.key + " for ".tr() + it.value.joinToString(", "))
        }

        if (techRequired != null) stringBuilder.appendln("Required tech: [$techRequired]".tr())

        for(unique in uniques)
            stringBuilder.appendln (unique.tr())

        return stringBuilder.toString()
    }

    fun hasUnique(unique: String) = uniques.contains(unique)
}

