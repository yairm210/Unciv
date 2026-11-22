package com.unciv.ui.objectdescriptions

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.logic.city.City
import com.unciv.models.metadata.GameSettings
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
import com.unciv.ui.objectdescriptions.FormattedLineListBuilder.Companion.buildCivilopediaText
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import yairm210.purity.annotations.Readonly

object BaseUnitDescriptions {

    /** Generate short description as comma-separated string for Technology description "Units enabled" and GreatPersonPickerScreen */
    @Readonly
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
    @Readonly
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
            if (baseUnit.ruleset.tileResources[resourceName] == null) continue
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

    fun BaseUnit.getBaseUnitCivilopediaTextLines(ruleset: Ruleset) = buildCivilopediaText {
        // Potentially show pixel unit on top (other civilopediaText is handled by the caller)
        addPixelUnitImage(this@getBaseUnitCivilopediaTextLines)

        // Don't call baseUnit.getType() here - coming from the main menu baseUnit isn't fully initialized
        val unitTypeLink = ruleset.unitTypes[unitType]?.makeLink() ?: ""
        add("{Unit type}: {$unitType}", unitTypeLink)

        val stats = ArrayList<String>()
        if (strength != 0) stats += "$strength${Fonts.strength}"
        if (rangedStrength != 0) {
            stats += "$rangedStrength${Fonts.rangedStrength}"
            stats += "$range${Fonts.range}"
        }
        if (movement != 0 && ruleset.unitTypes[unitType]?.isAirUnit() != true)
            stats += "$movement${Fonts.movement}"
        if (stats.isNotEmpty())
            add(stats.joinToString(", "))

        if (cost > 0) {
            stats.clear()
            stats += "$cost${Fonts.production}"
            if (canBePurchasedWithStat(null, Stat.Gold)) {
                stats += "${getCivilopediaGoldCost()}${Fonts.gold}"
            }
            add(stats.joinToString("/", "{Cost}: "))
        }

        if (interceptRange > 0)
            add("Air Intercept Range: [$interceptRange]")

        if (replacementTextForUniques.isNotEmpty()) {
            space()
            add(replacementTextForUniques)
        } else {
            addUniques(colorConsumesResources = true)
        }

        if (requiredResource != null) {
            space()
            val resource = ruleset.tileResources[requiredResource]
            add(
                requiredResource!!.getConsumesAmountString(1, resource!!.isStockpiled),
                link="Resources/$requiredResource", color="#F42"
            )
        }

        if (uniqueTo != null) {
            space()
            add("Unique to [$uniqueTo]", link = "Nation/$uniqueTo")
            if (replaces != null)
                add("Replaces [$replaces]", link = "Unit/$replaces", indent = 1)
        }

        if (requiredTech != null || upgradesTo != null || obsoleteTech != null)
            space()
        if (requiredTech != null)
            add("Required tech: [$requiredTech]", link = "Technology/$requiredTech")

        val canUpgradeFrom = ruleset.units
            .filterValues {
                (it.upgradesTo == name || it.upgradesTo != null && it.upgradesTo == replaces)
                        && (it.uniqueTo == null || it.uniqueTo == uniqueTo)
            }.keys
        if (canUpgradeFrom.isNotEmpty()) {
            if (canUpgradeFrom.size == 1)
                add("Can upgrade from [${canUpgradeFrom.first()}]", link = "Unit/${canUpgradeFrom.first()}")
            else {
                space()
                add("Can upgrade from:")
                for (unitName in canUpgradeFrom.sorted())
                    add(unitName, link = "Unit/$unitName", indent = 2)
                space()
            }
        }

        if (upgradesTo != null)
            add("Upgrades to [$upgradesTo]", link = "Unit/$upgradesTo")
        if (obsoleteTech != null)
            add("Obsolete with [$obsoleteTech]", link = "Technology/$obsoleteTech")

        if (promotions.isNotEmpty()) {
            space()
            promotions.withIndex().forEach {
                add(
                    when {
                        promotions.size == 1 -> "{Free promotion:} "
                        it.index == 0 -> "{Free promotions:} "
                        else -> ""
                    } + "{${it.value.tr()}}" +   // tr() not redundant as promotion names now can use []
                            (if (promotions.size == 1 || it.index == promotions.size - 1) "" else ","),
                    link = "Promotions/${it.value}",
                    indent = if (it.index == 0) 0 else 1
                )
            }
        }

        fun filterOtherUnit(unit: BaseUnit) = unit.replaces == name || unit.hasUniquesMentioning(name)
        val seeAlso = ruleset.units.values.asSequence().filter(::filterOtherUnit)
        addSeeAlso(seeAlso)
    }

