package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.objectdescriptions.uniquesToDescription
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen

class Promotion : RulesetObject() {
    var prerequisites = listOf<String>()

    var unitTypes = listOf<String>() // The json parser wouldn't agree to deserialize this as a list of UnitTypes. =(

    var innerColor: List<Int>? = null
    val innerColorObject by lazy { if (innerColor == null) null else colorFromRGB(innerColor!!)}
    var outerColor: List<Int>? = null
    val outerColorObject by lazy { if (outerColor == null) null else colorFromRGB(outerColor!!)}

    /** Used as **column** hint in the current [PromotionPickerScreen]
     *  This is no longer a direct position, it is used to sort before an automatic distribution.
     *  -1 determines that the modder has not set a position */
    var row = -1
    /** Used as **row** hint in the current [PromotionPickerScreen]
     *  This is no longer a direct position, it is used to sort before an automatic distribution.
     */
    var column = 0

    fun clone(): Promotion {
        val newPromotion = Promotion()

        // RulesetObject fields
        newPromotion.name = name
        newPromotion.uniques = uniques

        // Promotion fields
        newPromotion.prerequisites = prerequisites
        newPromotion.unitTypes = unitTypes
        newPromotion.row = row
        newPromotion.column = column
        return newPromotion
    }

    override fun getUniqueTarget() = UniqueTarget.Promotion


    /** Used to describe a Promotion on the PromotionPickerScreen - fully translated */
    fun getDescription(promotionsForUnitType: Collection<Promotion>): String {
        val textList = ArrayList<String>()

        uniquesToDescription(textList)

        if (prerequisites.isNotEmpty()) {
            val prerequisitesString: ArrayList<String> = arrayListOf()
            for (i in prerequisites.filter { promotionsForUnitType.any { promotion -> promotion.name == it } }) {
                prerequisitesString.add(i.tr())
            }
            textList += "{Requires}: ".tr() + prerequisitesString.joinToString(" OR ".tr())
        }
        return textList.joinToString("\n")
    }

    override fun makeLink() = "Promotion/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        uniquesToCivilopediaTextLines(textList, leadingSeparator = null)

        val filteredPrerequisites = prerequisites.mapNotNull {
            ruleset.unitPromotions[it]
        }
        if (filteredPrerequisites.isNotEmpty()) {
            textList += FormattedLine()
            if (filteredPrerequisites.size == 1) {
                filteredPrerequisites[0].let {
                    textList += FormattedLine("Requires [${it.name}]", link = it.makeLink())
                }
            } else {
                textList += FormattedLine("Requires at least one of the following:")
                filteredPrerequisites.forEach {
                    textList += FormattedLine(it.name, link = it.makeLink())
                }
            }
        }

        if (unitTypes.isNotEmpty()) {
            textList += FormattedLine()
            // This separates the linkable (corresponding to a BaseUnit name) unitFilter entries
            // from the others - `first` collects those for which the predicate is `true`.
            val types = unitTypes.partition { it in ruleset.units }
            if (unitTypes.size == 1) {
                if (types.first.isNotEmpty())
                    unitTypes.first().let {
                        textList += FormattedLine("Available for [$it]", link = "Unit/$it")
                    }
                else
                    unitTypes.first().let {
                        textList += FormattedLine("Available for [$it]", link = "UnitType/$it")
                    }

            } else {
                textList += FormattedLine("Available for:")
                types.first.forEach {
                    textList += FormattedLine(it, indent = 1, link = "Unit/$it")
                }
                types.second.forEach {
                    textList += FormattedLine(it, indent = 1, link = "UnitType/$it")
                }
            }
        }

        val freeForUnits = ruleset.units.filter { it.value.promotions.contains(name) }.map { it.key }
        if (freeForUnits.isNotEmpty()) {
            textList += FormattedLine()
            if (freeForUnits.size == 1) {
                freeForUnits[0].let {
                    textList += FormattedLine("Free for [$it]", link = "Unit/$it")
                }
            } else {
                textList += FormattedLine("Free for:")
                freeForUnits.forEach {
                    textList += FormattedLine(it, link = "Unit/$it")
                }
            }
        }

        val grantors = ruleset.buildings.values.filter {
            building -> building.getMatchingUniques(UniqueType.UnitStartingPromotions)
            .any { it.params[2] == name }
        } + ruleset.terrains.values.filter {
            terrain -> terrain.getMatchingUniques(UniqueType.GrantsPromotionToAdjacentUnits).any {
                name == it.params[0]
            }
        }
        if (grantors.isNotEmpty()) {
            textList += FormattedLine()
            if (grantors.size == 1) {
                grantors[0].let {
                    textList += FormattedLine("Granted by [${it.name}]", link = it.makeLink())
                }
            } else {
                textList += FormattedLine("Granted by:")
                grantors.forEach {
                    textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
                }
            }
        }

        return textList
    }

    companion object {
        data class PromotionBaseNameAndLevel(
            val nameWithoutBrackets: String,
            val level: Int,
            val basePromotionName: String
        )
        /** Split a promotion name into base and level, e.g. "Drill II" -> 2 to "Drill"
         *
         *  Used by Portrait (where it only has the string, the Promotion object is forgotten) and
         *  PromotionPickerScreen. Here to allow clear "Promotion.getBaseNameAndLevel" signature.
         */
        fun getBaseNameAndLevel(promotionName: String): PromotionBaseNameAndLevel {
            val nameWithoutBrackets = promotionName.replace("[", "").replace("]", "")
            val level = when {
                nameWithoutBrackets.endsWith(" I") -> 1
                nameWithoutBrackets.endsWith(" II") -> 2
                nameWithoutBrackets.endsWith(" III") -> 3
                else -> 0
            }
            return PromotionBaseNameAndLevel(nameWithoutBrackets, level, nameWithoutBrackets.dropLast(if (level == 0) 0 else level + 1))
        }
    }
}
