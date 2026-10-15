package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
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
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.view.CityView
import kotlin.math.ceil
import kotlin.math.roundToInt

class CityStatsTable(private val cityScreen: CityScreen) : Table() {
    private val cityView: CityView = cityScreen.cityView
    private val expander: ExpanderTab
    // table within this Table. Slightly smaller creates border
    private val miniStatsTable = MiniStatsTable(ExpanderTab.wasOpen("CityStatsTable"))
    private val lowerTable = Table() // table that will be in the ScrollPane
    private val lowerPane = AutoScrollPane(lowerTable)
    private var lowerCell: Cell<AutoScrollPane>? = null

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

        expander = ExpanderTab("",
            startsOutOpened = !cityScreen.isCrampedPortrait(),
            persistenceID = "CityStatsTable",
            defaultPad = 7f,
            headerPad = if (cityScreen.isCrampedPortrait()) 7f else 6f,
            topPad = 0f, // remove space between miniStatsTable and detailedStatsButton
            expanderWidth = miniStatsTable.width,
            expanderHeight = miniStatsTable.height,
            onChange = {
                cityScreen.updateWithoutConstructionAndMap()
            }
        ) {
            lowerCell = it.add(lowerPane).grow()
        }
        expander.headerContent.add(miniStatsTable).growX()
        expander.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityStatsTable/InnerTable",
            tintColor = ImageGetter.CHARCOAL.cpy().apply { a = 0.8f }
        )
        expander.header.background = null // Make header transparent
        /** Without this, the expander will keep initial header height, even when the
         *  row count of miniStatsTable changes when opening/closing in portrait mode */
        expander.setDynamicHeaderSize()
        // Don't toggle expander when clicking the stats icons
        expander.toggleOnIconOnly()

        lowerPane.setOverscroll(false, false)
        lowerPane.setScrollingDisabled(x = true, y = false)
        lowerTable.defaults().space(4f)

        add(expander).growX()
    }

    fun update(height: Float) {
        miniStatsTable.update()

        lowerTable.clear()

        lowerTable.add(detailedStatsButton).row()
        addText()

        // begin lowerTable
        addCitizenManagement()
        addGreatPersonPointInfo()
        if (!cityView.getMaxSpecialists().isEmpty()) {
            addSpecialistInfo()
        }
        if (cityView.getNumberOfFollowers().isNotEmpty() && cityView.viewingCiv().isReligionEnabled())
            addReligionInfo()

        addBuildingsInfo()

        lowerTable.pack()
        lowerPane.layout()
        lowerPane.updateVisualScroll()
        expander.header.pack() //  set header height correctly for next line
        lowerCell?.maxHeight(height - expander.header.height - 8f) // 2 on each side of each cell in expander
        expander.pack() // update expander so the scrollpane is scollable immediately without need for click
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
                cityView.getFreePopulation().tr() + "/" + cityView.getPopulationCount().tr()
        val unassignedPopLabel = unassignedPopString.toLabel()
        if (cityScreen.canChangeState)
            unassignedPopLabel.onClick { 
                cityView.tryReassignPopulation()
                cityScreen.updateAsync()
            }

        var turnsToExpansionString =
                if (cityView.getCurrentCityStats().culture > 0 && cityView.hasChoosableTiles()) {
                    val remainingCulture = cityView.getCultureToNextTile() - cityView.getCultureStored()
                    var turnsToExpansion = ceil(remainingCulture / cityView.getCurrentCityStats().culture).toInt()
                    if (turnsToExpansion < 1) turnsToExpansion = 1
                    "[$turnsToExpansion] turns to expansion".tr()
                } else "Stopped expansion".tr()
        if (cityView.hasChoosableTiles())
            turnsToExpansionString +=
                    " (${cityView.getCultureStored()}${Fonts.culture}/${cityView.getCultureToNextTile()}${Fonts.culture})"

        var turnsToPopString =
                when {
                    cityView.isStarving() -> "[${cityView.getNumTurnsToStarvation()}] turns to lose population"
                    cityView.getRuleset().units[cityView.currentConstructionName()]
                        .let { it != null && it.hasUnique(UniqueType.ConvertFoodToProductionWhenConstructed) }
                    -> "Food converts to production"
                    cityView.isGrowing() -> "[${cityView.getNumTurnsToNewPopulation()}] turns to new population"
                    else -> "Stopped population growth"
                }.tr()
        turnsToPopString += " (${cityView.getFoodStored()}${Fonts.food}/${cityView.getFoodToNextPopulation()}${Fonts.food})"

        lowerTable.add(unassignedPopLabel).row()
        lowerTable.add(turnsToExpansionString.toLabel()).row()
        lowerTable.add(turnsToPopString.toLabel()).row()

        val tableWithIcons = Table() // Each row has a SINGLE actor
        tableWithIcons.defaults().pad(2f)
        if (cityView.isInResistance()) {
            tableWithIcons.add(Table().apply {
                add(ImageGetter.getImage("StatIcons/Resistance")).size(20f).padRight(2f)
                add("In resistance for another [${cityView.getFlag(CityFlags.Resistance)}] turns".toLabel())
            }).row()
        }

        val resourceTable = Table()
        val resourceCounter = Counter<TileResource>()

        // Supply
        for (resourceSupply in cityView.getCityResourcesAvailableToCity())
            if (resourceSupply.resource.getMatchingUniques(UniqueType.NotShownOnWorldScreen, cityView.getState()).none())
                resourceCounter.add(resourceSupply.resource, resourceSupply.amount)

        // Stockpiles
        for ((resourceName, amount) in cityView.getResourceStockpiles()) {
            val resourceObj = cityView.getRuleset().tileResources[resourceName] ?: continue
            if (resourceObj.getMatchingUniques(UniqueType.NotShownOnWorldScreen, cityView.getState()).none())
                resourceCounter.add(resourceObj, amount)
        }

        for ((resource, amount) in resourceCounter) {
            if (resource.isCityWide) {
                val resourceIcon = Table()
                resourceIcon.addTooltip(resource.name, targetAlign = Align.bottom)
                resourceIcon.onClick { cityScreen.openCivilopedia(resource.makeLink()) }
                resourceIcon.add(ImageGetter.getResourcePortrait(resource.name, 20f)).padRight(5f)
                resourceIcon.add(amount.toLabel())
                resourceTable.add(resourceIcon).apply {
                    // Only add right padding if it's not the last one in the list
                    if (resourceCounter.keys.last() != resource) padRight(10f)
                }
            }
        }

        if (resourceTable.cells.notEmpty())
            tableWithIcons.add(resourceTable)

        val (wltkIcon: Actor?, wltkLabel: Label?) = when {
            cityView.isWeLoveTheKingDayActive() ->
                ImageGetter.getStatIcon("Food") to
                "We Love The King Day for another [${cityView.getFlag(CityFlags.WeLoveTheKing)}] turns".toLabel(Color.LIME)
            cityView.demandedResource.isNotEmpty() ->
                ImageGetter.getResourcePortrait(cityView.demandedResource, 20f) to
                "Demanding [${cityView.demandedResource}]".toLabel(Color.CORAL, hideIcons = true)
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
        val expanderTab = CityReligionInfoTable(cityView).asExpander { onContentResize() }
        lowerTable.add(expanderTab).growX().row()
    }

    private fun addBuildingsInfo() {
        val wonders = mutableListOf<Building>()
        val specialistBuildings = mutableListOf<Building>()
        val otherBuildings = mutableListOf<Building>()

        for (building in cityView.getBuiltBuildings()) {
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

        val stats = cityView.getBuildingStats(building).joinToString(separator = " ") {
            "" + it.value.toInt() + it.key.character
        }
        statsAndSpecialists.add(stats.toLabel(fontSize = Constants.defaultFontSize)).right()

        if (building.newSpecialists().any()) {
            val assignedSpec = cityView.getNewSpecialists().clone()
            val specialistIcons = Table()
            for ((specialistName, amount) in building.newSpecialists()) {
                val specialist = cityView.getRuleset().specialists[specialistName]
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
        }

        info.add(statsAndSpecialists).right()

        button.add(info).right().top().padRight(10f).padTop(5f)
        button.add(icon).right()

        button.onClick {
            cityScreen.selectConstruction(building)
            cityScreen.updateAsync()
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

    private fun addGreatPersonPointInfo() {

        val greatPeopleTable = Table()

        val gppBreakdown = cityView.getGreatPersonPointsBreakdown()
        if (gppBreakdown.allNames.isEmpty())
            return
        val greatPersonPoints = gppBreakdown.sum()

        // Iterating over allNames instead of greatPersonPoints will include those where the aggregation had points but ended up zero
        for (greatPersonName in gppBreakdown.allNames) {
            val gppPerTurn = greatPersonPoints[greatPersonName]

            val info = Table()

            val greatPerson = cityView.getRuleset().units[greatPersonName] ?: continue
            info.add(ImageGetter.getUnitIcon(greatPerson, Color.GOLD).toGroup(20f))
                .left().padBottom(4f).padRight(5f)
            info.add("{$greatPersonName} (+$gppPerTurn)".toLabel(hideIcons = true)).left().padBottom(4f).expandX().row()

            val gppCurrent = cityView.viewingCiv().getGreatPersonPoints(greatPersonName)
            val gppNeeded = cityView.viewingCiv().getPointsRequiredForGreatPerson(greatPersonName)

            val percent = gppCurrent / gppNeeded.toFloat()

            val progressBar = ImageGetter.ProgressBar(300f, 25f, false)
            progressBar.setBackground(ImageGetter.CHARCOAL.cpy().apply { a = 0.8f })
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

    private inner class MiniStatsTable(wasOpen: Boolean?) : Table() {
        // Challenge: we want this measured before instantating the ExpanderTab.
        // Ergo: update() must not access expander until _after_ init
        init {
            update(wasOpen)
            pack()
        }

        fun update() = update(expander.isOpen)
        private fun update(expanderIsOpen: Boolean?) {
            clear()
            val selected = BaseScreen.skin.getColor("selection")
            for (stat in Stat.entries) {
                if (stat == Stat.Faith && !cityView.viewingCiv().isReligionEnabled()) continue
                val amount = cityView.getCurrentCityStats()[stat]
                val icon = Table()
                val focus = CityFocus.safeValueOf(stat)
                val toggledFocus = if (focus == cityView.getCityFocus()) {
                    icon.add(ImageGetter.getStatIcon(stat.name).surroundWithCircle(27f, false, color = selected))
                    CityFocus.NoFocus
                } else {
                    icon.add(ImageGetter.getStatIcon(stat.name).surroundWithCircle(27f, false, color = Color.CLEAR))
                    focus
                }
                if (cityScreen.canCityBeChanged()) {
                    icon.onActivation(binding = toggledFocus.binding) {
                        cityView.trySetCityFocus(toggledFocus)
                        cityScreen.updateAsync()
                    }
                }
                add(icon).size(27f).padRight(3f)
                val valueToDisplay = if (stat == Stat.Happiness) cityView.getHappinessList().values.sum() else amount
                add((valueToDisplay.roundToInt()).toLabel()).padRight(5f)
                if (cityScreen.isCrampedPortrait() && (expanderIsOpen == null || !expanderIsOpen) && stat == Stat.Gold) {
                    row()
                }
            }
        }
    }
}
