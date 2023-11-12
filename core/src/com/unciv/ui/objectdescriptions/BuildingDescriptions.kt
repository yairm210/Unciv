package com.unciv.ui.objectdescriptions

import com.unciv.logic.city.City
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.getConsumesAmountString
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.civilopediascreen.FormattedLine

object BuildingDescriptions {
    // Note: These are not extension functions for receiver Building because that would mean renaming getCivilopediaTextLines
    // here, otherwise there is no override syntax for Building.getCivilopediaTextLines that can access the helper.
    // To stay consistent, all take the Building as normal parameter instead.

    /** Used for AlertType.WonderBuilt, and as sub-text in Nation and Tech descriptions */
    fun getShortDescription(building: Building, multiline: Boolean = false): String = building.run {
        val infoList = mutableListOf<String>()
        this.clone().toString().also { if (it.isNotEmpty()) infoList += it }
        for ((key, value) in getStatPercentageBonuses(null))
            infoList += "+${value.toInt()}% ${key.name.tr()}"

        if (requiredNearbyImprovedResources != null)
            infoList += "Requires worked [" + requiredNearbyImprovedResources!!.joinToString("/") { it.tr() } + "] near city"
        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques.isNotEmpty()) infoList += replacementTextForUniques
            else infoList += getUniquesStringsWithoutDisablers()
        }
        if (cityStrength != 0) infoList += "{City strength} +$cityStrength"
        if (cityHealth != 0) infoList += "{City health} +$cityHealth"
        val separator = if (multiline) "\n" else "; "
        return infoList.joinToString(separator) { it.tr() }
    }

    /** used in CityScreen (ConstructionInfoTable) */
    fun getDescription(building: Building, city: City, showAdditionalInfo: Boolean): String = building.run {
        val stats = getStats(city)
        val translatedLines = ArrayList<String>() // Some translations require special handling
        val isFree = city.civ.civConstructions.hasFreeBuilding(city, this)
        if (uniqueTo != null) translatedLines += if (replaces == null) "Unique to [$uniqueTo]".tr()
        else "Unique to [$uniqueTo], replaces [$replaces]".tr()
        val missingUnique = getMatchingUniques(UniqueType.RequiresBuildingInAllCities).firstOrNull()
        if (isWonder) translatedLines += "Wonder".tr()
        if (isNationalWonder) translatedLines += "National Wonder".tr()
        if (!isFree) {
            for ((resourceName, amount) in getResourceRequirementsPerTurn(StateForConditionals(city.civ, city))) {
                val available = city.getResourceAmount(resourceName)
                val resource = city.getRuleset().tileResources[resourceName] ?: continue
                val consumesString = resourceName.getConsumesAmountString(amount, resource.isStockpiled())

                translatedLines += if (showAdditionalInfo) "$consumesString ({[$available] available})".tr()
                else consumesString.tr()
            }
        }

        // Inefficient in theory. In practice, buildings seem to have only a small handful of uniques.
        val missingCities = if (missingUnique != null)
        // TODO: Unify with rejection reasons?
            city.civ.cities.filterNot {
                it.isPuppet
                    || it.cityConstructions.containsBuildingOrEquivalent(missingUnique.params[0])
            }
        else listOf()
        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques.isNotEmpty()) translatedLines += replacementTextForUniques.tr()
            else translatedLines += getUniquesStringsWithoutDisablers(
                filterUniques = if (missingCities.isEmpty()) null
                else { unique -> !unique.isOfType(UniqueType.RequiresBuildingInAllCities) }
                // Filter out the "Requires a [] in all cities" unique if any cities are still missing the required building, since in that case the list of cities will be appended at the end.
            ).map { it.tr() }
        }
        if (!stats.isEmpty())
            translatedLines += stats.toString()

        for ((stat, value) in getStatPercentageBonuses(city))
            if (value != 0f) translatedLines += "+${value.toInt()}% {${stat.name}}".tr()

        for ((greatPersonName, value) in greatPersonPoints)
            translatedLines += "+$value " + "[$greatPersonName] points".tr()

        for ((specialistName, amount) in newSpecialists())
            translatedLines += "+$amount " + "[$specialistName] slots".tr()

        if (requiredNearbyImprovedResources != null)
            translatedLines += "Requires worked [${requiredNearbyImprovedResources!!.joinToString("/") { it.tr() }}] near city".tr()

        if (cityStrength != 0) translatedLines += "{City strength} +$cityStrength".tr()
        if (cityHealth != 0) translatedLines += "{City health} +$cityHealth".tr()
        if (maintenance != 0 && !isFree) translatedLines += "{Maintenance cost}: $maintenance {Gold}".tr()
        if (showAdditionalInfo && missingCities.isNotEmpty()) {
            // Could be red. But IMO that should be done by enabling GDX's ColorMarkupLanguage globally instead of adding a separate label.
            translatedLines += "\n" +
                "[${city.civ.getEquivalentBuilding(missingUnique!!.params[0])}] required:".tr() +
                " " + missingCities.joinToString(", ") { it.name.tr(hideIcons = true) }
            // Can't nest square bracket placeholders inside curlies, and don't see any way to define wildcard placeholders. So run translation explicitly on base text.
        }
        return translatedLines.joinToString("\n").trim()
    }

    /**
     * Lists differences: how a nation-unique Building compares to its replacement.
     *
     * Cost is **is** included.
     * Result as indented, non-linking [FormattedLine]s
     *
     * @param originalBuilding The "standard" Building
     * @param replacementBuilding The "uniqueTo" Building
     */
    fun getDifferences(
        ruleset: Ruleset, originalBuilding: Building, replacementBuilding: Building
    ): Sequence<FormattedLine> = sequence {
        for ((key, value) in replacementBuilding)
            if (value != originalBuilding[key])
                yield(FormattedLine( key.name.tr() + " " +"[${value.toInt()}] vs [${originalBuilding[key].toInt()}]".tr(), indent=1))

        if (replacementBuilding.maintenance != originalBuilding.maintenance)
            yield(FormattedLine("{Maintenance} ".tr() + "[${replacementBuilding.maintenance}] vs [${originalBuilding.maintenance}]".tr(), indent=1))
        if (replacementBuilding.cost != originalBuilding.cost)
            yield(FormattedLine("{Cost} ".tr() + "[${replacementBuilding.cost}] vs [${originalBuilding.cost}]".tr(), indent=1))
        if (replacementBuilding.cityStrength != originalBuilding.cityStrength)
            yield(FormattedLine("{City strength} ".tr() + "[${replacementBuilding.cityStrength}] vs [${originalBuilding.cityStrength}]".tr(), indent=1))
        if (replacementBuilding.cityHealth != originalBuilding.cityHealth)
            yield(FormattedLine("{City health} ".tr() + "[${replacementBuilding.cityHealth}] vs [${originalBuilding.cityHealth}]".tr(), indent=1))

        if (replacementBuilding.replacementTextForUniques.isNotEmpty()) {
            yield(FormattedLine(replacementBuilding.replacementTextForUniques, indent=1))
        } else {
            val newAbilityPredicate: (Unique)->Boolean = { it.text in originalBuilding.uniques || it.isHiddenToUsers() }
            for (unique in replacementBuilding.uniqueObjects.filterNot(newAbilityPredicate))
                yield(FormattedLine(unique.text, indent=1))  // FormattedLine(unique) would look worse - no indent and autolinking could distract
        }

        val lostAbilityPredicate: (Unique)->Boolean = { it.text in replacementBuilding.uniques || it.isHiddenToUsers() }
        for (unique in originalBuilding.uniqueObjects.filterNot(lostAbilityPredicate)) {
            // Need double translation of the "ability" here - unique texts may contain square brackets
            yield(FormattedLine("Lost ability (vs [${originalBuilding.name}]): [${unique.text.tr()}]", indent=1))
        }
    }

    fun getCivilopediaTextLines(building: Building, ruleset: Ruleset): List<FormattedLine> = building.run {
        fun Float.formatSignedInt() = (if (this > 0f) "+" else "") + this.toInt().toString()

        val textList = ArrayList<FormattedLine>()

        if (isAnyWonder()) {
            textList += FormattedLine( if (isWonder) "Wonder" else "National Wonder", color="#CA4", header=3 )
        }

        if (uniqueTo != null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [$uniqueTo]", link="Nation/$uniqueTo")
            if (replaces != null) {
                val replacesBuilding = ruleset.buildings[replaces]
                textList += FormattedLine("Replaces [$replaces]", link=replacesBuilding?.makeLink() ?: "", indent=1)
            }
        }

        if (cost > 0) {
            val stats = mutableListOf("$cost${Fonts.production}")
            if (canBePurchasedWithStat(null, Stat.Gold)) {
                stats += "${getCivilopediaGoldCost()}${Fonts.gold}"
            }
            textList += FormattedLine(stats.joinToString("/", "{Cost}: "))
        }

        if (requiredTech != null)
            textList += FormattedLine("Required tech: [$requiredTech]",
                link="Technology/$requiredTech")
        if (requiredBuilding != null)
            textList += FormattedLine("Requires [$requiredBuilding] to be built in the city",
                link="Building/$requiredBuilding")

        if (requiredResource != null) {
            textList += FormattedLine()
            val resource = ruleset.tileResources[requiredResource]
            textList += FormattedLine(
                requiredResource!!.getConsumesAmountString(1, resource!!.isStockpiled()),
                link="Resources/$requiredResource", color="#F42" )
        }

        val stats = cloneStats()
        val percentStats = getStatPercentageBonuses(null)
        val specialists = newSpecialists()
        if (uniques.isNotEmpty() || !stats.isEmpty() || !percentStats.isEmpty() || this.greatPersonPoints.isNotEmpty() || specialists.isNotEmpty())
            textList += FormattedLine()

        if (replacementTextForUniques.isNotEmpty()) {
            textList += FormattedLine(replacementTextForUniques)
        } else {
            uniquesToCivilopediaTextLines(textList, colorConsumesResources = true)
        }

        if (!stats.isEmpty()) {
            textList += FormattedLine(stats.toString())
        }

        if (!percentStats.isEmpty()) {
            for ((key, value) in percentStats) {
                if (value == 0f) continue
                textList += FormattedLine(value.formatSignedInt() + "% {$key}")
            }
        }

        for ((greatPersonName, value) in greatPersonPoints) {
            textList += FormattedLine(
                "+$value " + "[$greatPersonName] points".tr(),
                link = "Unit/$greatPersonName"
            )
        }

        if (specialists.isNotEmpty()) {
            for ((specialistName, amount) in specialists)
                textList += FormattedLine("+$amount " + "[$specialistName] slots".tr())
        }

        if (requiredNearbyImprovedResources != null) {
            textList += FormattedLine()
            textList += FormattedLine("Requires at least one of the following resources worked near the city:")
            requiredNearbyImprovedResources!!.forEach {
                textList += FormattedLine(it, indent = 1, link = "Resource/$it")
            }
        }

        if (cityStrength != 0 || cityHealth != 0 || maintenance != 0) textList += FormattedLine()
        if (cityStrength != 0) textList +=  FormattedLine("{City strength} +$cityStrength")
        if (cityHealth != 0) textList +=  FormattedLine("{City health} +$cityHealth")
        if (maintenance != 0) textList +=  FormattedLine("{Maintenance cost}: $maintenance {Gold}")

        val seeAlso = ArrayList<FormattedLine>()
        for (seeAlsoBuilding in ruleset.buildings.values) {
            if (seeAlsoBuilding.replaces == name
                || seeAlsoBuilding.uniqueObjects.any { unique -> unique.params.any { it == name } })
                seeAlso += FormattedLine(seeAlsoBuilding.name, link = seeAlsoBuilding.makeLink(), indent=1)
        }
        seeAlso += Belief.getCivilopediaTextMatching(name, ruleset, false)
        if (seeAlso.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{See also}:")
            textList += seeAlso
        }

        return textList
    }

    /**
     * @param filterUniques If provided, include only uniques for which this function returns true.
     */
    private fun Building.getUniquesStrings(filterUniques: ((Unique) -> Boolean)? = null) = sequence {
        val tileBonusHashmap = HashMap<String, ArrayList<String>>()
        for (unique in uniqueObjects) if (filterUniques == null || filterUniques(unique)) when {
            unique.isOfType(UniqueType.StatsFromTiles) && unique.params[2] == "in this city" -> {
                val stats = unique.params[0]
                if (!tileBonusHashmap.containsKey(stats)) tileBonusHashmap[stats] = ArrayList()
                tileBonusHashmap[stats]!!.add(unique.params[1])
            }
            else -> yield(unique.text)
        }
        for ((key, value) in tileBonusHashmap)
            yield( "[stats] from [tileFilter] tiles in this city"
                .fillPlaceholders( key,
                    // A single tileFilter will be properly translated later due to being within []
                    // advantage to not translate prematurely: FormatLine.formatUnique will recognize it
                    if (value.size == 1) value[0] else value.joinToString { it.tr() }
                ))
    }

    /**
     * @param filterUniques If provided, include only uniques for which this function returns true.
     */
    private fun Building.getUniquesStringsWithoutDisablers(filterUniques: ((Unique) -> Boolean)? = null) = getUniquesStrings {
        !it.isHiddenToUsers()
            && (filterUniques?.invoke(it) ?: true)
    }

}
