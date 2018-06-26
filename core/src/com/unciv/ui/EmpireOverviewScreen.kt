package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.utils.*
import kotlin.math.roundToInt

class EmpireOverviewScreen : CameraStageBaseScreen(){
    init {
        val civInfo = UnCivGame.Current.gameInfo.getPlayerCivilization()

        val closeButton = TextButton("Close".tr(), skin)
        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        stage.addActor(closeButton)

        val table=Table()
        table.defaults().pad(20f)
        table.add(getCityInfoTable(civInfo))
        table.add(getHappinessTable(civInfo))
        table.add(getGoldTable(civInfo))
        table.center(stage)
        stage.addActor(table)
    }



    private fun getHappinessTable(civInfo: CivilizationInfo): Table {
        val happinessTable = Table(skin)
        happinessTable.defaults().pad(5f)
        happinessTable.add(Label("Happiness", skin).setFont(24)).colspan(2).row()
        for (entry in civInfo.getHappinessForNextTurn()) {
            happinessTable.add(entry.key)
            happinessTable.add(entry.value.toString()).row()
        }
        happinessTable.add("Total")
        happinessTable.add(civInfo.getHappinessForNextTurn().values.sum().toString())
        happinessTable.pack()
        return happinessTable
    }

    private fun getGoldTable(civInfo: CivilizationInfo): Table {
        val goldTable = Table(skin)
        goldTable.defaults().pad(5f)
        goldTable.add(Label("Gold", skin).setFont(24)).colspan(2).row()
        var total=0f
        for (entry in civInfo.getStatsForNextTurn()) {
            if(entry.value.gold==0f) continue
            goldTable.add(entry.key)
            goldTable.add(entry.value.gold.toString()).row()
            total += entry.value.gold
        }
        goldTable.add("Total")
        goldTable.add(total.toString())
        goldTable.pack()
        return goldTable
    }

    private fun getCityInfoTable(civInfo: CivilizationInfo): Table {
        val cityInfotable = Table()
        cityInfotable.skin = skin
        cityInfotable.defaults().pad(5f)
        cityInfotable.add(Label("Cities", skin).setFont(24)).colspan(8).row()
        cityInfotable.add()
        cityInfotable.add(ImageGetter.getStatIcon("Population")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Food")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Gold")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Science")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Production")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Culture")).size(20f)
        cityInfotable.add(ImageGetter.getStatIcon("Happiness")).size(20f).row()

        for (city in civInfo.cities) {
            cityInfotable.add(city.name)
            cityInfotable.add(city.population.population.toString())
            cityInfotable.add(city.cityStats.currentCityStats.food.roundToInt().toString())
            cityInfotable.add(city.cityStats.currentCityStats.gold.roundToInt().toString())
            cityInfotable.add(city.cityStats.currentCityStats.science.roundToInt().toString())
            cityInfotable.add(city.cityStats.currentCityStats.production.roundToInt().toString())
            cityInfotable.add(city.cityStats.currentCityStats.culture.roundToInt().toString())
            cityInfotable.add(city.cityStats.currentCityStats.happiness.roundToInt().toString()).row()
        }
        cityInfotable.add("Total")
        cityInfotable.add(civInfo.cities.sumBy { it.population.population }.toString())
        cityInfotable.add("")
        cityInfotable.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.gold.toInt() }.toString())
        cityInfotable.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.science.toInt() }.toString())
        cityInfotable.add("")
        cityInfotable.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.culture.toInt() }.toString())
        cityInfotable.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.happiness.toInt() }.toString())

        cityInfotable.pack()
        return cityInfotable
    }
}