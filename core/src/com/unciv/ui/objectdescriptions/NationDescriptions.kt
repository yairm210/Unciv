package com.unciv.ui.objectdescriptions

import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.nation.Personality
import com.unciv.models.ruleset.nation.PersonalityValue
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.squareBraceRegex
import com.unciv.models.translations.tr
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import yairm210.purity.annotations.Readonly
import kotlin.collections.get

object NationDescriptions {
    fun Nation.getCivilopediaTextLinesImpl(ruleset: Ruleset): List<FormattedLine> {
        val personalityObj = ruleset.personalities[personality]

        val textList = ArrayList<FormattedLine>()

        if (isCityState) textList += getCityStateInfo(ruleset)

        if (leaderName.isNotEmpty()) {
            textList += FormattedLine(extraImage = "LeaderIcons/$leaderName", imageSize = 200f)
            textList += FormattedLine(getLeaderDisplayName(), centered = true, header = 3)
        }
        if (personalityObj != null)
            textList += FormattedLine("{Personality}: {$personalityObj}", link = personalityObj.makeLink(), centered = true)
        if (leaderName.isNotEmpty() || personalityObj != null)
            textList += FormattedLine()

        if (uniqueName != "")
            textList += FormattedLine("{$uniqueName}:", header = 4)
        if (uniqueText != "") {
            textList += FormattedLine(uniqueText, indent = 1)
        } else {
            uniquesToCivilopediaTextLines(textList, leadingSeparator = null)
        }
        textList += FormattedLine()

        val effectiveStartBias = getStartBias(ruleset)
        if (effectiveStartBias.isNotEmpty()) {
            effectiveStartBias.withIndex().forEach {
                // can be "Avoid []"
                val link = if ('[' !in it.value) it.value
                else squareBraceRegex.find(it.value)!!.groups[1]!!.value
                textList += FormattedLine(
                    (if (it.index == 0) "[Start bias:] " else "") + it.value.tr(),  // extra tr because tr cannot nest {[]}
                    link = "Terrain/$link",
                    indent = if (it.index == 0) 0 else 1,
                    iconCrossed = it.value.startsWith("Avoid "))
            }
            textList += FormattedLine()
        }
        textList += getUniqueBuildingsText(ruleset)
        textList += getUniqueUnitsText(ruleset)
        textList += getUniqueImprovementsText(ruleset)

        return textList
    }

    @Readonly
    private fun Nation.getCityStateInfo(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        val cityStateType = ruleset.cityStateTypes[cityStateType]!!
        textList += FormattedLine("{Type}: {${cityStateType.name}}", header = 4, color = "#"+cityStateType.getColor().toString())

        var showResources = false

        fun addBonusLines(header: String, uniqueMap: UniqueMap) {
            // Note: Using getCityStateBonuses would be nice, but it's bound to a CityStateFunctions instance without even using `this`.
            // Too convoluted to reuse that here - but feel free to refactor that into a static.
            val bonuses = uniqueMap.getAllUniques().filterNot { it.isHiddenToUsers() }
            if (bonuses.none()) return
            textList += FormattedLine()
            textList += FormattedLine("{$header} ")
            for (unique in bonuses) {
                textList += FormattedLine(unique, indent = 1)
                if (unique.type == UniqueType.CityStateUniqueLuxury) showResources = true
            }
        }

        addBonusLines("When Friends:", cityStateType.friendBonusUniqueMap)
        addBonusLines("When Allies:", cityStateType.allyBonusUniqueMap)

        if (showResources) {
            val allMercantileResources = ruleset.tileResources.values
                .filter { it.hasUnique(UniqueType.CityStateOnlyResource) }

            if (allMercantileResources.isNotEmpty()) {
                textList += FormattedLine()
                textList += FormattedLine("The unique luxury is one of:")
                allMercantileResources.forEach {
                    textList += FormattedLine(it.name, it.makeLink(), indent = 1)
                }
            }
        }
        textList += FormattedLine(separator = true)

        // personality is not a nation property, it gets assigned to the civ randomly
        return textList
    }

    private fun Nation.getUniqueBuildingsText(ruleset: Ruleset) = sequence {
        for (building in ruleset.buildings.values) {
            if (building.uniqueTo == null) continue
            if (!matchesFilter(building.uniqueTo!!)) continue
            if (building.isHiddenFromCivilopedia(ruleset)) continue
            yield(FormattedLine(separator = true))
            yield(FormattedLine("{${building.name}} -", link = building.makeLink()))
            if (building.replaces != null && ruleset.buildings.containsKey(building.replaces!!)) {
                val originalBuilding = ruleset.buildings[building.replaces!!]!!
                yield(FormattedLine("Replaces [${originalBuilding.name}]", link = originalBuilding.makeLink(), indent = 1))
                yieldAll(BuildingDescriptions.getDifferences(originalBuilding, building))
                yield(FormattedLine())
            } else if (building.replaces != null) {
                yield(FormattedLine("Replaces [${building.replaces}], which is not found in the ruleset!", indent = 1))
            } else {
                yield(FormattedLine(building.getShortDescription(true), indent = 1))
            }
        }
    }

