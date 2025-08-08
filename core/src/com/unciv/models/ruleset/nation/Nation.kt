package com.unciv.models.ruleset.nation

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.squareBraceRegex
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.objectdescriptions.BaseUnitDescriptions
import com.unciv.ui.objectdescriptions.BuildingDescriptions
import com.unciv.ui.objectdescriptions.ImprovementDescriptions
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import yairm210.purity.annotations.Readonly
import kotlin.math.pow

class Nation : RulesetObject() {
    var leaderName = ""
    @Readonly
    fun getLeaderDisplayName() = if (isCityState || isSpectator) name
        else "[$leaderName] of [$name]"

    val style = ""
    @Readonly fun getStyleOrCivName() = style.ifEmpty { name }

    var cityStateType: String? = null
    var preferredVictoryType: String = Constants.neutralVictoryType

    /// The following all have audio hooks to play corresponding leader
    /// voice clips - named <civName>.<fieldName>, e.g. "America.defeated.ogg"
    /** Shown for AlertType.WarDeclaration, when other Civs declare war on a player */
    var declaringWar = ""
    /** Shown in DiplomacyScreen when a player declares war */
    var attacked = ""
    /** Shown for AlertType.Defeated */
    var defeated = ""
    /** Shown for MajorCivDiplomacyTable.getDenounceButton */
    var denounced = ""
    /** Shown for Declaration of Friendship */
    var declaringFriendship = ""
    /** Shown for AlertType.FirstContact */
    var introduction = ""
    /** Shown in TradePopup when other Civs initiate trade with a player */
    var tradeRequest = ""
    /** Shown in DiplomacyScreen when a player contacts another major civ with RelationshipLevel.Afraid or better */
    var neutralHello = ""
    /** Shown in DiplomacyScreen when a player contacts another major civ with RelationshipLevel.Enemy or worse */
    var hateHello = ""

    lateinit var outerColor: List<Int>
    var uniqueName = ""
    var uniqueText = ""
    var innerColor: List<Int>? = null
    var startBias = ArrayList<String>()
    var personality: String? = null

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
    private var outerColorObject = Color.WHITE // Not lateinit for unit tests
    fun getOuterColor(): Color = outerColorObject

    @Transient
    private var innerColorObject = Color.BLACK // Not lateinit for unit tests

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
        fun safeColorFromRGB(rgb: List<Int>) = if (rgb.size >= 3) colorFromRGB(rgb) else Color.PURPLE

        outerColorObject = safeColorFromRGB(outerColor)

        innerColorObject = if (innerColor == null) ImageGetter.CHARCOAL
                           else safeColorFromRGB(innerColor!!)

