package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.CityFlags
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.pad
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import kotlin.math.roundToInt

class CityOverviewTab(
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class CityTabPersistableData(
        var sortedBy: String = CITY,
        var descending: Boolean = false
    ) : EmpireOverviewTabPersistableData() {
        override fun isEmpty() = sortedBy == CITY
    }

    override val persistableData = (persistedData as? CityTabPersistableData) ?: CityTabPersistableData()

    companion object {
        const val iconSize = 50f  //if you set this too low, there is a chance that the tables will be misaligned
        const val paddingVert = 5f      // vertical padding
        const val paddingHorz = 8f      // horizontal padding

        private const val CITY = "City"
        private const val WLTK = "WLTK"
        private const val CONSTRUCTION = "Construction"
        private val alphabeticColumns = listOf(CITY, CONSTRUCTION, WLTK)

        private val citySortIcon = ImageGetter.getUnitIcon("Settler")
            .surroundWithCircle(iconSize)
            .apply { addTooltip("Name", 18f, tipAlign = Align.center) }
        private val wltkSortIcon = ImageGetter.getImage("OtherIcons/WLTK 2")
            .apply { color = Color.BLACK }
            .surroundWithCircle(iconSize, color = Color.TAN)
            .apply { addTooltip("We Love The King Day", 18f, tipAlign = Align.center) }
        private val constructionSortIcon = ImageGetter.getImage("OtherIcons/Settings")
            .apply { color = Color.BLACK }
            .surroundWithCircle(iconSize, color = Color.LIGHT_GRAY)
            .apply { addTooltip("Current construction", 18f, tipAlign = Align.center) }

        // Readability helpers
        private fun String.isStat() = Stat.isStat(this)

        private fun CityInfo.getStat(stat: Stat) =
            if (stat == Stat.Happiness)
                cityStats.happinessList.values.sum().roundToInt()
            else cityStats.currentCityStats[stat].roundToInt()

        private fun Int.toCenteredLabel(): Label =
            this.toLabel().apply { setAlignment(Align.center) }
    }

    private val columnsNames = arrayListOf("Population", "Food", "Gold", "Science", "Production", "Culture", "Happiness")
            .apply { if (gameInfo.isReligionEnabled()) add("Faith") }

    private val cityInfoTableHeader = Table(skin)
    private val cityInfoTableDetails = Table(skin)
    private val cityInfoTableTotal = Table(skin)

    private val collator = UncivGame.Current.settings.getCollatorFromLocale()

    override fun getFixedContent() = Table().apply {
        add("Cities".toLabel(fontSize = Constants.headingFontSize)).padTop(10f).row()
        add(cityInfoTableHeader).padBottom(paddingVert).row()
        addSeparator(Color.GRAY)
    }

    init {
        cityInfoTableHeader.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)
        cityInfoTableDetails.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)
        cityInfoTableTotal.defaults().pad(paddingVert, paddingHorz).minWidth(iconSize)

        updateTotal()
        update()

        top()
        add(cityInfoTableDetails).row()
        addSeparator(Color.GRAY).pad(paddingVert, 0f)
        add(cityInfoTableTotal)
    }

    fun toggleSort(sortBy: String) {
        if (sortBy == persistableData.sortedBy) {
            persistableData.descending = !persistableData.descending
        } else {
            persistableData.sortedBy = sortBy
            persistableData.descending = sortBy !in alphabeticColumns  // Start numeric columns descending
        }
    }

    fun getComparator() = Comparator { city2: CityInfo, city1: CityInfo ->
        when(persistableData.sortedBy) {
            CITY -> collator.compare(city2.name.tr(), city1.name.tr())
            CONSTRUCTION -> collator.compare(
                city2.cityConstructions.currentConstructionFromQueue.tr(),
                city1.cityConstructions.currentConstructionFromQueue.tr())
            "Population" -> city2.population.population - city1.population.population
            WLTK -> city2.isWeLoveTheKingDayActive().compareTo(city1.isWeLoveTheKingDayActive())
            else -> {
                val stat = Stat.safeValueOf(persistableData.sortedBy)!!
                city2.getStat(stat) - city1.getStat(stat)
            }
        }
    }

    private fun getSortSymbol() = if(persistableData.descending) "￬" else "￪"

    private fun update() {
        updateHeader()
        updateCities()
        equalizeColumns(cityInfoTableDetails, cityInfoTableHeader, cityInfoTableTotal)
        layout()
    }

    private fun updateHeader() {
        fun sortOnClick(sortBy: String) {
            toggleSort(sortBy)
            // sort the table: clear and fill with sorted data
            update()
        }

        fun addSortIcon(iconName: String, iconParam: Actor? = null): Cell<Group> {
            val image = iconParam ?: ImageGetter.getStatIcon(iconName)

            val icon = Group().apply {
                isTransform = false
                setSize(iconSize, iconSize)
                image.setSize(iconSize, iconSize)
                image.center(this)
                image.setOrigin(Align.center)
                onClick { sortOnClick(iconName) }
            }
            if (iconName == persistableData.sortedBy) {
                val label = getSortSymbol().toLabel()
                label.setOrigin(Align.bottomRight)
                label.setPosition(iconSize - 2f, 0f)
                icon.addActor(label)
            }
            icon.addActor(image)
            return cityInfoTableHeader.add(icon).size(iconSize)
        }

        cityInfoTableHeader.clear()
        addSortIcon(CITY, citySortIcon).left()
        cityInfoTableHeader.add()  // construction _icon_ column
        addSortIcon(CONSTRUCTION, constructionSortIcon).left()
        for (name in columnsNames) {
            addSortIcon(name)
        }
        addSortIcon(WLTK, wltkSortIcon)
        cityInfoTableHeader.pack()
    }

    private fun updateCities() {
        cityInfoTableDetails.clear()
        if (viewingPlayer.cities.isEmpty()) return

        val sorter = getComparator()
        var cityList = viewingPlayer.cities.sortedWith(sorter)
        if (persistableData.descending)
            cityList = cityList.reversed()

        val constructionCells: MutableList<Cell<Label>> = mutableListOf()
        for (city in cityList) {
            val button = city.name.toTextButton()
            button.onClick {
                overviewScreen.game.pushScreen(CityScreen(city))
            }
            cityInfoTableDetails.add(button).left().fillX()

            val construction = city.cityConstructions.currentConstructionFromQueue
            if (construction.isNotEmpty()) {
                cityInfoTableDetails.add(ImageGetter.getConstructionImage(construction).surroundWithCircle(iconSize*0.8f)).padRight(paddingHorz)
            } else {
                cityInfoTableDetails.add()
            }

            val cell = cityInfoTableDetails.add(city.cityConstructions.getCityProductionTextForCityButton().toLabel()).left().expandX()
            constructionCells.add(cell)

            cityInfoTableDetails.add(city.population.population.toCenteredLabel())

            for (column in columnsNames) {
                val stat = Stat.safeValueOf(column) ?: continue
                cityInfoTableDetails.add(city.getStat(stat).toCenteredLabel())
            }

            when {
                city.isWeLoveTheKingDayActive() -> {
                    val image = ImageGetter.getImage("OtherIcons/WLTK 1").surroundWithCircle(iconSize, color = Color.CLEAR)
                    image.addTooltip("[${city.getFlag(CityFlags.WeLoveTheKing)}] turns", 18f, tipAlign = Align.topLeft)
                    cityInfoTableDetails.add(image)
                }
                city.demandedResource.isNotEmpty() -> {
                    val image = ImageGetter.getResourceImage(city.demandedResource, iconSize*0.7f)
                    image.addTooltip("Demanding [${city.demandedResource}]", 18f, tipAlign = Align.topLeft)
                    cityInfoTableDetails.add(image).padLeft(iconSize*0.3f)
                }
                else -> cityInfoTableDetails.add()
            }

            cityInfoTableDetails.row()
        }

        // row heights may diverge - fix it by setting minHeight to
        // largest actual height (of the construction cell) - !! guarded by isEmpty test above
        val largestLabelHeight = constructionCells.maxByOrNull{ it.prefHeight }!!.prefHeight
        for (cell in constructionCells) cell.minHeight(largestLabelHeight)

        cityInfoTableDetails.pack()
    }

    private fun updateTotal() {
        cityInfoTableTotal.add("Total".toLabel()).left()
        cityInfoTableTotal.add()  // construction icon column
        cityInfoTableTotal.add().expandX()  // construction label column
        cityInfoTableTotal.add(viewingPlayer.cities.sumOf { it.population.population }.toCenteredLabel())
        for (column in columnsNames.filter { it.isStat() }) {
            val stat = Stat.valueOf(column)
            if (stat == Stat.Food || stat == Stat.Production) cityInfoTableTotal.add() // an intended empty space
            else cityInfoTableTotal.add(viewingPlayer.cities.sumOf { it.getStat(stat) }.toCenteredLabel())
        }
        cityInfoTableTotal.add(viewingPlayer.cities.count { it.isWeLoveTheKingDayActive() }.toCenteredLabel())
        cityInfoTableTotal.pack()
    }
}
