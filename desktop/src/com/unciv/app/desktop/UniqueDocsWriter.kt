package com.unciv.app.desktop

import com.unciv.logic.map.mapunit.MapUnitCache
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import java.io.File

class UniqueDocsWriter {
    companion object {
        /**
         * Switch from each Unique shown once under one UniqueTarget heading chosen from targetTypes (`true`)
         * to showing each Unique repeatedly under each UniqueTarget heading it applies to (`false`).
         */
        private const val showUniqueOnOneTarget = false

        /** Switch **on** the display of _inherited_ UniqueTargets in "Applicable to:" */
        private const val showInheritedTargets = false

        private fun UniqueTarget.allTargets(): Sequence<UniqueTarget> = sequence {
            if (showInheritedTargets && inheritsFrom != null) yieldAll(inheritsFrom!!.allTargets())
            yield(this@allTargets)
        }
        private fun UniqueType.allTargets(): Sequence<UniqueTarget> =
            targetTypes.asSequence().flatMap { it.allTargets() }.distinct()
        private fun UniqueTarget.allUniqueTypes(): Sequence<UniqueType> =
            UniqueType.entries.asSequence().filter {
                this in it.targetTypes
            }
    }
    fun toLink(string: String): String {
        return "#" + string.split(' ').joinToString("-") { it.lowercase() }
    }

    fun write() {
        // This will output each unique only once, even if it has several targets.
        // Each is grouped under the UniqueTarget is is allowed for with the lowest enum ordinal.
        // UniqueTarget.inheritsFrom is _not_ resolved for this.
        // The UniqueType are shown in enum order within their group, and groups are ordered
        // by their UniqueTarget.ordinal as well - source code order.
        val targetTypesToUniques: Map<UniqueTarget, List<UniqueType>> =
            if (showUniqueOnOneTarget)
                UniqueType.entries
                    .groupBy { it.targetTypes.minOrNull()!! }
                    .toSortedMap()
            else
        // if, on the other hand, we wish to list every UniqueType with multiple targets under
        // _each_ of the groups it is applicable to, then this might do:
                UniqueTarget.entries.asSequence().associateWith { target ->
                    target.allTargets().flatMap { inheritedTarget ->
                        inheritedTarget.allUniqueTypes()
                    }.distinct().toList()
                }

        val capacity = 25 + targetTypesToUniques.size + UniqueType.entries.size * (if (showUniqueOnOneTarget) 3 else 16)
        val lines = ArrayList<String>(capacity)
        lines += "# Uniques"
        lines += "An overview of uniques can be found [here](../Developers/Uniques.md)"
        lines += "\nSimple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](../Unique-parameters)"
        lines += ""

        for ((targetType, uniqueTypes) in targetTypesToUniques) {
            if (uniqueTypes.isEmpty()) continue
            lines += "## " + targetType.name + " uniques"

            if (targetType.documentationString.isNotEmpty())
                lines += "!!! note \"\"\n\n    ${targetType.documentationString}\n"


            for (uniqueType in uniqueTypes) {
                if (uniqueType.getDeprecationAnnotation() != null) continue

                val uniqueText = if (targetType.modifierType != UniqueTarget.ModifierType.None)
                    "&lt;${uniqueType.text}&gt;"
                else uniqueType.text
                lines += "??? example  \"$uniqueText\"" // collapsable material mkdocs block, see https://squidfunk.github.io/mkdocs-material/reference/admonitions/?h=%3F%3F%3F#collapsible-blocks
                if (uniqueType.docDescription != null)
                    lines += "\t${uniqueType.docDescription!!.replace("\n","\n\t")}"
                if (uniqueType.parameterTypeMap.isNotEmpty()) {
                    // This one will give examples for _each_ filter in a "tileFilter/specialist/buildingFilter" kind of parameter e.g. "Farm/Merchant/Library":
                    // `val paramExamples = uniqueType.parameterTypeMap.map { it.joinToString("/") { pt -> pt.docExample } }.toTypedArray()`
                    // Might confuse modders to think "/" can go into the _actual_ unique and mean "or", so better show just one ("Farm" in the example above):
                    val paramExamples = uniqueType.parameterTypeMap.map { it.first().docExample }.toTypedArray()
                    lines += "\tExample: \"${uniqueText.fillPlaceholders(*paramExamples)}\"\n"
                }
                if (uniqueType.flags.contains(UniqueFlag.AcceptsSpeedModifier))
                    lines += "\tThis unique's effect can be modified with &lt;${UniqueType.ModifiedByGameSpeed.text}&gt;"
                if (uniqueType.flags.contains(UniqueFlag.AcceptsGameProgressModifier))
                    lines += "\tThis unique's effect can be modified with &lt;${UniqueType.ModifiedByGameProgress.text}&gt;"
                if (uniqueType in MapUnitCache.UnitMovementUniques) {
                    lines += "\tDue to performance considerations, this unique is cached, thus conditionals that may change within a turn may not work."
                }
                lines += "\tApplicable to: " + uniqueType.allTargets().sorted().joinToString()
                lines += ""
            }
        }

        // Abbreviations, for adding short unique parameter help - see https://squidfunk.github.io/mkdocs-material/reference/abbreviations/
        lines += ""
        // order irrelevant for rendered wiki, but could potentially reduce source control differences
        for (paramType in UniqueParameterType.entries.asSequence().sortedBy { it.parameterName }) {
            if (paramType.docDescription == null) continue
            val punctuation = if (paramType.docDescription!!.last().category == '.'.category) "" else "."
            lines += "*[${paramType.parameterName}]: ${paramType.docDescription}$punctuation"
        }

        File("../../docs/Modders/uniques.md").writeText(lines.joinToString("\n"))
    }
}
