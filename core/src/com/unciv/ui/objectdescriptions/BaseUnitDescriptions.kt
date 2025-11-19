package com.unciv.ui.objectdescriptions

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.logic.city.City
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitMovementType
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.getConsumesAmountString
import com.unciv.ui.components.extensions.getCostsAmountString
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import yairm210.purity.annotations.Readonly

object BaseUnitDescriptions {

    /** Generate short description as comma-separated string for Technology description "Units enabled" and GreatPersonPickerScreen */
    fun getShortDescription(baseUnit: BaseUnit, uniqueExclusionFilter: Unique.() -> Boolean = {false}): String {
        val infoList = mutableListOf<String>()
        if (baseUnit.strength != 0) infoList += "${baseUnit.strength.tr()}${Fonts.strength}"
        if (baseUnit.rangedStrength != 0) infoList += "${baseUnit.rangedStrength.tr()}${Fonts.rangedStrength}"
        if (baseUnit.movement != 2) infoList += "${baseUnit.movement.tr()}${Fonts.movement}"
        for (promotion in baseUnit.promotions)
            infoList += promotion.tr()
        if (baseUnit.replacementTextForUniques != "") infoList += baseUnit.replacementTextForUniques
        else baseUnit.uniquesToDescription(infoList, uniqueExclusionFilter)
        return infoList.joinToString()
    }


    /** Generate description as multi-line string for CityScreen addSelectedConstructionTable
     * @param city Supplies civInfo to show available resources after resource requirements */
    fun getDescription(baseUnit: BaseUnit, city: City): String {
        val lines = mutableListOf<String>()
        val availableResources = city.civ.getCivResourcesByName()

        // Consumes
        for ((resourceName, amount) in baseUnit.getResourceRequirementsPerTurn(city.civ.state)) {
            val available = availableResources[resourceName] ?: 0
            val resource = baseUnit.ruleset.tileResources[resourceName] ?: continue
            lines += resourceName.getConsumesAmountString(amount, resource.isStockpiled, available).tr()
        }

        // Costs
        for ((resourceName, amount) in baseUnit.getStockpiledResourceRequirements(city.civ.state)) {
            val available = city.getAvailableResourceAmount(resourceName)
            val resource = city.getRuleset().tileResources[resourceName] ?: continue
            lines += resourceName.getCostsAmountString(amount, available).tr()
        }

        var strengthLine = ""
        if (baseUnit.strength != 0) {
            strengthLine += "${baseUnit.strength}${Fonts.strength}, "
            if (baseUnit.rangedStrength != 0)
                strengthLine += "${baseUnit.rangedStrength}${Fonts.rangedStrength}, ${baseUnit.range}${Fonts.range}, "
        }
        lines += "$strengthLine${baseUnit.movement}${Fonts.movement}"

        if (baseUnit.replacementTextForUniques != "") lines += baseUnit.replacementTextForUniques
        else baseUnit.uniquesToDescription(lines) {
            type == UniqueType.Unbuildable
            // Already displayed in the resource requirements
            || type == UniqueType.ConsumesResources
            || type == UniqueType.CostsResources
        } 

        if (baseUnit.promotions.isNotEmpty()) {
            val prefix = "Free promotion${if (baseUnit.promotions.size == 1) "" else "s"}:".tr() + " "
            lines += baseUnit.promotions.joinToString(", ", prefix) { it.tr() }
        }

        return lines.joinToString("\n")
    }

