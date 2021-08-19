package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
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
        for ((stat, amount) in cityInfo.cityStats.currentCityStats.toHashMap()) {
            if (stat == Stat.Faith && !cityInfo.civInfo.gameInfo.hasReligionEnabled()) continue
            miniStatsTable.add(ImageGetter.getStatIcon(stat.name)).size(20f).padRight(5f)
            val valueToDisplay = if (stat == Stat.Happiness) cityInfo.cityStats.happinessList.values.sum() else amount
            miniStatsTable.add(round(valueToDisplay).toInt().toLabel()).padRight(10f)
        }
        innerTable.add(miniStatsTable)

        innerTable.addSeparator()
        addText()
        if (!cityInfo.population.getMaxSpecialists().isEmpty()) {
            innerTable.addSeparator()
            innerTable.add(SpecialistAllocationTable(cityScreen).apply { update() })
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
        // This will later become large enough to be its own class, but for now it is small enough to fit inside a single function
        innerTable.addSeparator()
        val label = cityInfo.religion.getMajorityReligion()
            ?: "None"
        innerTable.add("Majority Religion: [$label]".toLabel()).pad(5f).row()
        
        innerTable.add(addReligionsTable())
    }
    
    private fun addReligionsTable(): Table {
        val religionsTable = Table(CameraStageBaseScreen.skin)
        val followers = cityInfo.religion.getNumberOfFollowers()
        val futurePressures = cityInfo.religion.getPressuresFromSurroundingCities()
        
        religionsTable.add("Religion").pad(5f)
        religionsTable.addSeparatorVertical()
        val followerImage = ImageGetter.getReligionIcon("Follower")
            .apply { color = Color.BLACK }
        religionsTable.add(followerImage.surroundWithCircle(30f)).pad(5f)
        religionsTable.addSeparatorVertical()
        religionsTable.add("Pressure").pad(5f)
        religionsTable.row()
        religionsTable.addSeparator()
        
        for ((religion, followerCount) in followers) {
            val religionImage = ImageGetter.getReligionIcon(cityInfo.civInfo.gameInfo.religions[religion]!!.iconName)
                .apply { color = Color.BLACK }
            religionsTable.add(religionImage.surroundWithCircle(30f)).pad(5f)
            religionsTable.addSeparatorVertical()
            religionsTable.add(followerCount.toLabel()).pad(5f)
            religionsTable.addSeparatorVertical()
            if (futurePressures.containsKey(religion))
                religionsTable.add(("+ [${futurePressures[religion]!!}] pressure").toLabel()).pad(5f)
            else
                religionsTable.add()
            religionsTable.row()
        }
        
        
        return religionsTable
    }
}