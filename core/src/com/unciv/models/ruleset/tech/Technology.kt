package com.unciv.models.ruleset.tech

import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts
import java.util.*

class Technology: RulesetObject() {

    var cost: Int = 0
    var prerequisites = HashSet<String>()
    override fun getUniqueTarget() = UniqueTarget.Tech

    var column: TechColumn? = null // The column that this tech is in the tech tree
    var row: Int = 0
    var quote = ""

    fun era(): String = column!!.era

    fun isContinuallyResearchable() = hasUnique(UniqueType.ResearchableMultipleTimes)


    /**
     * Textual description used in TechPickerScreen and AlertPopup(AlertType.TechResearched)
     */
    fun getDescription(ruleset: Ruleset): String {
        val lineList = ArrayList<String>() // more readable than StringBuilder, with same performance for our use-case
        for (unique in uniques) lineList += unique.tr()

        for (improvement in ruleset.tileImprovements.values) {
            for (unique in improvement.uniqueObjects) {
                if (unique.isOfType(UniqueType.Stats)) {
                    val requiredTech = unique.conditionals.firstOrNull { it.isOfType(UniqueType.ConditionalTech) }?.params?.get(0)
                    if (requiredTech != name) continue
                    lineList += "[${unique.params[0]}] from every [${improvement.name}]"
                } else if (unique.isOfType(UniqueType.ImprovementStatsOnTile)) {
                    val requiredTech = unique.conditionals.firstOrNull { it.isOfType(UniqueType.ConditionalTech) }?.params?.get(0)
                    if (requiredTech != name) continue
                    lineList += "[${unique.params[0]}] from every [${improvement.name}] on [${unique.params[1]}] tiles"
                }
            }
        }

        val viewingCiv = UncivGame.Current.worldScreen!!.viewingCiv
        val enabledUnits = getEnabledUnits(ruleset, viewingCiv)
        if (enabledUnits.any()) {
            lineList += "{Units enabled}: "
            for (unit in enabledUnits)
                lineList += " * " + unit.name.tr() + " (" + unit.getShortDescription() + ")"
        }

        val enabledBuildings = getEnabledBuildings(ruleset, viewingCiv)

        val regularBuildings = enabledBuildings.filter { !it.isAnyWonder() }
        if (regularBuildings.any()) {
            lineList += "{Buildings enabled}: "
            for (building in regularBuildings)
                lineList += "* " + building.name.tr() + " (" + building.getShortDescription() + ")"
        }

        val wonders = enabledBuildings.filter { it.isAnyWonder() }
        if (wonders.any()) {
            lineList += "{Wonders enabled}: "
            for (wonder in wonders)
                lineList += " * " + wonder.name.tr() + " (" + wonder.getShortDescription() + ")"
        }

        for (obj in getObsoletedObjects(ruleset, viewingCiv))
            lineList += "[${obj.name}] obsoleted"

        for (resource in ruleset.tileResources.values.asSequence().filter { it.revealedBy == name }
                .map { it.name })
            lineList += "Reveals [$resource] on the map"

        val tileImprovements = ruleset.tileImprovements.values.filter { it.techRequired == name }
        if (tileImprovements.isNotEmpty())
            lineList += "{Tile improvements enabled}: " + tileImprovements.joinToString { it.name.tr() }

        return lineList.joinToString("\n") { it.tr() }
    }

    /**
     * Returns a Sequence of [Building]s enabled by this Technology, filtered for [civInfo]'s uniques,
     * nuclear weapons and religion settings, and without those expressly hidden from Civilopedia.
     */
    // Used for Civilopedia, Alert and Picker, so if any of these decide to ignore the "Will not be displayed in Civilopedia" unique this needs refactoring
    fun getEnabledBuildings(ruleset: Ruleset, civInfo: CivilizationInfo?) = getFilteredBuildings(ruleset, civInfo)
        { it.requiredTech == name }

    /**
     * Returns a Sequence of [Building]s obsoleted by this Technology, filtered for [civInfo]'s uniques,
     * nuclear weapons and religion settings, and without those expressly hidden from Civilopedia.
     */
    // Used for Civilopedia, Alert and Picker, so if any of these decide to ignore the "Will not be displayed in Civilopedia" unique this needs refactoring

