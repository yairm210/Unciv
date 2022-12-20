package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.CityFlags
import com.unciv.logic.city.CityFocus
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.CityStats
import com.unciv.logic.city.StatTreeNode
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ExpanderTab
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.setSize
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toGroup
import com.unciv.ui.utils.extensions.toLabel
import java.text.DecimalFormat
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class CityStatsTable(val cityScreen: CityScreen): Table() {
    private val innerTable = Table() // table within this Table. Slightly smaller creates border
    private val upperTable = Table() // fixed position table
    private val lowerTable = Table() // table that will be in the ScrollPane
    private val lowerPane: ScrollPane
    private val cityInfo = cityScreen.city
    private val lowerCell: Cell<ScrollPane>

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
            if (stat == Stat.Faith && !cityInfo.civInfo.gameInfo.isReligionEnabled()) continue
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
        addText()

        // begin lowerTable
        addCitizenManagement()
        addStatInfo()
        addGreatPersonPointInfo(cityInfo)
        if (!cityInfo.population.getMaxSpecialists().isEmpty()) {
            addSpecialistInfo()
        }
        if (cityInfo.religion.getNumberOfFollowers().isNotEmpty() && cityInfo.civInfo.gameInfo.isReligionEnabled())
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
                    cityInfo.isStarving() -> "[${cityInfo.getNumTurnsToStarvation()}] turns to lose population"
                    cityInfo.getRuleset().units[cityInfo.cityConstructions.currentConstructionFromQueue]
                        .let { it != null && it.hasUnique(UniqueType.ConvertFoodToProductionWhenConstructed) }
                    -> "Food converts to production"
                    cityInfo.isGrowing() -> "[${cityInfo.getNumTurnsToNewPopulation()}] turns to new population"
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
                ImageGetter.getResourceImage(cityInfo.demandedResource, 20f) to
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
            //totalTable.addCategory("Specialist Buildings", specialistBuildingsTable)
            totalTable.addSeparator(color = Color.LIGHT_GRAY)
            for (building in specialistBuildings) addBuildingButton(building, specialistBuildingsTable)
            totalTable.add(specialistBuildingsTable).growX().right().row()
        }

        if (wonders.isNotEmpty()) {
            val wondersTable = Table()
            totalTable.addSeparator()
            totalTable.add("Wonders".toLabel().apply { setAlignment(Align.center) }).growX()
            //totalTable.addCategory("Wonders", wondersTable)
            totalTable.addSeparator()
            for (building in wonders) addBuildingButton(building, wondersTable)
            totalTable.add(wondersTable).growX().right().row()
        }

        if (otherBuildings.isNotEmpty()) {
            val regularBuildingsTable = Table()
            totalTable.addSeparator()
            totalTable.add("Other".toLabel().apply { setAlignment(Align.center) }).growX()
            //totalTable.addCategory("Other", regularBuildingsTable)
            totalTable.addSeparator()
            for (building in otherBuildings) addBuildingButton(building, regularBuildingsTable)
            totalTable.add(regularBuildingsTable).growX().right().row()
        }
    }

    private fun addBuildingButton(building: Building, destinationTable: Table) {

        val button = Table()

        val info = Table()
        val statsAndSpecialists = Table()

        val icon = ImageGetter.getPortraitImage(building.name, 50f)
        val isFree = building.name in cityScreen.city.civInfo.civConstructions.getFreeBuildings(cityScreen.city.id)
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


    private fun addStatsToHashmap(
        statTreeNode: StatTreeNode,
        hashMap: HashMap<String, Float>,
        stat: Stat,
        showDetails: Boolean,
        indentation: Int = 0
    ) {
        for ((name, child) in statTreeNode.children) {
            val statAmount = child.totalStats[stat]
            if (statAmount == 0f) continue
            hashMap["- ".repeat(indentation) + name.tr()] = statAmount
            if (showDetails) addStatsToHashmap(child, hashMap, stat, showDetails, indentation + 1)
        }
    }

    private fun addStatInfo() {
        val cityStats = cityScreen.city.cityStats
        val showFaith = cityScreen.city.civInfo.gameInfo.isReligionEnabled()

        val totalTable = Table()
        lowerTable.addCategory("Detailed stats", totalTable, false)

        for (stat in Stat.values()) {
            if (stat == Stat.Faith && !showFaith) continue
            val statValuesTable = Table()
            statValuesTable.touchable = Touchable.enabled
            if (updateStatValuesTable(stat, cityStats, statValuesTable))
                totalTable.addCategory(stat.name, statValuesTable, false)
        }
    }

    private fun updateStatValuesTable(
        stat: Stat,
        cityStats: CityStats,
        statValuesTable: Table,
        showDetails:Boolean = false
    ) : Boolean {
        statValuesTable.clear()
        statValuesTable.defaults().pad(2f)
        statValuesTable.onClick {
            updateStatValuesTable(
                stat,
                cityStats,
                statValuesTable,
                !showDetails
            )
            onContentResize()
        }

        val relevantBaseStats = LinkedHashMap<String, Float>()

        if (stat != Stat.Happiness)
            addStatsToHashmap(cityStats.baseStatTree, relevantBaseStats, stat, showDetails)
        else relevantBaseStats.putAll(cityStats.happinessList)
        for (key in relevantBaseStats.keys.toList())
            if (relevantBaseStats[key] == 0f) relevantBaseStats.remove(key)

        if (relevantBaseStats.isEmpty())
            return false

        statValuesTable.add("Base values".toLabel(fontSize = FONT_SIZE_STAT_INFO_HEADER)).pad(4f)
            .colspan(2).row()
        var sumOfAllBaseValues = 0f
        for (entry in relevantBaseStats) {
            val specificStatValue = entry.value
            if (!entry.key.startsWith('-'))
                sumOfAllBaseValues += specificStatValue
            statValuesTable.add(entry.key.toLabel()).left()
            statValuesTable.add(specificStatValue.toOneDecimalLabel()).row()
        }
        statValuesTable.addSeparator()
        statValuesTable.add("Total".toLabel())
        statValuesTable.add(sumOfAllBaseValues.toOneDecimalLabel()).row()

        val relevantBonuses = LinkedHashMap<String, Float>()
        addStatsToHashmap(cityStats.statPercentBonusTree, relevantBonuses, stat, showDetails)

        val totalBonusStats = cityStats.statPercentBonusTree.totalStats
        if (totalBonusStats[stat] != 0f) {
            statValuesTable.add("Bonuses".toLabel(fontSize = FONT_SIZE_STAT_INFO_HEADER)).colspan(2)
                .padTop(20f).row()
            for ((source, bonusAmount) in relevantBonuses) {
                statValuesTable.add(source.toLabel()).left()
                statValuesTable.add(bonusAmount.toPercentLabel()).row() // negative bonus
            }
            statValuesTable.addSeparator()
            statValuesTable.add("Total".toLabel())
            statValuesTable.add(totalBonusStats[stat].toPercentLabel()).row() // negative bonus


            statValuesTable.add("Final".toLabel(fontSize = FONT_SIZE_STAT_INFO_HEADER)).colspan(2)
                .padTop(20f).row()
            var finalTotal = 0f
            for (entry in cityStats.finalStatList) {
                val specificStatValue = entry.value[stat]
                finalTotal += specificStatValue
                if (specificStatValue == 0f) continue
                statValuesTable.add(entry.key.toLabel())
                statValuesTable.add(specificStatValue.toOneDecimalLabel()).row()
            }
            statValuesTable.addSeparator()
            statValuesTable.add("Total".toLabel())
            statValuesTable.add(finalTotal.toOneDecimalLabel()).row()
        }

        statValuesTable.pack()

        if (stat != Stat.Happiness) {
            val toggleButton = getToggleButton(showDetails)
            statValuesTable.addActor(toggleButton)
            toggleButton.setPosition(0f, statValuesTable.height, Align.topLeft)
        }

        statValuesTable.padBottom(4f)
        return true
    }

    private fun getToggleButton(showDetails: Boolean): IconCircleGroup {
        val label = (if (showDetails) "-" else "+").toLabel()
        label.setAlignment(Align.center)
        return label
            .surroundWithCircle(25f, color = BaseScreen.skinStrings.skinConfig.baseColor)
            .surroundWithCircle(27f, false)
    }

    private fun addGreatPersonPointInfo(cityInfo: CityInfo) {

        val greatPeopleTable = Table()

        val greatPersonPoints = cityInfo.getGreatPersonPointsForNextTurn()
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
            info.add("$greatPersonName (+$gppPerTurn)".toLabel()).left().padBottom(4f).expandX().row()

            val gppCurrent = cityInfo.civInfo.greatPeople.greatPersonPointsCounter[greatPersonName]
            val gppNeeded = cityInfo.civInfo.greatPeople.pointsForNextGreatPerson

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
            greatPeopleTable.add(ImageGetter.getPortraitImage(greatPersonName, 50f)).row()
        }

        lowerTable.addCategory("Great People", greatPeopleTable)
    }

    companion object {
        private const val FONT_SIZE_STAT_INFO_HEADER = 22

        private fun Float.toPercentLabel() =
                "${if (this>0f) "+" else ""}${DecimalFormat("0.#").format(this)}%".toLabel()
        private fun Float.toOneDecimalLabel() =
                DecimalFormat("0.#").format(this).toLabel()
    }

}
