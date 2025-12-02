package com.unciv.ui.objectdescriptions

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.PortraitUnavailableWonderForTechTree
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.ICivilopediaText
import com.unciv.ui.screens.pickerscreens.TechButton


object TechnologyDescriptions {
    //region Methods called from Technology
    /**
     * Textual description used in TechPickerScreen and AlertPopup(AlertType.TechResearched) -
     * Civilization always known and description tailored to
     */
    fun getDescription(technology: Technology, viewingCiv: Civilization): String = technology.run {
        val ruleset = viewingCiv.gameInfo.ruleset
        val lineList = ArrayList<String>() // more readable than StringBuilder, with same performance for our use-case

        for (pediaText in technology.civilopediaText) {
            // This is explicitly to get the "Who knows what the future holds" of Future Tech back into
            // the Tech Picker and Tech Researched Alert display, without making it an untyped Unique.
            // May need tuning for mods, in vanilla there is just the one case.
            if (pediaText.text.isEmpty() || pediaText.header != 0) continue
            lineList += pediaText.text
        }

        uniquesToDescription(lineList)

        lineList.addAll(
            getAffectedImprovements(name, ruleset)
            .filter { it.improvement.uniqueTo == null || viewingCiv.matchesFilter(it.improvement.uniqueTo!!) }
            .map { it.getText() }
        )

        val enabledUnits = getEnabledUnits(name, ruleset, viewingCiv)
        if (enabledUnits.any()) {
            lineList += "{Units enabled}: "
            for (unit in enabledUnits)
                lineList += " • ${unit.name.tr()} (${unit.getShortDescription(uniqueExclusionFilter=technology::uniqueIsRequirementForThisTech)})\n"
        }

        val (wonders, regularBuildings) = getEnabledBuildings(name, ruleset, viewingCiv)
            .partition { it.isAnyWonder() }

        if (regularBuildings.isNotEmpty()) {
            lineList += "{Buildings enabled}: "
            for (building in regularBuildings)
                lineList += " • ${building.name.tr()} (${building.getShortDescription(uniqueInclusionFilter=technology::uniqueIsNotRequirementForThisTech)})\n"
        }

        if (wonders.isNotEmpty()) {
            lineList += "{Wonders enabled}: "
            for (wonder in wonders)
                lineList += " • ${wonder.name.tr()} (${wonder.getShortDescription(uniqueInclusionFilter=technology::uniqueIsNotRequirementForThisTech)})\n"
        }

        for (obj in getObsoletedObjects(name, ruleset, viewingCiv))
            lineList += "[${obj.name}] obsoleted"

        val resourcesRevealed = ruleset.tileResources.values.asSequence()
            .filter { it.revealedBy == name }
            .map { it.name }
        for (resource in resourcesRevealed)
            lineList += "Reveals [$resource] on the map"

        val tileImprovements = ruleset.tileImprovements.values.asSequence()
            .filter { it.techRequired == name }
            .filter { it.uniqueTo == null || viewingCiv.matchesFilter(it.uniqueTo!!) }
            .toList()
        if (tileImprovements.isNotEmpty())
            lineList += "{Tile improvements enabled}: " + tileImprovements.joinToString { it.name.tr() }

        return lineList.joinToString("\n") { it.tr() }
    }

    /**
     *  Gets icons to display on a [TechButton] - all should be also described in [getDescription]
     */
    fun getTechEnabledIcons(tech: Technology, viewingCiv: Civilization, techIconSize: Float) = sequence {
        val ruleset = viewingCiv.gameInfo.ruleset
        val techName = tech.name

        for (unit in getEnabledUnits(techName, ruleset, viewingCiv)) {
            yield(ImageGetter.getConstructionPortrait(unit.name, techIconSize))
        }

        for (building in getEnabledBuildings(techName, ruleset, viewingCiv)) {
            // We don't need to show the unavailable marker for techs that are already researched
            // since this is mostly a feature to choose which technologies to research.
            if (building.isWonder && !viewingCiv.tech.isResearched(techName)) {
                val isAlreadyBuilt = viewingCiv.gameInfo.getCities()
                    // This is theoretically not necessary since we already checked the viewingCiv
                    // doesn't have the tech, so it can't have this built anyways. It should be a
                    // little more performant though to add this filter.
                    .filter{ it.civ != viewingCiv }
                    .any { it.cityConstructions.isBuilt(building.name)}
                val wonderConstructionPortrait =
                        if (isAlreadyBuilt)
                            PortraitUnavailableWonderForTechTree(building.name, techIconSize)
                        else
                            ImageGetter.getConstructionPortrait(building.name, techIconSize)
                yield(wonderConstructionPortrait)
            } else {
                yield(ImageGetter.getConstructionPortrait(building.name, techIconSize))
            }
        }

        yieldAll(
            getObsoletedObjects(techName, ruleset, viewingCiv)
                .mapNotNull { it.getObsoletedIcon(techIconSize) }
        )

        for (resource in ruleset.tileResources.values.filter { it.revealedBy == techName }) {
            yield(ImageGetter.getResourcePortrait(resource.name, techIconSize))
        }

        for (improvement in ruleset.tileImprovements.values.asSequence()
            .filter { it.techRequired == techName }
            .filter { it.uniqueTo == null || viewingCiv.matchesFilter(it.uniqueTo!!) }
        ) {
            yield(ImageGetter.getImprovementPortrait(improvement.name, techIconSize))
        }

        for (improvement in ruleset.tileImprovements.values.asSequence()
            .filter { it.uniqueObjects.any { u -> u.allParams.contains(techName) } }
            .filter { it.uniqueTo == null || viewingCiv.matchesFilter(it.uniqueTo!!) }
        ) {
            yield(ImageGetter.getUniquePortrait(improvement.name, techIconSize))
        }

        for (unique in tech.uniqueObjects) {
            if (unique.isHiddenToUsers()) continue
            yield(
                when {
                    unique.type == UniqueType.EnablesCivWideStatProduction ->
                        ImageGetter.getConstructionPortrait(unique.params[0], techIconSize)
                    else ->
                        ImageGetter.getUniquePortrait(unique.text, techIconSize)
                }
            )
        }
    }

