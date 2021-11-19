package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.GreatPersonManager
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.roundToInt

class StatsOverviewTable (
    private val viewingPlayer: CivilizationInfo,
    private val overviewScreen: EmpireOverviewScreen
) : Table() {
    //val game = overviewScreen.game

    init {
        defaults().pad(40f)
        add(getHappinessTable()).top()
        add(getGoldTable()).top()
        add(getScienceTable()).top()
        add(getGreatPeopleTable()).top()
    }

    private fun getHappinessTable(): Table {
        val happinessTable = Table(BaseScreen.skin)
        happinessTable.defaults().pad(5f)
        val happinessHeader = Table(BaseScreen.skin)
        happinessHeader.add(ImageGetter.getStatIcon("Happiness")).pad(5f,0f,5f,12f).size(20f)
        happinessHeader.add("Happiness".toLabel(fontSize = 24)).padTop(5f)
        happinessTable.add(happinessHeader).colspan(2).row()
        happinessTable.addSeparator()

        val happinessBreakdown = viewingPlayer.stats().getHappinessBreakdown()

        for (entry in happinessBreakdown.filterNot { it.value.roundToInt()==0 }) {
            happinessTable.add(entry.key.tr())
            happinessTable.add(entry.value.roundToInt().toString()).right().row()
        }
        happinessTable.add("Total".tr())
        happinessTable.add(happinessBreakdown.values.sum().roundToInt().toString()).right()
        happinessTable.pack()
        return happinessTable
    }

    private fun getGoldTable(): Table {
        val goldTable = Table(BaseScreen.skin)
        goldTable.defaults().pad(5f)
        val goldHeader = Table(BaseScreen.skin)
        goldHeader.add(ImageGetter.getStatIcon("Gold")).pad(5f, 0f, 5f, 12f).size(20f)
        goldHeader.add("Gold".toLabel(fontSize = 24)).padTop(5f)
        goldTable.add(goldHeader).colspan(2).row()
        goldTable.addSeparator()
        var total = 0f
        for (entry in viewingPlayer.stats().getStatMapForNextTurn()) {
            if (entry.value.gold == 0f) continue
            goldTable.add(entry.key.tr())
            goldTable.add(entry.value.gold.roundToInt().toString()).right().row()
            total += entry.value.gold
        }
        goldTable.add("Total".tr())
        goldTable.add(total.roundToInt().toString()).right()

        if (viewingPlayer.gameInfo.ruleSet.modOptions.uniques.contains(ModOptionsConstants.convertGoldToScience)) {
            goldTable.addSeparator()
            val sliderTable = Table()
            sliderTable.add("Convert gold to science".toLabel()).row()
            val slider = Slider(0f, 1f, 0.1f, false, BaseScreen.skin)
            slider.value = viewingPlayer.tech.goldPercentConvertedToScience

            slider.onChange {
                viewingPlayer.tech.goldPercentConvertedToScience = slider.value
                viewingPlayer.cities.forEach { it.cityStats.update() }
                overviewScreen.setCategoryActions["Stats"]!!()      // ? will probably steal focus and so prevent dragging the slider
            }
            sliderTable.add(slider)
            goldTable.add(sliderTable).colspan(2)
        }

        goldTable.pack()
        return goldTable
    }

    private fun getScienceTable(): Table {
        val scienceTable = Table(BaseScreen.skin)
        scienceTable.defaults().pad(5f)
        val scienceHeader = Table(BaseScreen.skin)
        scienceHeader.add(ImageGetter.getStatIcon("Science")).pad(5f,0f,5f,12f).size(20f)
        scienceHeader.add("Science".toLabel(fontSize = 24)).padTop(5f)
        scienceTable.add(scienceHeader).colspan(2).row()
        scienceTable.addSeparator()
        val scienceStats = viewingPlayer.stats().getStatMapForNextTurn()
            .filter { it.value.science != 0f }
        for (entry in scienceStats) {
            scienceTable.add(entry.key.tr())
            scienceTable.add(entry.value.science.roundToInt().toString()).right().row()
        }
        scienceTable.add("Total".tr())
        scienceTable.add(scienceStats.map { it.value.science }.sum().roundToInt().toString()).right()
        scienceTable.pack()
        return scienceTable
    }

    private fun getGreatPeopleTable(): Table {
        val greatPeopleTable = Table(BaseScreen.skin)

        greatPeopleTable.defaults().pad(5f)
        val greatPeopleHeader = Table(BaseScreen.skin)
        val greatPeopleIcon = ImageGetter.getStatIcon("Specialist")
        greatPeopleIcon.color = Color.ROYAL
        greatPeopleHeader.add(greatPeopleIcon).padRight(12f).size(30f)
        greatPeopleHeader.add("Great person points".toLabel(fontSize = 24)).padTop(5f)
        greatPeopleTable.add(greatPeopleHeader).colspan(3).row()
        greatPeopleTable.addSeparator()
        greatPeopleTable.add()
        greatPeopleTable.add("Current points".tr())
        greatPeopleTable.add("Points per turn".tr()).row()

        val greatPersonPoints = viewingPlayer.greatPeople.greatPersonPointsCounter
        val greatPersonPointsPerTurn = viewingPlayer.getGreatPersonPointsForNextTurn()
        val pointsToGreatPerson = viewingPlayer.greatPeople.pointsForNextGreatPerson

        for((greatPerson, points) in greatPersonPoints) {
            greatPeopleTable.add(greatPerson.tr())
            greatPeopleTable.add("$points/$pointsToGreatPerson")
            greatPeopleTable.add(greatPersonPointsPerTurn[greatPerson].toString()).row()
        }
        val pointsForGreatGeneral = viewingPlayer.greatPeople.greatGeneralPoints
        val pointsForNextGreatGeneral = viewingPlayer.greatPeople.pointsForNextGreatGeneral
        greatPeopleTable.add("Great General".tr())
        greatPeopleTable.add("$pointsForGreatGeneral/$pointsForNextGreatGeneral").row()
        greatPeopleTable.pack()
        return greatPeopleTable
    }
}