    fun getObsoletedObjects(ruleset: Ruleset, civInfo: CivilizationInfo?): Sequence<RulesetStatsObject> =
        (getFilteredBuildings(ruleset, civInfo){true}
                + ruleset.tileResources.values.asSequence()
                + ruleset.tileImprovements.values.filter {
                    it.uniqueTo == null || it.uniqueTo == civInfo?.civName
        }).filter { obj: RulesetStatsObject ->
            obj.getMatchingUniques(UniqueType.ObsoleteWith).any { it.params[0] == name }
        }

    // Helper: common filtering for both getEnabledBuildings and getObsoletedBuildings, difference via predicate parameter
    private fun getFilteredBuildings(
        ruleset: Ruleset,
        civInfo: CivilizationInfo?,
        predicate: (Building)->Boolean
    ): Sequence<Building> {
        val (nuclearWeaponsEnabled, religionEnabled) = getNukeAndReligionSwitches(civInfo)
        return ruleset.buildings.values.asSequence()
            .filter {
                predicate(it)   // expected to be the most selective, thus tested first
                && (it.uniqueTo == civInfo?.civName || it.uniqueTo == null && civInfo?.getEquivalentBuilding(it) == it)
                && (nuclearWeaponsEnabled || !it.hasUnique(UniqueType.EnablesNuclearWeapons))
                && (religionEnabled || !it.hasUnique(UniqueType.HiddenWithoutReligion))
                && !it.hasUnique(UniqueType.HiddenFromCivilopedia)
            }
    }

    private fun getNukeAndReligionSwitches(civInfo: CivilizationInfo?): Pair<Boolean, Boolean> {
        if (civInfo == null) return true to true
        return civInfo.gameInfo.run { gameParameters.nuclearWeaponsEnabled to isReligionEnabled() }
    }

    /**
     * Returns a Sequence of [BaseUnit]s enabled by this Technology, filtered for [civInfo]'s uniques,
     * nuclear weapons and religion settings, and without those expressly hidden from Civilopedia.
     */
    // Used for Civilopedia, Alert and Picker, so if any of these decide to ignore the "Will not be displayed in Civilopedia"/HiddenFromCivilopedia unique this needs refactoring
    fun getEnabledUnits(ruleset: Ruleset, civInfo: CivilizationInfo?): Sequence<BaseUnit> {
        val (nuclearWeaponsEnabled, religionEnabled) = getNukeAndReligionSwitches(civInfo)
        return ruleset.units.values.asSequence()
            .filter {
                it.requiredTech == name
                && (it.uniqueTo == civInfo?.civName || it.uniqueTo == null && civInfo?.getEquivalentUnit(it) == it)
                && (nuclearWeaponsEnabled || !it.isNuclearWeapon())
                && (religionEnabled || !it.hasUnique(UniqueType.HiddenWithoutReligion))
                && !it.hasUnique(UniqueType.HiddenFromCivilopedia)
            }
    }

    /** Get improvements related to this tech by a unique */
    private fun getSeeAlsoObjects(ruleset: Ruleset) =
        // This is a band-aid to clarify the relation Offshore platform - Refrigeration. A generic variant
        // covering all mentions in uniques in all ruleset objects would be possible here but - overkill for now.
        ruleset.tileImprovements.values
            .asSequence()
            .filter { improvement ->
                improvement.uniqueObjects.any {
                    unique -> unique.conditionals.any {
                        (it.isOfType(UniqueType.ConditionalTech) || it.isOfType(UniqueType.ConditionalNoTech))
                        && it.params[0] == name
                    }
                }
            }


    override fun makeLink() = "Technology/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val lineList = ArrayList<FormattedLine>()

        val eraColor = ruleset.eras[era()]?.getHexColor() ?: ""
        lineList += FormattedLine(era(), header = 3, color = eraColor)
        lineList += FormattedLine()
        lineList += FormattedLine("{Cost}: $cost${Fonts.science}")