    /**
     * Implementation of [ICivilopediaText.getCivilopediaTextLines]
     */
    fun getCivilopediaTextLines(technology: Technology, ruleset: Ruleset): List<FormattedLine> = technology.run {
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

        uniquesToCivilopediaTextLines(lineList)

        val affectedImprovements = getAffectedImprovements(name, ruleset)
        if (affectedImprovements.any()) {
            lineList += FormattedLine()
            for (entry in affectedImprovements) {
                lineList += FormattedLine(entry.getText(), link = entry.improvement.makeLink())
            }
        }

        val enabledUnits = getEnabledUnits(name, ruleset, null)
        if (enabledUnits.any()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Units enabled}:")
            for (unit in enabledUnits)
                lineList += FormattedLine(unit.name.tr(true) + " (" + unit.getShortDescription(uniqueExclusionFilter=technology::uniqueIsRequirementForThisTech) + ")", link = unit.makeLink())
        }

        val (wonders, regularBuildings) = getEnabledBuildings(name, ruleset, null)
            .partition { it.isAnyWonder() }

        if (wonders.isNotEmpty()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Wonders enabled}:")
            for (wonder in wonders)
                lineList += FormattedLine(wonder.name.tr(true) + " (" + wonder.getShortDescription(uniqueInclusionFilter=technology::uniqueIsNotRequirementForThisTech) + ")", link = wonder.makeLink())
        }

        if (regularBuildings.isNotEmpty()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{Buildings enabled}:")
            for (building in regularBuildings) {
                val text = building.name.tr(true) + " (" + building.getShortDescription(uniqueInclusionFilter = technology::uniqueIsNotRequirementForThisTech) + ")"
                lineList += FormattedLine(text, padding = 15f, link = building.makeLink())
            }
        }

        val obsoletedObjects = getObsoletedObjects(name, ruleset, null).toList()
        if (obsoletedObjects.isNotEmpty()) {
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
        if (seeAlsoObjects.isNotEmpty()) {
            lineList += FormattedLine()
            lineList += FormattedLine("{See also}:")
            seeAlsoObjects.forEach {
                lineList += FormattedLine(it.name, link = it.makeLink())
            }
        }

        return lineList
    }

    //endregion
    //region Helpers

    /**
     * Returns a Sequence of [Building]s enabled by this Technology, filtered for [civInfo]'s uniques,
     * nuclear weapons and religion settings, and without those expressly hidden from Civilopedia.
     */
    // Used for Civilopedia, Alert and Picker, so if any of these decide to ignore the "Will not be displayed in Civilopedia" unique this needs refactoring
    private fun getEnabledBuildings(techName: String, ruleset: Ruleset, civInfo: Civilization?) =
            getFilteredBuildings(ruleset, civInfo) { it.requiredTechs().contains(techName) }

    /**
     * Returns a Sequence of [RulesetStatsObject]s obsoleted by this Technology, filtered for [civInfo]'s uniques,
     * nuclear weapons and religion settings, and without those expressly hidden from Civilopedia.
     */
    // Used for Civilopedia, Alert and Picker, so if any of these decide to ignore the "Will not be displayed in Civilopedia" unique this needs refactoring
    private fun getObsoletedObjects(techName: String, ruleset: Ruleset, civInfo: Civilization?): Sequence<RulesetStatsObject> =
            (
                    getFilteredBuildings(ruleset, civInfo) { true }
                    + ruleset.tileResources.values.asSequence()
                    + ruleset.tileImprovements.values.filter {
                        it.uniqueTo == null || civInfo?.matchesFilter(it.uniqueTo!!) == true
                    }
            ).filter { obj: RulesetStatsObject ->
                obj.getMatchingUniques(UniqueType.ObsoleteWith).any { it.params[0] == techName }
            }

    /** Readability - for the 'obsoleted' in [getTechEnabledIcons] */
    private fun RulesetStatsObject.getObsoletedIcon(techIconSize: Float) =
        when (this) {
            is Building -> ImageGetter.getConstructionPortrait(name, techIconSize)
            is TileResource -> ImageGetter.getResourcePortrait(name, techIconSize)
            is TileImprovement -> ImageGetter.getImprovementPortrait(name, techIconSize)
            else -> null
        }?.also {
            val closeImage = ImageGetter.getRedCross(techIconSize / 2, 1f)
            closeImage.center(it)
            it.addActor(closeImage)
        }

    /** Common filtering for both [getEnabledBuildings] and [getObsoletedObjects], difference via predicate parameter */
    private fun getFilteredBuildings(
        ruleset: Ruleset,
        civInfo: Civilization?,
        predicate: (Building) -> Boolean
    ): Sequence<Building> {
        return ruleset.buildings.values.asSequence()
            .filter {
                predicate(it)   // expected to be the most selective, thus tested first
                && (it.uniqueTo != null && civInfo?.matchesFilter(it.uniqueTo!!) == true
                        || it.uniqueTo == null && (civInfo == null || civInfo.getEquivalentBuilding(it) == it))
                && !it.isHiddenFromCivilopedia(ruleset)
            }
    }

    /**
     * Returns a Sequence of [BaseUnit]s enabled by this Technology, filtered for [civInfo]'s uniques,
     * nuclear weapons and religion settings, and without those expressly hidden from Civilopedia.
     */
    // Used for Civilopedia, Alert and Picker, so if any of these decide to ignore the "Will not be displayed in Civilopedia"/HiddenFromCivilopedia unique this needs refactoring
    private fun getEnabledUnits(techName: String, ruleset: Ruleset, civInfo: Civilization?): Sequence<BaseUnit> {
        return ruleset.units.values.asSequence()
            .filter {
                it.requiredTechs().contains(techName)
                && (it.uniqueTo != null && civInfo?.matchesFilter(it.uniqueTo!!) == true ||
                        it.uniqueTo == null && (civInfo == null || civInfo.getEquivalentUnit(it) == it))
                && !it.isHiddenFromCivilopedia(ruleset)
            }
    }

    /** Tests whether a Unique means bonus Stats enabled by [techName] */
    private fun Unique.isImprovementStatsEnabledByTech(techName: String) =
            (type == UniqueType.Stats || type == UniqueType.ImprovementStatsOnTile) &&
                    getModifiers(UniqueType.ConditionalTech).any { it.params[0] == techName }

    /** Tests whether a Unique Conditional is enabling or disabling its parent by a tech */
    private fun Unique.isTechConditional() =
            type == UniqueType.ConditionalTech ||
            type == UniqueType.ConditionalNoTech

    /** Tests whether a Unique is enabled or disabled by [techName] */
    private fun Unique.isRelatedToTech(techName: String) =
            modifiers.any { it.isTechConditional() && it.params[0] == techName }

    /** Used by [getAffectedImprovements] only */
    private data class ImprovementAndUnique(val improvement: TileImprovement, val unique: Unique) {
        fun getText() = "[${unique.params[0]}] from every [${improvement.name}]" +
                (if (unique.type == UniqueType.Stats) "" else " on [${unique.params[1]}] tiles")
    }

    /** Yields Improvements with bonus Stats enabled by [techName] including the Unique doing it */
    private fun getAffectedImprovements(techName: String, ruleset: Ruleset): Sequence<ImprovementAndUnique> =
            ruleset.tileImprovements.values.asSequence()
                .flatMap { improvement ->
                    improvement.uniqueObjects.asSequence()
                        .filter { it.isImprovementStatsEnabledByTech(techName) }
                        .map { ImprovementAndUnique(improvement, it) }
                }

    /** Get other objects related to this tech by a unique,
     *  e.g. Petra/Archaeology or Work Boats/Astronomy
     *  but not including the Improvement Stats increases already listed */
    private fun Technology.getSeeAlsoObjects(ruleset: Ruleset) =
            ruleset.allRulesetObjects()
                .filter { iRulesetObject ->
                    iRulesetObject.uniqueObjects.any {
                        it.isRelatedToTech(name) && !it.isImprovementStatsEnabledByTech(name)
                    }
                }.toList()

    //endregion
}
