 package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CityStateType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.stats.INamed
import com.unciv.models.translations.squareBraceRegex
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.colorFromRGB

enum class VictoryType {
    Neutral,
    Cultural,
    Diplomatic,
    Domination,
    Scientific,
}

class Nation : INamed, ICivilopediaText, IHasUniques {
    override lateinit var name: String

    var leaderName = ""
    fun getLeaderDisplayName() = if (isCityState()) name
    else "[$leaderName] of [$name]"

    val style = ""
    var cityStateType: CityStateType? = null
    var preferredVictoryType: VictoryType = VictoryType.Neutral
    var declaringWar = ""
    var attacked = ""
    var defeated = ""
    var introduction = ""
    var tradeRequest = ""

    var neutralHello = ""
    var hateHello = ""

    lateinit var outerColor: List<Int>
    var uniqueName = ""
    override var uniques = ArrayList<String>()
    override val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
    var uniqueText = ""
    var innerColor: List<Int>? = null
    var startBias = ArrayList<String>()
    
    var startIntroPart1 = ""
    var startIntroPart2 = ""

    /* Properties present in json but not yet implemented:
    var adjective = ArrayList<String>()
     */

    override var civilopediaText = listOf<FormattedLine>()
    
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

    @Transient
    var disembarkCosts1 = false

    fun setTransients() {
        outerColorObject = colorFromRGB(outerColor)

        innerColorObject = if (innerColor == null) Color.BLACK
                           else colorFromRGB(innerColor!!)

        forestsAndJunglesAreRoads = uniques.contains("All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel.")
        ignoreHillMovementCost = uniques.contains("Units ignore terrain costs when moving into any tile with Hills")
        disembarkCosts1 = uniques.contains("Units pay only 1 movement point to disembark")
    }

    var cities: ArrayList<String> = arrayListOf()


    /** Used only by NewGame Nation picker */
    fun getUniqueString(ruleset: Ruleset): String {
        val textList = ArrayList<String>()

        if (uniqueName != "") textList += uniqueName.tr() + ":"
        if (uniqueText != "") {
            textList += " " + uniqueText.tr()
        } else {
            textList += "  " + uniques.joinToString(", ") { it.tr() }
            textList += ""
        }

        if (startBias.isNotEmpty()) {
            textList += "Start bias:".tr() + startBias.joinToString(", ", " ") { it.tr() }
            textList += ""
        }
        addUniqueBuildingsText(textList, ruleset)
        addUniqueUnitsText(textList, ruleset)
        addUniqueImprovementsText(textList, ruleset)

        return textList.joinToString("\n")
    }

    private fun addUniqueBuildingsText(textList: ArrayList<String>, ruleset: Ruleset) {
        for (building in ruleset.buildings.values
                .filter { it.uniqueTo == name && Constants.hideFromCivilopediaUnique !in it.uniques }) {
            if (building.replaces != null && ruleset.buildings.containsKey(building.replaces!!)) {
                val originalBuilding = ruleset.buildings[building.replaces!!]!!

                textList += building.name.tr() + " - " + "Replaces [${originalBuilding.name}]".tr()
                for ((key, value) in building)
                    if (value != originalBuilding[key])
                        textList += "  " + key.name.tr() + " " + "[${value.toInt()}] vs [${originalBuilding[key].toInt()}]".tr()

                for (unique in building.uniques.filter { it !in originalBuilding.uniques })
                    textList += "  " + unique.tr()
                if (building.maintenance != originalBuilding.maintenance)
                    textList += "  {Maintenance} " + "[${building.maintenance}] vs [${originalBuilding.maintenance}]".tr()
                if (building.cost != originalBuilding.cost)
                    textList += "  {Cost} " + "[${building.cost}] vs [${originalBuilding.cost}]".tr()
                if (building.cityStrength != originalBuilding.cityStrength)
                    textList += "  {City strength} " + "[${building.cityStrength}] vs [${originalBuilding.cityStrength}]".tr()
                if (building.cityHealth != originalBuilding.cityHealth)
                    textList += "  {City health} " + "[${building.cityHealth}] vs [${originalBuilding.cityHealth}]".tr()
                textList += ""
            } else if (building.replaces != null) {
                textList += building.name.tr() + " - " + "Replaces [${building.replaces}], which is not found in the ruleset!".tr()
            } else textList += building.getShortDescription(ruleset)
        }
    }

