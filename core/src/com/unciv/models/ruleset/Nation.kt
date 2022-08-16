 package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CityStateType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.squareBraceRegex
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaScreen.Companion.showReligionInCivilopedia
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.extensions.colorFromRGB

class Nation : RulesetObject() {
    var leaderName = ""
    fun getLeaderDisplayName() = if (isCityState()) name
    else "[$leaderName] of [$name]"

    val style = ""
    fun getStyleOrCivName() = style.ifEmpty { name }

    var cityStateType: CityStateType? = null
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
    override fun getUniqueTarget() = UniqueTarget.Nation
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

    @Transient
    private lateinit var outerColorObject: Color
    fun getOuterColor(): Color = outerColorObject

    @Transient
    private lateinit var innerColorObject: Color

    fun getInnerColor(): Color = innerColorObject

    fun isCityState() = cityStateType != null
    fun isMajorCiv() = !isBarbarian() && !isCityState() && !isSpectator()
    fun isBarbarian() = name == Constants.barbarians
    fun isSpectator() = name == Constants.spectator

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

    var cities: ArrayList<String> = arrayListOf()


    override fun makeLink() = "Nation/$name"
    override fun getSortGroup(ruleset: Ruleset) = when {
        isCityState() -> 1
        isBarbarian() -> 9
        else -> 0
    }

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        if (isCityState()) return getCityStateInfo(ruleset)

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

        textList += FormattedLine("{Type}: {$cityStateType}", header = 4, color = cityStateType!!.color)

        val era = if (UncivGame.isCurrentInitialized() && UncivGame.Current.gameInfo != null) {
            UncivGame.Current.gameInfo!!.getCurrentPlayerCivilization().getEra()
        } else {
            ruleset.eras.values.first()
        }
        var showResources = false

        val friendBonus = era.friendBonus[cityStateType!!.name]
        if (friendBonus != null && friendBonus.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{When Friends:} ")
            friendBonus.forEach {
                textList += FormattedLine(Unique(it), indent = 1)
                if (it == "Provides a unique luxury") showResources = true
            }
        }

        val allyBonus = era.allyBonus[cityStateType!!.name]
        if (allyBonus != null && allyBonus.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{When Allies:} ")
            allyBonus.forEach {
                textList += FormattedLine(Unique(it), indent = 1)
                if (it == "Provides a unique luxury") showResources = true
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
                yield(FormattedLine(building.getShortDescription(), indent=1))
            }
        }
    }

    private fun getUniqueUnitsText(ruleset: Ruleset) = sequence {
        for (unit in ruleset.units.values) {
            if (unit.uniqueTo != name || unit.hasUnique(UniqueType.HiddenFromCivilopedia)) continue
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
                for (resource in originalUnit.getResourceRequirements().keys)
                    if (!unit.getResourceRequirements().containsKey(resource)) {
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
                    yield(FormattedLine("Lost ability".tr() + " (" + "vs [${originalUnit.name}]".tr() + "): " +
                            unique.text.tr(), indent = 1))
                }
                for (promotion in unit.promotions.filter { it !in originalUnit.promotions }) {
                    val effect = ruleset.unitPromotions[promotion]!!.uniques
                    // "{$promotion} ({$effect})" won't work as effect may contain [] and tr() does not support that kind of nesting
                    yield(FormattedLine(
                        "${promotion.tr()} (${effect.joinToString(",") { it.tr() }})",
                        link = "Promotion/$promotion", indent = 1 ))
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
            if (improvement.uniqueTo != name) continue

            yield(FormattedLine(improvement.name, link = "Improvement/${improvement.name}"))
            yield(FormattedLine(improvement.cloneStats().toString(), indent = 1))   // = (improvement as Stats).toString minus import plus copy overhead
            if (improvement.terrainsCanBeBuiltOn.isNotEmpty()) {
                improvement.terrainsCanBeBuiltOn.withIndex().forEach {
                    yield(FormattedLine(if (it.index == 0) "{Can be built on} {${it.value}}" else "or [${it.value}]",
                        link = "Terrain/${it.value}", indent = if (it.index == 0) 1 else 2))
                }
            }
            for (unique in improvement.uniques)
                yield(FormattedLine(unique, indent = 1))
        }
    }

}
