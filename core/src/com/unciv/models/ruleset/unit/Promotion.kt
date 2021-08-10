package com.unciv.models.ruleset.unit

import com.unciv.models.ruleset.IHasUniques
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText


class Promotion : INamed, ICivilopediaText, IHasUniques {
    override lateinit var name: String
    var prerequisites = listOf<String>()
    var effect = ""
    var unitTypes = listOf<String>() // The json parser wouldn't agree to deserialize this as a list of UnitTypes. =(

    override var uniques = ArrayList<String>()
    fun uniquesWithEffect() = sequence {
        if (effect.isNotEmpty()) yield(effect)
        yieldAll(uniques)
    }
    override val uniqueObjects: List<Unique> by lazy { uniquesWithEffect().map { Unique(it) }.toList() }

    override var civilopediaText = listOf<FormattedLine>()


    /** Used to describe a Promotion on the PromotionPickerScreen */
    fun getDescription(promotionsForUnitType: Collection<Promotion>):String {
        val textList = ArrayList<String>()

        for (unique in uniquesWithEffect()) {
            textList += unique.tr()
        }

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
    override fun hasCivilopediaTextLines() = true
    override fun replacesCivilopediaDescription() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        for (unique in uniqueObjects) {
            textList += FormattedLine(unique)
        }

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
                    types.first.first().let {
                        textList += FormattedLine("Available for [${it.tr()}]", link = "Unit/$it")
                    }
                else
                    textList += FormattedLine("Available for [${types.second.first().tr()}]")
            } else {
                textList += FormattedLine("Available for:")
                types.first.forEach {
                    textList += FormattedLine(it, indent = 1, link = "Unit/$it")
                }
                types.second.forEach {
                    textList += FormattedLine(it, indent = 1)
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
            building -> building.uniqueObjects.any { 
                it.placeholderText == "All newly-trained [] units [] receive the [] promotion"
                    && it.params[2] == name
            }
        } + ruleset.terrains.values.filter {
            // once that unique is parameterized, this will be the efficient order of checks
            terrain -> terrain.uniques.any {
                it == "Grants Rejuvenation (all healing effects doubled) to adjacent military land units for the rest of the game"
                    && name == "Rejuvenation"
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
}