    private fun addUniqueUnitsText(textList: ArrayList<String>, ruleset: Ruleset) {
        for (unit in ruleset.units.values
                .filter { it.uniqueTo == name && Constants.hideFromCivilopediaUnique !in it.uniques }) {
            if (unit.replaces != null && ruleset.units.containsKey(unit.replaces!!)) {
                val originalUnit = ruleset.units[unit.replaces!!]!!
                textList += unit.name.tr() + " - " + "Replaces [${originalUnit.name}]".tr()
                if (unit.cost != originalUnit.cost)
                    textList += "  {Cost} " + "[${unit.cost}] vs [${originalUnit.cost}]".tr()
                if (unit.strength != originalUnit.strength)
                    textList += "  ${Fonts.strength} " + "[${unit.strength}] vs [${originalUnit.strength}]".tr()
                if (unit.rangedStrength != originalUnit.rangedStrength)
                    textList += "  ${Fonts.rangedStrength} " + "[${unit.rangedStrength}] vs [${originalUnit.rangedStrength}]".tr()
                if (unit.range != originalUnit.range)
                    textList += "  ${Fonts.range} " + "[${unit.range}] vs [${originalUnit.range}]".tr()
                if (unit.movement != originalUnit.movement)
                    textList += "  ${Fonts.movement} " + "[${unit.movement}] vs [${originalUnit.movement}]".tr()
                for (resource in originalUnit.getResourceRequirements().keys)
                    if (!unit.getResourceRequirements().containsKey(resource))
                        textList += "  " + "[$resource] not required".tr()
                for (unique in unit.uniques.filterNot { it in originalUnit.uniques })
                    textList += "  " + unique.tr()
                for (unique in originalUnit.uniques.filterNot { it in unit.uniques })
                    textList += "  " + "Lost ability".tr() + "(" + "vs [${originalUnit.name}]".tr() + "): " + unique.tr()
                for (promotion in unit.promotions.filter { it !in originalUnit.promotions })
                    textList += "  " + promotion.tr() + " (" + ruleset.unitPromotions[promotion]!!.uniquesWithEffect().joinToString(",") { it.tr() } + ")"
            } else if (unit.replaces != null) {
                textList += unit.name.tr() + " - " + "Replaces [${unit.replaces}], which is not found in the ruleset!".tr()
            } else {
                textList += unit.name.tr()
                textList += "  " + unit.getDescription().split("\n").joinToString("\n  ")
            }

            textList += ""
        }
    }

    private fun addUniqueImprovementsText(textList: ArrayList<String>, ruleset: Ruleset) {
        for (improvement in ruleset.tileImprovements.values
                .filter { it.uniqueTo == name }) {

            textList += improvement.name.tr()
            textList += "  " + improvement.clone().toString()
            for (unique in improvement.uniques)
                textList += "  " + unique.tr()
        }
    }

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
        addUniqueBuildingsText(textList, ruleset)
        addUniqueUnitsText(textList, ruleset)
        addUniqueImprovementsText(textList, ruleset)

