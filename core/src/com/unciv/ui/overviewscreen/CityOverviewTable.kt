package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.city.CityFlags
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import kotlin.math.max
import kotlin.math.roundToInt

class CityOverviewTab(
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class CityTabPersistableData(
        var sortedBy: String = "City"
    ) : EmpireOverviewTabPersistableData() {
        override fun isEmpty() = sortedBy == "City"
    }

    override val persistableData = (persistedData as? CityTabPersistableData) ?: CityTabPersistableData()

    companion object {
        const val iconSize = 50f  //if you set this too low, there is a chance that the tables will be misaligned
        const val paddingVert = 5f      // vertical padding
        const val paddingHorz = 8f      // horizontal padding
    }

    private val columnsNames = arrayListOf("Population", "Food", "Gold", "Science", "Production", "Culture", "Happiness")
            .apply { if (viewingPlayer.gameInfo.isReligionEnabled()) add("Faith") }

    init {
        val numHeaderCells = columnsNames.size + 2      // +1 City +1 Filler

        val cityInfoTableIcons = Table(skin)
        val cityInfoTableDetails = Table(skin)
        val cityInfoTableTotal = Table(skin)

        fun sortOnClick(iconName: String) {
            val descending = persistableData.sortedBy == iconName
            persistableData.sortedBy = iconName
            // sort the table: clear and fill with sorted data
            cityInfoTableDetails.clear()
            fillCitiesTable(cityInfoTableDetails, iconName, descending)
            // reset to return back for ascending next time
            if (descending) persistableData.sortedBy = ""
        }

        fun addSortIcon(iconName: String, iconParam: Actor? = null) {
            val icon = iconParam ?: ImageGetter.getStatIcon(iconName)
            icon.onClick { sortOnClick(iconName) }
            cityInfoTableIcons.add(icon).size(iconSize)
        }

        // Prepare top third: cityInfoTableIcons
        cityInfoTableIcons.defaults()
            .pad(paddingVert, paddingHorz)
            .align(Align.center)
        cityInfoTableIcons.add("Cities".toLabel(fontSize = Constants.headingFontSize)).colspan(numHeaderCells).align(Align.center).row()
        val citySortIcon: IconCircleGroup = ImageGetter.getUnitIcon("Settler").surroundWithCircle(iconSize)
        addSortIcon("City", citySortIcon)
        val headerFillerCell = cityInfoTableIcons.add(Table())  // will push the first icon to left-align
        for (name in columnsNames) {
            addSortIcon(name)
        }
        val wltkSortIcon: IconCircleGroup = ImageGetter.getImage("OtherIcons/WLTK 2")
            .apply { color = Color.BLACK }
            .surroundWithCircle(iconSize, color = Color.TAN)
        wltkSortIcon.addTooltip("We Love The King Day", 18f, tipAlign = Align.center )
        addSortIcon("WLTK", wltkSortIcon)
        cityInfoTableIcons.pack()

        // Prepare middle third: cityInfoScrollPane (a ScrollPane containing cityInfoTableDetails)
        cityInfoTableDetails.defaults()
            .pad(paddingVert, paddingHorz)
            .minWidth(iconSize)     //we need the min width so we can align the different tables
            .align(Align.left)

        fillCitiesTable(cityInfoTableDetails, persistableData.sortedBy, false)

        val cityInfoScrollPane = AutoScrollPane(cityInfoTableDetails)
        cityInfoScrollPane.pack()
        cityInfoScrollPane.setOverscroll(false, false) //I think it feels better with no overscroll

        // place the button for sorting by city name on top of the cities names
        // by sizing the filler to: subtract width of other columns and one cell padding from overall width
        val headingFillerWidth = max(0f, cityInfoTableDetails.width - (iconSize + 2*paddingHorz) * numHeaderCells - 2*paddingHorz)
        headerFillerCell.width(headingFillerWidth)
        cityInfoTableIcons.width = cityInfoTableDetails.width

        // Prepare bottom third: cityInfoTableTotal
        cityInfoTableTotal.defaults()
            .pad(paddingVert, paddingHorz)
            .minWidth(iconSize) //we need the min width so we can align the different tables

        cityInfoTableTotal.add("Total".toLabel())
        cityInfoTableTotal.add(viewingPlayer.cities.sumOf { it.population.population }.toString().toLabel()).myAlign(Align.center)
        for (column in columnsNames.filter { it.isStat() }) {
            val stat = Stat.valueOf(column)
            if (stat == Stat.Food || stat == Stat.Production) cityInfoTableTotal.add() // an intended empty space
            else cityInfoTableTotal.add(viewingPlayer.cities.sumOf { getStatOfCity(it, stat) }.toLabel()).myAlign(Align.center)
        }
        cityInfoTableTotal.add(viewingPlayer.cities.count { it.isWeLoveTheKingDayActive() }.toLabel()).myAlign(Align.center)
        cityInfoTableTotal.pack()

        // Stack cityInfoTableIcons, cityInfoScrollPane, and cityInfoTableTotal vertically
        val table = Table(skin)
        //since the names of the cities are on the left, and the length of the names varies
        //we align every row to the right, coz we set the size of the other(number) cells to the image size
        //and thus, we can guarantee that the tables will be aligned
        table.defaults().pad(paddingVert).align(Align.right)
        table.add(cityInfoTableIcons).row()
        table.add(cityInfoScrollPane).width(cityInfoTableDetails.width).row()
        table.add(cityInfoTableTotal)
        table.pack()
        add(table)
    }

    private fun getStatOfCity(cityInfo: CityInfo, stat: Stat): Int {
        return if (stat == Stat.Happiness)
             cityInfo.cityStats.happinessList.values.sum().roundToInt()
        else cityInfo.cityStats.currentCityStats[stat].roundToInt()
    }

    private fun fillCitiesTable(citiesTable: Table, sortType: String, descending: Boolean) {
        if (viewingPlayer.cities.isEmpty()) return

        val sorter = Comparator { city2, city1: CityInfo ->
            when {
                sortType == "Population" -> city1.population.population - city2.population.population
                sortType.isStat() -> {
                    val stat = Stat.valueOf(sortType)
                    getStatOfCity(city1, stat) - getStatOfCity(city2, stat)
                }
                sortType == "WLTK" -> city1.isWeLoveTheKingDayActive().compareTo(city2.isWeLoveTheKingDayActive())
                else -> city2.name.tr().compareTo(city1.name.tr())
            }
        }

        var cityList = viewingPlayer.cities.sortedWith(sorter)

        if (descending)
            cityList = cityList.reversed()

        val constructionCells: MutableList<Cell<Label>> = mutableListOf()
        for (city in cityList) {
            val button = city.name.toTextButton()
            button.onClick {
                overviewScreen.game.setScreen(CityScreen(city))
            }
            citiesTable.add(button)

            if (city.cityConstructions.currentConstructionFromQueue.isNotEmpty()) {
                citiesTable.add(ImageGetter.getConstructionImage(city.cityConstructions.currentConstructionFromQueue).surroundWithCircle(iconSize*0.8f)).padRight(paddingHorz)
            } else {
                citiesTable.add()
            }

            val cell = citiesTable.add(city.cityConstructions.getCityProductionTextForCityButton().toLabel())
            constructionCells.add(cell)

            citiesTable.add(city.population.population.toLabel()).myAlign(Align.center)
            for (column in columnsNames) {
                if (!column.isStat()) continue
                citiesTable.add(getStatOfCity(city, Stat.valueOf(column)).toLabel()).myAlign(Align.center)
            }

            when {
                city.isWeLoveTheKingDayActive() -> {
                    val image = ImageGetter.getImage("OtherIcons/WLTK 1").surroundWithCircle(iconSize, color = Color.CLEAR)
                    image.addTooltip("[${city.getFlag(CityFlags.WeLoveTheKing)}] turns", 18f, tipAlign = Align.topLeft)
                    citiesTable.add(image)
                }
                city.demandedResource.isNotEmpty() -> {
                    val image = ImageGetter.getResourceImage(city.demandedResource, iconSize*0.7f)
                    image.addTooltip("Demanding [${city.demandedResource}]", 18f, tipAlign = Align.topLeft)
                    citiesTable.add(image).padLeft(iconSize*0.3f)
                }
                else -> citiesTable.add()
            }

            citiesTable.row()
        }

        // row heights may diverge - fix it by setting minHeight to
        // largest actual height (of the construction cell) - !! guarded by isEmpty test above
        val largestLabelHeight = constructionCells.maxByOrNull{ it.prefHeight }!!.prefHeight
        constructionCells.forEach{ it.minHeight(largestLabelHeight ) }

        citiesTable.pack()
    }

    private fun String.isStat() = Stat.values().any { it.name == this }

    // Helper to prettify converting Cell.align() to the Cell's actor's .align()
    private fun Cell<Label>.myAlign(align: Int): Cell<Label> {
        (this.actor as Label).setAlignment(align)
        return this
    }
}
