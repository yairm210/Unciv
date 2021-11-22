package com.unciv.app.desktop

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.UniqueType
import java.io.File

class UniqueDocsWriter {
    fun toLink(string: String): String {
        return "#" + string.split(' ').joinToString("-") { it.lowercase() }
    }

    fun write() {
        val lines = ArrayList<String>()
        val targetTypesToUniques = UniqueType.values().groupBy { it.targetTypes.first() }

        fun replaceExamples(text:String):String {
            return text.replace("[amount]", "[20]")
                .replace("[stat]", "[Culture]")
                .replace("[stats]", "[+1 Gold, +2 Production]")
                .replace("[cityFilter]", "[in all cities]")
                .replace("[buildingName]", "[Library]")
                .replace("[tileFilter]", "[Farm]")
                .replace("[terrainFilter]", "[Grassland]")
                .replace("[baseUnitFilter]", "[Melee]")
                .replace("[mapUnitFilter]", "[Wounded]")
                .replace("[resource]", "[Iron]")
                .replace("[beliefType]", "[Follower]")
        }

        lines += "## Table of Contents\n"
        for (targetType in targetTypesToUniques) {
            val sectionName = targetType.key.name + " uniques"
            lines += " - [$sectionName](${toLink(sectionName)})"
        }
        lines += " - [Deprecated uniques](#deprecated-uniques)"
        lines += ""



        val deprecatedUniques = ArrayList<UniqueType>()
        for (targetType in targetTypesToUniques) {
            lines += "## " + targetType.key.name + " uniques"
            for (uniqueType in targetType.value) {

                val deprecationAnnotation = uniqueType.declaringClass.getField(uniqueType.name)
                    .getAnnotation(Deprecated::class.java)
                if (deprecationAnnotation != null){
                    deprecatedUniques += uniqueType
                    continue
                }

                lines += "#### " + uniqueType.text
                lines += "Example: \"${replaceExamples(uniqueType.text)}\""
                lines += "Applicable to: " + uniqueType.targetTypes.joinToString()
                lines += ""
            }
        }
        lines += "## Deprecated uniques"
        for (deprecatedUnique in deprecatedUniques) {
            val deprecationAnnotation =
                deprecatedUnique.declaringClass.getField(deprecatedUnique.name)
                    .getAnnotation(Deprecated::class.java)

            val deprecationText = "Deprecated ${deprecationAnnotation.message}," +
                    if (deprecationAnnotation.replaceWith.expression != "") " replace with \"${deprecationAnnotation.replaceWith.expression}\"" else ""

            lines += " - \"${deprecatedUnique.text}\" - $deprecationText"
        }

        File("../../docs/uniques.md").writeText(lines.joinToString("\n"))
    }
}