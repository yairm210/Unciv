package com.unciv.models.ruleset.tile

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Belief
import com.unciv.logic.map.RoadStatus
import com.unciv.models.ruleset.IHasUniques
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.NamedStats
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText
import java.util.*
import kotlin.math.roundToInt

class TileImprovement : NamedStats(), ICivilopediaText, IHasUniques {

    var terrainsCanBeBuiltOn: Collection<String> = ArrayList()
    var techRequired: String? = null
    var uniqueTo:String? = null
    override var uniques = ArrayList<String>()
    override val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
    val shortcutKey: Char? = null
    val turnsToBuild: Int = 0 // This is the base cost.

    override var civilopediaText = listOf<FormattedLine>()


    fun getTurnsToBuild(civInfo: CivilizationInfo): Int {
        var realTurnsToBuild = turnsToBuild.toFloat() * civInfo.gameInfo.gameParameters.gameSpeed.modifier
        for (unique in civInfo.getMatchingUniques("[]% tile improvement construction time")) {
            realTurnsToBuild *= 1 + unique.params[0].toFloat() / 100f
        }
        // Deprecated since 3.14.17
            if (civInfo.hasUnique("Worker construction increased 25%"))
                realTurnsToBuild *= 0.75f
            if (civInfo.hasUnique("Tile improvement speed +25%"))
                realTurnsToBuild *= 0.75f
        //
        // In some weird cases it was possible for something to take 0 turns, leading to it instead never finishing
        if (realTurnsToBuild < 1) realTurnsToBuild = 1f
        return realTurnsToBuild.roundToInt()
    }

    fun getDescription(ruleset: Ruleset): String {
        val lines = ArrayList<String>()

        val statsDesc = this.clone().toString()
        if (statsDesc.isNotEmpty()) lines += statsDesc
        if (!terrainsCanBeBuiltOn.isEmpty()) {
            val terrainsCanBeBuiltOnString: ArrayList<String> = arrayListOf()
            for (i in terrainsCanBeBuiltOn) {
                terrainsCanBeBuiltOnString.add(i.tr())
            }
            lines += "Can be built on".tr() + terrainsCanBeBuiltOnString.joinToString(", ", " ") //language can be changed when setting changes.
        }
        val statsToResourceNames = HashMap<String, ArrayList<String>>()
        for (tr: TileResource in ruleset.tileResources.values.filter { it.improvement == name }) {
            val statsString = tr.improvementStats.toString()
            if (!statsToResourceNames.containsKey(statsString))
                statsToResourceNames[statsString] = ArrayList()
            statsToResourceNames[statsString]!!.add(tr.name.tr())
        }
        statsToResourceNames.forEach {
            lines += "{${it.key}} {for} ".tr() + it.value.joinToString(", ")
        }

        if (techRequired != null) lines += "Required tech: [$techRequired]".tr()

        for(unique in uniques)
            lines += unique.tr()

        return lines.joinToString("\n")
    }

    fun hasUnique(unique: String) = uniques.contains(unique)
    fun isGreatImprovement() = hasUnique("Great Improvement")
    fun isRoad() = RoadStatus.values().any { it != RoadStatus.None && it.name == this.name }

    /**
     * Check: Is this improvement allowed on a [given][name] terrain feature?
     *
     * Background: This not used for e.g. a lumbermill - it derives the right to be placed on forest
     * from [terrainsCanBeBuiltOn]. Other improvements may be candidates without fulfilling the
     * [terrainsCanBeBuiltOn] check - e.g. they are listed by a resource as 'their' improvement.
     * I such cases, the 'unbuildable' property of the Terrain feature might prevent the improvement,
     * so this check is done in conjunction - for the user, success means he does not need to remove
     * a terrain feature, thus the unique name.
     */
    fun isAllowedOnFeature(name: String): Boolean {
        return uniqueObjects.filter { it.placeholderText == "Does not need removal of []"
                && it.params[0] == name
        }.any()
    }
    
    fun matchesFilter(filter: String): Boolean {
        return when (filter) {
            name -> true
            "All" -> true
            "All Road" -> isRoad()
            "Great Improvement", "Great" -> isGreatImprovement()
            else -> false
        }
    }

    override fun makeLink() = "Improvement/$name"
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        val statsDesc = this.clone().toString()
        if (statsDesc.isNotEmpty()) textList += FormattedLine(statsDesc)

        if (uniqueTo!=null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [$uniqueTo]", link="Nation/$uniqueTo")
        }

        if (terrainsCanBeBuiltOn.isNotEmpty()) {
            textList += FormattedLine()
            if (terrainsCanBeBuiltOn.size == 1) {
                with (terrainsCanBeBuiltOn.first()) {
                    textList += FormattedLine("{Can be built on} {$this}", link="Terrain/$this")
                }
            } else {
                textList += FormattedLine("{Can be built on}:")
                terrainsCanBeBuiltOn.forEach {
                    textList += FormattedLine(it, link="Terrain/$it", indent=1)
                }
            }
        }

        val statsToResourceNames = HashMap<String, ArrayList<String>>()
        for (resource in ruleset.tileResources.values.filter { it.improvement == name }) {
            val statsString = resource.improvementStats.toString()
            if (statsString !in statsToResourceNames)
                statsToResourceNames[statsString] = ArrayList()
            statsToResourceNames[statsString]!!.add(resource.name)
        }
        if (statsToResourceNames.isNotEmpty()) {
            statsToResourceNames.forEach {
                textList += FormattedLine()
                if (it.value.size == 1) {
                    with(it.value[0]) {
                        textList += FormattedLine("${it.key}{ for }{$this}", link="Resource/$this")
                    }
                } else {
                    textList += FormattedLine("${it.key}{ for }:")
                    it.value.forEach { resource ->
                        textList += FormattedLine(resource, link="Resource/$resource", indent=1)
                    }
                }
            }
        }

        if (techRequired != null) {
            textList += FormattedLine()
            textList += FormattedLine("Required tech: [$techRequired]", link="Technology/$techRequired")
        }

        if (uniques.isNotEmpty()) {
            textList += FormattedLine()
            for (unique in uniqueObjects)
                textList += FormattedLine(unique)
        }

        val unit = ruleset.units.asSequence().firstOrNull {
            entry -> entry.value.uniques.any { 
                it.startsWith("Can construct [$name]")
            }
        }?.key
        if (unit != null) {
            textList += FormattedLine()
            textList += FormattedLine("{Can be constructed by} {$unit}", link="Unit/$unit")
        }

        textList += Belief.getCivilopediaTextMatching(name, ruleset)

        return textList
    }
}
