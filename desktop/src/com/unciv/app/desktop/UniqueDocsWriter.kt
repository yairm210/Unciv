package com.unciv.app.desktop

import com.unciv.logic.map.mapunit.MapUnitCache
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.utils.Log
import java.io.File

class UniqueDocsWriter {
    companion object {
        /** Where the UniqueType file is to be overwritten,
         *  relative to the current (assets) directory (not incluenced by `--data-dir=`). */
        private const val uniqueTypesFileName = "../../docs/Modders/uniques.md"

        /** Where the Countables documentation is to inserted,
         *  relative to the current (assets) directory (not incluenced by `--data-dir=`). */
        private const val countablesFileName = "../../docs/Modders/Unique-parameters.md"
        /** Where in the documentation file the Countables are to be inserted, start marker. */
        private const val countablesBeginMarker = "[//]: # (Countables automatically generated BEGIN)"
        /** Where in the documentation file the Countables are to be inserted, end marker. An empty line will always be inserted above it. */
        private const val countablesEndMarker = "[//]: # (Countables automatically generated END)"

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

    @Suppress("unused")  // was used in the past? 
    /** Create the anchor-navigation part to append to a link for a given header */
    fun toLink(string: String): String {
        return "#" + string.split(' ').joinToString("-") { it.lowercase() }
    }

    // Thanks https://github.com/ktorio/ktor/blob/d89d41ef6dc91479e6c13c25eb306abc15040b8e/ktor-utils/common/src/io/ktor/util/Text.kt#L7-L28
    /** An escaping routine specifically for mkdocs input: `<>` are escaped **unless** within backticks, and newlines are doubled */
    private fun String.escapeHtml(indent: Int = 0) = buildString(capacity = length + indent + 6) {
        var inCodeBlock = false
        for (char in this@escapeHtml) {
            when(char) {
                //'\'' -> append("&#x27;") // not necessary for this case
                //'\"' -> append("&quot;") // not necessary for this case
                '`' -> {
                    inCodeBlock = !inCodeBlock
                    append('`')
                }
                '&' -> append("&amp;")
                '<' -> append(if (inCodeBlock) "<" else "&lt;")
                '>' -> append(if (inCodeBlock) ">" else "&gt;")
                '\n' -> append("\n\n" + "\t".repeat(indent))
                else -> append(char)
            }
        }
    }

    fun write() {
        writeUniqueTypes()
        writeCountables()
    }

    private fun writeUniqueTypes() {
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
        lines += "\nSimple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](Unique-parameters.md)"
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
                // These blocks will join all indented lines that follow, they need an empty line followed by more indented lines to render one break.
                // Thus, all optional lines up to "Applicable" get an extra `\n`, and the `escapeHtml` helper doubles newlines found in docDescription:
                if (uniqueType.docDescription != null)
                    lines += "\t${uniqueType.docDescription!!.escapeHtml(1)}\n"
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

        File(uniqueTypesFileName).writeText(lines.joinToString("\n"))
    }

    private fun writeCountables() {
        val file = File(countablesFileName)
        val oldContent = try {
            file.readText(Charsets.UTF_8)
        } catch (ex: Throwable) {
            Log.error("Can't read $countablesFileName", ex)
            return
        }
        val truncateBegin = oldContent.indexOf(countablesBeginMarker)
        if (truncateBegin < 0)
            Log.error("Can't find `%s` in %s", countablesBeginMarker, countablesFileName)
        val truncateEnd = oldContent.indexOf(countablesEndMarker)
        if (truncateEnd < 0)
            Log.error("Can't find `%s` in %s", countablesEndMarker, countablesFileName)
        if (truncateBegin < 0 || truncateEnd < 0) return
        if (truncateEnd < truncateBegin) {
            Log.error("Inverted Countables markers in %s", countablesEndMarker, countablesFileName)
            return
        }

        val newContent = StringBuilder(oldContent.length)
        newContent.append(oldContent, 0, truncateBegin + countablesBeginMarker.length)
        newContent.appendLine()

        for (countable in Countables.entries) {
            if (countable.getDeprecationAnnotation() != null) continue
            newContent.appendLine("-   ${countable.documentationHeader}")
            newContent.appendLine("    - Example: `Only available <when number of [${countable.example}] is more than [0]>`") // Sublist
            for (extraLine in countable.documentationStrings) {
                newContent.append("    - ") // Sublist
                newContent.appendLine(extraLine)
            }
        }

        newContent.appendLine()
        newContent.append(oldContent, truncateEnd, oldContent.length)
        try {
            file.writeText(newContent.toString(), Charsets.UTF_8)
        } catch (ex: Throwable) {
            Log.error("Can't write $countablesFileName", ex)
            return
        }
    }
}