    private fun Nation.getUniqueUnitsText(ruleset: Ruleset) = sequence {
        for (unit in ruleset.units.values) {
            if (unit.isHiddenFromCivilopedia(ruleset)) continue
            if (unit.uniqueTo == null || !matchesFilter(unit.uniqueTo!!)) continue
            yield(FormattedLine(separator = true))
            yield(FormattedLine("{${unit.name}} -", link = "Unit/${unit.name}"))
            if (unit.replaces != null && ruleset.units.containsKey(unit.replaces!!)) {
                val originalUnit = ruleset.units[unit.replaces!!]!!
                yield(FormattedLine("Replaces [${originalUnit.name}]", link = "Unit/${originalUnit.name}", indent = 1))
                if (unit.cost != originalUnit.cost)
                    yield(FormattedLine("{Cost} ".tr() + "[${unit.cost}] vs [${originalUnit.cost}]".tr(), indent = 1))
                yieldAll(
                    BaseUnitDescriptions.getDifferences(ruleset, originalUnit, unit)
                        .map { (text, link) -> FormattedLine(text, link = link ?: "", indent = 1) }
                )
            } else if (unit.replaces != null) {
                yield(FormattedLine("Replaces [${unit.replaces}], which is not found in the ruleset!", indent = 1))
            } else {
                yieldAll(unit.getCivilopediaTextLines(ruleset).map {
                    FormattedLine(it.text, link = it.link, indent = it.indent + 1, color = it.color)
                })
            }

            yield(FormattedLine())
        }
    }

    private fun Nation.getUniqueImprovementsText(ruleset: Ruleset) = sequence {
        for (improvement in ruleset.tileImprovements.values) {
            if (improvement.isHiddenFromCivilopedia(ruleset)) continue
            if (improvement.uniqueTo == null || !matchesFilter(improvement.uniqueTo!!)) continue

            yield(FormattedLine(separator = true))
            yield(FormattedLine(improvement.name, link = "Improvement/${improvement.name}"))
            yield(FormattedLine(improvement.cloneStats().toString(), indent = 1))   // = (improvement as Stats).toString minus import plus copy overhead
            if (improvement.replaces != null && ruleset.tileImprovements.containsKey(improvement.replaces!!)) {
                val originalImprovement = ruleset.tileImprovements[improvement.replaces!!]!!
                yield(FormattedLine("Replaces [${originalImprovement.name}]", link = originalImprovement.makeLink(), indent = 1))
                yieldAll(ImprovementDescriptions.getDifferences(ruleset, originalImprovement, improvement))
                yield(FormattedLine())
            } else if (improvement.replaces != null) {
                yield(FormattedLine("Replaces [${improvement.replaces}], which is not found in the ruleset!", indent = 1))
            } else {
                yieldAll(improvement.getShortDecription())
            }
        }
    }

    fun Personality.getShortDescription() = sequence {
        if (preferredVictoryType.isNotEmpty() && preferredVictoryType != Constants.neutralVictoryType)
            yield(preferredVictoryType)
        val maxFocus = PersonalityValue.entries.maxOfOrNull { get(it) }
        for (focus in PersonalityValue.entries)
            if (get(focus) == maxFocus) yield(focus.description)
    }.distinct().joinToString { "[$it]" }

    fun Personality.getCivilopediaTextHeaderImpl() =
        FormattedLine("[$name]'s personality", icon = makeLink(), header = 2)

    fun Personality.getCivilopediaTextLinesImpl(ruleset: Ruleset): List<FormattedLine> = ArrayList<FormattedLine>().apply {
        if (isNeutralPersonality) {
            add(FormattedLine("Neutral personality"))
            return@apply
        }

        val nations = ruleset.nations.values.filter { it.personality == name }
        if (nations.isNotEmpty()) {
            for (nation in nations)
                add(FormattedLine("See also: [$nation]", link = nation.makeLink()))
            add(FormattedLine())
        }

        if (preferredVictoryType.isNotEmpty() && preferredVictoryType != Constants.neutralVictoryType) {
            add(FormattedLine("Preferred victory type: [$preferredVictoryType]"))
            add(FormattedLine())
        }

        fun Float.toPercent() = ((this - 5f) * 20f).toInt()
        val biases = PersonalityValue.entries
            .mapNotNull { focus ->
                val value = get(focus)
                if (value == 5f) null
                else FormattedLine("{${focus.description}}: ${value.toPercent()}%", indent = 1)
            }
        if (biases.isNotEmpty()) {
            add(FormattedLine("Biases:"))
            addAll(biases)
            add(FormattedLine())
        }

        if (priorities.isEmpty()) return@apply
        add(FormattedLine("Policy priorities:"))
        for ((name, value) in priorities) {
            add(FormattedLine("[$name]: [$value]", indent = 1))
        }
    }
}