    fun getCivilopediaTextLines(baseUnit: BaseUnit, ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        // Potentially show pixel unit on top (other civilopediaText is handled by the caller)
        textList.addPixelUnitImage(baseUnit)

        // Don't call baseUnit.getType() here - coming from the main menu baseUnit isn't fully initialized
        val unitTypeLink = ruleset.unitTypes[baseUnit.unitType]?.makeLink() ?: ""
        textList += FormattedLine("{Unit type}: ${baseUnit.unitType.tr()}", unitTypeLink)

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
                stats += "${baseUnit.getCivilopediaGoldCost()}${Fonts.gold}"
            }
            textList += FormattedLine(stats.joinToString("/", "{Cost}: "))
        }

        if (baseUnit.interceptRange > 0) {
            textList += FormattedLine("Air Intercept Range: [${baseUnit.interceptRange}]")
        }

        if (baseUnit.replacementTextForUniques.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine(baseUnit.replacementTextForUniques)
        } else {
            baseUnit.uniquesToCivilopediaTextLines(textList, colorConsumesResources = true)
        }

        if (baseUnit.requiredResource != null) {
            textList += FormattedLine()
            val resource = ruleset.tileResources[baseUnit.requiredResource]
            textList += FormattedLine(
                baseUnit.requiredResource!!.getConsumesAmountString(1, resource!!.isStockpiled),
                link="Resources/${baseUnit.requiredResource}", color="#F42")
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
                        && (it.uniqueTo == null || it.uniqueTo == baseUnit.uniqueTo)
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

    /** Show Pixel Unit Art for the unit.
     *  * _Unless_ the mod already uses [extraImage][FormattedLine.extraImage] in the unit's [civilopediaText][IRulesetObject.civilopediaText]
     *  * _Unless_ user has selected no [unitSet][GameSettings.unitSet]
     *  * For units with era or style variants, only the default is shown (todo: extend FormattedLine with slideshow capability)
     */
    // Note: By popular request (this is a simple variant of one of the ideas in #10175)
    private fun ArrayList<FormattedLine>.addPixelUnitImage(baseUnit: BaseUnit) {
        if (baseUnit.civilopediaText.any { it.extraImage.isNotEmpty() }) return
        val settings = GUI.getSettings()
        if (settings.unitSet.isNullOrEmpty() || settings.pediaUnitArtSize < 1f) return
        val imageName = "TileSets/${settings.unitSet}/Units/${baseUnit.name}"
        if (!ImageGetter.imageExists(imageName)) return  // Some units don't have Unit art (e.g. nukes)
        add(FormattedLine(extraImage = imageName, imageSize = settings.pediaUnitArtSize, centered = true))
        add(FormattedLine(separator = true, color = "GRAY"))
    }

    @Suppress("RemoveExplicitTypeArguments")  // for faster IDE - inferring sequence types can be slow
    
    fun UnitType.getUnitTypeCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        @Readonly
        fun getDomainLines() = sequence<FormattedLine> {
            yield(FormattedLine("{Unit types}:", header = 4))
            val myMovementType = getMovementType()
            for (unitType in ruleset.unitTypes.values) {
                if (unitType.getMovementType() != myMovementType) continue
                if (!unitType.isUsed(ruleset)) continue
                yield(FormattedLine(unitType.name, unitType.makeLink()))
            }
        }
        fun getUnitTypeLines() = sequence<FormattedLine> {
            getMovementType()?.let {
                val color = when (it) {
                    UnitMovementType.Land -> "#ffc080"
                    UnitMovementType.Water -> "#80d0ff"
                    UnitMovementType.Air -> "#e0e0ff"
                }
                yield(FormattedLine("Domain: [${it.name}]", "UnitType/Domain: [${it.name}]", color = color))
                yield(FormattedLine(separator = true))
            }
            yield(FormattedLine("Units:", header = 4))
            for (unit in ruleset.units.values) {
                if (unit.unitType != name) continue
                yield(FormattedLine(unit.name, unit.makeLink()))
            }

            val relevantPromotions = ruleset.unitPromotions.values.filter { it.unitTypes.contains(name) }
            if (relevantPromotions.isNotEmpty()) {
                yield(FormattedLine("Promotions", header = 4))
                for (promotion in relevantPromotions)
                    yield(FormattedLine(promotion.name, promotion.makeLink()))
            }

            yieldAll(uniquesToCivilopediaTextLines(leadingSeparator = { yield(FormattedLine(separator = true)) }))
        }
        return (if (name.startsWith("Domain: ")) getDomainLines() else getUnitTypeLines()).toList()
    }

    /**
     * Lists differences e.g. for help on an upgrade, or how a nation-unique compares to its replacement.
     *
     * Cost is **not** included.
     * Result lines are **not** translated.
     *
     * @param originalUnit The "older" unit
     * @param betterUnit The "newer" unit
     * @return Sequence of Pairs - first is the actual text, second is an optional link for Civilopedia use
     */
    fun getDifferences(ruleset: Ruleset, originalUnit: BaseUnit, betterUnit: BaseUnit):
            Sequence<Pair<String, String?>> = sequence {
        if (betterUnit.strength != originalUnit.strength)
            yield("${Fonts.strength} {[${betterUnit.strength}] vs [${originalUnit.strength}]}" to null)

        if (betterUnit.rangedStrength > 0 && originalUnit.rangedStrength == 0)
            yield("[Gained] ${Fonts.rangedStrength} [${betterUnit.rangedStrength}] ${Fonts.range} [${betterUnit.range}]" to null)
        else if (betterUnit.rangedStrength == 0 && originalUnit.rangedStrength > 0)
            yield("[Lost] ${Fonts.rangedStrength} [${originalUnit.rangedStrength}] ${Fonts.range} [${originalUnit.range}]" to null)
        else {
            if (betterUnit.rangedStrength != originalUnit.rangedStrength)
                yield("${Fonts.rangedStrength} " + "{[${betterUnit.rangedStrength}] vs [${originalUnit.rangedStrength}]}" to null)
            if (betterUnit.range != originalUnit.range)
                yield("${Fonts.range} {[${betterUnit.range}] vs [${originalUnit.range}]}" to null)
        }

        if (betterUnit.movement != originalUnit.movement)
            yield("${Fonts.movement} {[${betterUnit.movement}] vs [${originalUnit.movement}]}" to null)

        for (resource in originalUnit.getResourceRequirementsPerTurn(GameContext.IgnoreConditionals).keys)
            if (!betterUnit.getResourceRequirementsPerTurn(GameContext.IgnoreConditionals).containsKey(resource)) {
                yield("[$resource] not required" to "Resource/$resource")
            }
        // We return the unique text directly, so Nation.getUniqueUnitsText will not use the
        // auto-linking FormattedLine(Unique) - two reasons in favor:
        // would look a little chaotic as unit uniques unlike most uniques are a HashSet and thus do not preserve order
        // No .copy() factory on FormattedLine and no (Unique, all other val's) constructor either
        if (betterUnit.replacementTextForUniques.isNotEmpty()) {
            yield(betterUnit.replacementTextForUniques to null)
        } else {
            val newAbilityPredicate: (Unique)->Boolean = { it.text in originalUnit.uniques || it.isHiddenToUsers() }
            for (unique in betterUnit.uniqueObjects.filterNot(newAbilityPredicate))
                yield(unique.text to null)
        }

        val lostAbilityPredicate: (Unique)->Boolean = { it.text in betterUnit.uniques || it.isHiddenToUsers() }
        for (unique in originalUnit.uniqueObjects.filterNot(lostAbilityPredicate)) {
            // Need double translation of the "ability" here - unique texts may contain nuts - pardon, square brackets
            yield("Lost ability (vs [${originalUnit.name}]): [${unique.getDisplayText().tr()}]" to null)
        }
        for (promotionName in betterUnit.promotions.filter { it !in originalUnit.promotions }) {
            val promotion = ruleset.unitPromotions[promotionName]!!
            val effects = promotion.uniquesToDescription().joinToString()
            yield("{$promotionName} ($effects)" to promotion.makeLink())
        }
    }

    /** Prepares a Widget with [information about the differences][getDifferences] between units.
     *  Used by UnitUpgradeMenu (but formerly also for a tooltip).
     */
    fun getUpgradeInfoTable(title: String, unitUpgrading: BaseUnit, unitToUpgradeTo: BaseUnit): Table {
        val ruleset = unitToUpgradeTo.ruleset
        val info = sequenceOf(FormattedLine(title, color = "#FDA", icon = unitToUpgradeTo.makeLink(), header = 5)) +
            getDifferences(ruleset, unitUpgrading, unitToUpgradeTo)
                .map { FormattedLine(it.first, icon = it.second ?: "") }
        return MarkupRenderer.render(info.asIterable(), 400f)
    }
}
