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
import com.unciv.ui.objectdescriptions.BaseUnitDescriptions.addUnitDifferences
import com.unciv.ui.objectdescriptions.BuildingDescriptions.addBuildingDifferences
import com.unciv.ui.objectdescriptions.FormattedLineListBuilder.Companion.buildCivilopediaText
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import yairm210.purity.annotations.Readonly
import kotlin.collections.get

object NationDescriptions {
    fun Nation.getNationCivilopediaTextLines(ruleset: Ruleset) = buildCivilopediaText {
        val personalityObj = ruleset.personalities[personality]

        if (isCityState) addCityStateInfo(ruleset)

        if (leaderName.isNotEmpty()) {
            add(extraImage = "LeaderIcons/$leaderName", imageSize = 200f)
            add(getLeaderDisplayName(), centered = true, header = 3)
        }
        if (personalityObj != null)
            add("{Personality}: {$personalityObj}", link = personalityObj.makeLink(), centered = true)
        if (leaderName.isNotEmpty() || personalityObj != null)
            space()

        if (uniqueName != "")
            add("{$uniqueName}:", header = 4)
        if (uniqueText != "") {
            add(uniqueText, indent = 1)
        } else {
            addUniques(FormattedLineListBuilder.SeparatorType.None)
        }
        space()

        val effectiveStartBias = getStartBias(ruleset)
        if (effectiveStartBias.isNotEmpty()) {
            for ((index, bias) in effectiveStartBias.withIndex()) {
                // can be "Avoid []"
                val link = if ('[' !in bias) bias
                else squareBraceRegex.find(bias)!!.groups[1]!!.value
                add(
                    (if (index == 0) "[Start bias:] " else "") + bias.tr(),  // extra tr because tr cannot nest {[]}
                    link = "Terrain/$link",
                    indent = if (index == 0) 0 else 1,
                    iconCrossed = bias.startsWith("Avoid ")
                )
            }
            space()
        }
        addUniqueBuildingsText(ruleset)
        addUniqueUnitsText(ruleset)
        addUniqueImprovementsText(ruleset)
    }

    @Readonly
    context(nation: Nation)
    private fun FormattedLineListBuilder.addCityStateInfo(ruleset: Ruleset): Unit = with(nation) {
        val cityStateType = ruleset.cityStateTypes[cityStateType]!!
        add("{Type}: {${cityStateType.name}}", header = 4, color = "#"+cityStateType.getColor().toString())

        var showResources = false

        fun addBonusLines(header: String, uniqueMap: UniqueMap) {
            // Note: Using getCityStateBonuses would be nice, but it's bound to a CityStateFunctions instance without even using `this`.
            // Too convoluted to reuse that here - but feel free to refactor that into a static.
            val bonuses = uniqueMap.getAllUniques().filterNot { it.isHiddenToUsers() }
            if (bonuses.none()) return
            space()
            add("{$header} ")
            for (unique in bonuses) {
                add(unique, indent = 1)
                if (unique.type == UniqueType.CityStateUniqueLuxury) showResources = true
            }
        }

        addBonusLines("When Friends:", cityStateType.friendBonusUniqueMap)
        addBonusLines("When Allies:", cityStateType.allyBonusUniqueMap)

        if (showResources) {
            val allMercantileResources = ruleset.tileResources.values
                .filter { it.hasUnique(UniqueType.CityStateOnlyResource) }

            if (allMercantileResources.isNotEmpty()) {
                space()
                add("The unique luxury is one of:")
                allMercantileResources.forEach {
                    add(it.name, it.makeLink(), indent = 1)
                }
            }
        }
        separator()
    }

    context(nation: Nation)
    private fun FormattedLineListBuilder.addUniqueBuildingsText(ruleset: Ruleset) = with(nation) {
        for (building in ruleset.buildings.values) {
            if (building.uniqueTo == null) continue
            if (!matchesFilter(building.uniqueTo!!)) continue
            if (building.isHiddenFromCivilopedia(ruleset)) continue
            separator()
            add("{${building.name}} -", link = building.makeLink())
            if (building.replaces != null && ruleset.buildings.containsKey(building.replaces!!)) {
                val originalBuilding = ruleset.buildings[building.replaces!!]!!
                add("Replaces [${originalBuilding.name}]", link = originalBuilding.makeLink(), indent = 1)
                addBuildingDifferences(originalBuilding, building)
                space()
            } else if (building.replaces != null) {
                add("Replaces [${building.replaces}], which is not found in the ruleset!", indent = 1)
            } else {
                add(building.getShortDescription(true), indent = 1)
            }
        }
    }

    context(nation: Nation)
    private fun FormattedLineListBuilder.addUniqueUnitsText(ruleset: Ruleset) = with(nation) {
        for (unit in ruleset.units.values) {
            if (unit.isHiddenFromCivilopedia(ruleset)) continue
            if (unit.uniqueTo == null || !matchesFilter(unit.uniqueTo!!)) continue

            separator()
            add("{${unit.name}} -", link = "Unit/${unit.name}")
            if (unit.replaces != null && ruleset.units.containsKey(unit.replaces!!)) {
                val originalUnit = ruleset.units[unit.replaces!!]!!
                add("Replaces [${originalUnit.name}]", link = "Unit/${originalUnit.name}", indent = 1)
                if (unit.cost != originalUnit.cost)
                    add("{Cost} ".tr() + "[${unit.cost}] vs [${originalUnit.cost}]".tr(), indent = 1)
                addUnitDifferences(ruleset, originalUnit, unit, true, 1)
            } else if (unit.replaces != null) {
                add("Replaces [${unit.replaces}], which is not found in the ruleset!", indent = 1)
            } else {
                add(unit.getCivilopediaTextLines(ruleset).asSequence().map {
                    it.copy(indent = it.indent + 1)
                })
            }
            space()
        }
    }

    context(nation: Nation)
    private fun FormattedLineListBuilder.addUniqueImprovementsText(ruleset: Ruleset) = with(nation) {
        for (improvement in ruleset.tileImprovements.values) {
            if (improvement.isHiddenFromCivilopedia(ruleset)) continue
            if (improvement.uniqueTo == null || !matchesFilter(improvement.uniqueTo!!)) continue

            separator()
            add(improvement.name, link = "Improvement/${improvement.name}")
            add(improvement.cloneStats().toString(), indent = 1)   // = (improvement as Stats).toString minus import plus copy overhead
            if (improvement.replaces != null && ruleset.tileImprovements.containsKey(improvement.replaces!!)) {
                val originalImprovement = ruleset.tileImprovements[improvement.replaces!!]!!
                add("Replaces [${originalImprovement.name}]", link = originalImprovement.makeLink(), indent = 1)
                add(ImprovementDescriptions.getDifferences(ruleset, originalImprovement, improvement))
                space()
            } else if (improvement.replaces != null) {
                add("Replaces [${improvement.replaces}], which is not found in the ruleset!", indent = 1)
            } else {
                add(improvement.getShortDecription())
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

    fun Personality.getPersonalityCivilopediaTextLines(ruleset: Ruleset) = buildCivilopediaText {
        if (isNeutralPersonality) {
            add(FormattedLine("Neutral personality"))
            return@buildCivilopediaText
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
            add("Biases:")
            add(biases)
            space()
        }

        if (priorities.isEmpty()) return@buildCivilopediaText
        add("Policy priorities:")
        for ((name, value) in priorities) {
            add("[$name]: [$value]", indent = 1)
        }
    }
}
