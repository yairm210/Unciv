package com.unciv.app.desktop

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import java.io.File

class UniqueDocsWriter {
    fun toLink(string: String): String {
        return "#" + string.split(' ').joinToString("-") { it.lowercase() }
    }

    fun write() {
        val lines = ArrayList<String>()
        val targetTypesToUniques = UniqueType.values().groupBy { it.targetTypes.minOrNull()!! }
            .toSortedMap()

        fun replaceExamples(text:String):String {
            return text
                .replace("[amount]", "[20]")
                .replace("[combatantFilter]", "[City]")
                .replace("[mapUnitFilter]", "[Wounded]")
                .replace("[baseUnitFilter]", "[Melee]")
                .replace("[greatPerson]", "[Great Scientist]")
                .replace("[greatPersonType]", "[War]")
                .replace("[stats]", "[+1 Gold, +2 Production]")
                .replace("[stat]", "[Culture]")
                .replace("[plunderableStat]", "[Gold]")
                .replace("[cityFilter]", "[in all cities]")
                .replace("[buildingName]", "[Library]")
                .replace("[buildingFilter]", "[Culture]")
                .replace("[constructionFilter]", "[Spaceship Part]")
                .replace("[terrainFilter]", "[Forest]")
                .replace("[tileFilter]", "[Farm]")
                .replace("[simpleTerrain]", "[Elevated]")
                .replace("[baseTerrain]", "[Grassland]")
                .replace("[regionType]", "[Hybrid]")
                .replace("[terrainQuality]","[Undesirable]")
                .replace("[promotion]","[Shock I]")
                .replace("[era]", "[Ancient era]")
                .replace("[improvementName]", "[Trading Post]")
                .replace("[improvementFilter]", "[All Road]")
                .replace("[resource]", "[Iron]")
                .replace("[beliefType]", "[Follower]")
                .replace("[belief]","[God of War]")
                .replace("[foundingOrEnhancing]", "[founding]")
                .replace("[tech]", "[Agriculture]")
                .replace("[specialist]","[Merchant]")
                .replace("[policy]", "[Oligarchy]")
                .replace("[victoryType]", "[Domination]")
                .replace("[costOrStrength]", "[Cost]")
                .replace("[action]", "[Spread Religion]")
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
                if (deprecationAnnotation != null) {
                    deprecatedUniques += uniqueType
                    continue
                }

                val uniqueText = if (targetType.key == UniqueTarget.Conditional) "<${uniqueType.text}>"
                else uniqueType.text
                lines += "#### $uniqueText"
                if (uniqueType.text.contains('['))
                    lines += "Example: \"${replaceExamples(uniqueText)}\"\n"
                lines += "Applicable to: " + uniqueType.targetTypes.sorted().joinToString()
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
