package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.CityFlags
import com.unciv.logic.city.CityFocus
import com.unciv.logic.city.City
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.BaseScreen
import com.unciv.ui.components.ExpanderTab
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toGroup
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import kotlin.math.ceil
import kotlin.math.round
import com.unciv.ui.components.AutoScrollPane as ScrollPane

class CityStatsTable(val cityScreen: CityScreen): Table() {
    private val innerTable = Table() // table within this Table. Slightly smaller creates border
    private val upperTable = Table() // fixed position table
    private val lowerTable = Table() // table that will be in the ScrollPane
    private val lowerPane: ScrollPane
    private val cityInfo = cityScreen.city
    private val lowerCell: Cell<ScrollPane>

    private val detailedStatsButton = "Stats".toTextButton().apply {
        labelCell.pad(10f)
        onActivation {
            DetailedStatsPopup(cityScreen).open()
        }
    }

    init {
        pad(2f)
        background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityStatsTable/Background",
            tintColor = colorFromRGB(194, 180, 131)
        )

        innerTable.pad(5f)
        innerTable.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityStatsTable/InnerTable",
            tintColor = Color.BLACK.cpy().apply { a = 0.8f }
        )
        innerTable.add(upperTable).row()

        upperTable.defaults().pad(2f)
        lowerTable.defaults().pad(2f)
        lowerPane = ScrollPane(lowerTable)
        lowerPane.setOverscroll(false, false)
        lowerPane.setScrollingDisabled(true, false)
        lowerCell = innerTable.add(lowerPane)

        add(innerTable)
    }

    fun update(height: Float) {
        upperTable.clear()
        lowerTable.clear()

        val miniStatsTable = Table()
        val selected = BaseScreen.skin.get("selection", Color::class.java)
        for ((stat, amount) in cityInfo.cityStats.currentCityStats) {
            if (stat == Stat.Faith && !cityInfo.civ.gameInfo.isReligionEnabled()) continue
            val icon = Table()
            if (cityInfo.cityAIFocus.stat == stat) {
                icon.add(ImageGetter.getStatIcon(stat.name).surroundWithCircle(27f, false, color = selected))
                if (cityScreen.canCityBeChanged()) {
                    icon.onClick {
                        cityInfo.cityAIFocus = CityFocus.NoFocus
                        cityInfo.reassignPopulation(); cityScreen.update()
                    }
                }
            } else {
                icon.add(ImageGetter.getStatIcon(stat.name).surroundWithCircle(27f, false, color = Color.CLEAR))
                if (cityScreen.canCityBeChanged()) {
                    icon.onClick {
                        cityInfo.cityAIFocus = cityInfo.cityAIFocus.safeValueOf(stat)
                        cityInfo.reassignPopulation(); cityScreen.update()
                    }
                }
            }
            miniStatsTable.add(icon).size(27f).padRight(3f)
            val valueToDisplay = if (stat == Stat.Happiness) cityInfo.cityStats.happinessList.values.sum() else amount
            miniStatsTable.add(round(valueToDisplay).toInt().toLabel()).padRight(5f)
        }
        upperTable.add(miniStatsTable)

        upperTable.addSeparator()
        upperTable.add(detailedStatsButton).row()
        addText()

        // begin lowerTable
        addCitizenManagement()
        addGreatPersonPointInfo(cityInfo)
        if (!cityInfo.population.getMaxSpecialists().isEmpty()) {
            addSpecialistInfo()
        }
        if (cityInfo.religion.getNumberOfFollowers().isNotEmpty() && cityInfo.civ.gameInfo.isReligionEnabled())
            addReligionInfo()

        addBuildingsInfo()

        upperTable.pack()
        lowerTable.pack()
        lowerPane.layout()
        lowerPane.updateVisualScroll()
        lowerCell.maxHeight(height - upperTable.height - 8f) // 2 on each side of each cell in innerTable

        innerTable.pack()  // update innerTable
        pack()  // update self last
    }

    private fun onContentResize() {
        pack()
        setPosition(
            stage.width - CityScreen.posFromEdge,
            stage.height - CityScreen.posFromEdge,
            Align.topRight
        )
    }

    private fun addText() {
        val unassignedPopString = "{Unassigned population}: ".tr() +
                cityInfo.population.getFreePopulation().toString() + "/" + cityInfo.population.population
        val unassignedPopLabel = unassignedPopString.toLabel()
        if (cityScreen.canChangeState)
            unassignedPopLabel.onClick { cityInfo.reassignPopulation(); cityScreen.update() }

        var turnsToExpansionString =
                if (cityInfo.cityStats.currentCityStats.culture > 0 && cityInfo.expansion.getChoosableTiles().any()) {
                    val remainingCulture = cityInfo.expansion.getCultureToNextTile() - cityInfo.expansion.cultureStored
                    var turnsToExpansion = ceil(remainingCulture / cityInfo.cityStats.currentCityStats.culture).toInt()
                    if (turnsToExpansion < 1) turnsToExpansion = 1
                    "[$turnsToExpansion] turns to expansion".tr()
                } else "Stopped expansion".tr()
        if (cityInfo.expansion.getChoosableTiles().any())
            turnsToExpansionString +=
                    " (${cityInfo.expansion.cultureStored}${Fonts.culture}/${cityInfo.expansion.getCultureToNextTile()}${Fonts.culture})"

        var turnsToPopString =
                when {
                    cityInfo.isStarving() -> "[${cityInfo.population.getNumTurnsToStarvation()}] turns to lose population"
                    cityInfo.getRuleset().units[cityInfo.cityConstructions.currentConstructionFromQueue]
                        .let { it != null && it.hasUnique(UniqueType.ConvertFoodToProductionWhenConstructed) }
                    -> "Food converts to production"
                    cityInfo.isGrowing() -> "[${cityInfo.population.getNumTurnsToNewPopulation()}] turns to new population"
                    else -> "Stopped population growth"
                }.tr()
        turnsToPopString += " (${cityInfo.population.foodStored}${Fonts.food}/${cityInfo.population.getFoodToNextPopulation()}${Fonts.food})"

        upperTable.add(unassignedPopLabel).row()
        upperTable.add(turnsToExpansionString.toLabel()).row()
        upperTable.add(turnsToPopString.toLabel()).row()

        val tableWithIcons = Table()
        tableWithIcons.defaults().pad(2f)
        if (cityInfo.isInResistance()) {
            tableWithIcons.add(ImageGetter.getImage("StatIcons/Resistance")).size(20f)
            tableWithIcons.add("In resistance for another [${cityInfo.getFlag(CityFlags.Resistance)}] turns".toLabel()).row()
        }

        val (wltkIcon: Actor?, wltkLabel: Label?) = when {
            cityInfo.isWeLoveTheKingDayActive() ->
                ImageGetter.getStatIcon("Food") to
                "We Love The King Day for another [${cityInfo.getFlag(CityFlags.WeLoveTheKing)}] turns".toLabel(Color.LIME)
            cityInfo.demandedResource.isNotEmpty() ->
                ImageGetter.getResourcePortrait(cityInfo.demandedResource, 20f) to
                "Demanding [${cityInfo.demandedResource}]".toLabel(Color.CORAL)
            else -> null to null
        }
        if (wltkLabel != null) {
            tableWithIcons.add(wltkIcon!!).size(20f).padRight(5f)
            wltkLabel.onClick {
                UncivGame.Current.pushScreen(CivilopediaScreen(cityInfo.getRuleset(), link = "We Love The King Day"))
            }
            tableWithIcons.add(wltkLabel).row()
        }

        upperTable.add(tableWithIcons).row()
    }

    private fun addCitizenManagement() {
        val expanderTab = CitizenManagementTable(cityScreen).asExpander { onContentResize() }
        lowerTable.add(expanderTab).growX().row()
    }

    private fun addSpecialistInfo() {
        val expanderTab = SpecialistAllocationTable(cityScreen).asExpander { onContentResize() }
        lowerTable.add(expanderTab).growX().row()
    }

    private fun addReligionInfo() {
        val expanderTab = CityReligionInfoTable(cityInfo.religion).asExpander { onContentResize() }
        lowerTable.add(expanderTab).growX().row()
    }

    private fun addBuildingsInfo() {
        val wonders = mutableListOf<Building>()
        val specialistBuildings = mutableListOf<Building>()
        val otherBuildings = mutableListOf<Building>()

        for (building in cityInfo.cityConstructions.getBuiltBuildings()) {
            when {
                building.isAnyWonder() -> wonders.add(building)
                !building.newSpecialists().isEmpty() -> specialistBuildings.add(building)
                else -> otherBuildings.add(building)
            }
        }

        // Buildings sorted alphabetically
        wonders.sortBy { it.name }
        specialistBuildings.sortBy { it.name }
        otherBuildings.sortBy { it.name }

        val totalTable = Table()
        lowerTable.addCategory("Buildings", totalTable, false)

        if (specialistBuildings.isNotEmpty()) {
            val specialistBuildingsTable = Table()
            totalTable.add().row()
            totalTable.addSeparator(color = Color.LIGHT_GRAY)
            totalTable.add("Specialist Buildings".toLabel().apply { setAlignment(Align.center) }).growX()
            totalTable.addSeparator(color = Color.LIGHT_GRAY)
            for (building in specialistBuildings) addBuildingButton(building, specialistBuildingsTable)
            totalTable.add(specialistBuildingsTable).growX().right().row()
        }

        if (wonders.isNotEmpty()) {
            val wondersTable = Table()
            totalTable.addSeparator(color = Color.LIGHT_GRAY)
            totalTable.add("Wonders".toLabel().apply { setAlignment(Align.center) }).growX()
            totalTable.addSeparator(color = Color.LIGHT_GRAY)
            for (building in wonders) addBuildingButton(building, wondersTable)
            totalTable.add(wondersTable).growX().right().row()
        }

        if (otherBuildings.isNotEmpty()) {
            val regularBuildingsTable = Table()
            totalTable.addSeparator(color = Color.LIGHT_GRAY)
            totalTable.add("Other".toLabel().apply { setAlignment(Align.center) }).growX()
            totalTable.addSeparator(color = Color.LIGHT_GRAY)
            for (building in otherBuildings) addBuildingButton(building, regularBuildingsTable)
            totalTable.add(regularBuildingsTable).growX().right().row()
        }
    }

    private fun addBuildingButton(building: Building, destinationTable: Table) {

        val button = Table()

        val info = Table()
        val statsAndSpecialists = Table()

        val icon = ImageGetter.getConstructionPortrait(building.name, 50f)
        val isFree = building.name in cityScreen.city.civ.civConstructions.getFreeBuildings(cityScreen.city.id)
        val displayName = if (isFree) "{${building.name}} ({Free})" else building.name

        info.add(displayName.toLabel(fontSize = Constants.defaultFontSize)).padBottom(5f).right().row()

        val stats = building.getStats(cityInfo).joinToString(separator = " ") {
            "" + it.value.toInt() + it.key.character
        }
        statsAndSpecialists.add(stats.toLabel(fontSize = Constants.defaultFontSize)).right()

        val assignedSpec = cityInfo.population.getNewSpecialists().clone()

        val specialistIcons = Table()
        for ((specialistName, amount) in building.newSpecialists()) {
            val specialist = cityInfo.getRuleset().specialists[specialistName]
                ?: continue // probably a mod that doesn't have the specialist defined yet
            for (i in 0 until amount) {
                if (assignedSpec[specialistName]!! > 0) {
                    specialistIcons.add(ImageGetter.getSpecialistIcon(specialist.colorObject))
                        .size(20f)
                    assignedSpec.add(specialistName, -1)
                } else {
                    specialistIcons.add(ImageGetter.getSpecialistIcon(Color.GRAY)).size(20f)
                }
            }
        }
        statsAndSpecialists.add(specialistIcons).right()

        info.add(statsAndSpecialists).right()

        button.add(info).right().top().padRight(10f).padTop(5f)
        button.add(icon).right()

        button.onClick {
            cityScreen.selectConstruction(building)
            cityScreen.update()
        }

        destinationTable.add(button).pad(1f).padBottom(2f).padTop(2f).expandX().right().row()
    }

    private fun Table.addCategory(category: String, showHideTable: Table, startsOpened: Boolean = true, innerPadding: Float = 10f) : ExpanderTab {
        val expanderTab = ExpanderTab(
            title = category,
            fontSize = Constants.defaultFontSize,
            persistenceID = "CityInfo.$category",
            startsOutOpened = startsOpened,
            defaultPad = innerPadding,
            onChange = { onContentResize() }
        ) {
            it.add(showHideTable).fillX().right()
        }
        add(expanderTab).growX().row()
        return expanderTab
    }

    private fun addGreatPersonPointInfo(city: City) {

        val greatPeopleTable = Table()

        val greatPersonPoints = city.getGreatPersonPointsForNextTurn()
        val allGreatPersonNames = greatPersonPoints.asSequence().flatMap { it.value.keys }.distinct()

        if (allGreatPersonNames.none())
            return

        for (greatPersonName in allGreatPersonNames) {

            var gppPerTurn = 0

            for ((_, gppCounter) in greatPersonPoints) {
                val gppPointsFromSource = gppCounter[greatPersonName]!!
                if (gppPointsFromSource == 0) continue
                gppPerTurn += gppPointsFromSource
            }

            val info = Table()

            info.add(ImageGetter.getUnitIcon(greatPersonName, Color.GOLD).toGroup(20f))
                .left().padBottom(4f).padRight(5f)
            info.add("{$greatPersonName} (+$gppPerTurn)".toLabel()).left().padBottom(4f).expandX().row()

            val gppCurrent = city.civ.greatPeople.greatPersonPointsCounter[greatPersonName]
            val gppNeeded = city.civ.greatPeople.getPointsRequiredForGreatPerson()

            val percent = gppCurrent!! / gppNeeded.toFloat()

            val progressBar = ImageGetter.ProgressBar(300f, 25f, false)
            progressBar.setBackground(Color.BLACK.cpy().apply { a = 0.8f })
            progressBar.setProgress(Color.ORANGE, percent)
            progressBar.apply {
                val bar = ImageGetter.getWhiteDot()
                bar.color = Color.GRAY
                bar.setSize(width+5f, height+5f)
                bar.center(this)
                addActor(bar)
                bar.toBack()
            }
            progressBar.setLabel(Color.WHITE, "$gppCurrent/$gppNeeded", fontSize = 14)

            info.add(progressBar).colspan(2).left().expandX().row()

            greatPeopleTable.add(info).growX().top().padBottom(10f)
            greatPeopleTable.add(ImageGetter.getConstructionPortrait(greatPersonName, 50f)).row()
        }

        lowerTable.addCategory("Great People", greatPeopleTable)
    }

}
