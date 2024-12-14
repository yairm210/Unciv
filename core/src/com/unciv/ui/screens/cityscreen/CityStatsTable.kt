package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.city.*
import com.unciv.models.Counter
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.*
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.math.ceil
import kotlin.math.round
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

class CityStatsTable(private val cityScreen: CityScreen) : Table() {
    private val innerTable = Table() // table within this Table. Slightly smaller creates border
    private val upperTable = Table() // fixed position table
    private val lowerTable = Table() // table that will be in the ScrollPane
    private val lowerPane: ScrollPane
    private val city = cityScreen.city
    private val headerIcon = ImageGetter.getImage("OtherIcons/BackArrow").apply {
        setSize(18f, 18f)
        setOrigin(Align.center)
        rotation = 90f
    }
    private var headerIconClickArea = Table()
    private var isOpen = true
    
    private val detailedStatsButton = "Stats".toTextButton().apply {
        labelCell.pad(10f)
        onActivation(binding = KeyboardBinding.ShowStats) {
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

        upperTable.defaults().pad(2f)
        lowerTable.defaults().pad(2f)
        lowerPane = ScrollPane(lowerTable)
        lowerPane.setOverscroll(false, false)
        lowerPane.setScrollingDisabled(true, false)

        add(innerTable).growX()

        // collapse icon with larger click area
        headerIconClickArea.add(headerIcon).size(headerIcon.width).pad(6f+2f, 12f, 6f, 2f )
        headerIconClickArea.touchable = Touchable.enabled
        headerIconClickArea.onClick {
            isOpen = !isOpen
            cityScreen.updateWithoutConstructionAndMap()
        }
    }

    fun update(height: Float) {
        upperTable.clear()
        lowerTable.clear()

        val miniStatsTable = Table()
        val selected = BaseScreen.skin.getColor("selection")
        for ((stat, amount) in city.cityStats.currentCityStats) {
            if (stat == Stat.Faith && !city.civ.gameInfo.isReligionEnabled()) continue
            val icon = Table()
            val focus = CityFocus.safeValueOf(stat)
            val toggledFocus = if (focus == city.getCityFocus()) {
                icon.add(ImageGetter.getStatIcon(stat.name).surroundWithCircle(27f, false, color = selected))
                CityFocus.NoFocus
            } else {
                icon.add(ImageGetter.getStatIcon(stat.name).surroundWithCircle(27f, false, color = Color.CLEAR))
                focus
            }
            if (cityScreen.canCityBeChanged()) {
                icon.onActivation(binding = toggledFocus.binding) {
                    city.setCityFocus(toggledFocus)
                    city.reassignPopulation()
                    cityScreen.update()
                }
            }
            miniStatsTable.add(icon).size(27f).padRight(3f)
            val valueToDisplay = if (stat == Stat.Happiness) city.cityStats.happinessList.values.sum() else amount
            miniStatsTable.add(round(valueToDisplay).toInt().toLabel()).padRight(5f)
        }
        upperTable.add(miniStatsTable).expandX()
        upperTable.addSeparator()
        
        lowerTable.add(detailedStatsButton).row()
        addText()

        // begin lowerTable
        addCitizenManagement()
        addGreatPersonPointInfo(city)
        if (!city.population.getMaxSpecialists().isEmpty()) {
            addSpecialistInfo()
        }
        if (city.religion.getNumberOfFollowers().isNotEmpty() && city.civ.gameInfo.isReligionEnabled())
            addReligionInfo()

        addBuildingsInfo()

        headerIcon.rotation = if(isOpen) 90f else 0f
        
        innerTable.clear()
        innerTable.add(upperTable).expandX()
        innerTable.add(headerIconClickArea).row()
        val lowerCell = if (isOpen) {
            innerTable.add(lowerPane).colspan(2)
        } else null

        upperTable.pack()
        lowerTable.pack()
        lowerPane.layout()
        lowerPane.updateVisualScroll()
        lowerCell?.maxHeight(height - upperTable.height - 8f) // 2 on each side of each cell in innerTable

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
                city.population.getFreePopulation().tr() + "/" + city.population.population.tr()
        val unassignedPopLabel = unassignedPopString.toLabel()
        if (cityScreen.canChangeState)
            unassignedPopLabel.onClick { city.reassignPopulation(); cityScreen.update() }

        var turnsToExpansionString =
                if (city.cityStats.currentCityStats.culture > 0 && city.expansion.getChoosableTiles().any()) {
                    val remainingCulture = city.expansion.getCultureToNextTile() - city.expansion.cultureStored
                    var turnsToExpansion = ceil(remainingCulture / city.cityStats.currentCityStats.culture).toInt()
                    if (turnsToExpansion < 1) turnsToExpansion = 1
                    "[$turnsToExpansion] turns to expansion".tr()
                } else "Stopped expansion".tr()
        if (city.expansion.getChoosableTiles().any())
            turnsToExpansionString +=
                    " (${city.expansion.cultureStored}${Fonts.culture}/${city.expansion.getCultureToNextTile()}${Fonts.culture})"

        var turnsToPopString =
                when {
                    city.isStarving() -> "[${city.population.getNumTurnsToStarvation()}] turns to lose population"
                    city.getRuleset().units[city.cityConstructions.currentConstructionFromQueue]
                        .let { it != null && it.hasUnique(UniqueType.ConvertFoodToProductionWhenConstructed) }
                    -> "Food converts to production"
                    city.isGrowing() -> "[${city.population.getNumTurnsToNewPopulation()}] turns to new population"
                    else -> "Stopped population growth"
                }.tr()
        turnsToPopString += " (${city.population.foodStored}${Fonts.food}/${city.population.getFoodToNextPopulation()}${Fonts.food})"

        lowerTable.add(unassignedPopLabel).row()
        lowerTable.add(turnsToExpansionString.toLabel()).row()
        lowerTable.add(turnsToPopString.toLabel()).row()

        val tableWithIcons = Table() // Each row has a SINGLE actor
        tableWithIcons.defaults().pad(2f)
        if (city.isInResistance()) {
            tableWithIcons.add(Table().apply {
                add(ImageGetter.getImage("StatIcons/Resistance")).size(20f).padRight(2f)
                add("In resistance for another [${city.getFlag(CityFlags.Resistance)}] turns".toLabel())
            })
        }

        val resourceTable = Table()

        val resourceCounter = Counter<TileResource>()
        for (resourceSupply in CityResources.getCityResourcesAvailableToCity(city))
            resourceCounter.add(resourceSupply.resource, resourceSupply.amount)
        for ((resource, amount) in resourceCounter)
            if (resource.isCityWide) {
                resourceTable.add(amount.toLabel())
                resourceTable.add(ImageGetter.getResourcePortrait(resource.name, 20f))
                    .padRight(5f)
                }
        if (resourceTable.cells.notEmpty())
            tableWithIcons.add(resourceTable)

        val (wltkIcon: Actor?, wltkLabel: Label?) = when {
            city.isWeLoveTheKingDayActive() ->
                ImageGetter.getStatIcon("Food") to
                "We Love The King Day for another [${city.getFlag(CityFlags.WeLoveTheKing)}] turns".toLabel(Color.LIME)
            city.demandedResource.isNotEmpty() ->
                ImageGetter.getResourcePortrait(city.demandedResource, 20f) to
                "Demanding [${city.demandedResource}]".toLabel(Color.CORAL, hideIcons = true)
            else -> null to null
        }
        if (wltkLabel != null) {
            tableWithIcons.add(Table().apply {
                add(wltkIcon!!).size(20f).padRight(5f)
                add(wltkLabel).row()
            })
            wltkLabel.onClick {
                cityScreen.openCivilopedia("Tutorial/We Love The King Day")
            }
        }

        lowerTable.add(tableWithIcons).row()
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
        val expanderTab = CityReligionInfoTable(city.religion).asExpander { onContentResize() }
        lowerTable.add(expanderTab).growX().row()
    }

    private fun addBuildingsInfo() {
        val wonders = mutableListOf<Building>()
        val specialistBuildings = mutableListOf<Building>()
        val otherBuildings = mutableListOf<Building>()

        for (building in city.cityConstructions.getBuiltBuildings()) {
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
        lowerTable.addCategory("Buildings", totalTable, KeyboardBinding.BuildingsDetail, false)

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
        val isFree = cityScreen.hasFreeBuilding(building)
        val displayName = if (isFree) "{${building.name}} ({Free})" else building.name

        info.add(displayName.toLabel(fontSize = Constants.defaultFontSize, hideIcons = true)).padBottom(5f).right().row()

        val stats = building.getStats(city).joinToString(separator = " ") {
            "" + it.value.toInt() + it.key.character
        }
        statsAndSpecialists.add(stats.toLabel(fontSize = Constants.defaultFontSize)).right()

        val assignedSpec = city.population.getNewSpecialists().clone()

        val specialistIcons = Table()
        for ((specialistName, amount) in building.newSpecialists()) {
            val specialist = city.getRuleset().specialists[specialistName]
                ?: continue // probably a mod that doesn't have the specialist defined yet
            repeat(amount) {
                if (assignedSpec[specialistName] > 0) {
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

    private fun Table.addCategory(
        category: String,
        showHideTable: Table,
        toggleKey: KeyboardBinding,
        startsOpened: Boolean = true
    ) : ExpanderTab {
        val expanderTab = ExpanderTab(
            title = category,
            fontSize = Constants.defaultFontSize,
            persistenceID = "CityInfo.$category",
            startsOutOpened = startsOpened,
            toggleKey = toggleKey,
            onChange = { onContentResize() }
        ) {
            it.add(showHideTable).fillX().right()
        }
        add(expanderTab).growX().row()
        return expanderTab
    }

    private fun addGreatPersonPointInfo(city: City) {

        val greatPeopleTable = Table()

        val gppBreakdown = GreatPersonPointsBreakdown(city)
        if (gppBreakdown.allNames.isEmpty())
            return
        val greatPersonPoints = gppBreakdown.sum()

        // Iterating over allNames instead of greatPersonPoints will include those where the aggregation had points but ended up zero
        for (greatPersonName in gppBreakdown.allNames) {
            val gppPerTurn = greatPersonPoints[greatPersonName]

            val info = Table()

            val greatPerson = city.getRuleset().units[greatPersonName] ?: continue
            info.add(ImageGetter.getUnitIcon(greatPerson, Color.GOLD).toGroup(20f))
                .left().padBottom(4f).padRight(5f)
            info.add("{$greatPersonName} (+$gppPerTurn)".toLabel(hideIcons = true)).left().padBottom(4f).expandX().row()

            val gppCurrent = city.civ.greatPeople.greatPersonPointsCounter[greatPersonName]
            val gppNeeded = city.civ.greatPeople.getPointsRequiredForGreatPerson(greatPersonName)

            val percent = gppCurrent / gppNeeded.toFloat()

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
            info.onClick {
                GreatPersonPointsBreakdownPopup(cityScreen, gppBreakdown, greatPersonName)
            }
            greatPeopleTable.add(info).growX().top().padBottom(10f)
            val icon = ImageGetter.getConstructionPortrait(greatPersonName, 50f)
            icon.onClick {
                GreatPersonPointsBreakdownPopup(cityScreen, gppBreakdown, null)
            }
            greatPeopleTable.add(icon).row()
        }

        lowerTable.addCategory("Great People", greatPeopleTable, KeyboardBinding.GreatPeopleDetail)
    }

}