        if (prerequisites.isNotEmpty()) {
            lineList += FormattedLine()
            if (prerequisites.size == 1)
                prerequisites.first().let { lineList += FormattedLine("Required tech: [$it]", link = "Technology/$it") }
            else {
                lineList += FormattedLine("Requires all of the following:")
                prerequisites.forEach {
                    lineList += FormattedLine(it, link = "Technology/$it")
                }
            }
        }

        val leadsTo = ruleset.technologies.values.filter { name in it.prerequisites }
        if (leadsTo.isNotEmpty()) {
            lineList += FormattedLine()
            if (leadsTo.size == 1)
                leadsTo.first().let { lineList += FormattedLine("Leads to [${it.name}]", link = it.makeLink()) }
            else {
                lineList += FormattedLine("Leads to:")
                leadsTo.forEach {
                    lineList += FormattedLine(it.name, link = it.makeLink())
                }
            }
        }

        if (uniques.isNotEmpty()) {
            lineList += FormattedLine()
            uniqueObjects.forEach {
                if (!it.hasFlag(UniqueFlag.HiddenToUsers))
                    lineList += FormattedLine(it)
            }
        }

        for (improvement in ruleset.tileImprovements.values)
            for (unique in improvement.uniqueObjects) {
                if (unique.isOfType(UniqueType.Stats)) {
                    val requiredTech = unique.conditionals.firstOrNull { it.isOfType(UniqueType.ConditionalTech) }?.params?.get(0)
                    if (requiredTech != name) continue
                    lineList += FormattedLine("[${unique.params[0]}] from every [${improvement.name}]",
                        link = improvement.makeLink())
                } else if (unique.placeholderText == "[] on [] tiles") {
                    val requiredTech = unique.conditionals.firstOrNull { it.isOfType(UniqueType.ConditionalTech) }?.params?.get(0)
                    if (requiredTech != name) continue
                    lineList += FormattedLine("[${unique.params[0]}] from every [${improvement.name}] on [${unique.params[1]}] tiles",
                        link = improvement.makeLink())
                }
            }

        val viewingCiv = UncivGame.Current.worldScreen?.viewingCiv
        val enabledUnits = getEnabledUnits(ruleset, viewingCiv)
        if (enabledUnits.any()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Units enabled}:")
            for (unit in enabledUnits)
                lineList += FormattedLine(unit.name.tr() + " (" + unit.getShortDescription() + ")", link = unit.makeLink())
        }

        val enabledBuildings = getEnabledBuildings(ruleset, viewingCiv)
            .partition { it.isAnyWonder() }
        if (enabledBuildings.first.isNotEmpty()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Wonders enabled}:")
            for (wonder in enabledBuildings.first)
                lineList += FormattedLine(wonder.name.tr() + " (" + wonder.getShortDescription() + ")", link = wonder.makeLink())
        }
        if (enabledBuildings.second.isNotEmpty()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Buildings enabled}:")
            for (building in enabledBuildings.second)
                lineList += FormattedLine(building.name.tr() + " (" + building.getShortDescription() + ")", link = building.makeLink())
        }

        val obsoletedObjects = getObsoletedObjects(ruleset, viewingCiv)
        if (obsoletedObjects.any()) {
            lineList += FormattedLine()
            obsoletedObjects.forEach {
                lineList += FormattedLine("[${it.name}] obsoleted", link = it.makeLink())
            }
        }

        val revealedResources = ruleset.tileResources.values.asSequence().filter { it.revealedBy == name }
        if (revealedResources.any()) {
            lineList += FormattedLine()
            revealedResources.forEach {
                lineList += FormattedLine("Reveals [${it.name}] on the map", link = it.makeLink())
            }
        }

        val tileImprovements = ruleset.tileImprovements.values.asSequence().filter { it.techRequired == name }
        if (tileImprovements.any()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Tile improvements enabled}:")
            tileImprovements.forEach {
                lineList += FormattedLine(it.name, link = it.makeLink())
            }
        }

        val seeAlsoObjects = getSeeAlsoObjects(ruleset)
        if (seeAlsoObjects.any()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{See also}:")
            seeAlsoObjects.forEach {
                lineList += FormattedLine(it.name, link = it.makeLink())
            }
        }

        return lineList
    }
}
