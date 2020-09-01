package com.unciv.models.ruleset.tech

import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.tr
import com.unciv.models.ruleset.unit.BaseUnit
import java.util.*

class Technology {

    lateinit var name: String

    var cost: Int = 0
    var prerequisites = HashSet<String>()
    var uniques = ArrayList<String>()

    var column: TechColumn? = null // The column that this tech is in the tech tree
    var row: Int = 0
    var quote = ""

    fun getDescription(ruleset: Ruleset): String {
        val lineList = ArrayList<String>() // more readable than StringBuilder, with same performance for our use-case
        for (unique in uniques) lineList += unique.tr()

        val mapOfImprovedImprovements = HashMap<String, ArrayList<String>>()
        for (improvement in ruleset.tileImprovements.values) for ( unique in improvement.uniqueObjects
                .filter { it.placeholderText in setOf("[] once [] is discovered", "[] on [] tiles once [] is discovered")
                        && it.params.last() == name }) {
            val key = if (unique.params.size == 2 ) unique.params[0] else "[${unique.params[0]}] on [${unique.params[1]}] tiles"
            if (!mapOfImprovedImprovements.containsKey(key)) mapOfImprovedImprovements[key] = ArrayList()
            mapOfImprovedImprovements[key]!!.add(improvement.name)
        }
        for ( improvements in mapOfImprovedImprovements) {
            val impimpString = improvements.key.tr() + " from improvements: ".tr() + improvements.value.joinToString(", ") { it.tr() }
            lineList += impimpString
        }

        val improvedImprovements = ruleset.tileImprovements.values
                .filter { it.improvingTech == name }.groupBy { it.improvingTechStats.toString() }
        for (improvement in improvedImprovements) {
            val impimpString = improvement.value.joinToString { it.name.tr() } +
                    " {provide" + (if (improvement.value.size == 1) "s" else "") + "} " + improvement.key
            lineList += impimpString.tr()
        }

        val viewingCiv = UncivGame.Current.worldScreen.viewingCiv
        val enabledUnits = getEnabledUnits(viewingCiv)
        if (enabledUnits.isNotEmpty()) {
            lineList += "{Units enabled}: "
            for (unit in enabledUnits)
                lineList += " * " + unit.name.tr() + " (" + unit.getShortDescription() + ")"
        }

        val enabledBuildings = getEnabledBuildings(viewingCiv)

        val regularBuildings = enabledBuildings.filter { !it.isWonder && !it.isNationalWonder }
        if (regularBuildings.isNotEmpty()) {
            lineList += "{Buildings enabled}: "
            for (building in regularBuildings)
                lineList += "* " + building.name.tr() + " (" + building.getShortDescription(ruleset) + ")"
        }

        val wonders = enabledBuildings.filter { it.isWonder || it.isNationalWonder }
        if (wonders.isNotEmpty()) {
            lineList += "{Wonders enabled}: "
            for (wonder in wonders)
                lineList += " * " + wonder.name.tr() + " (" + wonder.getShortDescription(ruleset) + ")"
        }

        val revealedResource = ruleset.tileResources.values.filter { it.revealedBy == name }
                .map { it.name }.firstOrNull() // can only be one
        if (revealedResource != null) lineList += "Reveals [$revealedResource] on the map".tr()

        val tileImprovements = ruleset.tileImprovements.values.filter { it.techRequired == name }
        if (tileImprovements.isNotEmpty())
            lineList += "{Tile improvements enabled}: " + tileImprovements.joinToString { it.name.tr() }

        return lineList.joinToString("\n") { it.tr() }
    }

    fun getEnabledBuildings(civInfo: CivilizationInfo): List<Building> {
        var enabledBuildings = civInfo.gameInfo.ruleSet.buildings.values.filter {
            it.requiredTech == name &&
                    (it.uniqueTo == null || it.uniqueTo == civInfo.civName)
        }
        val replacedBuildings = enabledBuildings.mapNotNull { it.replaces }
        enabledBuildings = enabledBuildings.filter { it.name !in replacedBuildings }

        if (!civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled)
            enabledBuildings = enabledBuildings.filterNot { it.name == "Manhattan Project" }

        return enabledBuildings
    }

    fun getEnabledUnits(civInfo: CivilizationInfo): List<BaseUnit> {
        var enabledUnits = civInfo.gameInfo.ruleSet.units.values.filter {
            it.requiredTech == name &&
                    (it.uniqueTo == null || it.uniqueTo == civInfo.civName)
        }
        val replacedUnits = civInfo.gameInfo.ruleSet.units.values.filter { it.uniqueTo == civInfo.civName }
                .mapNotNull { it.replaces }
        enabledUnits = enabledUnits.filter { it.name !in replacedUnits }

        if (!civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled)
            enabledUnits = enabledUnits.filterNot { it.uniques.contains("Requires Manhattan Project") }

        return enabledUnits
    }

    override fun toString() = name

    fun era(): String = column!!.era

    fun isContinuallyResearchable() = uniques.contains("Can be continually researched")
}
