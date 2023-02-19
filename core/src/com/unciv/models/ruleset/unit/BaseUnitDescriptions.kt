package com.unciv.models.ruleset.unit

import com.unciv.logic.city.City
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.extensions.getConsumesAmountString
import com.unciv.ui.components.extensions.toPercent
import kotlin.math.pow

object BaseUnitDescriptions {

    fun getShortDescription(baseUnit: BaseUnit): String {
        val infoList = mutableListOf<String>()
        if (baseUnit.strength != 0) infoList += "${baseUnit.strength}${Fonts.strength}"
        if (baseUnit.rangedStrength != 0) infoList += "${baseUnit.rangedStrength}${Fonts.rangedStrength}"
        if (baseUnit.movement != 2) infoList += "${baseUnit.movement}${Fonts.movement}"
        for (promotion in baseUnit.promotions)
            infoList += promotion.tr()
        if (baseUnit.replacementTextForUniques != "") infoList += baseUnit.replacementTextForUniques
        else for (unique in baseUnit.uniqueObjects) if(!unique.hasFlag(UniqueFlag.HiddenToUsers))
            infoList += unique.text.tr()
        return infoList.joinToString()
    }


    /** Generate description as multi-line string for CityScreen addSelectedConstructionTable
     * @param city Supplies civInfo to show available resources after resource requirements */
    fun getDescription(baseUnit: BaseUnit, city: City): String {
        val lines = mutableListOf<String>()
        val availableResources = city.civ.getCivResources().associate { it.resource.name to it.amount }
        for ((resource, amount) in baseUnit.getResourceRequirements()) {
            val available = availableResources[resource] ?: 0
            lines += "{${resource.getConsumesAmountString(amount)}} ({[$available] available})".tr()
        }
        var strengthLine = ""
        if (baseUnit.strength != 0) {
            strengthLine += "${baseUnit.strength}${Fonts.strength}, "
            if (baseUnit.rangedStrength != 0)
                strengthLine += "${baseUnit.rangedStrength}${Fonts.rangedStrength}, ${baseUnit.range}${Fonts.range}, "
        }
        lines += "$strengthLine${baseUnit.movement}${Fonts.movement}"

        if (baseUnit.replacementTextForUniques != "") lines += baseUnit.replacementTextForUniques
        else for (unique in baseUnit.uniqueObjects.filterNot {
            it.type == UniqueType.Unbuildable
                    || it.type == UniqueType.ConsumesResources  // already shown from getResourceRequirements
                    || it.type?.flags?.contains(UniqueFlag.HiddenToUsers) == true
        })
            lines += unique.text.tr()

        if (baseUnit.promotions.isNotEmpty()) {
            val prefix = "Free promotion${if (baseUnit.promotions.size == 1) "" else "s"}:".tr() + " "
            lines += baseUnit.promotions.joinToString(", ", prefix) { it.tr() }
        }

        return lines.joinToString("\n")
    }

