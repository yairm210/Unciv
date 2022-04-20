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
                .replace("[unit]","[Musketman]")
                .replace("[great person]", "[Great Scientist]")
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
        }
        
        lines += "# Uniques\n" +
                "Simple unique parameters are explained by mouseover. Complex parameters are explained in [Unique parameter types](../unique parameters)"

        
        val deprecatedUniques = ArrayList<UniqueType>()
        for (targetType in targetTypesToUniques) {
            lines += "## " + targetType.key.name + " uniques"
            for (uniqueType in targetType.value) {
                if (uniqueType.getDeprecationAnnotation() != null) continue

                val uniqueText = if (targetType.key == UniqueTarget.Conditional) "&lt;${uniqueType.text}&gt;"
                else uniqueType.text
                lines += "??? example  \"$uniqueText\"" // collapsable material mkdocs block, see https://squidfunk.github.io/mkdocs-material/reference/admonitions/?h=%3F%3F%3F#collapsible-blocks
                if (uniqueType.text.contains('['))
                    lines += "\tExample: \"${replaceExamples(uniqueText)}\"\n"
                lines += "\tApplicable to: " + uniqueType.targetTypes.sorted().joinToString()
                lines += ""
            }
        }
        
        // Abbreviations, for adding short unique parameter help - see https://squidfunk.github.io/mkdocs-material/reference/abbreviations/

        lines += ""
        lines += "*[amount]: This indicates a whole number, possibly with a + or - sign, such as `2`, `+13`, or `-3`."
        lines += "*[baseTerrain]: The name of any terrain that is a base terrain according to the json file."
        lines += "*[action]: An action that a unit can preform. Currently, there are only two actions part of this: 'Spread Religion' and 'Remove Foreign religions from your own cities'"
        lines += "*[belief]: The name of any belief"
        lines += "*[beliefType]: 'Pantheon', 'Follower', 'Founder' or 'Enhancer'."
        lines += "*[victoryType]: The name of any victory type: 'Neutral', 'Cultural', 'Diplomatic', 'Domination', 'Scientific', 'Time'"
        lines += "*[tech]: The name of any tech"
        lines += "*[resource]: The name of any resource"
        lines += "*[specialist]: The name of any specialist"
        lines += "*[promotion]: The name of any promotion"
        lines += "*[policy]: The name of any policy"
        lines += "*[improvementName]: The name of any improvement"
        lines += "*[buildingName]: The name of any building"
        lines += "*[era]: The name of any era"
        lines += "*[constructionFilter]: A filter for used when testing the current construction of a city. All values of `baseUnitFilter` and `buildingFilter` are allowed."
        lines += "*[foundingOrEnhancing]: `founding` or `enhancing`"
        lines += "*[costOrStrength]: `Cost` or `Strength`"
        lines += "*[combatantFilter]: This indicates a combatant, which can either be a unit or a city (when bombarding). Must either be `City` or a `mapUnitFilter`."
        lines += "*[plunderableStat]: All the following stats can be plundered: `Gold`, `Science`, `Culture`, `Faith`"
        lines += "*[tileFilter]: Anything that can be used either in an improvementFilter or in a tileFilter can be used here"
        lines += "*[stat]: This is one of the 7 major stats in the game - `Gold`, `Science`, `Production`, `Food`, `Happiness`, `Culture` and `Faith`. Note that the stat names need to be capitalized!"
        lines += "*[stats]: For example: `+2 Production, +3 Food`. Note that the stat names need to be capitalized!"
        
        
        File("../../docs/Modders/uniques.md").writeText(lines.joinToString("\n"))
    }
}