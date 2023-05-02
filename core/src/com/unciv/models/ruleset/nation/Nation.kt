package com.unciv.models.ruleset.nation

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.squareBraceRegex
import com.unciv.models.translations.tr
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen.Companion.showReligionInCivilopedia
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import kotlin.math.pow

class Nation : RulesetObject() {
    var leaderName = ""
    fun getLeaderDisplayName() = if (isCityState || isSpectator) name
        else "[$leaderName] of [$name]"

    val style = ""
    fun getStyleOrCivName() = style.ifEmpty { name }

    var cityStateType: String? = null
    var preferredVictoryType: String = Constants.neutralVictoryType
    var declaringWar = ""
    var attacked = ""
    var defeated = ""
    var introduction = ""
    var tradeRequest = ""

    var neutralHello = ""
    var hateHello = ""

    lateinit var outerColor: List<Int>
    var uniqueName = ""
    var uniqueText = ""
    var innerColor: List<Int>? = null
    var startBias = ArrayList<String>()

    var startIntroPart1 = ""
    var startIntroPart2 = ""

    /* Properties present in json but not yet implemented:
    var adjective = ArrayList<String>()
     */

    var spyNames = ArrayList<String>()

    var favoredReligion: String? = null

    var cities: ArrayList<String> = arrayListOf()

    override fun getUniqueTarget() = UniqueTarget.Nation

    @Transient
    private lateinit var outerColorObject: Color
    fun getOuterColor(): Color = outerColorObject

    @Transient
    private lateinit var innerColorObject: Color

    fun getInnerColor(): Color = innerColorObject

    val isCityState by lazy { cityStateType != null }
    val isMajorCiv by lazy { !isBarbarian && !isCityState && !isSpectator }
    val isBarbarian by lazy { name == Constants.barbarians }
    val isSpectator by lazy { name == Constants.spectator }

    // This is its own transient because we'll need to check this for every tile-to-tile movement which is harsh
    @Transient
    var forestsAndJunglesAreRoads = false

    // Same for Inca unique
    @Transient
    var ignoreHillMovementCost = false

    fun setTransients() {
        outerColorObject = colorFromRGB(outerColor)

        innerColorObject = if (innerColor == null) Color.BLACK
                           else colorFromRGB(innerColor!!)

        forestsAndJunglesAreRoads = uniques.contains("All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel.")
        ignoreHillMovementCost = uniques.contains("Units ignore terrain costs when moving into any tile with Hills")
    }


    override fun makeLink() = "Nation/$name"
    override fun getSortGroup(ruleset: Ruleset) = when {
        isCityState -> 1
        isBarbarian -> 9
        else -> 0
    }

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        if (isCityState) return getCityStateInfo(ruleset)

        val textList = ArrayList<FormattedLine>()

        if (leaderName.isNotEmpty()) {
            textList += FormattedLine(extraImage = "LeaderIcons/$leaderName", imageSize = 200f)
            textList += FormattedLine(getLeaderDisplayName(), centered = true, header = 3)
            textList += FormattedLine()
        }

        if (uniqueName != "")
            textList += FormattedLine("{$uniqueName}:", header = 4)
        if (uniqueText != "") {
            textList += FormattedLine(uniqueText, indent = 1)
        } else {
            uniqueObjects.forEach {
                if (!it.hasFlag(UniqueFlag.HiddenToUsers))
                    textList += FormattedLine(it)
            }
            textList += FormattedLine()
        }

        if (startBias.isNotEmpty()) {
            startBias.withIndex().forEach {
                // can be "Avoid []"
                val link = if ('[' !in it.value) it.value
                    else squareBraceRegex.find(it.value)!!.groups[1]!!.value
                textList += FormattedLine(
                    (if (it.index == 0) "[Start bias:] " else "") + it.value.tr(),  // extra tr because tr cannot nest {[]}
                    link = "Terrain/$link",
                    indent = if (it.index == 0) 0 else 1,
                    iconCrossed = it.value.startsWith("Avoid "))
            }
            textList += FormattedLine()
        }
        textList += getUniqueBuildingsText(ruleset)
        textList += getUniqueUnitsText(ruleset)
        textList += getUniqueImprovementsText(ruleset)

