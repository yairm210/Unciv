package com.unciv.ui.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.utils.*
import kotlin.math.max
import kotlin.math.roundToInt

class CityOverviewTable(val viewingPlayer: CivilizationInfo, val overviewScreen: EmpireOverviewScreen): Table() {
    init {
        val iconSize = 50f//if you set this too low, there is a chance that the tables will be misaligned
        val padding = 5f
        var sortedBy = "City"

        val cityInfoTableDetails = Table()
        cityInfoTableDetails.defaults().pad(padding).minWidth(iconSize).align(Align.left)//we need the min width so we can align the different tables

        fun sortOnClick(iconName: String) {
            val descending = sortedBy == iconName
            sortedBy = iconName
            // sort the table: clear and fill with sorted data
            cityInfoTableDetails.clear()
            fillCitiesTable(cityInfoTableDetails, iconName, descending)
            // reset to return back for ascending next time
            if (descending) sortedBy = ""
        }

        val cityInfoTableIcons = Table(skin)
        cityInfoTableIcons.defaults().pad(padding).align(Align.center)

        cityInfoTableIcons.add("Cities".toLabel(fontSize = 24)).colspan(8).align(Align.center).row()

        val citySortIcon = ImageGetter.getUnitIcon("Settler").surroundWithCircle(iconSize)
        citySortIcon.onClick { sortOnClick("City") }
        cityInfoTableIcons.add(citySortIcon).align(Align.left)

        val columnsNames = arrayListOf("Population", "Food", "Gold", "Science", "Production", "Culture", "Happiness")
        for (name in columnsNames) {
            val icon = ImageGetter.getStatIcon(name)
            icon.onClick { sortOnClick(name) }
            cityInfoTableIcons.add(icon).size(iconSize)
        }
        cityInfoTableIcons.pack()

        fillCitiesTable(cityInfoTableDetails, "City", false)

        val cityInfoScrollPane = AutoScrollPane(cityInfoTableDetails)
        cityInfoScrollPane.pack()
        cityInfoScrollPane.setOverscroll(false, false)//I think it feels better with no overscroll

        val cityInfoTableTotal = Table(skin)
        cityInfoTableTotal.defaults().pad(padding).minWidth(iconSize)//we need the min width so we can align the different tables

        cityInfoTableTotal.add("Total".toLabel())
        cityInfoTableTotal.add(viewingPlayer.cities.sumBy { it.population.population }.toString().toLabel())
        cityInfoTableTotal.add()//an intended empty space
        cityInfoTableTotal.add(viewingPlayer.cities.sumBy { it.cityStats.currentCityStats.gold.toInt() }.toLabel())
        cityInfoTableTotal.add(viewingPlayer.cities.sumBy { it.cityStats.currentCityStats.science.toInt() }.toLabel())
        cityInfoTableTotal.add()//an intended empty space
        cityInfoTableTotal.add(viewingPlayer.cities.sumBy { it.cityStats.currentCityStats.culture.toInt() }.toLabel())
        cityInfoTableTotal.add(viewingPlayer.cities.sumBy { it.cityStats.currentCityStats.happiness.toInt() }.toLabel())

        cityInfoTableTotal.pack()

        val table = Table(skin)
        //since the names of the cities are on the left, and the length of the names varies
        //we align every row to the right, coz we set the size of the other(number) cells to the image size
        //and thus, we can guarantee that the tables will be aligned
        table.defaults().pad(padding).align(Align.right)

        // place the button for sorting by city name on top of the cities names
        citySortIcon.width = max(iconSize, cityInfoTableDetails.width - (iconSize + padding) * 8)

        table.add(cityInfoTableIcons).row()
        table.add(cityInfoScrollPane).width(cityInfoTableDetails.width).row()
        table.add(cityInfoTableTotal)
        table.pack()
        add(table)
    }

    private fun fillCitiesTable(citiesTable: Table, sortType: String, descending: Boolean) {

        val sorter = Comparator { city2, city1: CityInfo ->
            when (sortType) {
                "Population" -> city1.population.population - city2.population.population
                "Food" -> city1.cityStats.currentCityStats.food.compareTo(city2.cityStats.currentCityStats.food)
                "Gold" -> (city1.cityStats.currentCityStats.gold - city2.cityStats.currentCityStats.gold).toInt()
                "Science" -> (city1.cityStats.currentCityStats.science - city2.cityStats.currentCityStats.science).toInt()
                "Production" -> (city1.cityStats.currentCityStats.production - city2.cityStats.currentCityStats.production).toInt()
                "Culture" -> (city1.cityStats.currentCityStats.culture - city2.cityStats.currentCityStats.culture).toInt()
                "Happiness" -> (city1.cityStats.currentCityStats.happiness - city2.cityStats.currentCityStats.happiness).toInt()
                else -> city2.name.compareTo(city1.name)
            }
        }

        var cityList = viewingPlayer.cities.sortedWith(sorter)

        if (descending)
            cityList = cityList.reversed()

        for (city in cityList) {
            val button = Button(city.name.toLabel(), CameraStageBaseScreen.skin)
            button.onClick {
                overviewScreen.game.setScreen(CityScreen(city))
            }
            citiesTable.add(button)
            citiesTable.add(city.cityConstructions.getCityProductionTextForCityButton().toLabel())
            citiesTable.add(city.population.population.toLabel()).align(Align.center)
            citiesTable.add(city.cityStats.currentCityStats.food.roundToInt().toLabel()).align(Align.center)
            citiesTable.add(city.cityStats.currentCityStats.gold.roundToInt().toLabel()).align(Align.center)
            citiesTable.add(city.cityStats.currentCityStats.science.roundToInt().toLabel()).align(Align.center)
            citiesTable.add(city.cityStats.currentCityStats.production.roundToInt().toLabel()).align(Align.center)
            citiesTable.add(city.cityStats.currentCityStats.culture.roundToInt().toLabel()).align(Align.center)
            citiesTable.add(city.cityStats.currentCityStats.happiness.roundToInt().toLabel()).align(Align.center)
            citiesTable.row()
        }
        citiesTable.pack()
    }

}