        return textList
    }

    private fun getCityStateInfo(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        textList += FormattedLine("Type: [$cityStateType]", header = 4, color = cityStateType!!.color)
        val viewingCiv = UncivGame.Current.gameInfo.currentPlayerCiv
        val era = viewingCiv.getEra()
        var showResources = false

        val friendBonus = era.friendBonus[cityStateType!!.name]
        if (friendBonus != null && friendBonus.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("When Friends: ")
            friendBonus.forEach {
                textList += FormattedLine(Unique(it), indent = 1)
                if (it == "Provides a unique luxury") showResources = true
            }
        }

        val allyBonus = era.allyBonus[cityStateType!!.name]
        if (allyBonus != null && allyBonus.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("When Allies: ")
            allyBonus.forEach {
                textList += FormattedLine(Unique(it), indent = 1)
                if (it == "Provides a unique luxury") showResources = true
            }
        }

        if (showResources) {
            val allMercantileResources = ruleset.tileResources.values
                .filter { it.unique == "Can only be created by Mercantile City-States" // Deprecated 3.16.16
                        || it.uniques.contains("Can only be created by Mercantile City-States") }

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

    @JvmName("addUniqueBuildingsText1")  // These overloads are too similar - but I hope to remove the other one soon
    private fun addUniqueBuildingsText(textList: ArrayList<FormattedLine>, ruleset: Ruleset) {
        for (building in ruleset.buildings.values) {
            if (building.uniqueTo != name || Constants.hideFromCivilopediaUnique in building.uniques) continue
            textList += FormattedLine("{${building.name}} -", link=building.makeLink())
            if (building.replaces != null && ruleset.buildings.containsKey(building.replaces!!)) {
                val originalBuilding = ruleset.buildings[building.replaces!!]!!
                textList += FormattedLine("Replaces [${originalBuilding.name}]", link=originalBuilding.makeLink(), indent=1)

                for ((key, value) in building)
                    if (value != originalBuilding[key])
                        textList += FormattedLine( key.name.tr() + " " +"[${value.toInt()}] vs [${originalBuilding[key].toInt()}]".tr(), indent=1)

                for (unique in building.uniques.filter { it !in originalBuilding.uniques })
                    textList += FormattedLine(unique, indent=1)
                if (building.maintenance != originalBuilding.maintenance)
                    textList += FormattedLine("{Maintenance} ".tr() + "[${building.maintenance}] vs [${originalBuilding.maintenance}]".tr(), indent=1)
                if (building.cost != originalBuilding.cost)
                    textList += FormattedLine("{Cost} ".tr() + "[${building.cost}] vs [${originalBuilding.cost}]".tr(), indent=1)
                if (building.cityStrength != originalBuilding.cityStrength)
                    textList += FormattedLine("{City strength} ".tr() + "[${building.cityStrength}] vs [${originalBuilding.cityStrength}]".tr(), indent=1)
                if (building.cityHealth != originalBuilding.cityHealth)
                    textList += FormattedLine("{City health} ".tr() + "[${building.cityHealth}] vs [${originalBuilding.cityHealth}]".tr(), indent=1)
                textList += FormattedLine()
            } else if (building.replaces != null) {
                textList += FormattedLine("Replaces [${building.replaces}], which is not found in the ruleset!", indent=1)
            } else {
                textList += FormattedLine(building.getShortDescription(ruleset), indent=1)
            }
        }
    }

    @JvmName("addUniqueUnitsText1")
    private fun addUniqueUnitsText(textList: ArrayList<FormattedLine>, ruleset: Ruleset) {
        for (unit in ruleset.units.values) {
            if (unit.uniqueTo != name || Constants.hideFromCivilopediaUnique in unit.uniques) continue
            textList += FormattedLine("{${unit.name}} -", link="Unit/${unit.name}")
            if (unit.replaces != null && ruleset.units.containsKey(unit.replaces!!)) {
                val originalUnit = ruleset.units[unit.replaces!!]!!
                textList += FormattedLine("Replaces [${originalUnit.name}]", link="Unit/${originalUnit.name}", indent=1)
                if (unit.cost != originalUnit.cost)
                    textList += FormattedLine("{Cost} ".tr() + "[${unit.cost}] vs [${originalUnit.cost}]".tr(), indent=1)
                if (unit.strength != originalUnit.strength)
                    textList += FormattedLine("${Fonts.strength} " + "[${unit.strength}] vs [${originalUnit.strength}]".tr(), indent=1)
                if (unit.rangedStrength != originalUnit.rangedStrength)
                    textList += FormattedLine("${Fonts.rangedStrength} " + "[${unit.rangedStrength}] vs [${originalUnit.rangedStrength}]".tr(), indent=1)
                if (unit.range != originalUnit.range)
                    textList += FormattedLine("${Fonts.range} " + "[${unit.range}] vs [${originalUnit.range}]".tr(), indent=1)
                if (unit.movement != originalUnit.movement)
                    textList += FormattedLine("${Fonts.movement} " + "[${unit.movement}] vs [${originalUnit.movement}]".tr(), indent=1)
                for (resource in originalUnit.getResourceRequirements().keys)
                    if (!unit.getResourceRequirements().containsKey(resource)) {
                        textList += FormattedLine("[$resource] not required", link="Resource/$resource", indent=1)
                    }
                // This does not use the auto-linking FormattedLine(Unique) for two reasons:
                // would look a little chaotic as unit uniques unlike most uniques are a HashSet and thus do not preserve order
                // No .copy() factory on FormattedLine and no FormattedLine(Unique, all other val's) constructor either
                for (unique in unit.uniques.filterNot { it in originalUnit.uniques })
                    textList += FormattedLine(unique, indent=1)
                for (unique in originalUnit.uniques.filterNot { it in unit.uniques })
                    textList += FormattedLine("Lost ability".tr() + " (" + "vs [${originalUnit.name}]".tr() + "): " +
                            unique.tr(), indent=1)
                for (promotion in unit.promotions.filter { it !in originalUnit.promotions }) {
                    val effect = ruleset.unitPromotions[promotion]!!.uniquesWithEffect()
                    // "{$promotion} ({$effect})" won't work as effect may contain [] and tr() does not support that kind of nesting
                    textList += FormattedLine(
                        "${promotion.tr()} (${effect.joinToString(",") { it.tr() }})",
                        link = "Promotion/$promotion", indent = 1 )
                }
            } else if (unit.replaces != null) {
                textList += FormattedLine("Replaces [${unit.replaces}], which is not found in the ruleset!", indent = 1)
            } else {
                textList += unit.getCivilopediaTextLines(ruleset).map {
                    FormattedLine(it.text, link = it.link, indent = it.indent + 1, color = it.color)
                }
            }

            textList += FormattedLine()
        }
    }

    @JvmName("addUniqueImprovementsText1")
    private fun addUniqueImprovementsText(textList: ArrayList<FormattedLine>, ruleset: Ruleset) {
        for (improvement in ruleset.tileImprovements.values) {
            if (improvement.uniqueTo != name ) continue

            textList += FormattedLine(improvement.name, link = "Improvement/${improvement.name}")
            textList += FormattedLine(improvement.clone().toString(), indent = 1)   // = (improvement as Stats).toString minus import plus copy overhead
            if (improvement.terrainsCanBeBuiltOn.isNotEmpty()) {
                improvement.terrainsCanBeBuiltOn.withIndex().forEach {
                    textList += FormattedLine(if (it.index == 0) "{Can be built on} {${it.value}}" else "or [${it.value}]",
                        link = "Terrain/${it.value}", indent = if (it.index == 0) 1 else 2)
                }
            }
            for (unique in improvement.uniques)
                textList += FormattedLine(unique, indent = 1)
        }
    }

}