        forestsAndJunglesAreRoads = uniqueMap.hasUnique(UniqueType.ForestsAndJunglesAreRoads)
        ignoreHillMovementCost = uniqueMap.hasUnique(UniqueType.IgnoreHillMovementCost)
    }


    override fun makeLink() = "Nation/$name"
    override fun getSortGroup(ruleset: Ruleset) = when {
        isCityState -> 1
        isBarbarian -> 9
        else -> 0
    }

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        if (isCityState) textList += getCityStateInfo(ruleset)

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
            uniquesToCivilopediaTextLines(textList, leadingSeparator = null)
        }
        textList += FormattedLine()

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
        textList += FormattedLine("{Type}: {${cityStateType.name}}", header = 4, color = "#"+cityStateType.getColor().toString())

        var showResources = false

        fun addBonusLines(header: String, uniqueMap: UniqueMap) {
            // Note: Using getCityStateBonuses would be nice, but it's bound to a CityStateFunctions instance without even using `this`.
            // Too convoluted to reuse that here - but feel free to refactor that into a static.
            val bonuses = uniqueMap.getAllUniques().filterNot { it.isHiddenToUsers() }
            if (bonuses.none()) return
            textList += FormattedLine()
            textList += FormattedLine("{$header} ")
            for (unique in bonuses) {
                textList += FormattedLine(unique, indent = 1)
                if (unique.type == UniqueType.CityStateUniqueLuxury) showResources = true
            }
        }

        addBonusLines("When Friends:", cityStateType.friendBonusUniqueMap)
        addBonusLines("When Allies:", cityStateType.allyBonusUniqueMap)

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
        textList += FormattedLine(separator = true)

        // personality is not a nation property, it gets assigned to the civ randomly
        return textList
    }

    private fun getUniqueBuildingsText(ruleset: Ruleset) = sequence {
        for (building in ruleset.buildings.values) {
            if (building.uniqueTo == null) continue
            if (!matchesFilter(building.uniqueTo!!)) continue
            if (building.isHiddenFromCivilopedia(ruleset)) continue
            yield(FormattedLine(separator = true))
            yield(FormattedLine("{${building.name}} -", link = building.makeLink()))
            if (building.replaces != null && ruleset.buildings.containsKey(building.replaces!!)) {
                val originalBuilding = ruleset.buildings[building.replaces!!]!!
                yield(FormattedLine("Replaces [${originalBuilding.name}]", link = originalBuilding.makeLink(), indent = 1))
                yieldAll(BuildingDescriptions.getDifferences(originalBuilding, building))
                yield(FormattedLine())
            } else if (building.replaces != null) {
                yield(FormattedLine("Replaces [${building.replaces}], which is not found in the ruleset!", indent = 1))
            } else {
                yield(FormattedLine(building.getShortDescription(true), indent = 1))
            }
        }
    }

    private fun getUniqueUnitsText(ruleset: Ruleset) = sequence {
        for (unit in ruleset.units.values) {
            if (unit.isHiddenFromCivilopedia(ruleset)) continue
            if (unit.uniqueTo == null || !matchesFilter(unit.uniqueTo!!)) continue
            yield(FormattedLine(separator = true))
            yield(FormattedLine("{${unit.name}} -", link = "Unit/${unit.name}"))
            if (unit.replaces != null && ruleset.units.containsKey(unit.replaces!!)) {
                val originalUnit = ruleset.units[unit.replaces!!]!!
                yield(FormattedLine("Replaces [${originalUnit.name}]", link = "Unit/${originalUnit.name}", indent = 1))
                if (unit.cost != originalUnit.cost)
                    yield(FormattedLine("{Cost} ".tr() + "[${unit.cost}] vs [${originalUnit.cost}]".tr(), indent = 1))
                yieldAll(
                    BaseUnitDescriptions.getDifferences(ruleset, originalUnit, unit)
                    .map { (text, link) -> FormattedLine(text, link = link ?: "", indent = 1) }
                )
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
            if (improvement.isHiddenFromCivilopedia(ruleset)) continue
            if (improvement.uniqueTo == null || !matchesFilter(improvement.uniqueTo!!)) continue

            yield(FormattedLine(separator = true))
            yield(FormattedLine(improvement.name, link = "Improvement/${improvement.name}"))
            yield(FormattedLine(improvement.cloneStats().toString(), indent = 1))   // = (improvement as Stats).toString minus import plus copy overhead
            if (improvement.replaces != null && ruleset.tileImprovements.containsKey(improvement.replaces!!)) {
                val originalImprovement = ruleset.tileImprovements[improvement.replaces!!]!!
                yield(FormattedLine("Replaces [${originalImprovement.name}]", link = originalImprovement.makeLink(), indent = 1))
                yieldAll(ImprovementDescriptions.getDifferences(ruleset, originalImprovement, improvement))
                yield(FormattedLine())
            } else if (improvement.replaces != null) {
                yield(FormattedLine("Replaces [${improvement.replaces}], which is not found in the ruleset!", indent = 1))
            } else {
                yieldAll(improvement.getShortDecription())
            }
        }
    }

    @Readonly
    fun matchesFilter(filter: String, state: GameContext? = null, multiFilter: Boolean = true): Boolean {
        // Todo: Add 'multifilter=false' option to Multifilter itself to cut down on duplicate code
        return if (multiFilter) MultiFilter.multiFilter(filter, {
            matchesSingleFilter(filter) ||
                state != null && hasUnique(it, state) ||
                state == null && hasTagUnique(it)
        })
        else matchesSingleFilter(filter) ||
            state != null && hasUnique(filter, state) ||
            state == null && hasTagUnique(filter)
    }

    @Readonly
    private fun matchesSingleFilter(filter: String): Boolean {
        // All cases are compile-time constants, for performance
        return when (filter) {
            "All", "all" -> true
            "Major" -> isMajorCiv
            Constants.cityStates, "City-State" -> isCityState
            else -> filter == name
        }
    }
}


/** All defined by https://www.w3.org/TR/WCAG20/#relativeluminancedef */
fun getRelativeLuminance(color: Color): Double {
    fun getRelativeChannelLuminance(channel: Float): Double =
            if (channel < 0.03928) channel / 12.92
            else ((channel + 0.055) / 1.055).pow(2.4)

    val r = getRelativeChannelLuminance(color.r)
    val g = getRelativeChannelLuminance(color.g)
    val b = getRelativeChannelLuminance(color.b)

    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** https://www.w3.org/TR/WCAG20/#contrast-ratiodef */
fun getContrastRatio(color1: Color, color2: Color): Double { // ratio can range from 1 to 21
    val innerColorLuminance = getRelativeLuminance(color1)
    val outerColorLuminance = getRelativeLuminance(color2)

    return if (innerColorLuminance > outerColorLuminance)
        (innerColorLuminance + 0.05) / (outerColorLuminance + 0.05)
        else (outerColorLuminance + 0.05) / (innerColorLuminance + 0.05)
}