     fun getCivilopediaTextLines(baseUnit: BaseUnit, ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()
        textList += FormattedLine("{Unit type}: ${baseUnit.unitType.tr()}")

        val stats = ArrayList<String>()
        if (baseUnit.strength != 0) stats += "${baseUnit.strength}${Fonts.strength}"
        if (baseUnit.rangedStrength != 0) {
            stats += "${baseUnit.rangedStrength}${Fonts.rangedStrength}"
            stats += "${baseUnit.range}${Fonts.range}"
        }
        if (baseUnit.movement != 0 && ruleset.unitTypes[baseUnit.unitType]?.isAirUnit() != true)
            stats += "${baseUnit.movement}${Fonts.movement}"
        if (stats.isNotEmpty())
            textList += FormattedLine(stats.joinToString(", "))

        if (baseUnit.cost > 0) {
            stats.clear()
            stats += "${baseUnit.cost}${Fonts.production}"
            if (baseUnit.canBePurchasedWithStat(null, Stat.Gold)) {
                // We need what INonPerpetualConstruction.getBaseGoldCost calculates but without any game- or civ-specific modifiers
                val buyCost = (30.0 * baseUnit.cost.toFloat().pow(0.75f) * baseUnit.hurryCostModifier.toPercent()).toInt() / 10 * 10
                stats += "$buyCost${Fonts.gold}"
            }
            textList += FormattedLine(stats.joinToString(", ", "{Cost}: "))
        }

        if (baseUnit.replacementTextForUniques.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine(baseUnit.replacementTextForUniques)
        } else if (baseUnit.uniques.isNotEmpty()) {
            textList += FormattedLine()
            for (unique in baseUnit.uniqueObjects.sortedBy { it.text }) {
                if (unique.hasFlag(UniqueFlag.HiddenToUsers)) continue
                if (unique.type == UniqueType.ConsumesResources) continue  // already shown from getResourceRequirements
                textList += FormattedLine(unique)
            }
        }

        val resourceRequirements = baseUnit.getResourceRequirements()
        if (resourceRequirements.isNotEmpty()) {
            textList += FormattedLine()
            for ((resource, amount) in resourceRequirements) {
                textList += FormattedLine(
                    resource.getConsumesAmountString(amount),
                    link = "Resource/$resource", color = "#F42"
                )
            }
        }

        if (baseUnit.uniqueTo != null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [${baseUnit.uniqueTo}]", link = "Nation/${baseUnit.uniqueTo}")
            if (baseUnit.replaces != null)
                textList += FormattedLine(
                    "Replaces [${baseUnit.replaces}]",
                    link = "Unit/${baseUnit.replaces}",
                    indent = 1
                )
        }

        if (baseUnit.requiredTech != null || baseUnit.upgradesTo != null || baseUnit.obsoleteTech != null) textList += FormattedLine()
        if (baseUnit.requiredTech != null) textList += FormattedLine(
            "Required tech: [${baseUnit.requiredTech}]",
            link = "Technology/${baseUnit.requiredTech}"
        )

        val canUpgradeFrom = ruleset.units
            .filterValues {
                (it.upgradesTo == baseUnit.name || it.upgradesTo != null && it.upgradesTo == baseUnit.replaces)
                        && (it.uniqueTo == baseUnit.uniqueTo || it.uniqueTo == null)
            }.keys
        if (canUpgradeFrom.isNotEmpty()) {
            if (canUpgradeFrom.size == 1)
                textList += FormattedLine(
                    "Can upgrade from [${canUpgradeFrom.first()}]",
                    link = "Unit/${canUpgradeFrom.first()}"
                )
            else {
                textList += FormattedLine()
                textList += FormattedLine("Can upgrade from:")
                for (unitName in canUpgradeFrom.sorted())
                    textList += FormattedLine(unitName, indent = 2, link = "Unit/$unitName")
                textList += FormattedLine()
            }
        }

        if (baseUnit.upgradesTo != null) textList += FormattedLine(
            "Upgrades to [${baseUnit.upgradesTo}]",
            link = "Unit/${baseUnit.upgradesTo}"
        )
        if (baseUnit.obsoleteTech != null) textList += FormattedLine(
            "Obsolete with [${baseUnit.obsoleteTech}]",
            link = "Technology/${baseUnit.obsoleteTech}"
        )

        if (baseUnit.promotions.isNotEmpty()) {
            textList += FormattedLine()
            baseUnit.promotions.withIndex().forEach {
                textList += FormattedLine(
                    when {
                        baseUnit.promotions.size == 1 -> "{Free promotion:} "
                        it.index == 0 -> "{Free promotions:} "
                        else -> ""
                    } + "{${it.value.tr()}}" +   // tr() not redundant as promotion names now can use []
                            (if (baseUnit.promotions.size == 1 || it.index == baseUnit.promotions.size - 1) "" else ","),
                    link = "Promotions/${it.value}",
                    indent = if (it.index == 0) 0 else 1
                )
            }
        }

        val seeAlso = ArrayList<FormattedLine>()
        for ((other, unit) in ruleset.units) {
            if (unit.replaces == baseUnit.name || baseUnit.uniques.contains("[${baseUnit.name}]")) {
                seeAlso += FormattedLine(other, link = "Unit/$other", indent = 1)
            }
        }
        if (seeAlso.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{See also}:")
            textList += seeAlso
        }

        return textList
    }

}
