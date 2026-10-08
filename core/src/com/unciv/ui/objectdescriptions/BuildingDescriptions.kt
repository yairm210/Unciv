package com.unciv.ui.objectdescriptions

import com.unciv.logic.city.City
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.getConsumesAmountString
import com.unciv.ui.components.extensions.getCostsAmountString
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.objectdescriptions.FormattedLineListBuilder.Companion.buildCivilopediaText
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Mutated
import yairm210.purity.annotations.Readonly

object BuildingDescriptions {
    // Note: These are not extension functions for receiver Building because that would mean renaming getCivilopediaTextLines
    // here, otherwise there is no override syntax for Building.getCivilopediaTextLines that can access the helper.
    // To stay consistent, all take the Building as normal parameter instead.

    /** Used for AlertType.WonderBuilt, and as sub-text in Nation and Tech descriptions */
    fun getShortDescription(building: Building, multiline: Boolean = false, uniqueInclusionFilter: ((Unique) -> Boolean)? = null): String = building.run {
        val infoList = mutableListOf<String>()
        this.clone().toString().also { if (it.isNotEmpty()) infoList += it }
        for ((key, value) in getStatPercentageBonuses(null))
            infoList += "+${value.toInt()}% ${key.name.tr()}"

        if (requiredNearbyImprovedResources != null)
            infoList += "Requires improved [" + requiredNearbyImprovedResources!!.joinToString("/") { it.tr() } + "] near city"
        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques.isNotEmpty()) infoList += replacementTextForUniques
            else infoList += getUniquesStringsWithoutDisablers(uniqueInclusionFilter)
        }
        if (cityStrength != 0.0) infoList += "{City strength} +$cityStrength"
        if (cityHealth != 0) infoList += "{City health} +$cityHealth"
        val separator = if (multiline) "\n" else "; "
        return infoList.joinToString(separator) { it.tr() }
    }

    /** used in CityScreen (ConstructionInfoTable) */
    @Readonly
    fun getDescription(building: Building, city: City, showAdditionalInfo: Boolean): String = building.run {
        val stats = getStats(city)
        val translatedLines = ArrayList<String>() // Some translations require special handling
        val isFree = city.civ.civConstructions.hasFreeBuilding(city, this)
        if (uniqueTo != null) translatedLines += if (replaces == null) "Unique to [$uniqueTo]".tr()
        else "Unique to [$uniqueTo], replaces [$replaces]".tr()
        if (isWonder) translatedLines += "Wonder".tr()
        if (isNationalWonder) translatedLines += "National Wonder".tr()
        if (!isFree) {
            // Consumes
            for ((resourceName, amount) in getResourceRequirementsPerTurn(city.state)) {
                val available = if (showAdditionalInfo) city.getAvailableResourceAmount(resourceName) else -1
                val resource = city.getRuleset().tileResources[resourceName] ?: continue
                translatedLines += resourceName.getConsumesAmountString(amount, resource.isStockpiled, available).tr()
            }

            // Costs
            for ((resourceName, amount) in getStockpiledResourceRequirements(city.state)) {
                if (city.getRuleset().tileResources[resourceName] == null) continue
                val available = if (showAdditionalInfo) city.getAvailableResourceAmount(resourceName) else -1
                translatedLines += resourceName.getCostsAmountString(amount, available).tr()
            }
        }

        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques.isNotEmpty()) translatedLines += replacementTextForUniques.tr()
            else translatedLines += getUniquesStringsWithoutDisablers {
                it.type != UniqueType.ConsumesResources &&
                it.type != UniqueType.CostsResources
            }.map { it.tr() }
        }
        if (!stats.isEmpty())
            translatedLines += stats.toString()

        for ((stat, value) in getStatPercentageBonuses(city))
            if (value != 0f) translatedLines += "${value.toInt().toStringSigned()}% {${stat.name}}".tr()

        for ((greatPersonName, value) in greatPersonPoints)
            translatedLines += "+$value " + "[$greatPersonName] points".tr()

        for ((specialistName, amount) in newSpecialists())
            translatedLines += "+$amount " + "[$specialistName] slots".tr()

        if (requiredNearbyImprovedResources != null)
            translatedLines += "Requires improved [${requiredNearbyImprovedResources!!.joinToString("/") { it.tr() }}] near city".tr()

        if (cityStrength != 0.0) translatedLines += "{City strength} +$cityStrength".tr()
        if (cityHealth != 0) translatedLines += "{City health} +$cityHealth".tr()
        if (maintenance != 0 && !isFree) translatedLines += "{Maintenance cost}: $maintenance {Gold}".tr()
        if (showAdditionalInfo) additionalDescription(building, city, translatedLines)
        return translatedLines.joinToString("\n").trim()
    }

    @Readonly
    fun additionalDescription (building: Building, city: City, @Mutated lines: ArrayList<String>) {
        // Inefficient in theory. In practice, buildings seem to have only a small handful of uniques.
        for (unique in building.uniqueObjects) {
            if (unique.type == UniqueType.OnlyAvailable || unique.type == UniqueType.CanOnlyBeBuiltWhen)
                for (conditional in unique.getModifiers(UniqueType.ConditionalBuildingBuiltAll)) {
                    missingCityText(conditional.params[0], city, conditional.params[1], lines)
                }
        }
    }

    // TODO: Unify with rejection reasons?
    @Readonly
    fun missingCityText (building: String, city: City, filter: String, @Mutated lines: ArrayList<String>) {
        val missingCities = city.civ.cities.filter {
            it.matchesFilter(filter) && !it.cityConstructions.containsBuildingOrEquivalent(building)
        }
        // Could be red. But IMO that should be done by enabling GDX's ColorMarkupLanguage globally instead of adding a separate label.
        if (missingCities.isNotEmpty()) lines += "\n" +
            "[${city.civ.getEquivalentBuilding(building)}] required:".tr() +
            " " + missingCities.joinToString(", ") { it.name.tr(hideIcons = true) }
        // Can't nest square bracket placeholders inside curlies, and don't see any way to define wildcard placeholders. So run translation explicitly on base text.
    }

    /**
     * Lists differences to a [FormattedLineListBuilder]: how a nation-unique Building compares to its replacement.
     *
     * Cost **is** included.
     *
     * @param originalBuilding The "standard" Building
     * @param replacementBuilding The "uniqueTo" Building
     */
    fun FormattedLineListBuilder.addBuildingDifferences(originalBuilding: Building, replacementBuilding: Building) {
        for (stat in Stat.entries) // Do not iterate on object since that excludes zero values
            if (replacementBuilding[stat] != originalBuilding[stat])
                add(stat.name.tr() + " " +"[${replacementBuilding[stat].toInt()}] vs [${originalBuilding[stat].toInt()}]".tr(), indent=1)

        val originalStatBonus = originalBuilding.getStatPercentageBonuses(null)
        val replacementStatBonus = replacementBuilding.getStatPercentageBonuses(null)
        for (stat in Stat.entries)
            if (replacementStatBonus[stat] != originalStatBonus[stat])
                add("[${replacementStatBonus[stat].toInt()}]% [${stat.name}] vs [${originalStatBonus[stat].toInt()}]% [${stat.name}]", indent = 1)

        if (replacementBuilding.maintenance != originalBuilding.maintenance)
            add("{Maintenance} ".tr() + "[${replacementBuilding.maintenance}] vs [${originalBuilding.maintenance}]".tr(), indent = 1)
        if (replacementBuilding.cost != originalBuilding.cost)
            add("{Cost} ".tr() + "[${replacementBuilding.cost}] vs [${originalBuilding.cost}]".tr(), indent = 1)
        if (replacementBuilding.cityStrength != originalBuilding.cityStrength)
            add("{City strength} ".tr() + "[${replacementBuilding.cityStrength}] vs [${originalBuilding.cityStrength}]".tr(), indent = 1)
        if (replacementBuilding.cityHealth != originalBuilding.cityHealth)
            add("{City health} ".tr() + "[${replacementBuilding.cityHealth}] vs [${originalBuilding.cityHealth}]".tr(), indent = 1)

        if (replacementBuilding.replacementTextForUniques.isNotEmpty()) {
            add(replacementBuilding.replacementTextForUniques, indent = 1)
        } else {
            val newAbilityPredicate: (Unique)->Boolean = { it.text in originalBuilding.uniques || it.isHiddenToUsers() }
            for (unique in replacementBuilding.uniqueObjects.filterNot(newAbilityPredicate))
                add(unique.getDisplayText(), indent = 1)  // FormattedLine(unique) would look worse - no indent and autolinking could distract
        }

        val lostAbilityPredicate: (Unique)->Boolean = { it.text in replacementBuilding.uniques || it.isHiddenToUsers() }
        for (unique in originalBuilding.uniqueObjects.filterNot(lostAbilityPredicate)) {
            // Need double translation of the "ability" here - unique texts may contain square brackets
            add("Lost ability (vs [${originalBuilding.name}]): [${unique.text.tr()}]", indent = 1)
        }
    }

    fun Building.getBuildingCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> = buildCivilopediaText {
        fun Float.formatSignedInt() = (if (this > 0f) "+" else "") + this.toInt().tr()

        if (isAnyWonder()) {
            add(if (isWonder) "Wonder" else "National Wonder", color="#CA4", header=3 )
        }

        if (uniqueTo != null) {
            space()
            add("Unique to [$uniqueTo]", link="Nation/$uniqueTo")
            if (replaces != null) {
                val replacesBuilding = ruleset.buildings[replaces]
                add("Replaces [$replaces]", link=replacesBuilding?.makeLink() ?: "", indent = 1)
            }
        }

        if (cost > 0) {
            val stats = mutableListOf("$cost${Fonts.production}")
            if (canBePurchasedWithStat(null, Stat.Gold)) {
                stats += "${getCivilopediaGoldCost()}${Fonts.gold}"
            }
            add(stats.joinToString("/", "{Cost}: "))
        }

        if (requiredTech != null)
            add("Required tech: [$requiredTech]",
                link="Technology/$requiredTech")
        if (requiredBuilding != null) {
            val linkType = if (ruleset.buildings[requiredBuilding]?.isWonder == true) "Wonder" else "Building"
            add(
                "Requires [$requiredBuilding] to be built in the city",
                link="$linkType/$requiredBuilding"
            )
        }

        if (requiredResource != null) {
            space()
            val resource = ruleset.tileResources[requiredResource]
            add(
                requiredResource!!.getConsumesAmountString(1, resource!!.isStockpiled),
                link="Resources/$requiredResource", color="#F42" )
        }

        val stats = cloneStats()
        val percentStats = getStatPercentageBonuses(null)
        val specialists = newSpecialists()
        if (uniques.isNotEmpty() || !stats.isEmpty() || !percentStats.isEmpty() || greatPersonPoints.isNotEmpty() || specialists.isNotEmpty())
            space()

        if (replacementTextForUniques.isNotEmpty()) {
            add(replacementTextForUniques)
        } else {
            addUniques(colorConsumesResources = true)
        }

        if (!stats.isEmpty()) {
            add(stats.toString())
        }

        if (!percentStats.isEmpty()) {
            for ((key, value) in percentStats) {
                if (value == 0f) continue
                add(value.formatSignedInt() + "% {$key}")
            }
        }

        for ((greatPersonName, value) in greatPersonPoints) {
            add(
                "+$value " + "[$greatPersonName] points".tr(),
                link = "Unit/$greatPersonName"
            )
        }

        if (specialists.isNotEmpty()) {
            for ((specialistName, amount) in specialists)
                add("+$amount " + "[$specialistName] slots".tr())
        }

        if (requiredNearbyImprovedResources != null) {
            space()
            add("Requires at least one of the following resources improved near the city:")
            requiredNearbyImprovedResources!!.forEach {
                add(it, indent = 1, link = "Resource/$it")
            }
        }

        if (cityStrength != 0.0 || cityHealth != 0 || maintenance != 0) space()
        if (cityStrength != 0.0) add("{City strength} +$cityStrength")
        if (cityHealth != 0) add("{City health} +$cityHealth")
        if (maintenance != 0) add("{Maintenance cost}: $maintenance {Gold}")

        fun filterOtherBuilding(building: Building) = building.replaces == name || building.hasUniquesMentioning(name)
        val seeAlso = ruleset.buildings.values.asSequence().filter(::filterOtherBuilding) + Belief.getBeliefsMatching(name, ruleset)
        addSeeAlso(seeAlso)
    }

    /**
     * @param filterUniques If provided, include only uniques for which this function returns true.
     */
    @Readonly
    private fun Building.getUniquesStrings(filterUniques: ((Unique) -> Boolean)? = null) = sequence {
        val tileBonusHashmap = HashMap<String, ArrayList<String>>()
        for (unique in uniqueObjects) if (filterUniques == null || filterUniques(unique)) when {
            unique.type == UniqueType.StatsFromTiles && unique.params[2] == "in this city" -> {
                val stats = unique.params[0]
                if (!tileBonusHashmap.containsKey(stats)) tileBonusHashmap[stats] = ArrayList()
                @LocalState val statsBonus =tileBonusHashmap[stats]!! 
                statsBonus.add(unique.params[1])
            }
            else -> yield(unique.getDisplayText())
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
    @Readonly
    private fun Building.getUniquesStringsWithoutDisablers(filterUniques: ((Unique) -> Boolean)? = null): Sequence<String> = getUniquesStrings {
        !it.isHiddenToUsers()
            && (filterUniques?.invoke(it) ?: true)
    }

}
