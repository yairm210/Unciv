package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.gamebasics.RuleSet
import com.unciv.models.gamebasics.Nation
import com.unciv.models.gamebasics.Translations
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.toLabel

class NationTable(val nation: Nation, width:Float, ruleSet: RuleSet)
    : Table(CameraStageBaseScreen.skin) {
    private val innerTable = Table()

    init {
        background = ImageGetter.getBackground(nation.getInnerColor())
        innerTable.pad(10f)
        innerTable.background = ImageGetter.getBackground(nation.getOuterColor())

        val titleTable = Table()
        titleTable.add(ImageGetter.getNationIndicator(nation, 50f)).pad(10f)
        titleTable.add(nation.getLeaderDisplayName().toLabel(nation.getInnerColor(),24))
        innerTable.add(titleTable).row()

        innerTable.add(getUniqueLabel(nation,ruleSet).apply { setWrap(true) }).width(width)
        touchable = Touchable.enabled
        add(innerTable)
    }

    private fun getUniqueLabel(nation: Nation, ruleSet: RuleSet): Label {
        val textList = ArrayList<String>()

        if (nation.unique != null) {
            textList += nation.unique!!.tr()
            textList += ""
        }

        addUniqueBuildingsText(nation, textList,ruleSet)
        addUniqueUnitsText(nation, textList,ruleSet)
        addUniqueImprovementsText(nation, textList,ruleSet)

        return textList.joinToString("\n").tr().trim().toLabel(nation.getInnerColor())
    }

    private fun addUniqueBuildingsText(nation: Nation, textList: ArrayList<String>, ruleSet: RuleSet) {
        for (building in ruleSet.Buildings.values
                .filter { it.uniqueTo == nation.name }) {
            val originalBuilding = ruleSet.Buildings[building.replaces!!]!!

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

    private fun addUniqueUnitsText(nation: Nation, textList: ArrayList<String>, ruleSet: RuleSet) {
        for (unit in ruleSet.Units.values
                .filter { it.uniqueTo == nation.name }) {
            val originalUnit = ruleSet.Units[unit.replaces!!]!!

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
                textList += "  " + promotion.tr() + " (" + Translations.translateBonusOrPenalty(ruleSet.UnitPromotions[promotion]!!.effect) + ")"

            textList += ""
        }
    }

    private fun addUniqueImprovementsText(nation: Nation, textList: ArrayList<String>, ruleSet: RuleSet) {
        for (improvement in ruleSet.TileImprovements.values
                .filter { it.uniqueTo == nation.name }) {

            textList += improvement.name.tr()
            textList += "  "+improvement.clone().toString()
            for(unique in improvement.uniques)
                textList += "  "+unique.tr()
        }
    }

}