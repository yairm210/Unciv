package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GameParameters
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.Nation
import com.unciv.models.gamebasics.Translations
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.setFontColor

class NationTable(val nation: Nation, val newGameParameters: GameParameters, skin: Skin, width:Float, onClick:()->Unit): Table(skin){
    init {
        pad(10f)
        background= ImageGetter.getBackground(nation.getColor().apply { a = 0.5f })
        add(Label(nation.name.tr(), skin).apply { setFontColor(nation.getSecondaryColor())}).row()
        add(Label(getUniqueLabel(nation), skin).apply { setWrap(true);setFontColor(nation.getSecondaryColor())}).width(width)
        onClick { newGameParameters.nation=nation.name; onClick() }
        touchable= Touchable.enabled
        update()
    }

    private fun getUniqueLabel(nation: Nation): String {
        val textList = ArrayList<String>()

        if(nation.unique!=null) {
            textList += nation.unique!!.tr()
            textList += ""
        }

        for (building in GameBasics.Buildings.values)
            if (building.uniqueTo == nation.name) {
                val originalBuilding = GameBasics.Buildings[building.replaces!!]!!

                textList += building.name.tr() + " - {replaces} " + originalBuilding.name.tr()
                val originalBuildingStatMap = originalBuilding.toHashMap()
                for (stat in building.toHashMap())
                    if (stat.value != originalBuildingStatMap[stat.key])
                        textList += "  "+stat.key.toString().tr() +" "+stat.value.toInt() + " vs " + originalBuildingStatMap[stat.key]!!.toInt()
                for(unique in building.uniques.filter { it !in originalBuilding.uniques })
                    textList += "  "+unique.tr()
                if (building.maintenance != originalBuilding.maintenance)
                    textList += "  {Maintenance} " + building.maintenance + " vs " + originalBuilding.maintenance
                textList+=""
            }

        for (unit in GameBasics.Units.values)
            if (unit.uniqueTo == nation.name) {
                val originalUnit = GameBasics.Units[unit.replaces!!]!!

                textList += unit.name.tr() + " - {replaces} " + originalUnit.name.tr()
                if (unit.strength != originalUnit.strength)
                    textList += "  {Strength} " + unit.strength + " vs " + originalUnit.strength
                if (unit.rangedStrength!= originalUnit.rangedStrength)
                    textList+= "  {Ranged strength} " + unit.rangedStrength+ " vs " + originalUnit.rangedStrength
                if (unit.range!= originalUnit.range)
                    textList+= "  {Range} " + unit.range+ " vs " + originalUnit.range
                if (unit.movement!= originalUnit.movement)
                    textList+= "  {Movement} " + unit.movement+ " vs " + originalUnit.movement
                val newUniques = unit.uniques.filterNot { it in originalUnit.uniques }
                if(newUniques.isNotEmpty())
                    textList+="  {Uniques}: "+newUniques.joinToString{ Translations.translateBonusOrPenalty(it) }
                textList+=""
            }


        return textList.joinToString("\n").tr().trim()
    }


    fun update(){
        val color = nation.getColor()
        if(newGameParameters.nation!=nation.name) color.a=0.5f
        background= ImageGetter.getBackground(color)
    }
}