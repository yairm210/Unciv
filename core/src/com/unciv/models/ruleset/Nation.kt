package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.civilization.CityStateType
import com.unciv.models.stats.INamed
import com.unciv.models.translations.Translations
import com.unciv.models.translations.tr
import com.unciv.ui.utils.colorFromRGB

enum class VictoryType{
    Neutral,
    Cultural,
    Domination,
    Scientific
}

class Nation : INamed {
    override lateinit var name: String
    var translatedName=""
    fun getNameTranslation(): String {
        if(translatedName!="") return translatedName
        else return name
    }

    var leaderName=""
    fun getLeaderDisplayName() = if(isCityState()) getNameTranslation()
        else "[$leaderName] of [${getNameTranslation()}]"

    var cityStateType: CityStateType?=null
    var preferredVictoryType:VictoryType = VictoryType.Neutral
    var declaringWar=""
    var attacked=""
    var defeated=""
    var introduction=""
    var tradeRequest=""

    var neutralHello=""
    var hateHello=""

    var neutralLetsHearIt = ArrayList<String>()
    var neutralYes = ArrayList<String>()
    var neutralNo = ArrayList<String>()

    var hateLetsHearIt = ArrayList<String>()
    var hateYes = ArrayList<String>()
    var hateNo = ArrayList<String>()

    var afterPeace=""

    lateinit var outerColor: List<Int>
    var unique:String?=null
    var innerColor: List<Int>?=null
    var startBias = ArrayList<String>()

    @Transient private lateinit var outerColorObject:Color
    fun getOuterColor(): Color = outerColorObject

    @Transient private lateinit var innerColorObject:Color

    fun getInnerColor(): Color = innerColorObject

    fun isCityState()= cityStateType != null
    fun isMajorCiv() = !isBarbarian() && !isCityState()
    fun isBarbarian() = name=="Barbarians"

    // This is its own transient because we'll need to check this for every tile-to-tile movement which is harsh
    @Transient var forestsAndJunglesAreRoads = false

    fun setTransients(){
        outerColorObject = colorFromRGB(outerColor[0], outerColor[1], outerColor[2])

        if(innerColor==null) innerColorObject = Color.BLACK
        else innerColorObject = colorFromRGB(innerColor!![0], innerColor!![1], innerColor!![2])

        if(unique == "All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel.")
            forestsAndJunglesAreRoads = true
    }

    lateinit var cities: List<String>




    fun getUniqueString(ruleset: Ruleset): String {
        val textList = ArrayList<String>()

        if (unique != null) {
            textList += unique!!.tr()
            textList += ""
        }

        addUniqueBuildingsText(textList,ruleset)
        addUniqueUnitsText(textList,ruleset)
        addUniqueImprovementsText(textList,ruleset)

        return textList.joinToString("\n").tr().trim()
    }

    private fun addUniqueBuildingsText(textList: ArrayList<String>, ruleset: Ruleset) {
        for (building in ruleset.buildings.values
                .filter { it.uniqueTo == name }) {
            val originalBuilding = ruleset.buildings[building.replaces!!]!!

            textList += building.name.tr() + " - {replaces} " + originalBuilding.name.tr()
            val originalBuildingStatMap = originalBuilding.toHashMap()
            for (stat in building.toHashMap())
                if (stat.value != originalBuildingStatMap[stat.key])
                    textList += "  " + stat.key.toString().tr() + " " + stat.value.toInt() + " vs " + originalBuildingStatMap[stat.key]!!.toInt()

            for (unique in building.uniques.filter { it !in originalBuilding.uniques })
                textList += "  " + unique.tr()
            if (building.maintenance != originalBuilding.maintenance)
                textList += "  {Maintenance} " + building.maintenance + " vs " + originalBuilding.maintenance
            if (building.cost != originalBuilding.cost)
                textList += "  {Cost} " + building.cost + " vs " + originalBuilding.cost
            if (building.cityStrength != originalBuilding.cityStrength)
                textList += "  {City strength} " + building.cityStrength + " vs " + originalBuilding.cityStrength
            if (building.cityHealth != originalBuilding.cityHealth)
                textList += "  {City health} " + building.cityHealth + " vs " + originalBuilding.cityHealth
            textList += ""
        }
    }

    private fun addUniqueUnitsText(textList: ArrayList<String>, ruleset: Ruleset) {
        for (unit in ruleset.units.values
                .filter { it.uniqueTo == name }) {
            val originalUnit = ruleset.units[unit.replaces!!]!!

            textList += unit.name.tr() + " - {replaces} " + originalUnit.name.tr()
            if (unit.cost != originalUnit.cost)
                textList += "  {Cost} " + unit.cost + " vs " + originalUnit.cost
            if (unit.strength != originalUnit.strength)
                textList += "  {Strength} " + unit.strength + " vs " + originalUnit.strength
            if (unit.rangedStrength != originalUnit.rangedStrength)
                textList += "  {Ranged strength} " + unit.rangedStrength + " vs " + originalUnit.rangedStrength
            if (unit.range != originalUnit.range)
                textList += "  {Range} " + unit.range + " vs " + originalUnit.range
            if (unit.movement != originalUnit.movement)
                textList += "  {Movement} " + unit.movement + " vs " + originalUnit.movement
            if (originalUnit.requiredResource != null && unit.requiredResource == null)
                textList += "  " + "[${originalUnit.requiredResource}] not required".tr()
            for (unique in unit.uniques.filterNot { it in originalUnit.uniques })
                textList += "  " + Translations.translateBonusOrPenalty(unique)
            for (unique in originalUnit.uniques.filterNot { it in unit.uniques })
                textList += "  " + "Lost ability".tr() + "(vs " + originalUnit.name.tr() + "): " + Translations.translateBonusOrPenalty(unique)
            for (promotion in unit.promotions.filter { it !in originalUnit.promotions })
                textList += "  " + promotion.tr() + " (" + Translations.translateBonusOrPenalty(ruleset.unitPromotions[promotion]!!.effect) + ")"

            textList += ""
        }
    }

    private fun addUniqueImprovementsText(textList: ArrayList<String>, ruleset: Ruleset) {
        for (improvement in ruleset.tileImprovements.values
                .filter { it.uniqueTo == name }) {

            textList += improvement.name.tr()
            textList += "  "+improvement.clone().toString()
            for(unique in improvement.uniques)
                textList += "  "+unique.tr()
        }
    }
}
