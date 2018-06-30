package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.ui.List
import com.badlogic.gdx.utils.Align
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
        val iconSize = 25f//if you set this too low, there is a chance that the tables will be misaligned
        val padding = 5f

        val cityInfoTableIcons = Table(skin)
        cityInfoTableIcons.defaults().pad(padding).align(Align.center)

        cityInfoTableIcons.add(Label("Cities", skin).setFont(24)).colspan(8).align(Align.center).row()
        cityInfoTableIcons.add()
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Population")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Food")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Gold")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Science")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Production")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Culture")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Happiness")).size(iconSize)
        cityInfoTableIcons.pack()

        val cityInfoTableDetails = Table(skin)
        cityInfoTableDetails.defaults().pad(padding).minWidth(iconSize)//we need the min width so we can align the different tables

        for (city in civInfo.cities) {
            cityInfoTableDetails.add(city.name)

            val population = Label(city.population.population.toString(), skin)
            population.setAlignment(Align.center)
            cityInfoTableDetails.add(population)

            val food = Label(city.cityStats.currentCityStats.food.roundToInt().toString(), skin)
            food.setAlignment(Align.center)
            cityInfoTableDetails.add(food)

            val gold = Label(city.cityStats.currentCityStats.gold.roundToInt().toString(), skin)
            gold.setAlignment(Align.center)
            cityInfoTableDetails.add(gold)

            val science = Label(city.cityStats.currentCityStats.science.roundToInt().toString(), skin)
            science.setAlignment(Align.center)
            cityInfoTableDetails.add(science)

            val production = Label(city.cityStats.currentCityStats.production.roundToInt().toString(), skin)
            production.setAlignment(Align.center)
            cityInfoTableDetails.add(production)

            val culture = Label(city.cityStats.currentCityStats.culture.roundToInt().toString(), skin)
            culture.setAlignment(Align.center)
            cityInfoTableDetails.add(culture)

            val happiness = Label(city.cityStats.currentCityStats.happiness.roundToInt().toString(), skin)
            happiness.setAlignment(Align.center)
            cityInfoTableDetails.add(happiness)

            cityInfoTableDetails.row()
        }
        cityInfoTableDetails.pack()

        val cityInfoScrollPane = ScrollPane(cityInfoTableDetails)
        cityInfoScrollPane.pack()
        cityInfoScrollPane.setOverscroll(false, false)//I think it feels better with no overscroll

        val cityInfoTableTotal = Table(skin)
        cityInfoTableTotal.defaults().pad(padding).minWidth(iconSize)//we need the min width so we can align the different tables

        cityInfoTableTotal.add("Total")

        val population = Label(civInfo.cities.sumBy { it.population.population }.toString(), skin)
        population.setAlignment(Align.center)
        cityInfoTableTotal.add(population)

        cityInfoTableTotal.add()//an intended empty space

        val gold = Label(civInfo.cities.sumBy { it.cityStats.currentCityStats.gold.toInt() }.toString(), skin)
        gold.setAlignment(Align.center)
        cityInfoTableTotal.add(gold)

        val science = Label(civInfo.cities.sumBy { it.cityStats.currentCityStats.science.toInt() }.toString(), skin)
        science.setAlignment(Align.center)
        cityInfoTableTotal.add(science)

        cityInfoTableTotal.add()//an intended empty space

        val culture = Label(civInfo.cities.sumBy { it.cityStats.currentCityStats.culture.toInt() }.toString(), skin)
        culture.setAlignment(Align.center)
        cityInfoTableTotal.add(culture)

        val happiness = Label(civInfo.cities.sumBy {  it.cityStats.currentCityStats.happiness.toInt() }.toString(), skin)
        happiness.setAlignment(Align.center)
        cityInfoTableTotal.add(happiness)

        cityInfoTableTotal.pack()

        val table = Table(skin)
        //since the names of the cities are on the left, and the length of the names varies
        //we align every row to the right, coz we set the size of the other(number) cells to the image size
        //and thus, we can guarantee that the tables will be aligned
        table.defaults().pad(padding).align(Align.right)

        table.add(cityInfoTableIcons).row()
        val height = if(cityInfoTableDetails.rows > 0) cityInfoTableDetails.getRowHeight(0)*4f else 100f //if there are no cities, set the height of the scroll pane to 100
        table.add(cityInfoScrollPane).width(cityInfoTableDetails.width).height(height).row()
        table.add(cityInfoTableTotal)
        table.pack()

        return table
    }
}