        return textList
    }

    private fun getCityStateInfo(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        val cityStateType = ruleset.cityStateTypes[cityStateType]!!
        textList += FormattedLine("{Type}: {${cityStateType.name}}", header = 4, color = cityStateType.getColor().toString())

        var showResources = false

        val friendBonus = cityStateType.friendBonusUniqueMap
        if (friendBonus.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{When Friends:} ")
            friendBonus.getAllUniques().forEach {
                textList += FormattedLine(it, indent = 1)
                if (it.text == "Provides a unique luxury") showResources = true
            }
        }

        val allyBonus = cityStateType.allyBonusUniqueMap
        if (allyBonus.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{When Allies:} ")
            allyBonus.getAllUniques().forEach {
                textList += FormattedLine(it, indent = 1)
                if (it.text == "Provides a unique luxury") showResources = true
            }
        }

        if (showResources) {
            val allMercantileResources = ruleset.tileResources.values
                .filter { it.hasUnique(UniqueType.CityStateOnlyResource) }

            if (allMercantileResources.isNotEmpty()) {
                textList += FormattedLine()
                textList += FormattedLine("The unique luxury is one of:")
                allMercantileResources.forEach {
                    textList += FormattedLine(it.name, it.makeLink(), indent = 1)
                }
            }
        }

        // personality is not a nation property, it gets assigned to the civ randomly
        return textList
    }

    private fun getUniqueBuildingsText(ruleset: Ruleset) = sequence {
        val religionEnabled = showReligionInCivilopedia(ruleset)
        for (building in ruleset.buildings.values) {
            when {
                building.uniqueTo != name -> continue
                building.hasUnique(UniqueType.HiddenFromCivilopedia) -> continue
                !religionEnabled && building.hasUnique(UniqueType.HiddenWithoutReligion) -> continue
            }
            yield(FormattedLine(separator = true))
            yield(FormattedLine("{${building.name}} -", link=building.makeLink()))
            if (building.replaces != null && ruleset.buildings.containsKey(building.replaces!!)) {
                val originalBuilding = ruleset.buildings[building.replaces!!]!!
                yield(FormattedLine("Replaces [${originalBuilding.name}]", link=originalBuilding.makeLink(), indent=1))

                for ((key, value) in building)
                    if (value != originalBuilding[key])
                        yield(FormattedLine( key.name.tr() + " " +"[${value.toInt()}] vs [${originalBuilding[key].toInt()}]".tr(), indent=1))

                for (unique in building.uniques.filter { it !in originalBuilding.uniques })
                    yield(FormattedLine(unique, indent=1))
                if (building.maintenance != originalBuilding.maintenance)
                    yield(FormattedLine("{Maintenance} ".tr() + "[${building.maintenance}] vs [${originalBuilding.maintenance}]".tr(), indent=1))
                if (building.cost != originalBuilding.cost)
                    yield(FormattedLine("{Cost} ".tr() + "[${building.cost}] vs [${originalBuilding.cost}]".tr(), indent=1))
                if (building.cityStrength != originalBuilding.cityStrength)
                    yield(FormattedLine("{City strength} ".tr() + "[${building.cityStrength}] vs [${originalBuilding.cityStrength}]".tr(), indent=1))
                if (building.cityHealth != originalBuilding.cityHealth)
                    yield(FormattedLine("{City health} ".tr() + "[${building.cityHealth}] vs [${originalBuilding.cityHealth}]".tr(), indent=1))
                yield(FormattedLine())
            } else if (building.replaces != null) {
                yield(FormattedLine("Replaces [${building.replaces}], which is not found in the ruleset!", indent=1))
            } else {
                yield(FormattedLine(building.getShortDescription(true), indent=1))
            }
        }
    }

    private fun getUniqueUnitsText(ruleset: Ruleset) = sequence {
        for (unit in ruleset.units.values) {
            if (unit.uniqueTo != name || unit.hasUnique(UniqueType.HiddenFromCivilopedia)) continue
            yield(FormattedLine(separator = true))
            yield(FormattedLine("{${unit.name}} -", link="Unit/${unit.name}"))
            if (unit.replaces != null && ruleset.units.containsKey(unit.replaces!!)) {
                val originalUnit = ruleset.units[unit.replaces!!]!!
                yield(FormattedLine("Replaces [${originalUnit.name}]", link="Unit/${originalUnit.name}", indent=1))
                if (unit.cost != originalUnit.cost)
                    yield(FormattedLine("{Cost} ".tr() + "[${unit.cost}] vs [${originalUnit.cost}]".tr(), indent=1))
                if (unit.strength != originalUnit.strength)
                    yield(FormattedLine("${Fonts.strength} " + "[${unit.strength}] vs [${originalUnit.strength}]".tr(), indent=1))
                if (unit.rangedStrength != originalUnit.rangedStrength)
                    yield(FormattedLine("${Fonts.rangedStrength} " + "[${unit.rangedStrength}] vs [${originalUnit.rangedStrength}]".tr(), indent=1))
                if (unit.range != originalUnit.range)
                    yield(FormattedLine("${Fonts.range} " + "[${unit.range}] vs [${originalUnit.range}]".tr(), indent=1))
                if (unit.movement != originalUnit.movement)
                    yield(FormattedLine("${Fonts.movement} " + "[${unit.movement}] vs [${originalUnit.movement}]".tr(), indent=1))
                for (resource in originalUnit.getResourceRequirementsPerTurn().keys)
                    if (!unit.getResourceRequirementsPerTurn().containsKey(resource)) {
                        yield(FormattedLine("[$resource] not required", link="Resource/$resource", indent=1))
                    }
                // This does not use the auto-linking FormattedLine(Unique) for two reasons:
                // would look a little chaotic as unit uniques unlike most uniques are a HashSet and thus do not preserve order
                // No .copy() factory on FormattedLine and no FormattedLine(Unique, all other val's) constructor either
                if (unit.replacementTextForUniques.isNotEmpty()) {
                    yield(FormattedLine(unit.replacementTextForUniques))
                }
                else for (unique in unit.uniqueObjects.filterNot { it.text in originalUnit.uniques || it.hasFlag(UniqueFlag.HiddenToUsers) }) {
                    yield(FormattedLine(unique.text.tr(), indent = 1))
                }
                for (unique in originalUnit.uniqueObjects.filterNot { it.text in unit.uniques || it.hasFlag(UniqueFlag.HiddenToUsers) }) {
                    yield(
                        FormattedLine("Lost ability".tr() + " (" + "vs [${originalUnit.name}]".tr() + "): " +
                            unique.text.tr(), indent = 1)
                    )
                }
                for (promotion in unit.promotions.filter { it !in originalUnit.promotions }) {
                    val effect = ruleset.unitPromotions[promotion]!!.uniques
                    // "{$promotion} ({$effect})" won't work as effect may contain [] and tr() does not support that kind of nesting
                    yield(
                        FormattedLine(
                        "${promotion.tr(true)} (${effect.joinToString(",") { it.tr() }})",
                        link = "Promotion/$promotion", indent = 1 )
                    )
                }
            } else if (unit.replaces != null) {
                yield(FormattedLine("Replaces [${unit.replaces}], which is not found in the ruleset!", indent = 1))
            } else {
                yieldAll(unit.getCivilopediaTextLines(ruleset).map {
                    FormattedLine(it.text, link = it.link, indent = it.indent + 1, color = it.color)
                })
            }

            yield(FormattedLine())
        }
    }

    private fun getUniqueImprovementsText(ruleset: Ruleset) = sequence {
        for (improvement in ruleset.tileImprovements.values) {
            if (improvement.uniqueTo != name || improvement.hasUnique(UniqueType.HiddenFromCivilopedia)) continue

            yield(FormattedLine(separator = true))
            yield(FormattedLine(improvement.name, link = "Improvement/${improvement.name}"))
            yield(FormattedLine(improvement.cloneStats().toString(), indent = 1))   // = (improvement as Stats).toString minus import plus copy overhead
            if (improvement.terrainsCanBeBuiltOn.isNotEmpty()) {
                improvement.terrainsCanBeBuiltOn.withIndex().forEach {
                    yield(
                        FormattedLine(if (it.index == 0) "{Can be built on} {${it.value}}" else "or [${it.value}]",
                        link = "Terrain/${it.value}", indent = if (it.index == 0) 1 else 2)
                    )
                }
            }
            for (unique in improvement.uniques)
                yield(FormattedLine(unique, indent = 1))
        }
    }

    fun getContrastRatio() = getContrastRatio(getInnerColor(), getOuterColor())

    fun matchesFilter(filter: String): Boolean {
        return when (filter) {
            "All" -> true
            name -> true
            "Major" -> isMajorCiv
            // "CityState" to be deprecated, replaced by "City-States"
            "CityState", Constants.cityStates -> isCityState
            else -> uniques.contains(filter)
        }
    }
}


/** All defined by https://www.w3.org/TR/WCAG20/#relativeluminancedef */
fun getRelativeLuminance(color: Color): Double {
    fun getRelativeChannelLuminance(channel: Float): Double =
            if (channel < 0.03928) channel / 12.92
            else ((channel + 0.055) / 1.055).pow(2.4)

    val R = getRelativeChannelLuminance(color.r)
    val G = getRelativeChannelLuminance(color.g)
    val B = getRelativeChannelLuminance(color.b)

    return 0.2126 * R + 0.7152 * G + 0.0722 * B
}

/** https://www.w3.org/TR/WCAG20/#contrast-ratiodef */
fun getContrastRatio(color1: Color, color2: Color): Double { // ratio can range from 1 to 21
    val innerColorLuminance = getRelativeLuminance(color1)
    val outerColorLuminance = getRelativeLuminance(color2)

    return if (innerColorLuminance > outerColorLuminance)
        (innerColorLuminance + 0.05) / (outerColorLuminance + 0.05)
        else (outerColorLuminance + 0.05) / (innerColorLuminance + 0.05)
}
