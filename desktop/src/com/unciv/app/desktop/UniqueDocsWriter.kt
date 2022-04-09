package com.unciv.app.desktop

import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import java.io.File

class UniqueDocsWriter {
    fun toLink(string: String): String {
        return "#" + string.split(' ').joinToString("-") { it.lowercase() }
    }

    fun write() {
        val targetTypesToUniques = UniqueType.values().groupBy { it.targetTypes.minOrNull()!! }
            .toSortedMap()

        val capacity = 25 + targetTypesToUniques.size + UniqueType.values().size * 3
        val lines = ArrayList<String>(capacity)
        lines += "# Uniques"
        lines += "Simple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](../unique parameters)"

        for ((targetType, uniqueTypes) in targetTypesToUniques) {
            if (uniqueTypes.isEmpty()) continue
            lines += "## " + targetType.name + " uniques"
            for (uniqueType in uniqueTypes) {
                if (uniqueType.getDeprecationAnnotation() != null) continue

                val uniqueText = if (targetType == UniqueTarget.Conditional) "&lt;${uniqueType.text}&gt;"
                else uniqueType.text
                lines += "??? example  \"$uniqueText\"" // collapsable material mkdocs block, see https://squidfunk.github.io/mkdocs-material/reference/admonitions/?h=%3F%3F%3F#collapsible-blocks
                if (uniqueType.parameterTypeMap.isNotEmpty()) {
                    // This one will give examples for _each_ filter in a "tileFilter/specialist/buildingFilter" kind of parameter e.g. "Farm/Merchant/Library":
                    // `val paramExamples = uniqueType.parameterTypeMap.map { it.joinToString("/") { pt -> pt.docExample } }.toTypedArray()`
                    // Might confuse modders to think "/" can go into the _actual_ unique and mean "or", so better show just one ("Farm" in the example above):
                    val paramExamples = uniqueType.parameterTypeMap.map { it.first().docExample }.toTypedArray()
                    lines += "\tExample: \"${uniqueText.fillPlaceholders(*paramExamples)}\"\n"
                }
                lines += "\tApplicable to: " + uniqueType.targetTypes.sorted().joinToString()
                lines += ""
            }
        }

        // Abbreviations, for adding short unique parameter help - see https://squidfunk.github.io/mkdocs-material/reference/abbreviations/
        lines += ""
        // order irrelevant for rendered wiki, but could potentially reduce source control differences
        for (paramType in UniqueParameterType.values().asSequence().sortedBy { it.parameterName }) {
            if (paramType.docDescription == null) continue
            val punctuation = if (paramType.docDescription!!.last().category == '.'.category) "" else "."
            lines += "*[${paramType.parameterName}]: ${paramType.docDescription}$punctuation"
        }

        File("../../docs/Modders/uniques.md").writeText(lines.joinToString("\n"))
    }
}