    /** Show Pixel Unit Art for the unit.
     *  * _Unless_ the mod already uses [extraImage][FormattedLine.extraImage] in the unit's [civilopediaText][com.unciv.models.ruleset.IRulesetObject.civilopediaText]
     *  * _Unless_ user has selected no [unitSet][GameSettings.unitSet]
     *  * For units with era or style variants, only the default is shown (todo: extend FormattedLine with slideshow capability)
     */
    // Note: By popular request (this is a simple variant of one of the ideas in #10175)
    private fun FormattedLineListBuilder.addPixelUnitImage(baseUnit: BaseUnit) {
        val pixelUnitTexturePattern = Regex("TileSets/[^/]+/Units/${baseUnit.name}")
        if (baseUnit.civilopediaText.any { it.extraImage.matches(pixelUnitTexturePattern) }) return
        val settings = GUI.getSettings()
        if (settings.unitSet.isNullOrEmpty() || settings.pediaUnitArtSize < 1f) return
        val imageName = "TileSets/${settings.unitSet}/Units/${baseUnit.name}"
        if (!ImageGetter.imageExists(imageName)) return  // Some units don't have Unit art (e.g. nukes)
        add(extraImage = imageName, imageSize = settings.pediaUnitArtSize, centered = true)
        separator(color = "GRAY")
    }

    fun UnitType.getUnitTypeCivilopediaTextLines(ruleset: Ruleset) = buildCivilopediaText {
        @Readonly
        fun getDomainLines()  {
            add("{Unit types}:", header = 4)
            val myMovementType = getMovementType()
            for (unitType in ruleset.unitTypes.values) {
                if (unitType.getMovementType() != myMovementType) continue
                if (!unitType.isUsed(ruleset)) continue
                add(unitType.name, unitType.makeLink())
            }
        }
        fun getUnitTypeLines() {
            getMovementType()?.let {
                val color = when (it) {
                    UnitMovementType.Land -> "#ffc080"
                    UnitMovementType.Water -> "#80d0ff"
                    UnitMovementType.Air -> "#e0e0ff"
                }
                add("Domain: [${it.name}]", link = "UnitType/Domain: [${it.name}]", color = color)
                separator()
            }
            add("Units:", header = 4)
            for (unit in ruleset.units.values) {
                if (unit.unitType != name) continue
                add(unit.name, unit.makeLink())
            }

            val relevantPromotions = ruleset.unitPromotions.values.filter { it.unitTypes.contains(name) }
            if (relevantPromotions.isNotEmpty()) {
                add("Promotions", header = 4)
                for (promotion in relevantPromotions)
                    add(promotion.name, promotion.makeLink())
            }

            addUniques(FormattedLineListBuilder.SeparatorType.Line)
        }
        if (name.startsWith("Domain: ")) getDomainLines() else getUnitTypeLines()
    }

    /**
     * Lists differences e.g. for help on an upgrade, or how a nation-unique compares to its replacement to a FormattedLineListBuilder.
     *
     * Cost is **not** included.
     *
     * @param originalUnit The "older" unit
     * @param betterUnit The "newer" unit
     */
    fun FormattedLineListBuilder.addUnitDifferences(ruleset: Ruleset, originalUnit: BaseUnit, betterUnit: BaseUnit, linked: Boolean = false, indent: Int = 0) {
        for ((text, link) in getDifferences(ruleset, originalUnit, betterUnit)) {
            when {
                link == null -> add(text, indent = indent)
                linked -> add(text, link = link, indent = indent)
                else -> add(text, icon = link, indent = indent)
            }
        }
    }

    private fun getDifferences(ruleset: Ruleset, originalUnit: BaseUnit, betterUnit: BaseUnit):
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
        // Would look a little chaotic as unit uniques unlike most uniques are a HashSet and thus do not preserve order
        // No (Unique, all other val's) constructor on FormattedLine
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
        val info = buildCivilopediaText {
            add(title, color = "#FDA", icon = unitToUpgradeTo.makeLink(), header = 5)
            addUnitDifferences(unitToUpgradeTo.ruleset, unitUpgrading, unitToUpgradeTo)
        }
        return MarkupRenderer.render(info, 400f)
    }
}
