package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.objectdescriptions.FormattedLineListBuilder
import com.unciv.ui.objectdescriptions.FormattedLineListBuilder.Companion.buildCivilopediaText
import com.unciv.ui.objectdescriptions.uniquesToDescription
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import yairm210.purity.annotations.Pure

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

    override fun getCivilopediaTextLines(ruleset: Ruleset) = buildCivilopediaText {
        addUniques(FormattedLineListBuilder.SeparatorType.None)

        val filteredPrerequisites = prerequisites.mapNotNull { ruleset.unitPromotions[it] }
        if (filteredPrerequisites.isNotEmpty()) {
            space()
            val single = filteredPrerequisites.singleOrNull()
            if (single != null) {
                add("Requires [${single.name}]", link = single.makeLink())
            } else {
                add("Requires at least one of the following:")
                addObjects(filteredPrerequisites)
            }
        }

        val enables = ruleset.unitPromotions.values.filter { name in it.prerequisites }
        if (enables.isNotEmpty()) {
            space()
            val single = enables.singleOrNull()
            if (single != null) {
                add("Leads to [${single.name}]", link = single.makeLink())
            } else {
                add("Leads to:")
                addObjects(enables)
            }
        }

        if (unitTypes.isNotEmpty()) {
            space()
            // This separates the linkable (corresponding to a BaseUnit name, or else UnitType name) unitFilter entries but keeps unlinkable ones at the end
            val availableFor = (
                    unitTypes.asSequence().mapNotNull { ruleset.units[it] } +
                    unitTypes.asSequence().mapNotNull { ruleset.unitTypes[it] } +
                    unitTypes.asSequence().map { UnlinkedName(it) }
                ).distinctBy { it.name }.toList()
            val single = availableFor.singleOrNull()
            if (single != null) {
                add("Available for [${single.name}]", link = single.makeLink())
            } else {
                add("Available for:")
                addObjects(availableFor, indent = 1)
            }
        }

        val freeForUnits = ruleset.units.values.filter { it.promotions.contains(name) }
        if (freeForUnits.isNotEmpty()) {
            space()
            val single = freeForUnits.singleOrNull()
            if (single != null) {
                add("Free for [${single.name}]", link = single.makeLink())
            } else {
                add("Free for:")
                addObjects(freeForUnits)
            }
        }

        val grantors = (
            ruleset.buildings.values.asSequence().filter { building ->
                building.getMatchingUniques(UniqueType.UnitStartingPromotions)
                    .any { it.params[2] == name }
            } + ruleset.terrains.values.asSequence().filter { terrain ->
                terrain.getMatchingUniques(UniqueType.TerrainGrantsPromotion)
                    .any { it.params[0] == name }
            }
        ).toList()
        if (grantors.isNotEmpty()) {
            space()
            val single = grantors.singleOrNull()
            if (single != null) {
                add("Granted by [${single.name}]", link = single.makeLink())
            } else {
                add("Granted by:")
                addObjects(grantors, indent = 1)
            }
        }
    }

    override fun getSubCategory(ruleset: Ruleset): String = unitTypes.firstOrNull() ?: "Other"
    override fun getSortGroup(ruleset: Ruleset): Int {
        val unitTypeName = unitTypes.firstOrNull() ?: return 1000
        if (!ruleset.unitTypes.contains(unitTypeName)) return 1000
        return ruleset.unitTypes.keys.indexOf(unitTypeName)
    }

    /** For the ability to display invalid available-for entries, so a modder can see mistakes here too */
    private class UnlinkedName(override var name: String) : RulesetObject() {
        override fun getUniqueTarget() = UniqueTarget.MetaModifier // Irrelevant but required
        override fun makeLink() = ""
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
        @Pure
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
