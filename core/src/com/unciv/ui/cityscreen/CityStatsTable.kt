package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.CityFlags
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.images.ImageGetter
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
        
        if (cityInfo.religion.getNumberOfFollowers().isNotEmpty() && cityInfo.civInfo.gameInfo.isReligionEnabled())
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
                    " (${cityInfo.expansion.cultureStored}${Fonts.culture}/${cityInfo.expansion.getCultureToNextTile()}${Fonts.culture})"

        var turnsToPopString =
                when {
                    cityInfo.getRuleset().units[cityInfo.cityConstructions.currentConstructionFromQueue]
                        .let { it != null && it.hasUnique(UniqueType.ConvertFoodToProductionWhenConstructed) }
                    -> "Food converts to production"
                    cityInfo.isGrowing() -> "[${cityInfo.getNumTurnsToNewPopulation()}] turns to new population"
                    cityInfo.isStarving() -> "[${cityInfo.getNumTurnsToStarvation()}] turns to lose population"
                    else -> "Stopped population growth"
                }.tr()
        turnsToPopString += " (${cityInfo.population.foodStored}${Fonts.food}/${cityInfo.population.getFoodToNextPopulation()}${Fonts.food})"

        innerTable.add(unassignedPopString.toLabel()).row()
        innerTable.add(turnsToExpansionString.toLabel()).row()
        innerTable.add(turnsToPopString.toLabel()).row()

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
                UncivGame.Current.setScreen(CivilopediaScreen(cityInfo.getRuleset(), cityScreen, link = "We Love The King Day"))
            }
            tableWithIcons.add(wltkLabel).row()
        }

        innerTable.add(tableWithIcons).row()
    }

    private fun addReligionInfo() {
        val expanderTab = CityReligionInfoTable(cityInfo.religion).asExpander {
            pack()
            // We have to re-anchor as our position in the city screen, otherwise it expands upwards.
            // ToDo: This probably should be refactored so its placed somewhere else in due time
            setPosition(stage.width - CityScreen.posFromEdge, stage.height - CityScreen.posFromEdge, Align.topRight)
        }
        innerTable.add(expanderTab).growX().row()
    }
}
