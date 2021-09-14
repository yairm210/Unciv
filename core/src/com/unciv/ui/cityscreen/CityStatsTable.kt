package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.ceil
import kotlin.math.round

class CityStatsTable(val cityScreen: CityScreen): Table() {
    private val innerTable = Table()
    private val cityInfo = cityScreen.city

    init {
        pad(2f)
        background = ImageGetter.getBackground(colorFromRGB(194, 180, 131))

        innerTable.pad(5f)
        innerTable.defaults().pad(2f)
        innerTable.background = ImageGetter.getBackground(Color.BLACK.cpy().apply { a = 0.8f })

        add(innerTable).fill()
    }

    fun update() {
        innerTable.clear()

        val miniStatsTable = Table()
        for ((stat, amount) in cityInfo.cityStats.currentCityStats) {
            if (stat == Stat.Faith && !cityInfo.civInfo.gameInfo.isReligionEnabled()) continue
            miniStatsTable.add(ImageGetter.getStatIcon(stat.name)).size(20f).padRight(5f)
            val valueToDisplay = if (stat == Stat.Happiness) cityInfo.cityStats.happinessList.values.sum() else amount
            miniStatsTable.add(round(valueToDisplay).toInt().toLabel()).padRight(10f)
        }
        innerTable.add(miniStatsTable)

        innerTable.addSeparator()
        addText()
        if (!cityInfo.population.getMaxSpecialists().isEmpty()) {
            innerTable.addSeparator()
            innerTable.add(SpecialistAllocationTable(cityScreen).apply { update() }).row()
        }
        
        if (cityInfo.religion.getNumberOfFollowers().isNotEmpty())
            addReligionInfo()

        pack()
    }

    private fun addText() {
        val unassignedPopString = "{Unassigned population}: ".tr() +
                cityInfo.population.getFreePopulation().toString() + "/" + cityInfo.population.population

        var turnsToExpansionString =
                if (cityInfo.cityStats.currentCityStats.culture > 0 && cityInfo.expansion.chooseNewTileToOwn() != null) {
                    val remainingCulture = cityInfo.expansion.getCultureToNextTile() - cityInfo.expansion.cultureStored
                    var turnsToExpansion = ceil(remainingCulture / cityInfo.cityStats.currentCityStats.culture).toInt()
                    if (turnsToExpansion < 1) turnsToExpansion = 1
                    "[$turnsToExpansion] turns to expansion".tr()
                } else "Stopped expansion".tr()
        if (cityInfo.expansion.chooseNewTileToOwn() != null)
            turnsToExpansionString +=
                    " (${cityInfo.expansion.cultureStored}/${cityInfo.expansion.getCultureToNextTile()})"

        var turnsToPopString =
                when {
                    cityInfo.isGrowing() -> "[${cityInfo.getNumTurnsToNewPopulation()}] turns to new population"
                    cityInfo.isStarving() -> "[${cityInfo.getNumTurnsToStarvation()}] turns to lose population"
                    cityInfo.getRuleset().units[cityInfo.cityConstructions.currentConstructionFromQueue]
                            .let { it != null && it.uniques.contains("Excess Food converted to Production when under construction") }
                    -> "Food converts to production"
                    else -> "Stopped population growth"
                }.tr()
        turnsToPopString += " (${cityInfo.population.foodStored}/${cityInfo.population.getFoodToNextPopulation()})"

        innerTable.add(unassignedPopString.toLabel()).row()
        innerTable.add(turnsToExpansionString.toLabel()).row()
        innerTable.add(turnsToPopString.toLabel()).row()
        if (cityInfo.isInResistance())
            innerTable.add("In resistance for another [${cityInfo.resistanceCounter}] turns".toLabel()).row()
    }

    private fun addReligionInfo() {
        val label = cityInfo.religion.getMajorityReligion()?.getReligionDisplayName()
            ?: "None"
        val icon = 
            if (label == "None") "Religion" 
            else cityInfo.religion.getMajorityReligion()!!.getIconName()
        val expanderTab =
            ExpanderTab(
                title = "Majority Religion: [$label]",
                fontSize = 18,
                icon = ImageGetter.getCircledReligionIcon(icon, 30f),
                defaultPad = 0f,
                persistenceID = "CityStatsTable.Religion",
                startsOutOpened = false,
                onChange = {
                    pack()
                    // We have to re-anchor as our position in the city screen, otherwise it expands upwards.
                    // ToDo: This probably should be refactored so its placed somewhere else in due time
                    setPosition(stage.width - CityScreen.posFromEdge, stage.height - CityScreen.posFromEdge, Align.topRight)
                }
            ) {
                if (cityInfo.religion.religionThisIsTheHolyCityOf != null) {
                    // I want this to be centered, but `.center()` doesn't seem to do anything,
                    // regardless of where I place it :(
                    it.add(
                        "Holy city of: [${cityInfo.civInfo.gameInfo.religions[cityInfo.religion.religionThisIsTheHolyCityOf!!]!!.getReligionDisplayName()}]".toLabel()
                    ).center().colspan(2).pad(5f).row()
                }
                it.add(getReligionsTable()).colspan(2).pad(5f)
            }
        
        innerTable.add(expanderTab).growX().row()
    }
    
    private fun getReligionsTable(): Table {
        val gridColor = Color.DARK_GRAY
        val religionsTable = Table(CameraStageBaseScreen.skin)
        val followers = cityInfo.religion.getNumberOfFollowers()
        val futurePressures = cityInfo.religion.getPressuresFromSurroundingCities()
        
        religionsTable.add().pad(5f)
        religionsTable.addSeparatorVertical(gridColor)
        religionsTable.add("Followers".toLabel()).pad(5f)
        religionsTable.addSeparatorVertical(gridColor)
        religionsTable.add("Pressure".toLabel()).pad(5f)
        religionsTable.row()
        religionsTable.addSeparator(gridColor)
        
        for ((religion, followerCount) in followers) {
            religionsTable.add(
                ImageGetter.getCircledReligionIcon(cityInfo.civInfo.gameInfo.religions[religion]!!.getIconName(), 30f)
            ).pad(5f)
            religionsTable.addSeparatorVertical(gridColor)
            religionsTable.add(followerCount.toLabel()).pad(5f)
            religionsTable.addSeparatorVertical(gridColor)
            if (futurePressures.containsKey(religion))
                religionsTable.add(("+ [${futurePressures[religion]!!}] pressure").toLabel()).pad(5f)
            else
                religionsTable.add()
            religionsTable.row()
        }
        
        return religionsTable
    }
}